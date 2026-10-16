package com.labok.goat.data.tweaks

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.labok.goat.background.ResolutionRevertWorker
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.display.DpiManager
import com.labok.goat.data.display.ResolutionState
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.domain.DpiFixer
import com.labok.goat.domain.DpiPolicy
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 해상도 낮추기: 기본 화면(0)의 강제 해상도(wm size와 같은 setForcedDisplaySize)를 줄이고, 화면 크기가 그대로 보이도록 밀도도
 * 같은 비율로 줄인다. 게임·배터리용으로 그리는 픽셀 수를 줄이는 기능이다. 폴드는 두 패널의 강제 해상도가 어떻게 저장되는지
 * 실측하지 못해 쓰지 않는다.
 *
 * 순서(DPI 적용과 겹치지 않게 [DpiFixer.exclusive] 안에서):
 * - 적용: ① 처음이면 직전 논리 해상도·강제 여부·밀도를 [ResolutionState]에 남긴다 ② 해상도를 건다 ③ 고급 화면 배율 설정에 목표가 있으면
 *   그 경로(목표 × 비율)로, 없으면 직전 밀도 × 비율을 직접 적용한다 ④ [CONFIRM_MS] 뒤 자동 되돌림을 예약한다.
 * - 되돌리기: ① 직전에 강제 해상도가 있었으면 그 크기로, 없었으면 지운다 ② 기록을 지워 비율을 1로 ③ 목표가 있으면 그 경로로, 없으면
 *   남긴 강제 밀도로(없었으면 지움) 되돌린다.
 *
 * 확인하지 않으면 되돌린다: 1차는 앱 프로세스의 카운트다운, 2차는 WorkManager(프로세스가 죽은 경우), 3차는 부팅 수신기
 * (잠금을 풀기 전 LOCKED_BOOT_COMPLETED 포함, 기록이 기기 보호 저장소라 읽을 수 있다).
 */
object Resolution {
    /** 확인을 기다리는 시간. 이 안에 '유지'를 누르지 않으면 되돌린다. */
    const val CONFIRM_MS = 15_000L
    private const val WORK = "resolution_revert"

    /** PC로 되돌리는 명령(화면을 쓸 수 없을 때). 고급 안내 전용이라 Copy가 아닌 여기 둔다. */
    const val PC_UNDO = "adb shell wm size reset && adb shell wm density reset"

    /** 고를 수 있는 크기. [short]·[long]은 논리 해상도(짧은 변·긴 변), [percent]는 직전 크기 대비, [points]는 그리는 점(넓이) 비율. */
    data class Option(val short: Int, val long: Int, val name: String?, val percent: Int, val points: Int = 100)

