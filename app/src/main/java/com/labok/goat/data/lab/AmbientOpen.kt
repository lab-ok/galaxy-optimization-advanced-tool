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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.background.GoatService
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.MainActivity
import com.labok.goat.ui.liquid.UiGoat
import kotlin.concurrent.thread
import kotlin.math.pow

/**
 * 앰비언트 모드(실험실, 11-02 소장님). 닫힌 폴드를 펼치기 시작하면 안쪽 화면이 바깥 화면 색으로 은은하게 빛나는 간접등이 된다.
 * - 펼치기 시작하면(세밀한 각도 [START_DEG] 넘게, 커지는 중) 바깥 화면을 기본 화면으로 둔 두 화면 동시 표시(폴드8 상태 5)로 바꾸고,
 *   안쪽 화면 두 패널에 바깥 화면을 흐리게 비춘다([TentAmbientView] both). 막 펼칠 때 가장 밝고 각도가 커질수록 어두워져 90°에서 사라진다.
 * - [END_DEG]에 닿으면 빛을 거두고 기기 상태를 풀어, 시스템이 원래대로 안쪽 화면으로 넘어가게 둔다. 다시 닫거나 화면이 꺼지면 바로 끝낸다.
 * - 펼치는 동안 플렉스 모드를 멈춘다(상태를 되돌리면 동시 표시가 풀린다). 텐트 모드가 켜져 있으면 움직이지 않는다.
 * 세밀한 각도는 [FineHinge](Shizuku로 시스템 로그). 기본 힌지 센서는 0·90·180°만 와서 닫힘 확인에만 쓴다.
 */
object AmbientOpen {
    private const val PREF = "ambient_open"
    private const val PAUSE_KEY = "ambient_open"
    private const val CHANNEL_ID = "ambient_open"
    const val START_DEG = 8f
    const val END_DEG = 88f

