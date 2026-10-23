package com.labok.goat.data.lab

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.labok.goat.background.GoatService
import com.labok.goat.data.appsize.ForegroundWatcher
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * 상단바 제스처(실험실, 폴드8 전용 앱 폭 좁히기와 함께): 접근성 서비스가 상단바 위에 얇은 띠를 깐다.
 * 일반 앱 창은 상단바보다 아래 층이라 상단바 터치를 받지 못하지만, 접근성 오버레이는 상단바 위 층이라 받을 수 있다
 * (AOSP WindowManagerPolicy 층 순서, TapLock과 같은 방식).
 * - 두 번 누르기(어디든): 지금 앱을 맨 위로 스크롤한다(아이폰처럼).
 * - 꾹 누르기: 띠를 4:2:4로 나눠 왼쪽·오른쪽이면 그쪽에 컨트롤러가 오도록 전체 좁히기를 켜고, 가운데면 끈다.
 * - 아래로 끌기: 띠가 상단바를 덮고 있어 GOAT가 알림창을 대신 내린다.
 * 상단바가 숨은 전체 화면 앱에서는 띠가 터치를 받지 않는다.
 */
object StatusGestures {
    private const val PREF = "status_gestures"

    enum class Zone { LEFT, CENTER, RIGHT }

    /** 띠 안의 가로 위치 [x](폭 [w])가 어느 구역인지. 왼쪽 40% · 가운데 20% · 오른쪽 40%(소장님 지정 4:2:4). */
    internal fun zone(x: Float, w: Float): Zone = when {
        x < w * 0.4f -> Zone.LEFT
        x > w * 0.6f -> Zone.RIGHT
        else -> Zone.CENTER
    }

    fun component(context: Context) = ComponentName(context, StatusGestureService::class.java)

    /** 접근성 설정에 서비스가 켜져 있는지. */
    fun enabled(context: Context): Boolean {
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    }

    /** 상단바 두 번 누르기 = 맨 위로(별도 기능, 소장님 지정). */
    fun scrollTop(context: Context) = prefs(context).getBoolean("scroll_top", false)

    /** 상단바 꾹 누르기 = 앱 폭 좁히기(왼쪽·오른쪽 켜기, 가운데 끄기). 1.24.4에서 '상단바 제스처'를 켰으면 켜진 것으로 읽는다. */
    fun narrowPress(context: Context) = prefs(context).getBoolean("narrow_press", prefs(context).getBoolean("wanted", false))

    /** 두 기능 중 하나를 바꾸고, 하나라도 켜져 있으면 접근성 서비스를 켜고 둘 다 꺼지면 끈다. 성공하면 true. */
    fun setFeature(context: Context, scrollTop: Boolean? = null, narrowPress: Boolean? = null): Boolean {
        prefs(context).edit().apply {
            scrollTop?.let { putBoolean("scroll_top", it) }
            narrowPress?.let { putBoolean("narrow_press", it) }
        }.apply()
        val need = scrollTop(context) || narrowPress(context)
        return setEnabled(context, need)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 서비스를 켜거나 끈다(시스템 설정 권한, 볼륨 버튼 회전과 같은 방식). 성공하면 true. */
    fun setEnabled(context: Context, on: Boolean): Boolean = runCatching {
        val r = context.contentResolver
        val c = component(context)
        val others = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != c }
        val next = (if (on) others + c.flattenToString() else others).joinToString(":")
        Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
        if (on) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        prefs(context).edit().putBoolean("wanted", on).apply()
        LogRepository.from(context).add("상단바 제스처 서비스: ${if (on) "켬" else "끔"}")
        enabled(context) == on
    }.getOrDefault(false)

    /** 강제 종료로 시스템이 서비스를 뺐으면 앱이 다시 뜰 때 되살린다. */
    fun restoreIfWanted(context: Context) {
        if (!(scrollTop(context) || narrowPress(context)) || enabled(context)) return
        runCatching { setEnabled(context, true) }
    }
}

