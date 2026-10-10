package com.local.folddpifix.data.appsize

import com.local.folddpifix.data.log.LogRepository
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import com.local.folddpifix.AppInfo
import com.local.folddpifix.R
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.ui.MainActivity
import com.local.folddpifix.ui.text.Copy
import com.local.folddpifix.ui.appsize.DensityStepActivity

/**
 * 알림창 앱별 화면 크기 조절. [AppDensityService]가 이 알림을 띄워 둔 채로
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
        if (on) start(context) else { context.stopService(Intent(context, AppDensityService::class.java)); cancel(context) }
    }

    /** 켜 두었으면 감시 서비스를 띄운다(부팅·업데이트 뒤에도). */
    fun start(context: Context) {
        if (!enabled(context) || !ExternalChangeNotifier.canNotify(context)) return
        runCatching { context.startForegroundService(Intent(context, AppDensityService::class.java)) }
    }

    /** 알림 내용을 바꾼다(감시 서비스가 띄운 알림만 고친다. 서비스 없이 남는 알림을 만들지 않는다). */
    fun show(context: Context, status: String) {
        if (!enabled(context) || !ExternalChangeNotifier.canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.activeNotifications.none { it.id == NOTIFICATION_ID }) return
        nm.notify(NOTIFICATION_ID, build(context, status))
    }

    /** "Discord · 기본(450)" / "Notion · 320 dpi"처럼 앱과 지금 크기를 적는다. 홈 화면이면 안내 문구. */
    fun describe(context: Context, pkg: String?): String {
        pkg ?: return "앱을 열면 그 앱의 화면 크기를 여기서 바꿀 수 있습니다."
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val v = runCatching { DensityShell.get(context, pkg) }.getOrNull()
        val size = when {
            v == null -> "크기 확인 불가"
            v == 0 -> "${Copy.APP_SIZE_DEFAULT}(${Resources.getSystem().configuration.densityDpi})"
            else -> "$v dpi"
        }
        return "$label · $size"
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    fun build(context: Context, status: String? = null): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "앱별 화면 크기 조절", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        fun step(s: Int, code: Int) = PendingIntent.getActivity(
            context, code,
            Intent(context, DensityStepActivity::class.java).putExtra(DensityStepActivity.EXTRA_STEP, s)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dpi)
            .setContentTitle("${AppInfo.NAME} · 앱 화면 크기")
            .setContentText(status ?: "지금 쓰는 앱의 화면 크기를 한 단계씩 바꿉니다.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "작게", step(-1, 11))
            .addAction(0, "기본", step(0, 12))
            .addAction(0, "크게", step(1, 13))
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
