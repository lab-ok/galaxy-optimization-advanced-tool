package com.labok.goat.data.rotate

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy

/**
 * 흔들기 감시 서비스. 화면이 켜져 있을 때만 가속도 센서를 듣고(꺼지면 멈춰 배터리를 아낀다),
 * 흔들면 폰이 놓인 방향으로 화면을 돌려 그 방향에 잠근다.
 */
class ShakeRotateService : Service(), SensorEventListener {
    private val detector = ShakeRotatePolicy.Detector()
    private var listening = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) listen() else stopListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ShakeRotate.NOTIFICATION_ID, ShakeRotate.buildNotification(this), type)
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        if (getSystemService(PowerManager::class.java).isInteractive) listen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        detector.sensitivity = ShakeRotate.sensitivity(this)
        return START_STICKY
    }

    private fun listen() {
        if (listening) return
        val sm = getSystemService(SensorManager::class.java)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopListening() {
        if (!listening) return
        getSystemService(SensorManager::class.java).unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (!detector.feed(v[0], v[1], v[2], SystemClock.elapsedRealtime())) return
        val now = ShakeRotate.currentRotation(this)
        val next = ShakeRotatePolicy.next(now, detector.heldBefore)
        if (!ShakeRotate.lock(this, next)) return
        // 알림은 바꾸지 않고 진동(토도독)으로만 알린다(사용자 요청)
        buzz()
        LogRepository.from(this).add("흔들어 화면 회전: ${ShakeRotate.label(now)}($now) → ${ShakeRotate.label(next)}($next)")
    }

    /** 짧은 틱 세 번(토도독). 기기가 틱 효과를 못 내면 짧은 진동 세 번으로 대신한다. */
    private fun buzz() = runCatching {
        val v = getSystemService(Vibrator::class.java) ?: return@runCatching
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val effect = if (v.areAllPrimitivesSupported(tick)) {
            VibrationEffect.startComposition().addPrimitive(tick).addPrimitive(tick, 1f, 70).addPrimitive(tick, 1f, 70).compose()
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 18, 60, 18, 60, 18), -1)
        }
        v.vibrate(effect)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        stopListening()
        runCatching { unregisterReceiver(screen) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
