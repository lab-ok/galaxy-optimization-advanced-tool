package com.labok.goat.background

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
import com.labok.goat.R
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.DpiFixer
import com.labok.goat.domain.ExternalChange
import com.labok.goat.ui.MainActivity

/** 외부 DPI 변경 알림. '이 값 기준으로'와 '복원' 두 가지를 알림에서 바로 고를 수 있다. */
object ExternalChangeNotifier {
    private const val CHANNEL_ID = "external_change"
    private const val NOTIFICATION_ID = 2
    const val ACTION_ADOPT = "com.labok.goat.EXTERNAL_ADOPT"
    const val ACTION_RESTORE = "com.labok.goat.EXTERNAL_RESTORE"

    /** 화면이 하나인 폰([foldable]이 false)에는 외부·내부 구분을 붙이지 않는다(앱 안 카드와 같은 문구). */
    fun text(change: ExternalChange, foldable: Boolean = true) = "다른 설정에서 ${if (foldable) "${change.role.label} 화면 " else ""}DPI가 ${change.from}에서 ${change.to}(으)로 변경되었습니다."

    fun show(context: Context, change: ExternalChange) {
        if (!canNotify(context)) return
        val body = text(change, DeviceKind.isFoldable(context))
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "외부 DPI 변경", NotificationManager.IMPORTANCE_DEFAULT))
        fun action(a: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ExternalChangeReceiver::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("화면 배율이 다른 곳에서 바뀌었습니다")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body 이 값을 기준으로 할지, 앱 설정으로 복원할지 선택하세요."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "${change.to} 기준으로 설정", action(ACTION_ADOPT, 1))
            .addAction(0, "${change.from}(으)로 복원", action(ACTION_RESTORE, 2))
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    /** Android 13부터 알림 권한이 필요하다. 12(minSdk 31)에서는 권한 없이 보낼 수 있다. */
    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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
