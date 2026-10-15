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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.appsize.AppList
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import kotlin.math.abs
import kotlin.math.sqrt
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
    const val DEFAULT_MIN = 30
    const val DEFAULT_MAX = 125
    const val ANGLE_LOW = 30
    const val ANGLE_HIGH = 160
    private const val CHANNEL_ID = "auto_flex_quiet"

    /** 폴드8(기본형, SM-F971)에서만 쓴다. 다른 폴드(울트라 포함)는 기본 플렉스 모드가 있다. */
    fun supported(): Boolean = Build.MANUFACTURER.equals("samsung", true) && Build.MODEL.uppercase().startsWith("SM-F971")

    fun enabled(context: Context) = supported() && prefs(context).getBoolean("on", false)

    /**
     * 가까이 접으면 밝기 고정: [DIM_ANGLE]° 안쪽으로 접으면 두 화면이 마주 보며 서로의 빛을 밝기 센서가 받아 화면이 밝아진다.
     * 그 안에서는 자동 밝기를 잠시 끄고 지금 밝기에 묶었다가, 다시 펴면 자동 밝기로 돌린다(Shizuku 셸로 설정을 바꾼다).
     */
    fun dimLock(context: Context) = supported() && prefs(context).getBoolean("dimLock", false)

    fun setDimLock(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("dimLock", on).apply()
        LogRepository.from(context).add("가까이 접으면 밝기 고정 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 상주 서비스가 힌지를 들어야 하는지(자동 플렉스나 밝기 고정 중 하나라도 켜짐). */
    fun engineNeeded(context: Context) = enabled(context) || dimLock(context)

    internal const val DIM_ANGLE = 50f

    /** 범위에서 이만큼(ms) 각도가 [STABLE_DEG]° 넘게 움직이지 않아야 플렉스 모드를 켠다. */
    internal const val STABLE_MS = 600L
    internal const val STABLE_DEG = 5f

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        LogRepository.from(context).add("자동 플렉스 모드 ${if (on) "켜짐" else "꺼짐"}")
    }

    /**
     * 앱별로 접었을 때 할 일. 기본은 [MODE_FLEX](반쯤 접힘만 알려 앱에 들어 있는 플렉스 모드를 쓴다).
     * [MODE_PANEL]은 앱을 위 절반, 삼성 터치패널을 아래 절반에 띄우고, [MODE_SPLIT]은 삼성 공식 화면 분할로 고른 앱과 나눈다.
     * [MODE_OFF]는 그 앱에서는 접어도 아무것도 하지 않는다.
     */
    const val MODE_FLEX = 0
    const val MODE_PANEL = 1
    const val MODE_SPLIT = 2
    const val MODE_OFF = 3
    val MODES = listOf(MODE_FLEX, MODE_PANEL, MODE_SPLIT, MODE_OFF)

    fun modeLabel(mode: Int) = when (mode) {
        MODE_PANEL -> "터치패널"
        MODE_SPLIT -> "분할 화면"
        MODE_OFF -> "사용 안 함"
        else -> "플렉스 모드"
    }

    fun mode(context: Context, pkg: String): Int = prefs(context).getInt("mode:$pkg", MODE_FLEX)

    /** 기본값이 아닌 모드를 고른 앱(패키지 → 모드). */
    fun modes(context: Context): Map<String, Int> = prefs(context).all
        .filterKeys { it.startsWith("mode:") }
        .mapNotNull { (k, v) -> (v as? Int)?.takeIf { it != MODE_FLEX }?.let { k.removePrefix("mode:") to it } }
        .toMap()

    fun setMode(context: Context, pkg: String, mode: Int) {
        prefs(context).edit().apply { if (mode == MODE_FLEX) remove("mode:$pkg") else putInt("mode:$pkg", mode) }.apply()
        LogRepository.from(context).add("자동 플렉스: $pkg 접으면 ${modeLabel(mode)}")
    }

    /** 패널을 띄우지 않을 앱(사용자가 끈 앱). 나머지 앱은 모두 패널을 띄운다. */
    fun excluded(context: Context): Set<String> = prefs(context).getStringSet("excluded", emptySet()).orEmpty()

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
        AppList.launcherApps(context)
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

    /**
     * 까딱으로 켜기: 완전히 펼친 채 130~160°까지 살짝 접었다가(까딱) 3초 안에 다시 펴면 플렉스 모드를 켜고, 한 번 더 하면 끈다.
     * 켜 두는 동안은 펼친 채로도 플렉스 모드처럼 쓴다(위 화면에 앱, 아래 화면에 패널).
     */
    fun click(context: Context) = prefs(context).getBoolean("click", false)

    fun setClick(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("click", on).apply()
        LogRepository.from(context).add("까딱으로 플렉스 모드 ${if (on) "켜짐" else "꺼짐"}")
        engine?.recheck()
    }

    /**
     * 까딱 판정: 완전히 펼친 상태([OPEN]° 이상)에서 [DIP_LOW]~[DIP_HIGH]°까지 접었다가 [CLICK_MS] 안에 다시 펴면([REOPEN]° 이상) 한 번.
     * 다 펴도 센서가 잠깐 167~170°에 머무를 때가 있어, 되펴는 기준은 [OPEN]보다 낮춘다.
     */
    internal const val OPEN = 175f
    internal const val REOPEN = 165f
    internal const val DIP_LOW = 130f
    internal const val DIP_HIGH = 160f
    internal const val CLICK_MS = 3000L

    /**
     * 펼침→살짝 접힘→펼침 흐름을 보고 까딱인지 가린다(시험할 수 있게 뗐다). 펼침을 벗어난 때부터 다시 펼 때까지 3초 안이어야 하고,
     * 그사이 가장 많이 접은 각도가 130~160° 안이어야 한다(덜 접었으면 흔들림, 더 접었으면 진짜로 접는 것).
     */
    internal class ClickDetector {
        private var open = false
        private var leftAt = -1L
        private var lowest = 180f

        /** 까딱이 끝난 순간 true. */
        fun feed(angle: Float, now: Long): Boolean {
            val dipped = leftAt >= 0 && lowest <= DIP_HIGH
            if (angle >= OPEN || (dipped && angle >= REOPEN)) {
                val hit = open && leftAt >= 0 && now - leftAt <= CLICK_MS && lowest in DIP_LOW..DIP_HIGH
                open = true
                leftAt = -1
                lowest = 180f
                return hit
            }
            if (open && leftAt < 0) leftAt = now
            if (leftAt >= 0) lowest = minOf(lowest, angle)
            if (leftAt >= 0 && now - leftAt > CLICK_MS) open = false
            return false
        }
    }

    private val pausedFlow = MutableStateFlow(false)
    private val pauses = mutableSetOf<String>()

    /**
     * 자동 플렉스를 잠시 멈춘다(걸어 둔 반쯤 접힘도 푼다). 멈추는 까닭마다 [key]를 따로 두고, 하나라도 남아 있으면 멈춰 있다.
     * 예: 자동 플렉스 설정 화면을 보는 동안("screen"), 게이지 손잡이를 끄는 동안("drag").
     */
    fun setPaused(key: String, on: Boolean) {
        synchronized(pauses) {
            if (on) pauses.add(key) else pauses.remove(key)
            pausedFlow.value = pauses.isNotEmpty()
        }
        engine?.recheck()
    }

    @Volatile private var engine: Engine? = null

    /**
     * 까딱 진동: 접었다 편 손놀림을 그대로 되짚는다. 켤 때는 접힘(가벼운 틱)→펴짐(까딱)→켜짐(까딱) 세 박,
     * 끌 때는 힘이 빠지듯 내려가는 틱 한 번. 효과 조합을 못 쓰는 기기는 짧은 진동 길이로 같은 박자를 낸다.
     */
    private fun buzz(context: Context, on: Boolean) {
        val v = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!v.hasVibrator()) return
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val clickP = VibrationEffect.Composition.PRIMITIVE_CLICK
        val fall = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        val effect = if (on && v.areAllPrimitivesSupported(tick, clickP)) {
            VibrationEffect.startComposition()
                .addPrimitive(tick, 0.6f)
                .addPrimitive(clickP, 1f, 70)
                .addPrimitive(clickP, 1f, 110)
                .compose()
        } else if (!on && v.areAllPrimitivesSupported(fall)) {
            VibrationEffect.startComposition().addPrimitive(fall, 0.8f).compose()
        } else if (on) {
            VibrationEffect.createWaveform(longArrayOf(0, 12, 70, 25, 110, 25), intArrayOf(0, 120, 0, 255, 0, 255), -1)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 40), intArrayOf(0, 140), -1)
        }
        runCatching { v.vibrate(effect) }
    }

    /**
     * 범위 진동: 들어오면 스스스 점점 세지는 진동(천천히 차오름), 벗어나면 탁 치고 힘이 빠지는 진동.
     * 효과 조합을 못 쓰는 기기는 진동 세기를 단계로 올리고 내려 비슷하게 낸다.
     */
    fun rangeBuzz(context: Context, inside: Boolean) {
        val v = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!v.hasVibrator()) return
        val rise = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        val clickP = VibrationEffect.Composition.PRIMITIVE_CLICK
        val fall = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        val effect = if (inside && v.areAllPrimitivesSupported(rise)) {
            VibrationEffect.startComposition().addPrimitive(rise, 0.9f).compose()
        } else if (!inside && v.areAllPrimitivesSupported(clickP, fall)) {
            VibrationEffect.startComposition().addPrimitive(clickP, 1f).addPrimitive(fall, 0.6f, 10).compose()
        } else if (inside) {
            VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60, 60, 60), intArrayOf(0, 40, 90, 150, 210, 255), -1)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 18, 50), intArrayOf(0, 255, 50), -1)
        }
        runCatching { v.vibrate(effect) }
    }

    /** 범위 안인지(시험할 수 있게 뗐다). */
    internal fun inRange(angle: Float, min: Int, max: Int) = angle >= min && angle <= max

    private val angleFlow = MutableStateFlow<Float?>(null)

    /** 지금 힌지 각도(듣는 동안만, 아니면 null). 화면에 실시간으로 보여 준다. */
    val angle: StateFlow<Float?> = angleFlow.asStateFlow()

    private val activeFlow = MutableStateFlow(false)

    /**
     * 접는 선 방향: 플렉스 모드는 노트북처럼 접는 선이 가로로 놓일 때만 쓴다. 중력 중 접는 선(힌지 축) 방향 성분이 작으면
     * (축이 바닥과 거의 나란하면) 가로로 본다. 폴드의 힌지 축은 기본 방향에서 세로(Y)지만, 화면이 알려 준 방향으로 고쳐 둘 수 있다([setHingeAlongY]).
     */
    /**
     * 세밀한 값이 끊겼을 때 기본 센서 값 [coarse]로 바꿔 믿을지. 끝값(0°·180°)이고, 마지막 세밀한 값 [lastFine]도
     * 그쪽 끝 가까이(45° 이하·135° 이상)였을 때만 믿는다. 빨리 펴거나 닫아 로그가 멈춘 경우는 마지막 값이 이미 끝 가까이다.
     */
    internal fun coarseOverride(coarse: Float, lastFine: Float?): Boolean = when {
        lastFine == null -> coarse <= 0f || coarse >= 180f
        coarse == lastFine -> false
        coarse <= 0f -> lastFine <= 45f
        coarse >= 180f -> lastFine >= 135f
        else -> false
    }

    internal fun isHorizontal(gx: Float, gy: Float, gz: Float, alongY: Boolean): Boolean {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 1f) return true
        return abs(if (alongY) gy else gx) < 0.5f * g
    }

    fun hingeAlongY(context: Context) = prefs(context).getBoolean("hingeY", true)

    fun setHingeAlongY(context: Context, alongY: Boolean) {
        if (hingeAlongY(context) != alongY) prefs(context).edit().putBoolean("hingeY", alongY).apply()
    }

    private val horizontalFlow = MutableStateFlow(true)

    /** 지금 접는 선이 가로로 놓였는지(서비스가 듣는 동안). */
    val horizontal: StateFlow<Boolean> = horizontalFlow.asStateFlow()

    private val latchedFlow = MutableStateFlow(false)

    /** 까딱으로 켜 둔 상태인지. */
    val latched: StateFlow<Boolean> = latchedFlow.asStateFlow()

    /** 지금 반쯤 접힘을 걸어 둔 상태인지. */
    val active: StateFlow<Boolean> = activeFlow.asStateFlow()

    fun hasHingeSensor(context: Context) = context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "자동 플렉스 모드", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 21, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle(if (enabled(context)) "자동 플렉스 모드" else "가까이 접으면 밝기 고정")
            .setContentText(if (enabled(context)) "반쯤 접으면 플렉스 모드로 바뀝니다" else "50° 안쪽으로 접으면 밝기를 고정합니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * 상주 서비스 안에서 도는 감시. 화면이 켜진 동안만 힌지 각도를 듣고, 범위에 0.25초 머물면 걸고/벗어나면 푼다.
     * 까딱으로 켜기를 쓰면 까딱마다 켜고 끄며, 켜 둔 동안은 각도와 상관없이 걸어 둔다(까딱 도중 범위를 지나쳐도 깜빡이지 않게 머무는 시간을 늘린다).
     * 셸 명령은 따로 만든 스레드에서 부른다(센서 스레드를 막지 않게).
     */
    class Engine(private val context: Context) : SensorEventListener {
        private val thread = HandlerThread("auto-flex").also { it.start() }
        private val worker = Handler(thread.looper)
        private var listening = false
        private var started = false
        private var want = false
        private val apply = Runnable { set(want) }
        private val clicks = ClickDetector()

        // 켜기를 기다리기 시작한 각도(멈춰 있는지 보려고)
        private var dwellAngle = 0f

        // 밝기 고정: GOAT가 자동 밝기를 꺼 둔 상태인지
        private var dimmed = false

        private fun dimCheck(a: Float) {
            if (!dimLock(context) && !dimmed) return
            val want = dimLock(context) && a < DIM_ANGLE
            // 경계에서 깜빡이지 않게 풀 때는 2° 더 펴야 한다
            if (want && !dimmed) {
                val mode = ShizukuAccess.shell("settings get system screen_brightness_mode")?.out?.trim()
                if (mode == "1" && ShizukuAccess.shell("settings put system screen_brightness_mode 0")?.ok == true) {
                    dimmed = true
                    LogRepository.from(context).add("가까이 접어 밝기 고정")
                }
            } else if (dimmed && (!dimLock(context) || a >= DIM_ANGLE + 2)) {
                undim()
            }
        }

        private fun undim() {
            if (!dimmed) return
            ShizukuAccess.shell("settings put system screen_brightness_mode 1")
            dimmed = false
        }

        // 다음 바뀜은 진동 없이(까딱은 따로 울리고, 멈춤·설정 바뀜은 손놀림이 아니라서)
        @Volatile private var quiet = false
        private var last: Float? = null

        // 세밀한 각도(시스템 로그). 최근 1초 안에 값이 왔으면 거친 센서 값(0·90·180)은 무시한다
        @Volatile private var fineAt = 0L
        private val fine = FineHinge { a ->
            fineAt = SystemClock.elapsedRealtime()
            // 시스템이 '닫힘'(기본 센서 0°)이라고 하면 세밀한 값은 믿지 않는다: 접은 채 움직이면 센서 부품이 엉뚱한 각도를 낼 수 있어,
            // 커버 화면이 켜진 채(접혀서 서비스가 계속 듣는 동안) 범위에 든 것으로 잘못 보고 플렉스·진동이 켜질 수 있었다
            worker.post { onAngle(if (coarseLast == 0f) 0f else a) }
            worker.removeCallbacks(coarseFallback)
            worker.postDelayed(coarseFallback, 1100)
        }

        private val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    listen()
                    if (syncDue(context)) worker.post { syncApps(context) }
                } else {
                    stopListening()
                    worker.post { undim() }
                    // 화면을 끄면 까딱으로 켠 플렉스 모드도 끈다
                    worker.post {
                        if (latchedFlow.value) {
                            latchedFlow.value = false
                            set(false)
                        }
                    }
                }
            }
        }

        fun start() {
            if (started) return
            started = true
            engine = this
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
            if (engine === this) engine = null
            latchedFlow.value = false
            runCatching { context.unregisterReceiver(screen) }
            stopListening()
            worker.removeCallbacksAndMessages(null)
            worker.post {
                undim()
                set(false)
                thread.quitSafely()
            }
        }

        // 접는 선 방향: 세로로 접힌 채(책처럼)는 플렉스 모드를 걸지 않는다. 방향이 바뀌면 지금 각도로 다시 정한다
        private val gravity = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val h = isHorizontal(e.values[0], e.values[1], e.values[2], hingeAlongY(context))
                if (h == horizontalFlow.value) return
                horizontalFlow.value = h
                decide(last, 300L)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        private fun listen() {
            if (listening) return
            val sm = context.getSystemService(SensorManager::class.java)
            val hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
            listening = sm.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI, worker)
            (sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))?.let {
                sm.registerListener(gravity, it, SensorManager.SENSOR_DELAY_NORMAL, worker)
            }
            fine.start()
        }

        private fun stopListening() {
            if (!listening) return
            context.getSystemService(SensorManager::class.java).unregisterListener(this)
            context.getSystemService(SensorManager::class.java).unregisterListener(gravity)
            fine.stop()
            listening = false
            angleFlow.value = null
        }

        // 기본 센서의 마지막 값. 세밀한 값이 1초 넘게 끊기면(빨리 펴거나 닫아 로그가 멈춘 때) 끝값(0°·180°)이면 그걸 믿는다.
        // 다만 세밀한 값도 그 끝 가까이에 있었을 때만 믿는다([coarseOverride]): 반쯤 접어 가만히 들고 있으면 세밀한 값이
        // 멈추는데(1.5° 안 움직이면 새 값이 없다), 기본 센서는 반쯤 접혀도 180°로 남아 있을 수 있어 범위로 켠 플렉스가 1초 만에 꺼졌다
        @Volatile private var coarseLast: Float? = null
        private val coarseFallback = Runnable {
            val c = coarseLast ?: return@Runnable
            if (SystemClock.elapsedRealtime() - fineAt >= 1000 && coarseOverride(c, last)) onAngle(c)
        }

        override fun onSensorChanged(event: SensorEvent) {
            coarseLast = event.values[0]
            if (SystemClock.elapsedRealtime() - fineAt < 1000) {
                worker.removeCallbacks(coarseFallback)
                worker.postDelayed(coarseFallback, 1100)
                return
            }
            onAngle(event.values[0])
        }

        private fun onAngle(a: Float) {
            angleFlow.value = a
            last = a
            dimCheck(a)
            if (!enabled(context)) return
            val click = click(context)
            var hit = false
            if (click && !pausedFlow.value && clicks.feed(a, SystemClock.elapsedRealtime()) && horizontalFlow.value) {
                // '사용 안 함'으로 둔 앱에서는 까딱해도 켜지 않고 울리지도 않는다(켜 둔 것을 끄는 까딱은 그대로 받는다)
                if (!latchedFlow.value && FlexPanel.frontIsOff(context)) {
                    LogRepository.from(context).add("까딱: 사용 안 함 앱이라 무시")
                } else {
                    latchedFlow.value = !latchedFlow.value
                    hit = true
                    LogRepository.from(context).add("까딱: 플렉스 모드 ${if (latchedFlow.value) "켬" else "끔"}")
                    buzz(context, latchedFlow.value)
                }
            }
            if (a < ANGLE_LOW && latchedFlow.value) {
                latchedFlow.value = false // 닫으면 까딱으로 켠 것도 끈다
                hit = true
            }
            if (hit) quiet = true
            // 까딱은 바로 바꾼다. 각도 범위는 그 안에서 멈춰 있을 때만(STABLE_MS 동안 STABLE_DEG 넘게 움직이지 않음) 켠다:
            // 펴거나 닫는 도중에 범위를 지나칠 때는 각도가 계속 움직이므로 켜지지도, 울리지도 않는다
            decide(a, if (hit || latchedFlow.value) 0L else STABLE_MS)
        }

        private fun decide(a: Float?, delay: Long) {
            val next = when {
                !enabled(context) -> false
                pausedFlow.value -> false
                latchedFlow.value -> true
                a == null -> false
                else -> inRange(a, min(context), max(context)) && horizontalFlow.value
            }
            if (next == want && worker.hasCallbacks(apply)) {
                // 켜기를 기다리는 동안 각도가 크게 움직이면(펴거나 닫는 중) 기다림을 처음부터 다시 센다
                if (next && a != null && abs(a - dwellAngle) > STABLE_DEG) {
                    dwellAngle = a
                    worker.removeCallbacks(apply)
                    worker.postDelayed(apply, delay)
                }
                return
            }
            want = next
            if (a != null) dwellAngle = a
            worker.removeCallbacks(apply)
            // 끌 때는 기다리지 않아도 된다(빨리 원래대로): 켤 때만 멈춰 있는지 기다린다
            if (next != activeFlow.value) worker.postDelayed(apply, if (next) delay else minOf(delay, 250L)) else quiet = false
        }

        /** 멈춤·까딱 설정이 바뀌었을 때 지금 각도로 다시 정한다. */
        fun recheck() {
            worker.post {
                if (!click(context)) latchedFlow.value = false
                quiet = true
                decide(last, 0L)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun set(on: Boolean) {
            if (on == activeFlow.value) return
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return
            // '사용 안 함'으로 둔 앱: 반쯤 접힘을 걸지 않고 울리지도 않는다. 켜진 것으로만 두어 알림에서 다른 모드로 바꿀 수 있게 한다
            if (on && FlexPanel.frontIsOff(context)) {
                activeFlow.value = true
                FlexPanel.apply(context)
                LogRepository.from(context).add("자동 플렉스: 사용 안 함 앱이라 반쯤 접힘·진동 없이 둠")
                quiet = false
                return
            }
            val err = if (on) FlexPanel.forceHalfOpened(context) else FlexPanel.resetPosture(context)
            if (err == null) {
                activeFlow.value = on
                // 앞 앱에 고른 모드(플렉스 모드·터치패널·분할 화면)를 적용하고, 끄면 원래대로 돌린다(따로 한 줄로 돈다)
                if (on) FlexPanel.apply(context) else FlexPanel.release(context)
                // 켜고 끈 까닭을 남긴다(범위·까딱, 그때 각도와 기본 센서 값, 가로 여부): 안 켜지거나 바로 꺼질 때 원인을 볼 수 있게
                LogRepository.from(context).add(
                    "자동 플렉스 ${if (on) "켬" else "끔"}: 각도 ${last?.toInt()}° · 기본 센서 ${coarseLast?.toInt()}° · " +
                        "${if (latchedFlow.value) "까딱" else "범위"} · ${if (horizontalFlow.value) "가로" else "세로"}"
                )
                // 실사용에서는 들어올 때만 울린다(펴거나 닫으며 빠져나갈 때마다 울리면 거슬린다). 설정 화면은 따로 둘 다 울린다
                if (!quiet && on) rangeBuzz(context, true)
            }
            quiet = false
        }
    }
}
