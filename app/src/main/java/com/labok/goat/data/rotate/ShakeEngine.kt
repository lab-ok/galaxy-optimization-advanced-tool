package com.labok.goat.data.rotate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy

/**
 * 흔들어서 화면 회전의 실제 동작. GOAT 상주 서비스([com.labok.goat.background.GoatService]) 안에서 돈다.
 *
 * 화면이 켜져 있을 때만 가속도 센서를 듣는다. 흔들면 그 순간만 시스템 자동 회전을 켜서, 시스템이 폰을 든 방향대로
 * 화면을 돌리게 둔 뒤 그 방향에 다시 잠근다(세로로 든 채 흔들면 세로 그대로). 돌아갔으면 흔든 리듬대로 진동한다.
 */
class ShakeEngine(private val context: Context) : SensorEventListener {
    private val detector = ShakeRotatePolicy.Detector().also { applyLevel(it) }

    /** 근접 센서가 가려져 있으면(주머니·가방 속) 흔들어도 돌리지 않는다. */
    private var covered = false
    private val main = Handler(Looper.getMainLooper())
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var listening = false
    private var started = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) listen() else stopListening()
        }
    }

    fun start() {
        if (started) return
        started = true
        context.registerReceiver(
            screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
        if (context.getSystemService(PowerManager::class.java).isInteractive) listen()
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacksAndMessages(null)
        if (turning) finishTurn()
        stopListening()
        runCatching { context.unregisterReceiver(screen) }
    }

    /** 민감도를 다시 읽는다. */
    fun refresh() = applyLevel(detector)

    private fun applyLevel(d: ShakeRotatePolicy.Detector) {
        val level = ShakeRotate.level(context)
        d.threshold = ShakeRotatePolicy.thresholdOf(level)
        d.gyroMin = ShakeRotatePolicy.gyroOfTurn(ShakeRotate.turn(context))
        d.maxGap = ShakeRotatePolicy.gapOfPace(ShakeRotate.pace(context))
        d.needed = ShakeRotate.swings(context)
        d.rejectTaps = ShakeRotate.rejectTaps(context)
    }

    /** 가속도(판정), 자이로(손목 회전 확인), 근접(주머니 속 무시)을 듣는다. 자이로·근접 센서가 없는 기기면 가속도만. */
    private fun listen() {
        if (listening) return
        val sm = context.getSystemService(SensorManager::class.java)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopListening() {
        if (!listening) return
        context.getSystemService(SensorManager::class.java).unregisterListener(this)
        listening = false
        covered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        // 센서 값이 실제로 잰 시각(ms). 전달이 몰려 와도 휘두름 간격을 정확히 잰다.
        val now = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> detector.feedGyro(v[0], v[1], v[2], now)
            // 근접 센서는 기기마다 '멀다'를 최대 거리나 5cm 등으로 알린다: 최대 거리의 절반보다 가까울 때만 가려진 것으로 본다
            Sensor.TYPE_PROXIMITY -> covered = v[0] < event.sensor.maximumRange * 0.5f
            Sensor.TYPE_ACCELEROMETER -> {
                // 빠른 회전용 중력: 흔드는 동안의 출렁임이 평균으로 지워지도록 느린 저역 통과(시간 상수 약 0.4초)
                if (gravityAt == 0L) {
                    gx = v[0]
                    gy = v[1]
                } else {
                    gx += (v[0] - gx) * 0.1f
                    gy += (v[1] - gy) * 0.1f
                }
                gravityAt = now
                val shook = detector.feed(v[0], v[1], v[2], now)
                detector.lastMiss?.let { missed(it) }
                if (!shook) return
                if (covered) {
                    missed("근접 센서 가려짐(주머니 속)")
                    return
                }
                // '큰 화면에서만'이면 접은 상태(작은 화면)에서는 돌리지 않는다
                if (ShakeRotate.largeOnly(context) && !ShakeRotate.isLargeScreen(context)) {
                    missed("큰 화면에서만 켜져 있어 작은 화면에서는 건너뜀")
                    return
                }
                if (ShakeRotate.calibrating || turning) return
                beginTurn()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private var lastMissLog = 0L

    /** 흔들었는데 돌리지 않은 이유를 로그에 남긴다(문제 신고로 원인을 볼 수 있게, 5초에 한 번만). */
    private fun missed(why: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMissLog < 5000) return
        lastMissLog = now
        LogRepository.from(context).add("흔들어서 화면 회전: 흔들림은 감지했지만 회전하지 않음 - $why")
    }

    // --- 흔든 순간만 자동 회전 ---

    private var turning = false
    private var before = 0

    private val rotated = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY || ShakeRotate.currentRotation(context) == before) return
            // 돌아간 뒤 바로 잠그면 시스템이 마저 돌리는 중일 수 있어 잠깐 기다린다
            main.removeCallbacks(timeout)
            main.postDelayed({ finishTurn() }, SETTLE_MS)
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }
    private val timeout = Runnable { finishTurn() }

    private var gx = 0f
    private var gy = 0f
    private var gravityAt = 0L

    private fun beginTurn() {
        before = ShakeRotate.currentRotation(context)
        // 빠른 회전(기본 켬): 든 방향이 분명하면 시스템을 기다리지 않고 바로 그 방향으로 잠근다(애매하면 아래 기존 방식)
        if (ShakeRotate.fastTurn(context)) {
            ShakeRotatePolicy.rotationOf(gx, gy)?.let { r ->
                val log = LogRepository.from(context)
                if (r == before) {
                    log.add("흔들어서 화면 회전(빠른 회전): 든 방향이 그대로라 ${ShakeRotate.label(r)} 유지")
                    return
                }
                if (!ShakeRotate.lock(context, r)) return
                ShakeHaptics.play(context, detector.lastPattern, ShakeRotate.vibration(context))
                log.add("흔들어서 화면 회전(빠른 회전): ${ShakeRotate.label(before)}($before) → ${ShakeRotate.label(r)}($r)")
                return
            }
        }
        val ok = runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) }.getOrDefault(false)
        if (!ok) return
        turning = true
        ShakeRotate.turning = true
        displays.registerDisplayListener(rotated, main)
        // 이미 든 방향과 화면 방향이 같으면 돌아가지 않는다: 그때는 이 시간이 지나면 그대로 다시 잠근다
        main.postDelayed(timeout, WAIT_MS)
    }

    private fun finishTurn() {
        if (!turning) return
        displays.unregisterDisplayListener(rotated)
        main.removeCallbacks(timeout)
        val now = ShakeRotate.currentRotation(context)
        ShakeRotate.lock(context, now)
        turning = false
        ShakeRotate.turning = false
        val log = LogRepository.from(context)
        if (now != before) {
            // 알림 없이, 방금 흔든 리듬 그대로 진동으로만 알린다(휙 휘힉 → 톡 토독)
            ShakeHaptics.play(context, detector.lastPattern, ShakeRotate.vibration(context))
            log.add("흔들어서 화면 회전: ${ShakeRotate.label(before)}($before) → ${ShakeRotate.label(now)}($now)")
        } else {
            log.add("흔들어서 화면 회전: 흔들었지만 든 방향이 그대로라 ${ShakeRotate.label(now)} 유지")
        }
    }

    private companion object {
        /** 자동 회전을 켜 둔 채 시스템이 돌리기를 기다리는 최대 시간. */
        const val WAIT_MS = 1600L

        /** 돌아간 뒤 잠그기 전에 기다리는 시간. */
        const val SETTLE_MS = 250L
    }
}
