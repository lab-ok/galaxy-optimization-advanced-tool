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
import com.labok.goat.data.lab.AmbientOpen
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.PowerRotateEngine
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
    private var power: PowerRotateEngine? = null
    private var flex: AutoFlex.Engine? = null
    private var ambient: AmbientOpen.Engine? = null
    private var shizukuHooked = false
    private var lastRunning: String? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apps = AppDensityNotifier.enabled(this)
        val rotate = ShakeRotate.enabled(this) && ShakeRotate.canWrite(this)
        val powerRotate = PowerRotate.enabled(this)
        val autoFlex = AutoFlex.engineNeeded(this)
        val ambientOpen = AmbientOpen.enabled(this)
        // 실험실 위스퍼 음성 입력: 입력기 바뀜을 지켜보려면 GOAT 프로세스가 살아 있어야 한다
        val voice = com.labok.goat.data.lab.VoiceRedirect.enabled(this)
        if (voice) com.labok.goat.data.lab.VoiceRedirect.watch(this)
        if (!apps && !rotate && !powerRotate && !autoFlex && !ambientOpen && !voice) {
            stopSelf()
            return START_NOT_STICKY
        }
        val running = listOfNotNull("알림창 앱 배율".takeIf { apps }, "흔들어서 화면 회전".takeIf { rotate }, getString(com.labok.goat.R.string.power_rotate_title).takeIf { powerRotate }, "플렉스 모드".takeIf { autoFlex }, "앰비언트 모드".takeIf { ambientOpen }, com.labok.goat.ui.text.Copy.FEATURE_LAB_VOICE.takeIf { voice }).joinToString("·")
        if (running != lastRunning) {
            LogRepository.from(this).add("GOAT 상주 서비스: $running")
            lastRunning = running
        }
        // 켜진 기능에 맞는 알림으로 바꾼다(같은 번호라 알림은 하나로 남는다)
        startForegroundNow()
        // Shizuku 끊김 알림이 떠 있으면 켜진 기능에 맞게 고친다
        com.labok.goat.data.shizuku.ShizukuNotice.refreshIfShown(this)

        // 자동 플렉스는 반쯤 접힘을 Shizuku로 걸어서, Shizuku 연결을 지켜본다
        if (apps || autoFlex || ambientOpen) {
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
        if (!apps) ForegroundWatcher.stop()
        if (autoFlex) {
            if (flex == null) flex = AutoFlex.Engine(this).also { it.start() }
        } else {
            flex?.stop()
            flex = null
        }
        if (ambientOpen) {
            if (ambient == null) ambient = AmbientOpen.Engine(this).also { it.start() }
        } else {
            ambient?.stop()
            ambient = null
        }

        val powerOnce = powerRotate && PowerRotate.mode(this) == PowerRotate.Mode.ONCE && PowerRotate.canWriteRotation(this) && PowerRotate.canDraw(this)
        if (rotate || powerOnce) {
            (
                shake ?: ShakeEngine(this) { ShakeRotate.enabled(this) && ShakeRotate.canWrite(this) }.also {
                    shake = it
                    it.start()
                }
                ).refresh()
        } else {
            shake?.stop()
            shake = null
        }
        if (powerRotate) {
            if (power == null) power = PowerRotateEngine(this) { rotation ->
                when (PowerRotate.mode(this)) {
                    PowerRotate.Mode.TOGGLE -> {
                        shake?.cancelForPowerToggle()
                        PowerRotate.toggle(this, rotation)
                    }
                    PowerRotate.Mode.ONCE -> shake?.rotateOnce() ?: false
                }
            }
            power?.refresh()
        } else {
            power?.stop()
            power = null
        }
        return START_STICKY
    }

    private fun startForegroundNow() {
        // specialUse 종류는 Android 14부터 있다. 그 전에는 종류 없이 띄운다(FoldWatchService와 같은 방식).
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, AppDensityNotifier.NOTIFICATION_ID, notification(this), type)
    }

    private fun watchApps() {
        if (!AppDensityNotifier.enabled(this)) return
        ForegroundWatcher.start(this) { pkg ->
            if (AppDensityNotifier.enabled(this)) AppDensityNotifier.show(this, AppDensityNotifier.describe(this, pkg))
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
        power?.stop()
        power = null
        shake?.stop()
        shake = null
        flex?.stop()
        flex = null
        ambient?.stop()
        ambient = null
        unhookShizuku()
        AppDensityNotifier.cancel(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** 켜진 기능이 하나라도 있으면 서비스를 띄우거나 설정을 다시 읽게 하고, 없으면 멈춘다. */
        fun sync(context: Context) {
            val any = PowerRotate.enabled(context) || AppDensityNotifier.enabled(context) ||
                (ShakeRotate.enabled(context) && ShakeRotate.canWrite(context)) ||
                AutoFlex.engineNeeded(context) ||
                AmbientOpen.enabled(context)
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
        } else if (PowerRotate.enabled(context)) {
            PowerRotate.buildNotification(context)
        } else {
            AmbientOpen.buildNotification(context)
        }
    }
}
