package com.labok.goat.ui.lab

import com.labok.goat.AppHooks
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.withoutGoat
import com.labok.goat.data.lab.TentMode
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
 * 텐트 모드 시험 화면(실험실, 11-01 소장님). 폴드를 반쯤 접어 엎어 세웠을 때 두 화면을 함께 쓸 수 있는지 본다.
 * - [MODE_OFFICIAL]: 공식 듀얼 화면(WindowAreaController.presentContentOnWindowArea)으로 바깥에 시계, 안쪽은 주황 간접조명.
 * - [MODE_ALL]: 전체 시험. ① 공식 → ② 상태 4(안쪽 기본)에서 바깥에 시계 창 → ③ 상태 5(바깥 기본)에서 바깥은 GOAT 시계, 안쪽은 조명 창
 *   → ④ 상태 5에서 유튜브를 바깥에 열고 안쪽 조명 창이 영상 가장자리 색을 따라감. 끝나면 진단 파일 공유 창을 연다.
 *   다른 화면에 앱(액티비티)을 띄우는 것은 폴드8에서 셸도 권한 거부라(2차 시험) '다른 앱 위에 표시' 창으로 그린다.
 * 화면을 누르면 지금 상태 글이 보였다 숨고, 뒤로 가면 끝나며 기기 상태를 되돌린다.
 */
class TentActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var forcedState = false
    private var session: WindowAreaSessionPresenter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시험하는 동안 플렉스 모드를 멈춘다: 플렉스 모드가 접힘 상태를 되돌리면 시험이 건 두 화면 동시 표시 상태가 풀리고,
        // 텐트 자세에서 플렉스 진동이 울렸다(폴드8 3차 시험 11-01). 동시 표시 상태를 걸기 전에 멈춰야 되돌림이 끼어들지 않는다
        AutoFlex.setPaused(PAUSE_KEY, true)
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
                val states = TentMode.states()
                note("기기 상태: " + (states?.let { st -> Regex("identifier=(\\d+), name='([^']*)'").findAll(st).joinToString { "${it.groupValues[1]}:${it.groupValues[2]}" } } ?: "읽지 못함"))
                stepA(::note)
                stepB(::note)
                stepC(::note)
                val text = "== 텐트 모드 전체 시험 ==\n$trail\n" + AppHooks.diagnosticReport(this@TentActivity)
                val uri = PublicLogFile.saveNew(this@TentActivity, PublicLogFile.timestampedName("goat-tent-test"), text)
                withContext(Dispatchers.Main) {
                    if (uri == null) return@withContext say("진단 파일을 저장하지 못했습니다.")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { startActivity(Intent.createChooser(send, "텐트 모드 시험 결과 보내기").withoutGoat(this@TentActivity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

    /** [displayId] 화면에 [view]를 띄운다: Presentation 먼저, 안 되면 '다른 앱 위에 표시' 창. 닫는 함수 또는 실패 이유. 메인 스레드. */
    private fun showOn(displayId: Int, view: View, note: (String) -> Unit): Any {
        val p = TentMode.addPresentation(this, displayId, view)
        if (p is Function0<*>) {
            note("Presentation으로 띄움")
            return p
        }
        note("Presentation 실패: $p")
        val o = TentMode.addOverlay(this, displayId, view)
        if (o is Function0<*>) note("다른 앱 위에 표시 창으로 띄움")
        return o
    }

    /** 화면들 한 줄 요약. */
    private fun displaysLine() = TentMode.displays(this).joinToString { "${it.first}:${it.second.x}x${it.second.y}/${it.third}" } + " · " + TentMode.flagsLine(this)

    /** 물리 화면 가운데 크기가 [w]x[h](또는 돌린 크기)인 것. */
    private fun physFor(w: Int, h: Int): String? = TentMode.physicalIds().firstOrNull { id ->
        TentMode.edgeColor(id)?.let { (_, cw, ch) -> (cw == w && ch == h) || (cw == h && ch == w) } == true
    }

    /** 두 물리 화면의 전체 평균 색(무엇이 그려졌는지 확인용). */
    private fun screensLine() = TentMode.physicalIds().joinToString { id ->
        TentMode.edgeColor(id, edgeOnly = false)?.let { (c, w, h) -> "$w" + "x$h=#%06X".format(c and 0xFFFFFF) } ?: "$id 못 찍음"
    }

    /** ②: 안쪽이 기본인 두 화면 동시 표시(상태 4)에서 바깥 화면에 '화면 위 표시' 창으로 시계를 그린다. 6초. */
    private fun stepA(note: (String) -> Unit) {
        note("== ② 상태 4 + 바깥에 시계 창 ==")
        val err = TentMode.enterConcurrent(this)
        if (err != null) return note("실패: $err")
        forcedState = true
        Thread.sleep(1000)
        val here = display?.displayId ?: 0
        val other = TentMode.otherDisplay(this, here)
        note("화면들 ${displaysLine()} · 지금 $here · 바깥 $other")
        if (other != null) {
            var close: (() -> Unit)? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            runOnUiThread {
                val r = showOn(other, clockView(this), note)
                @Suppress("UNCHECKED_CAST")
                if (r is Function0<*>) close = r as () -> Unit else note("시계 창 실패: $r")
                latch.countDown()
            }
            latch.await()
            if (close != null) note("시계 창 띄움")
            Thread.sleep(5000)
            note("찍은 색: ${screensLine()}")
            runOnUiThread { close?.invoke() }
        }
        TentMode.reset(this)
        forcedState = false
        Thread.sleep(1200)
    }

    /** ③: 바깥이 기본인 두 화면 동시 표시(상태 5). 이 화면(GOAT)을 시계로 바꾸고, 안쪽 화면에는 주황 조명 창을 띄운다. 6초. */
    private fun stepB(note: (String) -> Unit) {
        note("== ③ 상태 5 + 바깥 시계 · 안쪽 조명 창 ==")
        val err = TentMode.enterConcurrent(this, outer = true)
        if (err != null) return note("실패: $err")
        forcedState = true
        Thread.sleep(1500)
        val here = display?.displayId ?: 0
        val other = TentMode.otherDisplay(this, here)
        note("화면들 ${displaysLine()} · 지금 $here · 다른 화면 $other")
        runOnUiThread { setContentView(clockView(this)) }
        var close: (() -> Unit)? = null
        if (other != null) {
            val latch = java.util.concurrent.CountDownLatch(1)
            runOnUiThread {
                val light = View(this).apply { background = glow(WARM) }
                val r = showOn(other, light, note)
                @Suppress("UNCHECKED_CAST")
                if (r is Function0<*>) close = r as () -> Unit else note("조명 창 실패: $r")
                latch.countDown()
            }
            latch.await()
            if (close != null) note("조명 창 띄움")
        }
        Thread.sleep(5000)
        note("찍은 색: ${screensLine()}")
        runOnUiThread {
            close?.invoke()
            setContentView(root)
        }
        TentMode.reset(this)
        forcedState = false
        Thread.sleep(1200)
    }

    /** ④: 상태 5에서 유튜브를 보통 방법으로 열어(기본 화면 = 바깥) 바깥에 띄우고, 안쪽 조명 창이 바깥 화면 가장자리 색을 따라간다. 10초. */
    private fun stepC(note: (String) -> Unit) {
        note("== ④ 상태 5 + 바깥 유튜브 · 안쪽 뒷조명 창 ==")
        val err = TentMode.enterConcurrent(this, outer = true)
        if (err != null) return note("실패: $err")
        forcedState = true
        Thread.sleep(1500)
        val here = display?.displayId ?: 0
        val other = TentMode.otherDisplay(this, here)
        note("화면들 ${displaysLine()} · 지금 $here · 다른 화면 $other")
        val launch = packageManager.getLaunchIntentForPackage(YOUTUBE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        note("유튜브 열기: " + (launch?.let { runCatching { startActivity(it) }.exceptionOrNull()?.message ?: "성공" } ?: "유튜브 없음"))
        Thread.sleep(2500)
        val main = TentMode.displays(this).firstOrNull { it.first == here }?.second
        val mainPhys = main?.let { physFor(it.x, it.y) }
        note("기본 화면 물리 번호: ${mainPhys ?: "찾지 못함"} · 찍은 색: ${screensLine()}")
        var light: View? = null
        var close: (() -> Unit)? = null
        if (other != null) {
            val latch = java.util.concurrent.CountDownLatch(1)
            runOnUiThread {
                val v = View(this).apply { background = glow(WARM) }
                val r = showOn(other, v, note)
                @Suppress("UNCHECKED_CAST")
                if (r is Function0<*>) {
                    close = r as () -> Unit
                    light = v
                } else {
                    note("뒷조명 창 실패: $r")
                }
                latch.countDown()
            }
            latch.await()
        }
        if (mainPhys != null && light != null) {
            repeat(20) {
                TentMode.edgeColor(mainPhys)?.let { (c, _, _) ->
                    val target = boost(c)
                    runOnUiThread { light?.background = glow(target) }
                    if (it % 4 == 0) note("가장자리 색 #%06X".format(c and 0xFFFFFF))
                }
                Thread.sleep(500)
            }
        }
        note("뒷조명 뒤 찍은 색: ${screensLine()}")
        runOnUiThread { close?.invoke() }
        TentMode.reset(this)
        forcedState = false
        note("기기 상태 원래대로")
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
        scope.cancel()
        runCatching { session?.close() }
        if (forcedState) Thread { TentMode.reset(applicationContext) }.start()
        AutoFlex.setPaused(PAUSE_KEY, false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_OFFICIAL = "official"
        const val MODE_ALL = "all"
        private const val PAUSE_KEY = "tent"
        private const val YOUTUBE = "com.google.android.youtube"

        /** 따뜻한 주황 간접조명 색. */
        private val WARM = Color.rgb(255, 140, 40)

        fun start(context: Context, mode: String) {
            context.startActivity(Intent(context, TentActivity::class.java).putExtra(EXTRA_MODE, mode))
        }

        /** 바깥 화면 시계: 검은 바탕에 아주 큰 시·분과 날짜. */
        fun clockView(context: Context): View = LinearLayout(context).apply {
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
