package com.local.folddpifix.data.appsize

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.local.folddpifix.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 알림창 앱별 화면 크기 조절을 띄워 두는 서비스.
 * 알림을 늘 보이게 하고, Shizuku가 준비되면 [ForegroundWatcher]로 맨 앞 앱이 바뀔 때만 알림 내용을 고친다.
 * Shizuku가 늦게 켜지면(부팅 직후 등) 바인더가 오는 순간 감시를 시작한다.
 */
class AppDensityService : Service() {
    private val binderReceived = Shizuku.OnBinderReceivedListener { watch() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> watch() }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            AppDensityNotifier.NOTIFICATION_ID,
            AppDensityNotifier.build(this, AppDensityNotifier.describe(this, null)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        ShizukuAccess.watch(this)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addRequestPermissionResultListener(permission)
    }

    private fun watch() {
        ShizukuAccess.refresh()
        ForegroundWatcher.start(this) { pkg -> AppDensityNotifier.show(this, AppDensityNotifier.describe(this, pkg)) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        watch()
        return START_STICKY
    }

    override fun onDestroy() {
        ForegroundWatcher.stop()
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeRequestPermissionResultListener(permission)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
