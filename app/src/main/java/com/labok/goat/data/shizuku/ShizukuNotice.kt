package com.labok.goat.data.shizuku

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.lab.AmbientOpen
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.shizuku.ShizukuReconnectActivity
import com.labok.goat.ui.text.Copy

/**
 * Shizuku 연결이 끊긴 동안 계속 떠 있는 알림(11-08 소장님 "끊기면 끊겼다고, 다시 연결해 달라고 알려야", "상시로 띄워도 좋다",
 * "개발자 옵션까지 한 번에 다시 설정할 수 있게").
 * 켜 둔 기능 중 Shizuku가 필요한 것이 있으면 그 이름을 알리고, 누르면 [ShizukuReconnectActivity]가 개발자 옵션·무선 디버깅을 켜고
 * Shizuku를 연다. 다시 연결되면 저절로 지운다.
 */
object ShizukuNotice {
    private const val CHANNEL_ID = "shizuku_lost"
    private const val ID = 4420

    /** 켜 둔 기능 가운데 Shizuku가 있어야 동작하는 것들의 이름. */
    fun affected(context: Context): List<String> = listOfNotNull(
        Copy.text(R.string.feature_auto_flex).takeIf { AutoFlex.enabled(context) },
        Copy.text(R.string.feature_app_size).takeIf { AppDensityNotifier.enabled(context) },
        Copy.text(R.string.feature_lab_ambient).takeIf { AmbientOpen.enabled(context) },
        Copy.text(R.string.feature_camera_mute).takeIf { CameraMute.wanted(context) }
    )

    /** 알림이 떠 있을 때만 다시 맞춘다(기능을 끄고 켤 때: 영향받는 기능 이름을 고치거나, 모두 껐으면 지운다). */
    fun refreshIfShown(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.activeNotifications.any { it.id == ID }) update(context)
    }

    /** 지금 상태에 맞게 알림을 띄우거나 지운다: 연결이 없고 영향받는 기능이 있으면 띄운다. 어느 스레드에서 불러도 된다. */
    fun update(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val names = if (ShizukuAccess.status.value == ShizukuAccess.State.READY) emptyList() else affected(context)
        if (names.isEmpty()) {
            nm.cancel(ID)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, Copy.text(R.string.shizuku_lost_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, ShizukuReconnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = Copy.text(R.string.shizuku_lost_text_fmt, names.joinToString(", "))
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(Copy.text(R.string.shizuku_lost_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, Copy.text(R.string.shizuku_lost_action), open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(ID, n)
        // 떠 있는 동안 15초마다 연결을 다시 확인해, 연결되면 지운다(연결 알림을 놓쳐도 남지 않게)
        if (!checking) {
            checking = true
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                checking = false
                ShizukuAccess.refresh()
                update(app)
            }, 15_000)
        }
    }

    @Volatile private var checking = false
}