    /** 짧은 변 [short]일 때 기준 짧은 변 [base] 대비 그리는 점 비율(%). 화면비가 같으므로 변 비율의 제곱이다(반올림한 변 %로 계산하면 1%p씩 어긋났다). */
    fun points(short: Int, base: Int): Int = (short.toDouble() / base).let { (it * it * 100).roundToInt() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdown: Job? = null
    private val pendingState = MutableStateFlow(0L)

    /** 확인을 기다리는 중이면 자동으로 되돌릴 시각(ms), 아니면 0. 화면이 다시 만들어져도 이것으로 확인 창을 다시 띄운다. */
    val pending: StateFlow<Long> = pendingState.asStateFlow()

    /** 쓸 수 있는 기기인지: 폴드가 아니어야 한다. */
    fun supported(context: Context) = !DeviceKind.isFoldable(context)

    /** 지금 논리 해상도(짧은 변, 긴 변). */
    @Suppress("DEPRECATION")
    fun current(context: Context): Pair<Int, Int> {
        val m = DisplayMetrics()
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return minOf(m.widthPixels, m.heightPixels) to maxOf(m.widthPixels, m.heightPixels)
    }

    /** 기준(되돌아갈) 해상도: GOAT가 바꿔 둔 동안은 그 직전 크기, 아니면 지금 크기. 낡은 기록이면 지금 크기(정리는 [dropStale]). */
    fun base(context: Context): Pair<Int, Int> {
        val now = current(context)
        val saved = ResolutionState.base(context) ?: return now
        return if (now.first > saved.first) now else saved
    }

    /**
     * GOAT로 낮춰 둔 사이에 삼성 설정 등에서 더 크게 바꿨으면 기록이 낡은 것이라 지운다(작업 스레드, 화면을 열 때·앱 시작 때).
     */
    fun dropStale(context: Context) {
        val saved = ResolutionState.base(context) ?: return
        val now = current(context)
        if (now.first <= saved.first) return
        ResolutionState.clear(context)
        LogRepository.from(context).add("해상도 낮추기: 다른 곳에서 해상도를 ${now.first}x${now.second}로 바꿔 GOAT 기록을 지움")
    }

    /** 기준 크기보다 작은 선택지(시험할 수 있게 계산은 [optionsFor]). */
    fun options(context: Context): List<Option> = optionsFor(base(context))

    /**
     * 선택지: 기준 짧은 변보다 작은 표준 폭(1080 FHD+, 900, 720 HD+) 가운데 기준의 55% 이상인 것, 없거나 하나뿐이면 85%·75%를 더한다.
     * 긴 변은 화면비를 지키도록 같은 비율로, 둘 다 짝수로 맞춘다. 맨 앞은 기준 크기(원래대로, 100%).
     */
    internal fun optionsFor(base: Pair<Int, Int>): List<Option> {
        val (bs, bl) = base
        fun even(v: Double) = (v / 2).roundToInt() * 2
        fun of(short: Int, name: String?) = Option(short, even(bl.toDouble() * short / bs), name, (short * 100.0 / bs).roundToInt(), points(short, bs))
        val standard = listOf(1080 to "FHD+", 900 to null, 720 to "HD+")
            .filter { (s, _) -> s < bs - 20 && s >= bs * 0.55 }
            .map { (s, n) -> of(s, n) }
        val ratios = if (standard.size >= 2) {
            emptyList()
        } else {
            listOf(0.85, 0.75)
                .map { of(even(bs * it), null) }
                .filter { r -> standard.none { abs(it.short - r.short) < 40 } }
        }
        return listOf(Option(bs, bl, null, 100)) + (standard + ratios).sortedByDescending { it.short }
    }

    /** [option]으로 바꾼다(작업 스레드). 100%면 [revert]. 성공하면 확인을 기다린다. */
    fun apply(context: Context, option: Option, how: String): Boolean {
        val app = context.applicationContext
        val log = LogRepository.from(app)
        if (!supported(app)) {
            log.add("해상도 낮추기($how): 폴드는 지원하지 않아 바꾸지 않음")
            return false
        }
        if (option.percent >= 100) return revert(app, how)
        val dpi = DpiManager(app)
        if (!dpi.hasPermission()) {
            log.add("해상도 낮추기($how): 시스템 설정 권한 없음")
            return false
        }
        val ok = DpiFixer.from(app).exclusive {
            // ① 처음이면 직전 상태를 남긴다(이미 낮춰 둔 상태에서 다른 크기로 바꾸면 처음 기록을 그대로 둔다)
            val saved = ResolutionState.saved(app) ?: run {
                val now = current(app)
                val physical = dpi.physicalSize()
                val state = dpi.read()
                ResolutionState.Saved(now, hadForced = physical != null && physical != now, baseDensity = state.current ?: state.physical ?: 0, densityOverride = state.override)
                    .also { ResolutionState.save(app, it) }
            }
            // ② 해상도: 자연 방향(가로가 긴 태블릿은 가로·세로가 반대)에 맞춰 건다
            val (w, h) = if (isPortraitNatural(app)) option.short to option.long else option.long to option.short
            if (!dpi.setForcedSize(Display.DEFAULT_DISPLAY, w, h)) {
                log.add("해상도 낮추기($how): ${w}x$h 적용 실패")
                // 처음 기록만 남고 바뀐 것이 없으면 기록을 지운다(비율이 잘못 걸리지 않게)
                if (current(app) == saved.base) ResolutionState.clear(app)
                return@exclusive false
            }
            // 바뀐 크기가 화면 정보에 반영될 때까지 잠깐 기다린다(반영 전에 밀도를 계산하면 이전 비율로 계산된다)
            waitFor(app, option.short)
            // ③ 밀도: 고급 화면 배율 설정 목표가 있으면 그 경로가 비율을 반영해 적용한다
            applyDensity(app, saved, option.short, how)
            log.add("해상도 낮추기($how): ${saved.base.first}x${saved.base.second} → ${option.short}x${option.long} (${option.percent}%)")
            true
        }
        if (ok) startConfirm(app)
        return ok
    }

    private fun applyDensity(context: Context, saved: ResolutionState.Saved, short: Int, how: String) {
        val log = LogRepository.from(context)
        if (SettingsRepository.from(context).targetDpi != null) {
            DpiFixer.from(context).ensure("해상도 낮추기($how)", automatic = false)
            return
        }
        if (saved.baseDensity <= 0) return
        val d = (saved.baseDensity * short.toDouble() / saved.base.first).roundToInt().coerceIn(DpiPolicy.MIN_DPI, DpiPolicy.MAX_DPI)
        val r = DpiManager(context).apply(d)
        log.add("해상도 낮추기($how): 밀도 ${saved.baseDensity} → $d ${if (r.success) "성공" else "실패(${r.detail})"}")
    }

    /** 확인: 자동 되돌림을 멈추고 지금 해상도를 유지한다. */
    fun keep(context: Context) {
        val app = context.applicationContext
        // 자동 되돌림과 겹치지 않게 같은 잠금에서 마감을 지운다(되돌림은 잠금 안에서 마감을 다시 확인한다)
        DpiFixer.from(app).exclusive {
            countdown?.cancel()
            runCatching { WorkManager.getInstance(app).cancelUniqueWork(WORK) }
            ResolutionState.setDeadline(app, 0)
            pendingState.value = 0
        }
        LogRepository.from(app).add("해상도 낮추기: 지금 해상도 유지(확인)")
    }

    /**
     * 원래 해상도·밀도로 되돌린다(작업 스레드). [onlyIfPending]이면(자동 되돌림) 잠금 안에서 확인 대기 중인지 다시 보고,
     * 그 사이 '유지'를 눌렀으면 되돌리지 않는다.
     */
    fun revert(context: Context, how: String, onlyIfPending: Boolean = false): Boolean {
        val app = context.applicationContext
        // 마감 확인과 되돌리기를 한 잠금 안에서 한다(그 사이에 '유지'가 끼어들 틈이 없게, 잠금은 다시 들어갈 수 있다)
        return DpiFixer.from(app).exclusive {
            if (onlyIfPending && ResolutionState.deadline(app) == 0L) false else revertLocked(app, how)
        }
    }

    private fun revertLocked(app: Context, how: String): Boolean {
        countdown?.cancel()
        runCatching { WorkManager.getInstance(app).cancelUniqueWork(WORK) }
        pendingState.value = 0
        val saved = ResolutionState.saved(app) ?: run {
            ResolutionState.setDeadline(app, 0)
            return true
        }
        val dpi = DpiManager(app)
        val log = LogRepository.from(app)
        return DpiFixer.from(app).exclusive {
            // ① 해상도: 직전에 강제 해상도가 있었으면 그 크기로, 없었으면 지운다
            val sizeOk = if (saved.hadForced) {
                val (w, h) = if (isPortraitNatural(app)) saved.base else saved.base.second to saved.base.first
                dpi.setForcedSize(Display.DEFAULT_DISPLAY, w, h)
            } else {
                dpi.clearForcedSize(Display.DEFAULT_DISPLAY)
            }
            if (!sizeOk) {
                log.add("해상도 낮추기 되돌리기($how): 해상도를 되돌리지 못함. PC에서 '$PC_UNDO'")
                return@exclusive false
            }
            // ② 기록을 지워 DPI 기능의 비율을 1로 돌린다 ③ 밀도(크기가 반영된 뒤)
            waitFor(app, saved.base.first)
            ResolutionState.clear(app)
            if (SettingsRepository.from(app).targetDpi != null) {
                DpiFixer.from(app).ensure("해상도 되돌리기($how)", automatic = false)
            } else {
                val r = saved.densityOverride?.let { dpi.apply(it) } ?: dpi.reset()
                log.add("해상도 낮추기 되돌리기($how): 밀도 ${saved.densityOverride ?: "기본"} ${if (r.success) "성공" else "실패(${r.detail})"}")
            }
            log.add("해상도 낮추기 되돌리기($how): ${saved.base.first}x${saved.base.second}${if (saved.hadForced) "(이전 강제 해상도)" else "(기본)"}로")
            true
        }
    }

    /** 확인을 기다리는 중인데 시간이 지났거나 [force]면 되돌린다(부팅·WorkManager·앱 시작). */
    fun revertIfPending(context: Context, how: String, force: Boolean = false) {
        val at = ResolutionState.deadline(context)
        if (at == 0L) return
        if (force || System.currentTimeMillis() >= at) revert(context, how, onlyIfPending = true) else resumeCountdown(context.applicationContext, at)
    }

    /** 앱 시작 때: 확인 대기 중이었으면 남은 시간으로 카운트다운을 이어 간다(지났으면 되돌린다). */
    fun resume(context: Context) {
        scope.launch { dropStale(context) }
        val at = ResolutionState.deadline(context)
        if (at == 0L) return
        scope.launch { revertIfPending(context, "앱 시작 시 확인 시간 초과") }
    }

    private fun startConfirm(context: Context) {
        val at = System.currentTimeMillis() + CONFIRM_MS
        ResolutionState.setDeadline(context, at)
        // 2차: 프로세스가 죽어도 되돌리도록 WorkManager에 같은 시각을 맡긴다(지연은 최소값이라 늦어질 수 있다)
        val work = OneTimeWorkRequestBuilder<ResolutionRevertWorker>().setInitialDelay(CONFIRM_MS, TimeUnit.MILLISECONDS).build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, work) }
        resumeCountdown(context, at)
    }

    /** 1차: 앱 프로세스의 카운트다운(정확한 시각에 되돌린다). */
    private fun resumeCountdown(context: Context, at: Long) {
        pendingState.value = at
        countdown?.cancel()
        countdown = scope.launch {
            delay((at - System.currentTimeMillis()).coerceAtLeast(0))
            if (ResolutionState.deadline(context) == at) revert(context, "확인하지 않아 자동 되돌림", onlyIfPending = true)
        }
    }

    /** 지금 논리 해상도의 짧은 변이 [short]가 될 때까지 최대 1.5초 기다린다(작업 스레드). */
    private fun waitFor(context: Context, short: Int) {
        val until = System.currentTimeMillis() + 1500
        while (current(context).first != short && System.currentTimeMillis() < until) Thread.sleep(50)
    }

    /** 기기의 자연 방향이 세로인지(폰은 세로, 대부분의 태블릿은 가로). 강제 해상도는 자연 방향의 가로·세로로 건다. */
    private fun isPortraitNatural(context: Context): Boolean = DpiManager(context).physicalSizeNatural()?.let { (w, h) -> w <= h } ?: true
}
