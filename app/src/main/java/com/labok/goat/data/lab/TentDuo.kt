package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.lab.StandbyActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 텐트 모드(실험실, 11-02 소장님). 폴드를 반쯤 접어 텐트(ㅅ)처럼 세웠을 때:
 * 1. 플렉스 모드를 멈추고, 바깥 화면을 기본 화면으로 둔 두 화면 동시 표시 상태(CONCURRENT_OUTER_DEFAULT, 폴드8 상태 5)로 바꾼다.
 * 2. 바깥 화면은 자동 회전을 켜 가로로 돌게 하고, 스탠바이 시계([StandbyActivity])를 띄운다. 다른 앱을 열면 그 앱이 바깥에 뜬다.
 * 3. 안쪽 화면에는 [TentAmbientView]를 Presentation(접근성 서비스가 있으면 그 문맥) 또는 '다른 앱 위에 표시' 창으로 띄워
 *    바깥 화면 가장자리 색을 구역별로 계속 읽어 한쪽 절반에 방향별 반사광으로 비춘다(모든 앱).
 * 4. 펴거나(힌지 170° 넘게) 닫거나(10° 아래) [끝내기]를 누르면 상태·회전·플렉스 모드를 모두 되돌린다.
 * 폴드8 시험(1.39.1~1.39.4)으로 상태 5·Presentation·화면 찍기가 되는 것을 확인했다. 텐트 자세 자동 감지는 다음 단계(지금은 실험실 버튼).
 */
object TentDuo {
    private const val PREF = "tent_duo"
    private const val PAUSE_KEY = "tent_duo"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var active = false
        private set
    private var closeAmbient: (() -> Unit)? = null
    private var sampler: Thread? = null
    private var hingeListener: SensorEventListener? = null
    private var autoBefore: Int? = null

    /** 빛을 오른쪽 절반으로 옮길지(방향 확인 뒤 소장님이 고른다). */
    fun swap(context: Context) = prefs(context).getBoolean("swap", false)
    fun setSwap(context: Context, on: Boolean) = prefs(context).edit().putBoolean("swap", on).apply()

    /** 텐트 양 옆 끝을 뒤집을지. */
    fun mirror(context: Context) = prefs(context).getBoolean("mirror", false)
    fun setMirror(context: Context, on: Boolean) = prefs(context).edit().putBoolean("mirror", on).apply()

    /** 텐트 모드를 시작한다. [check]면 시계 대신 방향 확인 화면을 띄운다. */
    fun start(context: Context, check: Boolean = false) {
        val app = context.applicationContext
        if (active) return
        active = true
        worker.execute {
            runCatching { enter(app, check) }.onFailure {
                log(app, "시작 실패: ${it.javaClass.simpleName} ${it.message}")
                stop(app, "시작 실패")
            }
        }
    }

    private fun enter(context: Context, check: Boolean) {
        // 플렉스 모드가 접힘 상태를 되돌리면 동시 표시가 풀린다(3차 시험): 상태를 걸기 전에 멈춘다
        AutoFlex.setPaused(PAUSE_KEY, true)
        TentMode.enterConcurrent(context, outer = true)?.let { err ->
            log(context, "두 화면 동시 표시 실패: $err")
            return stop(context, "동시 표시 실패")
        }
        Thread.sleep(1200)
        // 바깥 화면(기본 화면)이 가로로 돌게 자동 회전을 켠다(끝낼 때 되돌림)
        runCatching {
            val cr = context.contentResolver
            autoBefore = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
        }
        val inner = TentMode.otherDisplay(context, Display.DEFAULT_DISPLAY)
        log(context, "시작: 안쪽 화면 $inner · " + TentMode.displays(context).joinToString { "${it.first}:${it.second.x}x${it.second.y}" })
        context.startActivity(Intent(context, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(StandbyActivity.EXTRA_CHECK, check))
        var view: TentAmbientView? = null
        if (inner != null) {
            val latch = CountDownLatch(1)
            main.post {
                val v = TentAmbientView(context, swap(context), mirror(context))
                var r = TentMode.addPresentation(context, inner, v)
                if (r !is Function0<*>) {
                    log(context, "Presentation 실패($r) → 다른 앱 위에 표시 창")
                    r = TentMode.addOverlay(context, inner, v)
                }
                @Suppress("UNCHECKED_CAST")
                if (r is Function0<*>) {
                    closeAmbient = r as () -> Unit
                    view = v
                } else {
                    log(context, "안쪽 빛 창 실패: $r")
                }
                latch.countDown()
            }
            latch.await()
        }
        watchHinge(context)
        val v = view ?: return
        // 바깥(기본) 화면에 해당하는 물리 화면을 찾아 0.25초마다 가장자리 색을 읽는다
        val size = TentMode.displays(context).firstOrNull { it.first == Display.DEFAULT_DISPLAY }?.second
        val phys = TentMode.physicalIds().firstOrNull { id ->
            TentMode.edgeColor(id)?.let { (_, w, h) -> size == null || (w == size.x && h == size.y) || (w == size.y && h == size.x) } == true
        }
        log(context, "바깥 화면 물리 번호: ${phys ?: "찾지 못함"}")
        if (phys == null) {
            v.setSolid(0xFFFF8C28.toInt())
            return
        }
        sampler = Thread {
            var frames = 0
            val t0 = System.currentTimeMillis()
            while (active) {
                val s = System.currentTimeMillis()
                TentMode.edgeZones(phys)?.let { v.setZones(it) }
                frames++
                if (frames == 40) log(context, "앰비언트: 초당 %.1f번 읽음".format(frames * 1000f / (System.currentTimeMillis() - t0)))
                Thread.sleep((250 - (System.currentTimeMillis() - s)).coerceAtLeast(30))
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** 펴거나 닫으면 끝낸다(기본 힌지 센서는 폴드8에서 0·90·180 정도로 거칠게 온다). */
    private fun watchHinge(context: Context) {
        val sm = context.getSystemService(SensorManager::class.java)
        val hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val a = e.values[0]
                if (a >= 170f || a <= 10f) stop(context, "힌지 ${a.toInt()}°")
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        hingeListener = l
        sm.registerListener(l, hinge, SensorManager.SENSOR_DELAY_UI, main)
    }

    /** 텐트 모드를 끝내고 모두 되돌린다. */
    fun stop(context: Context, why: String) {
        val app = context.applicationContext
        if (!active && closeAmbient == null) return
        active = false
        main.post {
            closeAmbient?.invoke()
            closeAmbient = null
            StandbyActivity.current?.finish()
            hingeListener?.let { app.getSystemService(SensorManager::class.java).unregisterListener(it) }
            hingeListener = null
        }
        worker.execute {
            sampler = null
            TentMode.reset(app)
            autoBefore?.let { b -> runCatching { Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, b) } }
            autoBefore = null
            AutoFlex.setPaused(PAUSE_KEY, false)
            log(app, "끝: $why")
        }
    }

    private fun log(context: Context, t: String) = LogRepository.from(context).add("텐트 모드: $t".take(300))

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
