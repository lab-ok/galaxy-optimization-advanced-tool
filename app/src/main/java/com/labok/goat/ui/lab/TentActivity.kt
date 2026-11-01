package com.labok.goat.ui.lab

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.labok.goat.data.lab.TentMode
import com.labok.goat.data.log.DiagnosticReport
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.log.PublicLogFile
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 텐트 모드 시험 화면(실험실, 11-01 소장님). 안쪽 화면에 뜨고, 바깥 화면에는 [mode]에 따라 시계나 유튜브를 띄운다.
 * - [MODE_OFFICIAL]: 공식 듀얼 화면(WindowAreaController.presentContentOnWindowArea)으로 바깥에 시계, 안쪽은 주황 간접조명.
 * - [MODE_SHIZUKU]: Shizuku로 기기 상태를 '두 화면 동시 표시'로 바꾸고 바깥 화면에 [TentClockActivity], 안쪽은 주황 간접조명.
 * - [MODE_YOUTUBE]: 같은 방법으로 바깥 화면에 유튜브를 띄우고, 바깥 화면 가장자리 색을 0.5초마다 읽어 안쪽을 그 색으로 비춘다(앰비라이트).
 * 화면을 누르면 지금 상태 글이 잠깐 보이고, 뒤로 가면 끝나며 기기 상태를 되돌린다. 시험이라 화면 글과 로그로 결과를 남긴다.
 */
class TentActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var forcedState = false
    private var session: WindowAreaSessionPresenter? = null
    private var sampler: HandlerThread? = null
    private var color = WARM
    private var running = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        root = FrameLayout(this).apply { background = glow(WARM) }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0x88000000.toInt())
        }
        root.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        root.setOnClickListener { status.visibility = if (status.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        setContentView(root)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_OFFICIAL -> startOfficial()
            MODE_SHIZUKU -> startShizuku(youtube = false)
            MODE_YOUTUBE -> startShizuku(youtube = true)
            MODE_ALL -> runAll()
        }
    }

    /**
     * 전체 시험(11-01 소장님: 텐트 자세 그대로 다 시험하고 바로 진단을 보내게): ① 공식 듀얼 화면 5초 → ② 두 화면 동시 표시 + 바깥 시계 6초
     * → ③ 바깥 유튜브 + 뒷조명 10초를 차례로 하고, 기기 상태를 되돌린 뒤 진단 파일(시험 기록 포함)을 저장해 공유 창을 연다.
     */
    @OptIn(ExperimentalWindowApi::class)
    private fun runAll() {
        val trail = StringBuilder()
        fun note(t: String) {
            trail.append(t).append('\n')
            say(t)
        }
        scope.launch {
            note("== ① 공식 듀얼 화면 ==")
            val controller = runCatching { WindowAreaController.getOrCreate() }.getOrNull()
            val infos = controller?.let { withTimeoutOrNull(3000) { it.windowAreaInfos.first() } }
            val info = infos?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
            val cap = info?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
            note("영역 ${infos?.size ?: "없음"} · 상태 ${cap?.status ?: "없음"}")
            if (controller != null && info != null && cap?.status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
                controller.presentContentOnWindowArea(
                    info.token,
                    this@TentActivity,
                    { runOnUiThread(it) },
                    object : WindowAreaPresentationSessionCallback {
                        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                            this@TentActivity.session = session
                            session.setContentView(clockView(session.context))
                            note("시작함 · 바깥 화면에 시계")
                        }

                        override fun onSessionEnded(t: Throwable?) {
                            session = null
                            note("끝남" + (t?.let { " (${it.message})" } ?: ""))
                        }

                        override fun onContainerVisibilityChanged(isVisible: Boolean) = note("바깥 화면 ${if (isVisible) "보임" else "안 보임"}")
                    }
                )
                delay(5000)
                runCatching { session?.close() }
                delay(800)
            }
            withContext(Dispatchers.IO) {
                note("== ② 두 화면 동시 표시 + 바깥 시계 ==")
                note("기기 상태: " + (TentMode.states()?.let { s -> Regex("identifier=(\\d+), name='([^']*)'").findAll(s).joinToString { "${it.groupValues[1]}:${it.groupValues[2]}" } } ?: "읽지 못함"))
                val err = TentMode.enterConcurrent(this@TentActivity)
                if (err != null) {
                    note("실패: $err")
                } else {
                    forcedState = true
                    Thread.sleep(1000)
                    val here = display?.displayId ?: 0
                    val other = TentMode.otherDisplay(this@TentActivity, here)
                    note("화면들 " + TentMode.displays(this@TentActivity).joinToString { "${it.first}:${it.second.x}x${it.second.y}/${it.third}" } + " · 지금 $here · 바깥 $other")
                    if (other != null) {
                        note("시계 띄우기: " + (TentMode.launchComponent(this@TentActivity, "$packageName/${TentClockActivity::class.java.name}", other) ?: "성공"))
                        Thread.sleep(6000)
                        note("== ③ 바깥 유튜브 + 뒷조명 ==")
                        note("유튜브 띄우기: " + (TentMode.launchOnDisplay(this@TentActivity, YOUTUBE, other) ?: "성공"))
                        Thread.sleep(3000)
                        val size = TentMode.displays(this@TentActivity).firstOrNull { it.first == other }?.second
                        val ids = TentMode.physicalIds()
                        note("물리 화면 $ids · 바깥 크기 ${size?.x}x${size?.y}")
                        val phys = ids.firstOrNull { id ->
                            TentMode.edgeColor(id)?.let { (_, w, hh) -> size == null || (w == size.x && hh == size.y) || (w == size.y && hh == size.x) } == true
                        }
                        note("바깥 화면 물리 번호: ${phys ?: "찾지 못함"}")
                        if (phys != null) {
                            repeat(20) {
                                TentMode.edgeColor(phys)?.let { (c, _, _) ->
                                    val target = boost(c)
                                    runOnUiThread { root.background = glow(target) }
                                    if (it % 4 == 0) note("색 #%06X".format(c and 0xFFFFFF))
                                }
                                Thread.sleep(500)
                            }
                        }
                    }
                    TentMode.reset(this@TentActivity)
                    forcedState = false
                    note("기기 상태 원래대로")
                }
                val text = "== 텐트 모드 전체 시험 ==\n$trail\n" + DiagnosticReport.build(this@TentActivity)
                val uri = PublicLogFile.saveNew(this@TentActivity, PublicLogFile.timestampedName("goat-tent-test"), text)
                withContext(Dispatchers.Main) {
                    if (uri == null) return@withContext say("진단 파일을 저장하지 못했습니다.")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { startActivity(Intent.createChooser(send, "텐트 모드 시험 결과 보내기")) }
                    finish()
                }
            }
        }
    }

    /** 안쪽 화면 간접조명: 가운데가 밝고 가장자리로 갈수록 어두워지는 빛. */
    private fun glow(c: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c, c, darker(c))).apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = 2200f
    }

    private fun darker(c: Int) = Color.rgb(Color.red(c) / 3, Color.green(c) / 3, Color.blue(c) / 3)

    private fun say(text: String) {
        runOnUiThread { status.text = text }
        LogRepository.from(this).add("텐트 모드 시험: $text".take(300))
    }

    @OptIn(ExperimentalWindowApi::class)
    private fun startOfficial() {
        val controller = runCatching { WindowAreaController.getOrCreate() }.getOrElse {
            say("공식 듀얼 화면: 이 기기는 지원하지 않습니다(${it.javaClass.simpleName})")
            return
        }
        val executor = Executor { runOnUiThread(it) }
        scope.launch {
            controller.windowAreaInfos.collect { infos ->
                val info = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                val cap = info?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                say("공식 듀얼 화면: 영역 ${infos.size}개 · 상태 ${cap?.status ?: "없음"}")
                if (info == null || cap?.status != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE || session != null) return@collect
                controller.presentContentOnWindowArea(
                    info.token,
                    this@TentActivity,
                    executor,
                    object : WindowAreaPresentationSessionCallback {
                        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                            this@TentActivity.session = session
                            session.setContentView(clockView(session.context))
                            say("공식 듀얼 화면: 시작함 · 바깥 화면에 시계")
                        }

                        override fun onSessionEnded(t: Throwable?) {
                            session = null
                            say("공식 듀얼 화면: 끝남" + (t?.let { " (${it.message})" } ?: ""))
                        }

                        override fun onContainerVisibilityChanged(isVisible: Boolean) {
                            say("공식 듀얼 화면: 바깥 화면 ${if (isVisible) "보임" else "안 보임"}")
                        }
                    }
                )
            }
        }
    }

    private fun startShizuku(youtube: Boolean) {
        val worker = HandlerThread("tent").also { it.start() }
        sampler = worker
        val h = Handler(worker.looper)
        h.post {
            val err = TentMode.enterConcurrent(this)
            if (err != null) return@post say("두 화면 동시 표시 실패: $err")
            forcedState = true
            Thread.sleep(800)
            val here = display?.displayId ?: 0
            val other = TentMode.otherDisplay(this, here)
            val all = TentMode.displays(this).joinToString { "${it.first}:${it.second.x}x${it.second.y}/${it.third}" }
            if (other == null) return@post say("바깥 화면을 찾지 못했습니다 · 화면들 $all")
            if (!youtube) {
                TentMode.launchComponent(this, "$packageName/${TentClockActivity::class.java.name}", other)?.let { return@post say("바깥 화면에 시계를 띄우지 못했습니다: $it") }
                return@post say("두 화면 동시 표시: 바깥 화면 $other 에 시계 · 화면들 $all")
            }
            TentMode.launchOnDisplay(this, YOUTUBE, other)?.let { return@post say("유튜브를 바깥 화면에 띄우지 못했습니다: $it") }
            // 바깥 화면에 해당하는 물리 화면을 찾는다: 찍은 크기가 바깥 화면 크기와 같은 것
            val size = TentMode.displays(this).firstOrNull { it.first == other }?.second
            val phys = TentMode.physicalIds().firstOrNull { id ->
                TentMode.edgeColor(id)?.let { (_, w, hh) -> size == null || (w == size.x && hh == size.y) || (w == size.y && hh == size.x) } == true
            }
            if (phys == null) return@post say("바깥 화면을 찍지 못했습니다 · 물리 화면 ${TentMode.physicalIds()}")
            say("유튜브 뒷조명: 바깥 화면 $other(물리 $phys) 색을 따라갑니다")
            sample(h, phys)
        }
    }

    /** 0.5초마다 바깥 화면 가장자리 색을 읽어 안쪽 빛을 그 색으로 부드럽게 바꾼다. */
    private fun sample(h: Handler, phys: String) {
        if (!running) return
        val t0 = System.currentTimeMillis()
        TentMode.edgeColor(phys)?.let { (c, _, _) ->
            val target = boost(c)
            runOnUiThread {
                ValueAnimator.ofObject(ArgbEvaluator(), color, target).apply {
                    duration = 450
                    addUpdateListener { root.background = glow(it.animatedValue as Int) }
                    start()
                }
                color = target
            }
        }
        h.postDelayed({ sample(h, phys) }, (500 - (System.currentTimeMillis() - t0)).coerceAtLeast(50))
    }

    /** 영상 가장자리 평균 색은 흐리게 나와, 조명답게 채도와 밝기를 조금 올린다. */
    private fun boost(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 1.4f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.3f + 0.08f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        runCatching { session?.close() }
        sampler?.quitSafely()
        if (forcedState) Thread { TentMode.reset(applicationContext) }.start()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_OFFICIAL = "official"
        const val MODE_SHIZUKU = "shizuku"
        const val MODE_YOUTUBE = "youtube"
        const val MODE_ALL = "all"
        private const val YOUTUBE = "com.google.android.youtube"

        /** 따뜻한 주황 간접조명 색. */
        private val WARM = Color.rgb(255, 140, 40)

        fun start(context: Context, mode: String) {
            context.startActivity(Intent(context, TentActivity::class.java).putExtra(EXTRA_MODE, mode))
        }

        /** 바깥 화면 시계: 검은 바탕에 아주 큰 시·분과 날짜. */
        internal fun clockView(context: Context): View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(
                TextClock(context).apply {
                    format12Hour = "h:mm"
                    format24Hour = "H:mm"
                    setTextColor(Color.WHITE)
                    textSize = 96f
                    gravity = Gravity.CENTER
                }
            )
            addView(
                TextClock(context).apply {
                    format12Hour = "M월 d일 EEEE"
                    format24Hour = "M월 d일 EEEE"
                    setTextColor(0xFFBBBBBB.toInt())
                    textSize = 20f
                    gravity = Gravity.CENTER
                }
            )
        }
    }
}

/** 텐트 모드 시험: Shizuku 방법에서 바깥 화면에 띄우는 시계. */
class TentClockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        setContentView(TentActivity.clockView(this).apply { setOnClickListener { finish() } })
    }
}