class StatusGestureService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val strips = mutableMapOf<Int, View>()
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = refresh()
        override fun onDisplayRemoved(id: Int) = refresh()
        override fun onDisplayChanged(id: Int) = refresh()
    }

    override fun onServiceConnected() {
        displays.registerDisplayListener(listener, main)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) checkBars()
    }

    /**
     * 상단바가 숨은 전체 화면 앱(영상·게임)에서는 띠가 앱 맨 위 터치를 가로채지 않게 터치를 받지 않는다.
     * 띠 창은 상단바 인셋을 받지 못해(에뮬레이터 실측: 0), 접근성 창 목록에 화면 맨 위 시스템 창(상단바)이 있는지로 판단한다.
     */
    private fun checkBars() {
        val all = runCatching { windowsOnAllDisplays }.getOrNull() ?: return
        strips.forEach { (id, v) ->
            val strip = v as Strip
            val h = statusHeight(id)
            val visible = all.get(id)?.any { w ->
                val b = Rect().also { w.getBoundsInScreen(it) }
                w.type == AccessibilityWindowInfo.TYPE_SYSTEM && b.top <= 0 && b.height() in 1..(h * 2) && b.width() >= v.width * 0.9f
            } ?: true
            if (visible != strip.barVisible) {
                strip.barVisible = visible
                relayout(id, v)
            }
        }
    }
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        displays.unregisterDisplayListener(listener)
        strips.keys.toList().forEach { remove(it) }
        return super.onUnbind(intent)
    }

    /** 켜져 있는 화면마다 띠를 하나씩 둔다(폴드는 접고 펼 때 화면이 바뀐다). */
    private fun refresh() {
        val on = displays.displays.filter { it.state == Display.STATE_ON }.map { it.displayId }.toSet()
        strips.keys.filter { it !in on }.forEach { remove(it) }
        on.filter { it !in strips }.forEach { add(it) }
        strips.forEach { (id, v) -> relayout(id, v) }
    }

    private fun windowManager(id: Int): WindowManager {
        val d = displays.getDisplay(id) ?: return getSystemService(WindowManager::class.java)
        return createDisplayContext(d).getSystemService(WindowManager::class.java)
    }

    private fun statusHeight(id: Int): Int {
        val h = windowManager(id).maximumWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        return if (h > 0) h else (24 * resources.displayMetrics.density).toInt()
    }

    private fun params(id: Int, touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        statusHeight(id),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "GOAT status gestures"
    }

    private fun add(id: Int) {
        val d = displays.getDisplay(id) ?: return
        val v = Strip(createDisplayContext(d), id)
        runCatching { windowManager(id).addView(v, params(id, touchable = true)) }
            .onSuccess { strips[id] = v }
            .onFailure { LogRepository.from(this).add("상단바 제스처: 띠를 띄우지 못함(${it.message})") }
    }

    private fun remove(id: Int) {
        val v = strips.remove(id) ?: return
        runCatching { windowManager(id).removeView(v) }
    }

    /** 높이·터치 여부가 바뀌었을 때만 다시 배치한다(1.24.4는 화면 상태가 바뀔 때마다 다시 배치해 로그가 넘쳤다, 실기기 로그). */
    private fun relayout(id: Int, v: View) {
        val strip = v as Strip
        val h = statusHeight(id)
        val touch = strip.barVisible
        if (strip.laidHeight == h && strip.laidTouch == touch) return
        strip.laidHeight = h
        strip.laidTouch = touch
        runCatching { windowManager(id).updateViewLayout(v, params(id, touch)) }
    }

    /** 지금 맨 앞 앱(작업 스레드). */
    private fun foreground(): String? = ShizukuAccess.focusedTask()?.basePkg ?: ForegroundWatcher.current

    private fun longPress(zone: StatusGestures.Zone) {
        val app = applicationContext
        if (!StatusGestures.narrowPress(app) || !NarrowApps.on(app) || !NarrowApps.supported()) return
        io.execute {
            val pkg = foreground()
            when (zone) {
                // 누른 쪽에 컨트롤러가 오도록 앱은 반대쪽에 붙인다
                StatusGestures.Zone.LEFT -> NarrowApps.startGlobal(app, NarrowApps.Side.RIGHT, pkg)
                StatusGestures.Zone.RIGHT -> NarrowApps.startGlobal(app, NarrowApps.Side.LEFT, pkg)
                StatusGestures.Zone.CENTER -> NarrowApps.stopGlobal(app, pkg)
            }
            main.post { GoatService.sync(app) }
            NarrowBar.update(app, pkg)
        }
    }

    /**
     * 맨 위로: 지금 앱에서 가장 큰 스크롤 영역을 찾아 위로 한 화면씩 끝까지 올린다(접근성 스크롤 동작).
     * 스크롤 영역을 못 찾으면 앱 가운데를 아래로 여러 번 빠르게 쓸어 올린다.
     */
    private fun scrollTop() {
        io.execute {
            val root = rootInActiveWindow
            val target = root?.let { biggestScrollable(it) }
            var moved = 0
            if (target != null) {
                // 한 화면씩 올리고, 화면 내용(보이는 자식들의 위치·글)이 두 번 연속 그대로면 맨 위로 본다.
                // 1.24.4는 크롬처럼 '올림 성공'만 계속 돌려주는 화면에서 300번 헛돌았다(실기기 로그)
                val until = SystemClock.uptimeMillis() + 2500
                var last = ""
                var same = 0
                while (moved < 120 && SystemClock.uptimeMillis() < until) {
                    val ok = target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
                        target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    if (!ok) break
                    moved++
                    Thread.sleep(16)
                    target.refresh()
                    val sig = signature(target)
                    if (sig == last) {
                        if (++same >= 2) break
                    } else {
                        same = 0
                        last = sig
                    }
                }
            }
            // 스크롤 영역을 못 찾았으면 여러 번, 찾았으면 접히는 머리글까지 펼치도록 한 번 쓸어내린다
            fling(if (moved == 0) 6 else 1)
            LogRepository.from(this).add("상단바 제스처: 맨 위로(${if (moved > 0) "스크롤 ${moved}번" else "쓸어 올리기"})")
        }
    }

    /** 스크롤 영역의 지금 모습: 앞쪽 자식 몇 개의 위치와 글. 올렸는데 그대로면 더 올라갈 데가 없다. */
    private fun signature(n: AccessibilityNodeInfo): String {
        val r = Rect()
        return buildString {
            for (i in 0 until minOf(n.childCount, 4)) {
                val c = n.getChild(i) ?: continue
                c.getBoundsInScreen(r)
                append(r.top).append(':').append(c.text ?: c.contentDescription ?: "").append('|')
            }
        }
    }

    private fun biggestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val r = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited < 2000) {
            val n = stack.removeLast()
            visited++
            if (n.isScrollable && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area > bestArea) {
                    best = n
                    bestArea = area
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return best
    }

    private fun fling(times: Int) {
        val m = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val x = m.width() / 2f
        repeat(times) { i ->
            val p = Path().apply {
                moveTo(x, m.height() * 0.3f)
                lineTo(x, m.height() * 0.85f)
            }
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, i * 140L, 90)).build(), null, null)
        }
    }

    /** 상단바 위 띠: 두 번 누르기·꾹 누르기·아래로 끌기를 가린다. */
    @SuppressLint("ViewConstructor")
    private inner class Strip(context: Context, val onDisplay: Int) : View(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longMs = ViewConfiguration.getLongPressTimeout().toLong()
        private val doubleMs = ViewConfiguration.getDoubleTapTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var lastUp = 0L
        private var handled = false
        /** 이 화면의 상단바가 보이는지(접근성 창 목록으로 판단, [checkBars]). */
        var barVisible = true
        var laidHeight = -1
        var laidTouch = true
        private val long = Runnable {
            handled = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            longPress(StatusGestures.zone(downX, width.toFloat()))
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    handled = false
                    postDelayed(long, longMs)
                }
                MotionEvent.ACTION_MOVE -> if (!handled && (e.y - downY > slop * 2)) {
                    // 아래로 끌면 알림창을 내린다(띠가 상단바를 덮고 있어 대신 내려 준다)
                    handled = true
                    removeCallbacks(long)
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                } else if (abs(e.x - downX) > slop) {
                    removeCallbacks(long)
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(long)
                    if (!handled) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUp < doubleMs && StatusGestures.scrollTop(context)) {
                            lastUp = 0L
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scrollTop()
                        } else {
                            lastUp = now
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> removeCallbacks(long)
            }
            return true
        }
    }
}
