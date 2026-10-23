package com.labok.goat.data.lab

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 좁힌 앱 옆 세로 컨트롤러(실험실, [NarrowApps]): 앱 폭 좁히기로 생긴 검은 여백에 버튼과 슬라이더를 세로로 띄운다.
 * 위에서부터 ① 쇼츠 넘기기(이전·다음) ② 최근 앱·홈·뒤로 ③ 밝기 ④ 소리.
 * 맨 앞 앱이 좁힌 앱일 때만 보이고(GOAT 상주 서비스가 [update]를 부른다), 버튼은 Shizuku 셸 권한으로 키·쓸어넘기기를 보낸다.
 * 여백 폭은 앱이 쓰는 높이(화면 높이 − 아래 내비게이션 막대)를 비율로 나눈 폭을 화면 폭에서 뺀 값이다(에뮬레이터 실측 1049px와 일치).
 */
object NarrowBar {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor() }
    private var view: BarView? = null

    /** 지금 컨트롤러를 띄운 앱(비율 계산용). */
    @Volatile private var current: String? = null

    /** 지금 앱이 있는 화면 번호. */
    @Volatile private var display: Int = Display.DEFAULT_DISPLAY

    /** 누르는 버튼. */
    internal enum class Key(val label: String) {
        PREV("이전 쇼츠"),
        NEXT("다음 쇼츠"),
        RECENTS("최근 앱"),
        HOME("홈"),
        BACK("뒤로")
    }

    /** 끌어서 바꾸는 슬라이더. */
    internal enum class Slider { BRIGHTNESS, VOLUME }

    fun enabled(context: Context) = NarrowApps.controller(context) && NarrowApps.apps(context).isNotEmpty()

    /** 맨 앞 앱이 [pkg]로 바뀌었을 때(작업 스레드에서 부른다): 좁힌 앱이면 그 앱이 있는 화면에 띄우고, 아니면 내린다. */
    fun update(context: Context, pkg: String?) {
        val app = context.applicationContext
        val want = enabled(app) && pkg != null && pkg in NarrowApps.apps(app)
        if (want) {
            current = pkg
            display = ShizukuAccess.focusedDisplay() ?: Display.DEFAULT_DISPLAY
        }
        main.post { if (want) attach(app) else detach(app) }
    }

    /** 붙일 쪽이 바뀌었을 때: 떠 있으면 새 여백 자리로 옮긴다. */
    fun relayout(context: Context) {
        val app = context.applicationContext
        main.post { if (view != null) attach(app) }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        main.post { detach(app) }
    }

    /**
     * 여백(px): 화면 [w]×[h], 아래 내비게이션 막대 [nav]일 때 [ratio](세로÷가로) 세로 앱 옆에 남는 폭 전체. 남지 않으면 0.
     * 앱이 가운데 붙으면 이 값의 절반씩 양옆에 생긴다.
     */
    internal fun margin(w: Int, h: Int, nav: Int, ratio: Float = 16f / 9): Int = (w - ((h - nav) / ratio).roundToInt()).coerceAtLeast(0)

    /** 컨트롤러를 둘 쪽과 폭: 앱이 왼쪽에 붙으면 오른쪽 여백 전체, 오른쪽이면 왼쪽, 가운데면 오른쪽 절반. */
    internal fun slot(side: NarrowApps.Side, margin: Int): Pair<Int, Int> = when (side) {
        NarrowApps.Side.LEFT -> Gravity.END to margin
        NarrowApps.Side.RIGHT -> Gravity.START to margin
        NarrowApps.Side.CENTER -> Gravity.END to margin / 2
    }

    /** 앱 가운데의 x(쇼츠를 넘길 때 쓸어 넘길 자리): 앱이 붙은 쪽에 따라 다르다. */
    internal fun appCenterX(side: NarrowApps.Side, w: Int, margin: Int): Float = when (side) {
        NarrowApps.Side.LEFT -> (w - margin) / 2f
        NarrowApps.Side.RIGHT -> margin + (w - margin) / 2f
        NarrowApps.Side.CENTER -> w / 2f
    }

    private fun windowContext(context: Context): Context {
        val d = context.getSystemService(DisplayManager::class.java).getDisplay(display) ?: return context
        return context.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private fun attach(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            io.execute {
                ShizukuAccess.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
                if (Settings.canDrawOverlays(context)) main.post { attach(context) } else LogRepository.from(context).add("좁힌 앱 컨트롤러: 다른 앱 위에 표시 권한 없음")
            }
            return
        }
        // 다른 화면으로 옮겨 갔으면(접고 펴기) 창을 새로 만든다
        view?.let { if (it.shownOn != display) detach(context) }
        val v = view ?: BarView(windowContext(context), display).also { view = it }
        val lp = layout(v.context) ?: return detach(context)
        val wm = v.context.getSystemService(WindowManager::class.java)
        runCatching { if (v.isAttachedToWindow) wm.updateViewLayout(v, lp) else wm.addView(v, lp) }
            .onFailure { LogRepository.from(context).add("좁힌 앱 컨트롤러: 창을 띄우지 못함(${it.message})") }
    }

    private fun detach(context: Context) {
        val v = view ?: return
        view = null
        if (v.isAttachedToWindow) runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
    }

    /** 지금 화면의 (폭, 높이, 여백). */
    private fun geometry(context: Context): Triple<Int, Int, Int> {
        val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val b = metrics.bounds
        val nav = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val ratio = current?.let { NarrowApps.ratios(context)[it] }?.value ?: (16f / 9)
        return Triple(b.width(), b.height(), margin(b.width(), b.height(), nav, ratio))
    }

    /** 지금 화면 크기로 계산한 창 배치. 여백이 너무 좁으면(버튼이 들어가지 않으면) null. */
    private fun layout(context: Context): WindowManager.LayoutParams? {
        val (gravity, width) = slot(NarrowApps.side(context), geometry(context).third)
        if (width < (MIN_DP * context.resources.displayMetrics.density)) return null
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity or Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GOAT narrow bar"
        }
    }

    private fun press(v: View, key: Key) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val app = v.context.applicationContext
        val (w, h, m) = geometry(v.context)
        val side = NarrowApps.side(app)
        val id = display
        io.execute {
            val ok = when (key) {
                Key.RECENTS -> ShizukuAccess.injectKey(KeyEvent.KEYCODE_APP_SWITCH)
                Key.HOME -> ShizukuAccess.injectKey(KeyEvent.KEYCODE_HOME)
                Key.BACK -> ShizukuAccess.injectKey(KeyEvent.KEYCODE_BACK)
                // 다음 쇼츠는 아래에서 위로, 이전 쇼츠는 위에서 아래로 앱 가운데를 쓸어 넘긴다
                Key.NEXT -> ShizukuAccess.injectSwipe(id, appCenterX(side, w, m), h * 0.72f, h * 0.28f)
                Key.PREV -> ShizukuAccess.injectSwipe(id, appCenterX(side, w, m), h * 0.28f, h * 0.72f)
            }
            if (!ok) LogRepository.from(app).add("좁힌 앱 컨트롤러: ${key.label} 보내기 실패(Shizuku 확인)")
        }
    }

    /** 밝기(0~1)를 읽는다. 못 읽으면 0.5. */
    private fun brightness(context: Context): Float =
        runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)

    /** 밝기 쓰기는 셸이라 느려서, 끄는 동안 마지막 값만 모아 보낸다. */
    @Volatile private var pendingBrightness: Int? = null

    @Volatile private var brightnessBusy = false

    private fun setBrightness(level: Float) {
        pendingBrightness = (level * 255).roundToInt().coerceIn(1, 255)
        if (brightnessBusy) return
        brightnessBusy = true
        io.execute {
            // 자동 밝기를 끄고(직접 정한 밝기가 바로 보이게) 마지막 값을 쓴다. 쓰는 동안 새 값이 오면 이어서 쓴다
            ShizukuAccess.shell("settings put system screen_brightness_mode 0")
            while (true) {
                val v = pendingBrightness ?: break
                pendingBrightness = null
                ShizukuAccess.shell("settings put system screen_brightness $v")
            }
            brightnessBusy = false
        }
    }

    /** 상주 서비스 알림(이 기능만 켜져 있을 때). */
    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "좁힌 앱 컨트롤러", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 22, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("좁힌 앱 컨트롤러")
            .setContentText("좁힌 앱 옆 여백에 버튼을 띄웁니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private const val CHANNEL_ID = "narrow_bar_quiet"

    /** 버튼이 들어갈 가장 좁은 여백(dp). */
    private const val MIN_DP = 28

    /**
     * 세로 알약들을 그리는 창. 버튼은 누른 동안 밝게 보이고, 슬라이더는 위아래로 끌면 지금 값에서 끈 만큼 바뀐다
     * (알약 높이만큼 끌면 처음부터 끝까지).
     */
    @SuppressLint("ViewConstructor")
    private class BarView(context: Context, val shownOn: Int) : View(context) {
        private val d = context.resources.displayMetrics.density
        private val audio = context.getSystemService(AudioManager::class.java)
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        private val level = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private var pressed: Key? = null
        private var dragging: Slider? = null
        private var dragStartY = 0f
        private var dragStartValue = 0f
        private val values = mutableMapOf(Slider.BRIGHTNESS to brightness(context), Slider.VOLUME to volume())
        private var lastTick = 0L

        init {
            contentDescription = "이전 쇼츠, 다음 쇼츠, 최근 앱, 홈, 뒤로, 밝기, 소리"
        }

        private fun volume(): Float {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }

        /** 칸 크기: 폭에 맞추되, 높이에 다 들어가지 않으면 줄인다. 알약 4개(2·3칸 버튼, 3.3칸 슬라이더 둘) = 11.6칸 + 간격. */
        private fun unit(): Float {
            val gap = 14 * d
            val byWidth = min(width - 10 * d, 48 * d)
            val byHeight = (height - 48 * d - gap * 3) / 11.6f
            return min(byWidth, byHeight).coerceAtLeast(20 * d)
        }

        /** 알약 4개의 자리(세로 가운데 정렬). */
        private fun groups(): List<RectF> {
            val s = unit()
            val gap = 14 * d
            val sizes = listOf(2f, 3f, 3.3f, 3.3f).map { it * s }
            var top = (height - (sizes.sum() + gap * 3)) / 2
            val left = (width - s) / 2
            return sizes.map { h -> RectF(left, top, left + s, top + h).also { top += h + gap } }
        }

        private fun keyCells(): List<Pair<Key, RectF>> {
            val g = groups()
            val s = unit()
            fun cells(r: RectF, keys: List<Key>) = keys.mapIndexed { i, k -> k to RectF(r.left, r.top + i * s, r.right, r.top + (i + 1) * s) }
            return cells(g[0], listOf(Key.PREV, Key.NEXT)) + cells(g[1], listOf(Key.RECENTS, Key.HOME, Key.BACK))
        }

        private fun sliderRects(): Map<Slider, RectF> = groups().let { mapOf(Slider.BRIGHTNESS to it[2], Slider.VOLUME to it[3]) }

        override fun onDraw(canvas: Canvas) {
            val s = unit()
            groups().forEach { g -> canvas.drawRoundRect(g, g.width() / 2, g.width() / 2, pill) }
            keyCells().forEach { (key, r) ->
                if (pressed == key) canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2, glow)
                drawKey(canvas, key, r)
            }
            sliderRects().forEach { (slider, g) ->
                // 채움: 아래에서부터 지금 값만큼
                val v = values[slider] ?: 0f
                canvas.save()
                canvas.clipRect(g.left, g.bottom - g.height() * v, g.right, g.bottom)
                canvas.drawRoundRect(g, g.width() / 2, g.width() / 2, if (dragging == slider) glow else level)
                canvas.restore()
                drawSliderIcon(canvas, slider, RectF(g.left, g.bottom - s, g.right, g.bottom))
            }
        }

        private fun drawKey(canvas: Canvas, key: Key, r: RectF) {
            val g = r.width() * 0.18f
            val cx = r.centerX()
            val cy = r.centerY()
            fun path(vararg p: Float) = Path().apply {
                moveTo(p[0], p[1])
                var i = 2
                while (i < p.size) {
                    lineTo(p[i], p[i + 1])
                    i += 2
                }
            }
            when (key) {
                Key.RECENTS -> canvas.drawRoundRect(RectF(cx - g, cy - g, cx + g, cy + g), g * 0.4f, g * 0.4f, line)
                Key.HOME -> canvas.drawCircle(cx, cy, g * 1.05f, line)
                Key.BACK -> canvas.drawPath(path(cx + g * 0.55f, cy - g, cx - g * 0.55f, cy, cx + g * 0.55f, cy + g), line)
                Key.PREV -> canvas.drawPath(path(cx - g, cy + g * 0.5f, cx, cy - g * 0.5f, cx + g, cy + g * 0.5f), line)
                Key.NEXT -> canvas.drawPath(path(cx - g, cy - g * 0.5f, cx, cy + g * 0.5f, cx + g, cy - g * 0.5f), line)
            }
        }

        private fun drawSliderIcon(canvas: Canvas, slider: Slider, r: RectF) {
            val g = r.width() * 0.16f
            val cx = r.centerX()
            val cy = r.centerY()
            when (slider) {
                // 해: 가운데 원과 빛살 8개
                Slider.BRIGHTNESS -> {
                    canvas.drawCircle(cx, cy, g * 0.75f, line)
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0)
                        val x = cos(a).toFloat()
                        val y = sin(a).toFloat()
                        canvas.drawLine(cx + x * g * 1.3f, cy + y * g * 1.3f, cx + x * g * 1.75f, cy + y * g * 1.75f, line)
                    }
                }
                // 스피커와 소리 물결
                Slider.VOLUME -> {
                    val body = Path().apply {
                        moveTo(cx - g * 1.4f, cy - g * 0.5f)
                        lineTo(cx - g * 0.7f, cy - g * 0.5f)
                        lineTo(cx, cy - g * 1.2f)
                        lineTo(cx, cy + g * 1.2f)
                        lineTo(cx - g * 0.7f, cy + g * 0.5f)
                        lineTo(cx - g * 1.4f, cy + g * 0.5f)
                        close()
                    }
                    canvas.drawPath(body, line)
                    canvas.drawArc(RectF(cx - g * 0.4f, cy - g, cx + g * 1.4f, cy + g), -50f, 100f, false, line)
                }
            }
        }

        private fun hitKey(y: Float): Key? = keyCells().firstOrNull { (_, r) -> y >= r.top - 4 * d && y <= r.bottom + 4 * d }?.first
        private fun hitSlider(y: Float): Slider? = sliderRects().entries.firstOrNull { (_, r) -> y >= r.top - 4 * d && y <= r.bottom + 4 * d }?.key

        private fun setValue(slider: Slider, v: Float) {
            val now = v.coerceIn(0f, 1f)
            values[slider] = now
            when (slider) {
                Slider.BRIGHTNESS -> setBrightness(now)
                Slider.VOLUME -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val step = (now * max).roundToInt()
                    if (step != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, AudioManager.FLAG_SHOW_UI) }
                        // 한 칸 바뀔 때마다 살짝 진동(너무 잦지 않게)
                        val t = SystemClock.uptimeMillis()
                        if (t - lastTick > 40) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastTick = t
                    }
                }
            }
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = hitKey(event.y)
                    dragging = if (pressed == null) hitSlider(event.y) else null
                    dragging?.let {
                        // 다른 곳에서 바뀌었을 수 있어 끌기 시작할 때 지금 값을 다시 읽는다
                        values[it] = if (it == Slider.BRIGHTNESS) brightness(context) else volume()
                        dragStartY = event.y
                        dragStartValue = values[it] ?: 0f
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> dragging?.let { s ->
                    val h = sliderRects()[s]?.height() ?: return true
                    setValue(s, dragStartValue + (dragStartY - event.y) / h)
                }
                MotionEvent.ACTION_UP -> {
                    val key = pressed?.takeIf { it == hitKey(event.y) }
                    pressed = null
                    dragging = null
                    invalidate()
                    if (key != null) press(this, key)
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressed = null
                    dragging = null
                    invalidate()
                }
            }
            return true
        }

        // 접고 펴거나 화면을 돌리면 여백이 달라지므로 다시 배치한다
        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            post { if (isAttachedToWindow) attach(context.applicationContext) }
        }
    }
}