    /** 앰비언트 모드를 켜 두었는지(기본 꺼짐). */
    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        GoatService.sync(context)
    }

    /** 펼친 각도에 따른 밝기: 막 펼칠 때 1, 90°에서 0. 처음엔 천천히, 끝으로 갈수록 빨리 줄어든다. */
    fun levelFor(angle: Float): Float {
        val x = ((angle - START_DEG) / (90f - START_DEG)).coerceIn(0f, 1f)
        return 1f - x.pow(1.6f)
    }

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "앰비언트 모드", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 23, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle("앰비언트 모드")
            .setContentText("펼칠 때 안쪽 화면이 간접등처럼 빛납니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun log(context: Context, t: String) = LogRepository.from(context).add("앰비언트 모드: $t".take(300))

    /** 상주 서비스 안에서 도는 감시. 화면이 켜진 동안만 힌지를 듣는다. 셸 명령은 감시 스레드에서 부른다. */
    class Engine(private val context: Context) : SensorEventListener {
        private val thread = HandlerThread("ambient-open").also { it.start() }
        private val worker = Handler(thread.looper)
        private val main = Handler(Looper.getMainLooper())
        private var started = false
        private var listening = false

        // 닫힌 것을 본 뒤에만 켠다(펼친 채 접었다 펴는 것은 해당 없음)
        private var armed = false
        private var lastFine = Float.NaN
        private var lastFineAt = 0L

        @Volatile private var glowing = false
        private var glowAt = 0L
        private var view: TentAmbientView? = null
        private var close: (() -> Unit)? = null
        private var outerPhys: String? = null

        private val fine = FineHinge { a -> worker.post { onFine(a) } }

        private val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    listen()
                } else {
                    stopListening()
                    worker.post { finish("화면 꺼짐") }
                }
            }
        }

        // 펼치다 멈추고 오래 들고 있으면(각도가 안 바뀌면 새 값이 없다) 상태를 오래 붙잡지 않게 끝낸다
        private val timeout = Runnable { finish("8초 지남") }

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
            runCatching { context.unregisterReceiver(screen) }
            stopListening()
            worker.post {
                finish("앰비언트 모드 끔")
                thread.quitSafely()
            }
        }

        private fun listen() {
            if (listening) return
            val sm = context.getSystemService(SensorManager::class.java)
            sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, worker) }
            fine.start()
            listening = true
            worker.post { findOuter() }
        }

        private fun stopListening() {
            if (!listening) return
            context.getSystemService(SensorManager::class.java).unregisterListener(this)
            fine.stop()
            listening = false
            armed = false
        }

        /** 바깥 화면의 물리 번호(screencap -d). 찍히는 화면 가운데 가장 작은 것. 한 번 찾으면 기억한다. */
        private fun findOuter() {
            if (outerPhys != null) return
            outerPhys = prefs(context).getString("outer_phys", null) ?: TentMode.physicalIds()
                .mapNotNull { id -> TentMode.edgeColor(id)?.let { (_, w, h) -> id to w * h } }
                .minByOrNull { it.second }?.first
                ?.also { prefs(context).edit().putString("outer_phys", it).apply() }
        }

        override fun onSensorChanged(e: SensorEvent) {
            val a = e.values[0]
            if (a <= 1f && !glowing) armed = true
            if (a >= 179f) {
                armed = false
                if (glowing) finish("다 펼침")
            }
            // 빨리 다시 닫아 세밀한 값이 끊기면 기본 센서의 0°로 끝낸다
            if (glowing && a <= 1f && SystemClock.elapsedRealtime() - lastFineAt > 400) finish("다시 닫음")
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun onFine(a: Float) {
            val now = SystemClock.elapsedRealtime()
            val rising = !lastFine.isNaN() && now - lastFineAt < 600 && a > lastFine
            lastFine = a
            lastFineAt = now
            if (glowing) {
                view?.level = levelFor(a)
                if (a >= END_DEG) {
                    finish("${a.toInt()}°")
                } else if (a < START_DEG / 2) {
                    finish("다시 닫음")
                }
                return
            }
            if (a >= END_DEG) armed = false
            if (!armed || !rising || a < START_DEG || a > 60f || !enabled(context) || TentDuo.active) return
            begin(a)
        }

        private fun begin(a: Float) {
            glowing = true
            armed = false
            glowAt = SystemClock.elapsedRealtime()
            AutoFlex.setPaused(PAUSE_KEY, true)
            // 상태를 바꾸는 동안 바깥 화면 첫 그림을 함께 찍는다
            var first: android.graphics.Bitmap? = null
            val shot = thread(isDaemon = true) { outerPhys?.let { first = TentMode.smallFrame(it) } }
            TentMode.enterConcurrent(context, outer = true)?.let { err ->
                log(context, "두 화면 동시 표시 실패: $err")
                finish("동시 표시 실패")
                return
            }
            var inner: Int? = null
            val until = SystemClock.elapsedRealtime() + 1500
            while (inner == null && SystemClock.elapsedRealtime() < until && glowing) {
                inner = TentMode.otherDisplay(context, Display.DEFAULT_DISPLAY)
                if (inner == null) Thread.sleep(40)
            }
            shot.join(400)
            if (!glowing) return
            if (inner == null) {
                log(context, "안쪽 화면이 켜지지 않음")
                finish("안쪽 화면 없음")
                return
            }
            val angle = lastFine.takeIf { !it.isNaN() } ?: a
            main.post {
                if (!glowing) return@post
                val v = TentAmbientView(context, swap = false, mirror = false, both = true)
                first?.let { v.setFrame(it) } ?: v.setSolid(v.mood)
                v.level = levelFor(angle)
                var r = TentMode.addPresentation(context, inner, v, bright = true)
                if (r !is Function0<*>) r = TentMode.addOverlay(context, inner, v)
                @Suppress("UNCHECKED_CAST")
                if (r is Function0<*>) {
                    view = v
                    close = r as () -> Unit
                } else {
                    log(context, "안쪽 빛 창 실패: $r")
                    worker.post { finish("빛 창 실패") }
                }
            }
            log(context, "펼치기 시작(${a.toInt()}°) → 안쪽 간접등, ${SystemClock.elapsedRealtime() - glowAt}ms")
            worker.postDelayed(timeout, 8000)
            val phys = outerPhys ?: return
            thread(isDaemon = true, name = "ambient-open-frames") {
                while (glowing) {
                    val s = SystemClock.elapsedRealtime()
                    TentMode.smallFrame(phys)?.let { f -> if (glowing) view?.setFrame(f) }
                    Thread.sleep((250 - (SystemClock.elapsedRealtime() - s)).coerceAtLeast(30))
                }
            }
        }

        /** 빛을 거두고 기기 상태를 풀어 시스템이 화면을 정하게 둔다. */
        private fun finish(why: String) {
            if (!glowing) return
            glowing = false
            worker.removeCallbacks(timeout)
            val c = close
            close = null
            view = null
            main.post { c?.invoke() }
            TentMode.reset(context)
            AutoFlex.setPaused(PAUSE_KEY, false)
            log(context, "끝: $why (${SystemClock.elapsedRealtime() - glowAt}ms)")
        }
    }
}
