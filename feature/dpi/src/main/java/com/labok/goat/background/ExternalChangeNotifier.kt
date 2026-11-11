package com.labok.goat.background

import com.labok.goat.ui.text.Particle
import com.labok.goat.AppHooks
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.labok.goat.core.R
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.DpiFixer
import com.labok.goat.domain.ExternalChange
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.text.Copy

/** 외부 DPI 변경 알림. '이 값 기준으로'와 '복원' 두 가지를 알림에서 바로 고를 수 있다. */
object ExternalChangeNotifier {
    private const val CHANNEL_ID = "external_change"
    private const val NOTIFICATION_ID = 2
    const val ACTION_ADOPT = "com.labok.goat.EXTERNAL_ADOPT"
    const val ACTION_RESTORE = "com.labok.goat.EXTERNAL_RESTORE"

    /** 화면이 하나인 폰([foldable]이 false)에는 외부·내부 구분을 붙이지 않는다(앱 안 카드와 같은 문구). */
    fun text(change: ExternalChange, foldable: Boolean = true) = if (foldable) {
        Copy.text(R.string.notif_external_text_fold_fmt, change.role.label, change.from, change.to, Particle.ro("${change.to}"))
    } else {
        Copy.text(R.string.notif_external_text_single_fmt, change.from, change.to, Particle.ro("${change.to}"))
    }

    fun show(context: Context, change: ExternalChange) {
        if (!canNotify(context)) return
        val body = text(change, DeviceKind.isFoldable(context))
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, Copy.text(R.string.notif_external_channel), NotificationManager.IMPORTANCE_DEFAULT))
        fun action(a: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ExternalChangeReceiver::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(context, 0, AppHooks.mainIntent(context), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(Copy.text(R.string.notif_external_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body ${Copy.EXTERNAL_BODY}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, Copy.text(R.string.notif_external_adopt_fmt, change.to), action(ACTION_ADOPT, 1))
            .addAction(0, Copy.text(R.string.notif_external_restore_fmt, change.from, Particle.ro("${change.from}")), action(ACTION_RESTORE, 2))
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    /** Android 13부터 알림 권한이 필요하다. 12(minSdk 31)에서는 권한 없이 보낼 수 있다. */
    fun canNotify(context: Context): Boolean = com.labok.goat.data.notify.Notify.canNotify(context)

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
}

class ExternalChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val adopt = when (intent.action) {
            ExternalChangeNotifier.ACTION_ADOPT -> true
            ExternalChangeNotifier.ACTION_RESTORE -> false
            else -> return
        }
        ExternalChangeNotifier.cancel(context)
        val pending = goAsync()
        Thread {
            try {
                DpiFixer.from(context).resolveExternal(adopt, if (adopt) "알림: 변경값 기준" else "알림: 복원")
            } catch (t: Throwable) {
                LogRepository.from(context).add("외부 변경 처리 실패: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
