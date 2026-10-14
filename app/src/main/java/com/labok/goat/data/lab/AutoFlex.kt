package com.labok.goat.data.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 자동 플렉스 모드(폴드8 전용, 실험실). 폴드8은 반쯤 접힌 채 멈추는 힌지가 빠져 시스템이 '반쯤 접힘' 상태를 내지 않는다.
 * 그래서 플렉스 모드 패널이 뜨지 않는데, 시스템에 반쯤 접힘을 알려 주면(Shizuku `cmd device_state state`) 패널이 뜬다(실기 확인).
 * GOAT 상주 서비스가 화면이 켜진 동안 힌지 각도 센서를 듣다가 [min]~[max]도로 접으면 반쯤 접힘을 걸고, 벗어나면 푼다.
 */
object AutoFlex {
    private const val PREF = "auto_flex"
    const val DEFAULT_MIN = 75
    const val DEFAULT_MAX = 115
    const val ANGLE_LOW = 30
    const val ANGLE_HIGH = 160
    private const val CHANNEL_ID = "auto_flex_quiet"

    /** 폴드8(기본형, SM-F971)에서만 쓴다. 다른 폴드(울트라 포함)는 기본 플렉스 모드가 있다. */
    fun supported(): Boolean = Build.MANUFACTURER.equals("samsung", true) && Build.MODEL.uppercase().startsWith("SM-F971")

    fun enabled(context: Context) = supported() && prefs(context).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        LogRepository.from(context).add("자동 플렉스 모드 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 패널을 띄우지 않을 앱(사용자가 끈 앱). 나머지 앱은 모두 패널을 띄운다. */
    fun excluded(context: Context): Set<String> = prefs(context).getStringSet("excluded", emptySet()).orEmpty()

    /** [pkg]의 패널을 켜거나 끈다(끄면 예외 목록에 넣는다). 성공하면 null. 작업 스레드에서 부른다. */
    fun setAppPanel(context: Context, pkg: String, on: Boolean): String? {
        val err = FlexPanel.setApp(context, pkg, on)
        if (err == null) {
            val set = excluded(context).toMutableSet().apply { if (on) remove(pkg) else add(pkg) }
            prefs(context).edit().putStringSet("excluded", set).apply()
        }
        return err
    }

    /**
     * 모든 앱(예외 제외)을 패널 대상에 맞춘다: 켜져 있으면 등록, 꺼지면 GOAT가 등록한 앱을 모두 푼다.
     * 새로 설치한 앱도 잡히도록 서비스가 시작될 때와 화면이 켜질 때(한 시간에 한 번) 부른다. 작업 스레드에서 부른다.
     */
    fun syncApps(context: Context) {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY || !FlexPanel.hasAppApi()) return
        if (!enabled(context)) {
            FlexPanel.restoreApps(context)
            return
        }
        val skip = excluded(context)
        val mine = FlexPanel.registered(context)
        com.labok.goat.data.appsize.AppList.launcherApps(context)
            .map { it.pkg }
            .filter { it != context.packageName && it !in skip && it !in mine }
            .forEach { FlexPanel.setApp(context, it, true) }
        prefs(context).edit().putLong("synced", System.currentTimeMillis()).apply()
    }

    internal fun syncDue(context: Context) = System.currentTimeMillis() - prefs(context).getLong("synced", 0L) > 60 * 60 * 1000L

    /** 스크롤 휠 위치(삼성 설정 global.flex_mode_scroll_wheel_pos, 1 왼쪽·2 오른쪽으로 보임). */
    val wheel = LabSetting("global", "flex_mode_scroll_wheel_pos", "플렉스 스크롤 휠 위치")

    fun min(context: Context) = prefs(context).getInt("min", DEFAULT_MIN)
    fun max(context: Context) = prefs(context).getInt("max", DEFAULT_MAX)

    fun setRange(context: Context, min: Int, max: Int) {
        prefs(context).edit().putInt("min", min.coerceIn(ANGLE_LOW, ANGLE_HIGH)).putInt("max", max.coerceIn(ANGLE_LOW, ANGLE_HIGH)).apply()
    }

    /** 범위 안인지(시험할 수 있게 뗐다). */
    internal fun inRange(angle: Float, min: Int, max: Int) = angle >= min && angle <= max

    private val angleFlow = MutableStateFlow<Float?>(null)

    /** 지금 힌지 각도(듣는 동안만, 아니면 null). 화면에 실시간으로 보여 준다. */
    val angle: StateFlow<Float?> = angleFlow.asStateFlow()

    private val activeFlow = MutableStateFlow(false)

    /** 지금 반쯤 접힘을 걸어 둔 상태인지. */
    val active: StateFlow<Boolean> = activeFlow.asStateFlow()

    fun hasHingeSensor(context: Context) = context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "자동 플렉스 모드", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 21, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("자동 플렉스 모드")
            .setContentText("반쯤 접으면 플렉스 모드로 바뀝니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * 상주 서비스 안에서 도는 감시. 화면이 켜진 동안만 힌지 각도를 듣고, 범위에 0.25초 머물면 걸고/벗어나면 푼다.
     * 셸 명령은 따로 만든 스레드에서 부른다(센서 스레드를 막지 않게).
     */
    class Engine(private val context: Context) : SensorEventListener {
        private val thread = HandlerThread("auto-flex").also { it.start() }
        private val worker = Handler(thread.looper)
        private var listening = false
        private var started = false
        private var want = false
        private val apply = Runnable { set(want) }

        // 세밀한 각도(시스템 로그). 최근 1초 안에 값이 왔으면 거친 센서 값(0·90·180)은 무시한다
        @Volatile private var fineAt = 0L
        private val fine = FineHinge { a ->
            fineAt = android.os.SystemClock.elapsedRealtime()
            worker.post { onAngle(a) }
        }

        private val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    listen()
                    if (syncDue(context)) worker.post { syncApps(context) }
                } else {
                    stopListening()
                }
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
            worker.post { syncApps(context) }
        }

        fun stop() {
            if (!started) return
            started = false
            runCatching { context.unregisterReceiver(screen) }
            stopListening()
            worker.removeCallbacksAndMessages(null)
            worker.post {
                set(false)
                thread.quitSafely()
            }
        }

        private fun listen() {
            if (listening) return
            val sm = context.getSystemService(SensorManager::class.java)
            val hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
            listening = sm.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI, worker)
            fine.start()
        }

        private fun stopListening() {
            if (!listening) return
            context.getSystemService(SensorManager::class.java).unregisterListener(this)
            fine.stop()
            listening = false
            angleFlow.value = null
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (android.os.SystemClock.elapsedRealtime() - fineAt < 1000) return
            onAngle(event.values[0])
        }

        private fun onAngle(a: Float) {
            angleFlow.value = a
            val next = inRange(a, min(context), max(context))
            if (next == want && worker.hasCallbacks(apply)) return
            want = next
            worker.removeCallbacks(apply)
            if (next != activeFlow.value) worker.postDelayed(apply, 250)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun set(on: Boolean) {
            if (on == activeFlow.value) return
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return
            val err = if (on) FlexPanel.forceHalfOpened(context) else FlexPanel.resetPosture(context)
            if (err == null) activeFlow.value = on
        }
    }
}
