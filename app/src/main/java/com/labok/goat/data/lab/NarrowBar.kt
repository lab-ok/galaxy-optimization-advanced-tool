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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 좁힌 앱 옆 세로 컨트롤러(실험실, [NarrowApps]): 앱 폭 좁히기로 생긴 검은 여백에 최근 앱·홈·뒤로 버튼을 세로로 띄운다.
 * 맨 앞 앱이 좁힌 앱일 때만 보이고(GOAT 상주 서비스가 [update]를 부른다), 버튼은 Shizuku 셸 권한으로 그 키를 보낸다.
 * 여백 폭은 앱이 쓰는 높이(화면 높이 − 아래 내비게이션 막대)의 9/16을 화면 폭에서 뺀 값이다(에뮬레이터 실측 1049px와 일치).
 */
object NarrowBar {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor() }
    private var view: BarView? = null

    /** 버튼 하나: 보낼 키와 화면 읽기용 이름. 위에서 아래로 이 순서다(삼성 내비게이션 막대의 왼쪽→오른쪽 순서). */
    internal enum class Key(val code: Int, val label: String) {
        RECENTS(KeyEvent.KEYCODE_APP_SWITCH, "최근 앱"),
        HOME(KeyEvent.KEYCODE_HOME, "홈"),
        BACK(KeyEvent.KEYCODE_BACK, "뒤로")
    }

    fun enabled(context: Context) = NarrowApps.controller(context) && NarrowApps.apps(context).isNotEmpty()

    /** 맨 앞 앱이 [pkg]로 바뀌었을 때(작업 스레드에서 불려도 된다): 좁힌 앱이면 띄우고, 아니면 내린다. */
    fun update(context: Context, pkg: String?) {
        val app = context.applicationContext
        val want = enabled(app) && pkg != null && pkg in NarrowApps.apps(app)
        main.post { if (want) attach(app) else detach(app) }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        main.post { detach(app) }
    }

    /**
     * 여백(px): 화면 [w]×[h], 아래 내비게이션 막대 [nav]일 때 16:9 세로 앱 옆에 남는 폭 전체. 남지 않으면 0.
     * 앱이 가운데 붙으면 이 값의 절반씩 양옆에 생긴다.
     */
    internal fun margin(w: Int, h: Int, nav: Int): Int = (w - ((h - nav) * 9.0 / 16).roundToInt()).coerceAtLeast(0)

    /** 컨트롤러를 둘 쪽과 폭: 앱이 왼쪽에 붙으면 오른쪽 여백 전체, 오른쪽이면 왼쪽, 가운데면 오른쪽 절반. */
    internal fun slot(side: NarrowApps.Side, margin: Int): Pair<Int, Int> = when (side) {
        NarrowApps.Side.LEFT -> Gravity.END to margin
        NarrowApps.Side.RIGHT -> Gravity.START to margin
        NarrowApps.Side.CENTER -> Gravity.END to margin / 2
    }

    private fun attach(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            io.execute {
                ShizukuAccess.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
                if (Settings.canDrawOverlays(context)) main.post { attach(context) } else LogRepository.from(context).add("좁힌 앱 컨트롤러: 다른 앱 위에 표시 권한 없음")
            }
            return
        }
        val v = view ?: BarView(context).also { view = it }
        val lp = layout(context) ?: return detach(context)
        val wm = context.getSystemService(WindowManager::class.java)
        runCatching { if (v.isAttachedToWindow) wm.updateViewLayout(v, lp) else wm.addView(v, lp) }
            .onFailure { LogRepository.from(context).add("좁힌 앱 컨트롤러: 창을 띄우지 못함(${it.message})") }
    }

    private fun detach(context: Context) {
        val v = view ?: return
        view = null
        if (v.isAttachedToWindow) runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
    }

    /** 지금 화면 크기로 계산한 창 배치. 여백이 너무 좁으면(버튼이 들어가지 않으면) null. */
    private fun layout(context: Context): WindowManager.LayoutParams? {
        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = wm.maximumWindowMetrics
        val b = metrics.bounds
        val nav = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val (gravity, width) = slot(NarrowApps.side(context), margin(b.width(), b.height(), nav))
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
        io.execute {
            if (!ShizukuAccess.injectKey(key.code)) LogRepository.from(v.context).add("좁힌 앱 컨트롤러: ${key.label} 키를 보내지 못함(Shizuku 확인)")
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
            .setContentText("좁힌 앱 옆 여백에 홈·뒤로 버튼을 띄웁니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private const val CHANNEL_ID = "narrow_bar_quiet"

    /** 버튼이 들어갈 가장 좁은 여백(dp). */
    private const val MIN_DP = 28

    /** 세로 알약 안에 버튼 세 개를 그리는 창. 누른 버튼은 잠깐 밝게 보인다. */
    @SuppressLint("ViewConstructor")
    private class BarView(context: Context) : View(context) {
        private val d = context.resources.displayMetrics.density
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private var pressed: Key? = null

        init {
            contentDescription = "최근 앱, 홈, 뒤로"
        }

        /** 버튼 하나의 칸(세로로 쌓인 정사각형). 알약은 화면 세로 가운데에 둔다. */
        private fun cell(i: Int): RectF {
            val s = min(width - 8 * d, 52 * d)
            val gap = 10 * d
            val total = s * 3 + gap * 2
            val top = (height - total) / 2 + i * (s + gap)
            val left = (width - s) / 2
            return RectF(left, top, left + s, top + s)
        }

        override fun onDraw(canvas: Canvas) {
            val first = cell(0)
            val last = cell(2)
            val pad = 6 * d
            canvas.drawRoundRect(RectF(first.left - pad / 2, first.top - pad, first.right + pad / 2, last.bottom + pad), first.width(), first.width(), pill)
            Key.entries.forEachIndexed { i, key ->
                val r = cell(i)
                if (pressed == key) canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2, glow)
                val g = r.width() * 0.18f
                when (key) {
                    Key.RECENTS -> canvas.drawRoundRect(RectF(r.centerX() - g, r.centerY() - g, r.centerX() + g, r.centerY() + g), g * 0.4f, g * 0.4f, line)
                    Key.HOME -> canvas.drawCircle(r.centerX(), r.centerY(), g * 1.05f, line)
                    Key.BACK -> canvas.drawPath(
                        Path().apply {
                            moveTo(r.centerX() + g * 0.55f, r.centerY() - g)
                            lineTo(r.centerX() - g * 0.55f, r.centerY())
                            lineTo(r.centerX() + g * 0.55f, r.centerY() + g)
                        },
                        line
                    )
                }
            }
        }

        private fun hit(y: Float): Key? = Key.entries.withIndex().firstOrNull { (i, _) -> cell(i).let { y >= it.top - 5 * d && y <= it.bottom + 5 * d } }?.value

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = hit(event.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val key = pressed?.takeIf { it == hit(event.y) }
                    pressed = null
                    invalidate()
                    if (key != null) press(this, key)
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressed = null
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
