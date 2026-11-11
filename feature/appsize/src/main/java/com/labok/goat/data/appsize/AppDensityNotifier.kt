package com.labok.goat.data.appsize

import com.labok.goat.AppHooks
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import com.labok.goat.core.R
import com.labok.goat.data.notify.Notify
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.appsize.DensityStepActivity
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.text.Copy

/**
 * 알림창 앱별 화면 크기 조절. GOAT 상주 서비스([GoatService])가 이 알림을 띄워 둔 채로
 * 맨 앞 앱이 바뀔 때마다([ForegroundWatcher]) 그 앱 이름과 지금 크기를 보여 준다.
 * [작게]·[기본]·[크게]를 누르면 [DensityStepActivity]가 그 앱의 크기를 한 단계 바꾼다.
 */
object AppDensityNotifier {
    private const val CHANNEL_ID = "app_density"
    const val NOTIFICATION_ID = 3

    // 이전 버전(실험실 시절)과 같은 이름을 유지한다(사용자 설정 보존)
    private const val PREF = "lab_app_density_notify"

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        LogRepository.from(context).add("알림창 앱 배율 조절 ${if (on) "켜짐" else "꺼짐"}")
        AppHooks.syncService(context)
    }

    /** 켜 두었으면 상주 서비스를 띄운다(부팅·업데이트 뒤에도). 흔들어서 화면 회전과 같은 서비스·알림 하나를 쓴다. */
    fun start(context: Context) {
        if (enabled(context)) AppHooks.syncService(context)
    }

    /** 알림 내용을 바꾼다(감시 서비스가 띄운 알림만 고친다. 서비스 없이 남는 알림을 만들지 않는다). */
    fun show(context: Context, status: String) {
        if (!enabled(context) || !Notify.canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.activeNotifications.none { it.id == NOTIFICATION_ID }) return
        nm.notify(NOTIFICATION_ID, build(context, status, shakeNote(context)))
    }

    /** "Discord · 기본(450)" / "Notion · 320 dpi"처럼 앱과 지금 크기를 적는다. 홈 화면이면 안내 문구. */
    fun describe(context: Context, pkg: String?): String {
        pkg ?: return Copy.text(R.string.appsize_notif_idle)
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val v = runCatching { DensityShell.get(context, pkg) }.getOrNull()
        val size = when {
            v == null -> Copy.text(R.string.appsize_notif_unknown)
            v == 0 -> "${Copy.DEFAULT}(${Resources.getSystem().configuration.densityDpi})"
            else -> "$v dpi"
        }
        return "$label · $size"
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    /** 흔들어서 화면 회전도 켜져 있으면 같은 알림에 한 줄로 알린다(알림을 따로 띄우지 않는다). */
    fun shakeNote(context: Context) = AppHooks.densityNoteExtras(context)

    fun build(context: Context, status: String? = null, extra: String? = null): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, Copy.text(R.string.appsize_notif_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        fun step(s: Int, code: Int) = PendingIntent.getActivity(
            context,
            code,
            Intent(context, DensityStepActivity::class.java).putExtra(DensityStepActivity.EXTRA_STEP, s)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(context, 10, AppHooks.mainIntent(context), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(Copy.FEATURE_APP_SIZE)
            .setContentText(status ?: Copy.text(R.string.appsize_notif_idle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setSubText(extra)
            .addAction(0, Copy.text(R.string.appsize_notif_smaller), step(-1, 11))
            .addAction(0, Copy.DEFAULT, step(0, 12))
            .addAction(0, Copy.text(R.string.appsize_notif_larger), step(1, 13))
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
