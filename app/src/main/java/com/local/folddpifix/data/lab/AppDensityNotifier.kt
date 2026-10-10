package com.local.folddpifix.data.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.local.folddpifix.AppInfo
import com.local.folddpifix.BuildConfig
import com.local.folddpifix.R
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.ui.MainActivity
import com.local.folddpifix.ui.lab.DensityStepActivity

/**
 * 알림창 앱별 화면 크기 조절(lab 빌드 전용). 늘 떠 있는 알림에 [작게]·[기본]·[크게] 버튼을 두고,
 * 누르면 [DensityStepActivity]가 방금 쓰던 앱의 화면 크기를 한 단계 바꾼다. 앱 감시(폴링)는 하지 않는다.
 */
object AppDensityNotifier {
    private const val CHANNEL_ID = "app_density"
    private const val NOTIFICATION_ID = 3
    private const val PREF = "lab_app_density_notify"

    fun enabled(context: Context) = BuildConfig.LAB && prefs(context).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) show(context) else cancel(context)
    }

    /** 알림을 띄운다. [status]는 마지막으로 바꾼 결과(없으면 안내 문구). */
    fun show(context: Context, status: String? = null) {
        if (!enabled(context) || !ExternalChangeNotifier.canNotify(context)) return
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
        val text = status ?: "지금 쓰는 앱의 화면 크기를 한 단계씩 바꿉니다."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dpi)
            .setContentTitle("${AppInfo.NAME} · 앱 화면 크기")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "작게", step(-1, 11))
            .addAction(0, "기본", step(0, 12))
            .addAction(0, "크게", step(1, 13))
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
