package com.labok.goat.data.shizuku

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.shizuku.ShizukuReconnectActivity
import com.labok.goat.ui.text.Copy

/**
 * Shizuku 연결이 끊겨 기능이 동작하지 못했을 때 알림(11-08 소장님 "플렉스 모드 안 되면 GOAT가 알려줬어야 하는 것 아니냐").
 * 예전에는 Shizuku가 끊기면 플렉스 모드가 조용히 건너뛰어, 사용자는 왜 안 되는지 알 수 없었다.
 * 끊긴 동안 한 번만 띄우고, 누르면 Shizuku 앱을 연다. Shizuku가 다시 연결되면 알림을 지우고 다음 끊김에 다시 띄운다.
 */
object ShizukuNotice {
    private const val PREF = "shizuku_notice"
    private const val CHANNEL_ID = "shizuku_lost"
    private const val ID = 4420

    /** Shizuku 연결이 없어 [feature](기능 이름, 모르면 null)가 동작하지 못했거나 연결이 끊겼다. 끊긴 동안 처음 한 번만 알린다. */
    fun lost(context: Context, feature: String?) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (p.getBoolean("shown", false)) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, Copy.text(R.string.shizuku_lost_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, ShizukuReconnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = if (feature != null) Copy.text(R.string.shizuku_lost_text_fmt, feature) else Copy.text(R.string.shizuku_lost_text)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(Copy.text(R.string.shizuku_lost_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
        p.edit().putBoolean("shown", true).apply()
    }

    /** Shizuku를 한 번이라도 연결해 쓴 적이 있는지(없으면 재부팅 뒤 미연결을 알리지 않는다). */
    fun everReady(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("ever_ready", false)

    /** Shizuku가 다시 연결됐다: 알림을 지우고, 다음에 끊기면 다시 알릴 수 있게 한다. */
    fun restored(context: Context) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean("ever_ready", false)) p.edit().putBoolean("ever_ready", true).apply()
        if (!p.getBoolean("shown", false)) return
        p.edit().putBoolean("shown", false).apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
