package com.labok.goat.data.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.background.GoatService
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.display.DpiManager
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.domain.AppDensityPolicy
import com.labok.goat.domain.ScreenPolicy
import com.labok.goat.ui.MainActivity

/**
 * 실험실: 앱별 배율을 접힘·펼침에 따라 다르게. 삼성 앱별 배율(앱 화면 크게/작게)은 앱마다 값이 하나뿐이라
 * 펼친 화면·접은 화면을 따로 둘 수 없다. 그래서 접고 펼 때마다 고른 앱의 앱별 배율을 그 화면용 값으로 바꿔 건다.
 * 예: 크롬은 펼치면 510(표준 보기처럼), 접으면 기본.
 * 접고 펴면 앱이 어차피 다시 그려지므로, 앱을 바꿀 때마다 전체 DPI를 바꾸는 방식보다 재시작 부담이 적을 것으로 본다(실기 확인 필요).
 * 값을 바꾸는 순간 그 앱은 다시 시작된다([DensityShell.set]). Shizuku 또는 PC 도우미가 연결돼 있어야 한다.
 */
object FoldAppScale {
    private const val PREF = "fold_app_scale"
    private const val CHANNEL_ID = "fold_app_scale"

    /** 펼친 화면([inner])·접은 화면([outer])의 앱별 배율. 0은 기본(앱별 설정 지움). */
    data class Rule(val inner: Int, val outer: Int) {
        fun forRole(role: ScreenPolicy.Role) = if (role == ScreenPolicy.Role.INNER) inner else outer
        val empty get() = inner == 0 && outer == 0
    }

    /** 고를 수 있는 값: 기본(0)과 삼성이 받는 단계. */
    val CHOICES: List<Int> = listOf(0) + AppDensityPolicy.STEPS

    fun label(dpi: Int) = if (dpi == 0) "기본" else "$dpi"

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        LogRepository.from(context).add("접힘별 앱 배율(실험실): ${if (on) "켜짐" else "꺼짐"}")
        GoatService.sync(context)
    }

    /** 앱별 규칙(패키지 → 규칙). */
    fun rules(context: Context): Map<String, Rule> = prefs(context).all.mapNotNull { (k, v) ->
        if (!k.startsWith(RULE)) return@mapNotNull null
        val parts = (v as? String)?.split(',') ?: return@mapNotNull null
        val inner = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val outer = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        k.removePrefix(RULE) to Rule(inner, outer)
    }.toMap()

    /** 규칙을 저장한다. 둘 다 기본이면 지운다. */
    fun setRule(context: Context, pkg: String, rule: Rule) {
        prefs(context).edit().apply { if (rule.empty) remove(RULE + pkg) else putString(RULE + pkg, "${rule.inner},${rule.outer}") }.apply()
        LogRepository.from(context).add("접힘별 앱 배율(실험실): $pkg → 펼침 ${label(rule.inner)} · 접힘 ${label(rule.outer)}")
        GoatService.sync(context)
    }

    /** 상주 서비스에서 엔진이 필요한지. 폴드에서 켜져 있고 규칙이 하나라도 있을 때만. */
    fun engineNeeded(context: Context) = enabled(context) && rules(context).isNotEmpty() && DeviceKind.isFoldable(context)

    /**
     * 지금 기본 화면이 펼친 화면(INNER)인지 접은 화면(OUTER)인지. 학습한 두 화면이 있으면 픽셀 수로,
     * 아직 하나만 알면 대각선 길이(7인치 이상이면 펼친 화면)로 정한다. 읽지 못하면 null.
     */
    fun currentRole(context: Context): ScreenPolicy.Role? {
        val now = DpiManager(context).currentScreen() ?: return null
        ScreenPolicy.roleOf(SettingsRepository.from(context).screens, now.key)?.let { return it }
        if (now.diagonalInch <= 0f) return null
        return if (now.diagonalInch >= INNER_MIN_INCH) ScreenPolicy.Role.INNER else ScreenPolicy.Role.OUTER
    }

    /** 지금 화면에 맞춰 규칙을 건다(작업 스레드). 바꾼 앱 수를 돌려준다. 이미 맞는 앱은 건드리지 않는다(다시 시작되지 않게). */
    fun applyNow(context: Context, why: String): Int {
        val role = currentRole(context) ?: return 0
        val log = LogRepository.from(context)
        var changed = 0
        rules(context).forEach { (pkg, rule) ->
            val want = rule.forRole(role)
            runCatching {
                if (DensityShell.get(context, pkg) != want) {
                    DensityShell.set(context, pkg, want)
                    changed++
                }
            }.onFailure { log.add("접힘별 앱 배율($why): $pkg 실패 ${it.javaClass.simpleName}: ${it.message}") }
        }
        if (changed > 0) {
            log.add("접힘별 앱 배율($why): ${role.label} 화면, 앱 ${changed}개 바꿈")
            DensityShell.notifyChanged()
        }
        return changed
    }

    /** 접고 펼 때(기본 화면이 바뀔 때) 규칙을 다시 건다. [GoatService]가 켜고 끈다. */
    class Engine(private val context: Context) {
        private val thread = HandlerThread("fold-app-scale").also { it.start() }
        private val worker = Handler(thread.looper)
        private val check = Runnable { applyNow(context, "접기·펼치기") }
        private val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                // 접고 펴는 동안 이벤트가 몰려오므로 마지막 이벤트 뒤 한 번만 건다
                worker.removeCallbacks(check)
                worker.postDelayed(check, DEBOUNCE_MS)
            }
        }

        fun start() {
            context.getSystemService(DisplayManager::class.java).registerDisplayListener(listener, worker)
            worker.post { applyNow(context, "시작") }
        }

        fun stop() {
            runCatching { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener) }
            worker.removeCallbacks(check)
            thread.quitSafely()
        }
    }

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "접힘별 앱 배율", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(context, 30, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("접힘별 앱 배율")
            .setContentText("접고 펼 때 고른 앱의 배율을 바꿉니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private const val RULE = "rule:"
    private const val DEBOUNCE_MS = 1_000L

    /** 학습한 화면이 하나뿐일 때 펼친 화면으로 보는 대각선 길이(인치). 폴드 커버는 6.5인치 안팎, 펼친 화면은 7.6인치 이상. */
    private const val INNER_MIN_INCH = 7.0f

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
