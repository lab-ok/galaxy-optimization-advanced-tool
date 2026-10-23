package com.labok.goat.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.appsize.ForegroundWatcher
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.NarrowBar
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.rotate.ShakeEngine
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * GOAT의 상주 서비스 하나. 켜 둔 기능을 한 서비스·한 알림에 모아, GOAT 알림이 둘 이상 뜨지 않게 한다.
 * - 알림창 앱별 배율 조절([AppDensityNotifier]): Shizuku가 준비되면 [ForegroundWatcher]로 맨 앞 앱이 바뀔 때 알림 내용을 고친다.
 * - 흔들어서 화면 회전([ShakeRotate]): [ShakeEngine]이 화면이 켜져 있는 동안 흔들기를 감지한다.
 * 알림은 앱별 배율 조절이 켜져 있으면 그 알림(작게·기본·크게 버튼)을 쓰고, 흔들기만 켜져 있으면 조용한 알림 하나를 쓴다.
 * 둘 다 꺼지면 스스로 멈춘다. 켜고 끄는 쪽은 [sync]만 부른다.
 */
class GoatService : Service() {
    private val binderReceived = Shizuku.OnBinderReceivedListener { watchApps() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> watchApps() }
    private var shake: ShakeEngine? = null
    private var flex: AutoFlex.Engine? = null
    private var shizukuHooked = false
    private var lastRunning: String? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apps = AppDensityNotifier.enabled(this)
        val rotate = ShakeRotate.enabled(this) && ShakeRotate.canWrite(this)
        val autoFlex = AutoFlex.engineNeeded(this)
        val narrowBar = NarrowBar.enabled(this)
        if (!apps && !rotate && !autoFlex && !narrowBar) {
            stopSelf()
            return START_NOT_STICKY
        }
        val running = listOfNotNull("알림창 앱 배율".takeIf { apps }, "흔들어서 화면 회전".takeIf { rotate }, "자동 플렉스 모드".takeIf { autoFlex }, "좁힌 앱 컨트롤러".takeIf { narrowBar }).joinToString("·")
        if (running != lastRunning) {
            LogRepository.from(this).add("GOAT 상주 서비스: $running")
            lastRunning = running
        }
        // 켜진 기능에 맞는 알림으로 바꾼다(같은 번호라 알림은 하나로 남는다)
        startForegroundNow()

        // 자동 플렉스는 반쯤 접힘을 Shizuku로 걸어서, Shizuku 연결을 지켜본다(좁힌 앱 컨트롤러는 맨 앞 앱을 Shizuku로 따라간다)
        if (apps || autoFlex || narrowBar) {
            if (!shizukuHooked) {
                ShizukuAccess.watch(this)
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addRequestPermissionResultListener(permission)
                shizukuHooked = true
            }
            watchApps()
        } else {
            unhookShizuku()
        }
        if (!apps && !narrowBar) ForegroundWatcher.stop()
        if (!narrowBar) NarrowBar.hide(this)
        if (autoFlex) {
            if (flex == null) flex = AutoFlex.Engine(this).also { it.start() }
        } else {
            flex?.stop()
            flex = null
        }

        if (rotate) {
            (
                shake ?: ShakeEngine(this).also {
                    shake = it
                    it.start()
                }
                ).refresh()
        } else {
            shake?.stop()
            shake = null
        }
        return START_STICKY
    }

    private fun startForegroundNow() {
        // specialUse 종류는 Android 14부터 있다. 그 전에는 종류 없이 띄운다(FoldWatchService와 같은 방식).
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, AppDensityNotifier.NOTIFICATION_ID, notification(this), type)
    }

    private fun watchApps() {
        if (!AppDensityNotifier.enabled(this) && !NarrowBar.enabled(this)) return
        ForegroundWatcher.start(this) { pkg ->
            if (AppDensityNotifier.enabled(this)) AppDensityNotifier.show(this, AppDensityNotifier.describe(this, pkg))
            NarrowBar.update(this, pkg)
        }
    }

    private fun unhookShizuku() {
        if (!shizukuHooked) return
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeRequestPermissionResultListener(permission)
        shizukuHooked = false
    }

    override fun onDestroy() {
        LogRepository.from(this).add("GOAT 상주 서비스 멈춤")
        ForegroundWatcher.stop()
        NarrowBar.hide(this)
        shake?.stop()
        shake = null
        flex?.stop()
        flex = null
        unhookShizuku()
        AppDensityNotifier.cancel(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** 켜진 기능이 하나라도 있으면 서비스를 띄우거나 설정을 다시 읽게 하고, 없으면 멈춘다. */
        fun sync(context: Context) {
            val any = AppDensityNotifier.enabled(context) || (ShakeRotate.enabled(context) && ShakeRotate.canWrite(context)) || AutoFlex.engineNeeded(context) ||
                NarrowBar.enabled(context)
            val intent = Intent(context, GoatService::class.java)
            if (any) {
                runCatching { context.startForegroundService(intent) }
            } else {
                context.stopService(intent)
            }
        }

        /** 지금 켜진 기능에 맞는 알림. */
        fun notification(context: Context) = if (AppDensityNotifier.enabled(context)) {
            AppDensityNotifier.build(context, AppDensityNotifier.describe(context, null), AppDensityNotifier.shakeNote(context))
        } else if (ShakeRotate.enabled(context)) {
            ShakeRotate.buildNotification(context)
        } else if (AutoFlex.engineNeeded(context)) {
            AutoFlex.buildNotification(context)
        } else {
            NarrowBar.buildNotification(context)
        }
    }
}
