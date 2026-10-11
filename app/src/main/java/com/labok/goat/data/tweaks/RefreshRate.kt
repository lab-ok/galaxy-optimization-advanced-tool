package com.labok.goat.data.tweaks

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.labok.goat.BuildConfig
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.text.Copy
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

/**
 * 주사율 고정: 화면이 1초에 몇 번 새로 그려지는지(Hz)의 위·아래 한계를 정한다.
 * - 안드로이드 공통 설정 [PEAK](최고)·[MIN](최저)을 같은 값으로 두면 그 주사율로 고정된다(AOSP DisplayModeDirector가 투표로 쓴다).
 * - 삼성 '화면 움직임 부드럽게'는 [MODE](0 표준 60Hz, 1 적응형)를 따로 읽으므로 함께 맞춘다.
 *
 * 최고·최저는 공개되지 않은 시스템 설정이라 일반 앱은 시스템 설정 권한(WRITE_SECURE_SETTINGS)이 있어도 쓰지 못하는 기기가 많다
 * (에뮬레이터 Android 15에서 확인). 권한으로 먼저 써 보고, 막히면 Shizuku 셸 `settings put`으로 쓴다. 화면은 Shizuku 연결을 요구한다.
 * 처음 바꾸기 전 값은 [restore]용으로 한 번만 남긴다. 절전 모드·일부 게임은 시스템이 잠시 60Hz로 내리는데,
 * 이는 고정 값을 바꾸지 않고 그 동안만 내리는 것이라 끝나면 돌아온다. 다른 앱·설정이 값을 바꿔 놓으면 [reapply]가 되돌린다.
 */
object RefreshRate {
    const val PEAK = "peak_refresh_rate"
    const val MIN = "min_refresh_rate"
    const val MODE = "refresh_rate_mode"
    private const val PREF = "refresh_rate"

    /** 고를 수 있는 방식. */
    enum class Choice(val label: String, val desc: String) {
        SYSTEM(Copy.REFRESH_SYSTEM, Copy.REFRESH_SYSTEM_DESC),
        SMOOTH(Copy.REFRESH_SMOOTH, Copy.REFRESH_SMOOTH_DESC),
        SAVER(Copy.REFRESH_SAVER, Copy.REFRESH_SAVER_DESC),
    }

    /** 이 기기 기본 화면이 지원하는 주사율(정수 Hz, 오름차순). lab 빌드 에뮬레이터는 60·120Hz로 흉내 낸다. */
    fun rates(context: Context): List<Int> {
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val real = d?.supportedModes?.map { it.refreshRate.roundToInt() }?.distinct()?.sorted().orEmpty()
        return if (BuildConfig.LAB && (real.maxOrNull() ?: 0) <= 60) listOf(60, 120) else real
    }

    /** 최고 주사율(바꿀 수 있는 기기인지는 이 값이 60보다 큰지로 본다). */
    fun max(context: Context) = rates(context).maxOrNull() ?: 60

    fun supported(context: Context) = max(context) > 60

    /** 지금 화면이 실제로 그리는 주사율(Hz). */
    fun now(context: Context): Int =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60

    /** 설정에 적힌 최고·최저(없으면 null). */
    data class Values(val peak: Float?, val min: Float?, val mode: Int?)

    fun values(context: Context): Values {
        val r = context.contentResolver
        return Values(
            runCatching { Settings.System.getString(r, PEAK)?.toFloat() }.getOrNull(),
            runCatching { Settings.System.getString(r, MIN)?.toFloat() }.getOrNull(),
            runCatching { Settings.Secure.getString(r, MODE)?.toInt() }.getOrNull(),
        )
    }

    /** 지금 설정이 어느 방식인지: 최저가 최고 주사율이면 항상 최고, 최고가 60이면 절전, 그 밖은 시스템 기본. */
    fun read(context: Context): Choice = classify(values(context), max(context))

    /** [read]의 판정(시험할 수 있게 시스템과 뗐다). [top]은 기기 최고 주사율. */
    internal fun classify(v: Values, top: Int): Choice = when {
        (v.min ?: 0f) >= top - 0.5f -> Choice.SMOOTH
        v.peak != null && v.peak <= 60.5f -> Choice.SAVER
        else -> Choice.SYSTEM
    }

