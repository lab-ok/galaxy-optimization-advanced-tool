package com.labok.goat.data.rotate

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * 흔들어서 화면 회전을 알림과 함께 띄워 두는 방식(옵션). 실제 동작은 [ShakeEngine]이 한다.
 * 알림 없이 쓰는 방식은 [ShakeAccessibilityService].
 */
class ShakeRotateService : Service() {
    private lateinit var engine: ShakeEngine

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ShakeRotate.NOTIFICATION_ID, ShakeRotate.buildNotification(this), type)
        engine = ShakeEngine(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        engine.refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
