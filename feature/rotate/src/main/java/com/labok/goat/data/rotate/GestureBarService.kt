package com.labok.goat.data.rotate

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.labok.goat.AppHooks

/** A narrow trusted overlay over the gesture handle. Does not inspect apps or request global input. */
class GestureBarService : AccessibilityService() {
    private var handle: View? = null
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { requested?.cancel(); update() }
    }

    override fun onServiceConnected() {
        active = this
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screen, filter)
        if (PowerRotate.enabled(this)) AppHooks.syncService(this)
        update()
    }

    private fun update() {
        remove()
        if (requested == null || !PowerRotate.enabled(this) || !ShakeRotate.canWrite(this)) return
        val id = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        val gestureNavigation = if (id != 0) resources.getInteger(id) == 2 else Settings.Secure.getInt(contentResolver, "navigation_mode", -1) == 2
        if (!gestureNavigation) {
            PowerRotate.status.value = PowerRotate.Status.ERROR
            PowerRotate.record(this, "gesture_navigation_required")
            return
        }
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val density = resources.displayMetrics.density
        val area = PowerRotate.area(this)
        val view = View(this)
        if (area.visible) view.setBackgroundColor(0x553CBFA3)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.setOnTouchListener { _, event -> requested?.touch(event) ?: false }
        val params = WindowManager.LayoutParams(
            (area.widthDp * density).toInt(), (area.heightDp * density).toInt(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setFitInsetsTypes(0)
            title = "GOAT gesture bar input"
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            handle = view
            PowerRotate.status.value = PowerRotate.Status.LISTENING
            view.post {
                val location = IntArray(2); view.getLocationOnScreen(location)
                PowerRotate.record(this, "overlay_started x=${location[0]} y=${location[1]} width=${view.width} height=${view.height}")
            }
        } catch (e: Exception) {
            PowerRotate.status.value = PowerRotate.Status.ERROR
            PowerRotate.record(this, "overlay_failed: ${e.message}")
        }
    }

    private fun remove() {
        handle?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        handle = null
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); requested?.cancel(); update() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { requested?.cancel() }
    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        remove(); active = null; requested?.cancel()
        PowerRotate.status.value = if (PowerRotate.enabled(this)) PowerRotate.Status.WAITING else PowerRotate.Status.OFF
        super.onDestroy()
    }

    companion object {
        private var active: GestureBarService? = null
        private var requested: PowerRotateEngine? = null
        fun enabled(context: Context): Boolean {
            val component = ComponentName(context, GestureBarService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').any { ComponentName.unflattenFromString(it) == component }
        }
        fun settings(context: Context) = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        internal fun areaChanged() { requested?.cancel(); active?.update() }
        internal fun attach(engine: PowerRotateEngine) {
            if (requested === engine && active?.handle != null) return
            requested = engine
            if (active == null) PowerRotate.status.value = PowerRotate.Status.WAITING else active?.update()
        }
        internal fun detach(engine: PowerRotateEngine) {
            if (requested !== engine) return
            requested = null; active?.remove()
        }
    }
}