    /** 사용자가 고른 방식(다시 맞추기의 기준). 고른 적이 없으면 null. */
    fun wanted(context: Context): Choice? = prefs(context).getString("wanted", null)?.let { n -> Choice.entries.firstOrNull { it.name == n } }

    /** [choice]로 바꾼다. 쓰고 나서 다시 읽어 확인한다. 성공하면 true. */
    fun set(context: Context, choice: Choice, how: String): Boolean {
        val log = LogRepository.from(context)
        val before = read(context)
        val top = max(context).toFloat()
        if (choice != Choice.SYSTEM && !supported(context)) {
            log.add("주사율 고정($how): 이 기기는 최고 ${top.toInt()}Hz라 바꾸지 않음")
            return false
        }
        if (choice == Choice.SYSTEM) return restore(context, how)
        remember(context)
        val (peak, min, mode) = when (choice) {
            Choice.SMOOTH -> Triple(top, top, 1)
            else -> Triple(60f, 60f, 0)
        }
        val wrote = write(context, peak, min, mode)
        val after = read(context)
        val ok = wrote != null && after == choice
        if (ok) prefs(context).edit().putString("wanted", choice.name).apply()
        log.add("주사율 고정($how): ${before.label} → ${after.label}" + when {
            wrote == null -> " 실패(시스템 설정 권한·Shizuku 모두 없음)"
            !ok -> " 실패(결과 다름)"
            else -> if (wrote == "shell") " · Shizuku로 씀" else ""
        })
        return ok
    }

    /**
     * 처음 바꾸기 전 값으로 되돌린다. 남긴 값이 없으면(다른 곳에서 고정해 둔 경우 등) 시스템 기본과 같은 값
     * (최고 = 기기 최고, 최저 = 0, 적응형)을 쓴다.
     */
    fun restore(context: Context, how: String): Boolean {
        val p = prefs(context)
        val before = read(context)
        val top = max(context).toFloat()
        val peak = if (p.contains("peak")) p.getFloat("peak", top) else top
        val min = if (p.contains("min")) p.getFloat("min", 0f) else 0f
        val mode = if (p.contains("mode")) p.getInt("mode", 1) else 1
        if (!p.contains("saved") && before == Choice.SYSTEM) { p.edit().remove("wanted").apply(); return true }
        val ok = write(context, peak, min, mode) != null
        if (ok) p.edit().clear().apply()
        LogRepository.from(context).add("주사율 고정 원래대로($how): ${before.label} → ${read(context).label}${if (ok) "" else " 실패"}")
        return ok
    }

    /** 고른 방식과 지금 설정이 다르면(삼성 설정·다른 앱이 바꿔 놓음) 다시 맞춘다(작업 스레드). */
    fun reapply(context: Context) {
        val want = wanted(context) ?: return
        if (want == Choice.SYSTEM || read(context) == want) return
        set(context, want, "다시 맞추기")
    }

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }
    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다: 고른 방식이 있으면 Shizuku가 연결되는 순간(재부팅 뒤 자동 시작 포함) 다시 맞추기를 확인한다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (wanted(app) == null) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun remember(context: Context) {
        val p = prefs(context)
        if (p.contains("saved")) return
        val v = values(context)
        p.edit().apply {
            putBoolean("saved", true)
            v.peak?.let { putFloat("peak", it) }
            v.min?.let { putFloat("min", it) }
            v.mode?.let { putInt("mode", it) }
        }.apply()
    }

    /** 세 값을 쓴다. 시스템 설정 권한으로 되면 "permission", Shizuku로 되면 "shell", 둘 다 안 되면 null. */
    private fun write(context: Context, peak: Float, min: Float, mode: Int): String? {
        val r = context.contentResolver
        val direct = runCatching {
            Settings.System.putFloat(r, PEAK, peak) && Settings.System.putFloat(r, MIN, min) && Settings.Secure.putInt(r, MODE, mode)
        }.getOrDefault(false)
        if (direct) return "permission"
        val shell = ShizukuAccess.shell(
            "settings put system $PEAK $peak && settings put system $MIN $min && settings put secure $MODE $mode",
        ) ?: return null
        return if (shell.ok) "shell" else null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
