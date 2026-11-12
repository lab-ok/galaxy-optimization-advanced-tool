package com.labok.goat.data.rotate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import android.view.Display
import com.labok.goat.core.R
import com.labok.goat.domain.ScreenTripleTransition

/** App-only experiment: screen transitions approximate presses without opening input devices. */
class PowerRotateEngine(private val context: Context, private val onTriplePress: (Int) -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    private var registered = false
    private var detector: ScreenTripleTransition? = null
    private var rotation = 0
    private var activeWindow = 0L
    private var lastEventAt: Long? = null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> true
                Intent.ACTION_SCREEN_OFF -> false
                else -> return
            }
            if (!registered || !PowerRotate.enabled(context)) return
            val now = SystemClock.elapsedRealtime()
            val gap = lastEventAt?.let { now - it }
            lastEventAt = now
            if (!ShakeRotate.canWrite(context)) {
                PowerRotate.record(context, "screen=$on gapMs=$gap permission_missing") {
                    it.copy(events = it.events + 1, screenOn = on, intervalMs = gap, count = 0, decision = null)
                }
                stop()
                PowerRotate.status.value = PowerRotate.Status.WAITING
                return
            }
            val policy = detector ?: return
            val result = policy.feed(on, now)
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val display = runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state }.getOrNull()
            PowerRotate.record(context, "screen=$on gapMs=$gap decision=${policy.decision} count=${policy.count}/3 windowMs=$activeWindow interactive=$interactive display=$display") {
                it.copy(events = it.events + 1, screenOn = on, intervalMs = gap,
                    count = if (result == ScreenTripleTransition.Result.TRIPLE) 3 else policy.count,
                    candidates = it.candidates + if (result == ScreenTripleTransition.Result.TRIPLE) 1 else 0,
                    decision = policy.decision,
                    actionSucceeded = if (result == ScreenTripleTransition.Result.TRIPLE) null else it.actionSucceeded)
            }
            when (result) {
                ScreenTripleTransition.Result.FIRST -> rotation = ShakeRotate.currentRotation(context)
                ScreenTripleTransition.Result.TRIPLE -> {
                    val captured = rotation
                    // A freshly lit screen needs sensor samples for the one-shot direction path.
                    val delay = if (on && PowerRotate.mode(context) == PowerRotate.Mode.ONCE) 250L else 0L
                    main.postDelayed({ perform(captured) }, delay)
                }
                else -> Unit
            }
        }
    }

    /** No Shizuku state check: the already granted settings permission is sufficient. */
    fun refresh() {
        if (!ShakeRotate.canWrite(context)) {
            stop()
            PowerRotate.status.value = PowerRotate.Status.WAITING
            PowerRotate.record(context, "waiting: settings permission missing")
            return
        }
        val window = PowerRotate.window(context).millis
        if (registered && activeWindow == window) return
        main.removeCallbacksAndMessages(null)
        activeWindow = window
        lastEventAt = null
        detector = ScreenTripleTransition(context.getSystemService(PowerManager::class.java).isInteractive, window)
        if (registered) {
            PowerRotate.record(context, "window updated: ${window}ms; sequence reset") { it.copy(count = 0, decision = null) }
            return
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(screen, filter)
            registered = true
            PowerRotate.status.value = PowerRotate.Status.LISTENING
            PowerRotate.record(context, "listener_started windowMs=$window") { it.copy(count = 0, decision = null) }
        } catch (e: Exception) {
            detector = null
            PowerRotate.status.value = PowerRotate.Status.ERROR
            PowerRotate.record(context, "listener_failed: ${e.message}")
        }
    }

    private fun perform(captured: Int) {
        if (!registered || !PowerRotate.enabled(context) || !ShakeRotate.canWrite(context)) return
        val ok = runCatching { onTriplePress(captured) }
            .onFailure { PowerRotate.record(context, "action_exception: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)
        val text = when {
            !ok -> R.string.power_rotate_failed
            PowerRotate.mode(context) == PowerRotate.Mode.ONCE -> R.string.power_rotate_once_feedback
            ShakeRotate.autoRotateOn(context) -> R.string.power_rotate_auto_on
            else -> R.string.power_rotate_locked
        }
        PowerRotate.record(context, "action=${PowerRotate.mode(context)} success=$ok rotation=$captured") { it.copy(actionSucceeded = ok) }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        if (registered) PowerRotate.record(context, "listener_stopped") { it.copy(count = 0, decision = null) }
        if (registered) runCatching { context.unregisterReceiver(screen) }
        registered = false
        detector = null
        lastEventAt = null
        main.removeCallbacksAndMessages(null)
        PowerRotate.status.value = PowerRotate.Status.OFF
    }
}
