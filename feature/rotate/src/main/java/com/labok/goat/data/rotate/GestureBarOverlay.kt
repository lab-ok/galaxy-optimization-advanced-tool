package com.labok.goat.data.rotate

import android.app.AppOpsManager
import com.labok.goat.AppHooks
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/** An ordinary overlay owned by GoatService. No accessibility service or global input access. */
internal class GestureBarOverlay(
    private val context: Context,
    private val onTouch: (MotionEvent) -> Boolean,
    private val onReset: () -> Unit
) {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val permissions = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == null || packageName == context.packageName) main.post {
            if (registered) {
                last = null
                update()
                // Re-evaluate the shared one-shot sensor engine after a grant/revocation.
                runCatching { AppHooks.syncService(context) }
            }
        }
    }
    private var registered = false
    private var view: View? = null
    private var manager: WindowManager? = null
    private var windowContext: Context? = null
    private var last: Snapshot? = null
    private data class Snapshot(val width: Int, val height: Int, val rotation: Int, val density: Float,
        val area: PowerRotate.Area, val awake: Boolean, val locked: Boolean, val overlay: Boolean, val rotationAllowed: Boolean, val gestures: Boolean)

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { update(intent.action == Intent.ACTION_SCREEN_OFF) }
    }
    private val display = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) update() }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    fun refresh() {
        if (!registered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
            try {
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(screen, filter)
                displays.registerDisplayListener(display, main)
                registered = true
                runCatching {
                    appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, permissions)
                    appOps.startWatchingMode(AppOpsManager.OPSTR_WRITE_SETTINGS, context.packageName, permissions)
                }.onFailure { PowerRotate.record(context, "permission_watch_unavailable: ${it.message}") }
            } catch (e: Exception) {
                runCatching { context.unregisterReceiver(screen) }
                PowerRotate.record(context, "overlay_listener_failed: ${e.message}")
                PowerRotate.status.value = PowerRotate.Status.ERROR
                return
            }
        }
        update()
    }

    private fun update(forceHidden: Boolean = false) {
        if (!registered) return
        try {
            val target = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val visual = windowContext ?: context.createDisplayContext(target)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null).also { windowContext = it }
            val wm = visual.getSystemService(WindowManager::class.java)
            val bounds = wm.maximumWindowMetrics.bounds
            val nav = visual.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
            val gestures = if (nav != 0) visual.resources.getInteger(nav) == 2 else Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1) == 2
            val next = Snapshot(bounds.width(), bounds.height(), target.rotation, visual.resources.displayMetrics.density,
                PowerRotate.area(context), !forceHidden && context.getSystemService(PowerManager::class.java).isInteractive,
                context.getSystemService(KeyguardManager::class.java).isKeyguardLocked, Settings.canDrawOverlays(context),
                PowerRotate.canWriteRotation(context), gestures)
            if (next == last) return
            last = next
            onReset()
            remove()
            if (!next.overlay || !next.rotationAllowed || !next.awake || next.locked || !PowerRotate.enabled(context)) {
                PowerRotate.status.value = PowerRotate.Status.WAITING
                return
            }
            if (!next.gestures) {
                PowerRotate.status.value = PowerRotate.Status.ERROR
                PowerRotate.record(context, "gesture_navigation_required")
                return
            }
            val handle = View(visual)
            if (next.area.visible) handle.setBackgroundColor(0x553CBFA3)
            handle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            handle.setOnTouchListener { _, event -> onTouch(event) }
            val params = WindowManager.LayoutParams(
                (next.area.widthDp * next.density).toInt().coerceAtMost(next.width),
                (next.area.heightDp * next.density).toInt().coerceAtMost(next.height),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setFitInsetsTypes(0); title = "GOAT gesture bar input" }
            wm.addView(handle, params)
            manager = wm
            view = handle
            PowerRotate.status.value = PowerRotate.Status.LISTENING
            handle.post {
                if (view !== handle) return@post
                val location = IntArray(2); handle.getLocationOnScreen(location)
                PowerRotate.record(context, "application_overlay_started x=${location[0]} y=${location[1]} width=${handle.width} height=${handle.height}")
            }
        } catch (e: Exception) {
            remove(); last = null
            PowerRotate.status.value = PowerRotate.Status.ERROR
            PowerRotate.record(context, "overlay_failed: ${e.message}")
        }
    }

    private fun remove() {
        view?.let { handle -> runCatching { manager?.removeView(handle) } }
        view = null; manager = null
    }
    fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(screen) }
            displays.unregisterDisplayListener(display)
            appOps.stopWatchingMode(permissions)
        }
        registered = false
        main.removeCallbacksAndMessages(null)
        remove(); last = null; windowContext = null
    }
}
