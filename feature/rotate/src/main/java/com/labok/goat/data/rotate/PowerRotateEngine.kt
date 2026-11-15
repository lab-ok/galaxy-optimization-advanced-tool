package com.labok.goat.data.rotate

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import com.labok.goat.core.R
import com.labok.goat.domain.TripleTap

/** Owned by the common foreground service; input comes only from our ordinary app overlay. */
class PowerRotateEngine(private val context: Context, private val onTriplePress: (Int) -> Boolean) {
    private var detector: TripleTap? = null
    private var activeWindow = 0L
    private var rotation = 0
    private var lastTapAt: Long? = null
    private var running = false
    private val overlay = GestureBarOverlay(context, { touch(it) }, { cancel() })

    fun refresh() {
        if (!PowerRotate.enabled(context) || !PowerRotate.canWriteRotation(context) || !PowerRotate.canDraw(context)) {
            stop()
            PowerRotate.status.value = PowerRotate.Status.WAITING
            return
        }
        val window = PowerRotate.window(context).millis
        if (!running || window != activeWindow) {
            activeWindow = window
            detector = TripleTap(window, ViewConfiguration.get(context).scaledTouchSlop.toFloat(), ViewConfiguration.getLongPressTimeout().toLong())
            lastTapAt = null
        }
        running = true
        overlay.refresh()
    }

    fun touch(event: MotionEvent): Boolean {
        if (!running || !PowerRotate.enabled(context)) return false
        if (!android.provider.Settings.canDrawOverlays(context)) { stop(); PowerRotate.status.value = PowerRotate.Status.WAITING; return false }
        if (!PowerRotate.canWriteRotation(context)) { stop(); PowerRotate.status.value = PowerRotate.Status.WAITING; return false }
        val policy = detector ?: return false
        if (event.pointerCount != 1) { cancel(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (policy.count == 0) rotation = ShakeRotate.currentRotation(context)
                policy.down(event.rawX, event.rawY, event.eventTime)
            }
            MotionEvent.ACTION_MOVE -> policy.move(event.rawX, event.rawY)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancel()
            MotionEvent.ACTION_UP -> {
                val matched = policy.up(event.rawX, event.rawY, event.eventTime)
                if (policy.decision == TripleTap.Decision.CANCELLED) return true
                if (policy.decision == TripleTap.Decision.FIRST || policy.decision == TripleTap.Decision.WINDOW_EXPIRED) rotation = ShakeRotate.currentRotation(context)
                val gap = lastTapAt?.let { event.eventTime - it }
                lastTapAt = event.eventTime
                PowerRotate.record(context, "tap gapMs=$gap decision=${policy.decision} windowMs=$activeWindow") {
                    it.copy(events = it.events + 1, intervalMs = gap, count = if (matched) 3 else policy.count,
                        decision = policy.decision, candidates = it.candidates + if (matched) 1 else 0)
                }
                if (matched) perform(rotation)
            }
        }
        return true
    }

    fun cancel() {
        detector?.cancel()
        lastTapAt = null
        PowerRotate.detection.value = PowerRotate.detection.value.copy(count = 0, decision = TripleTap.Decision.CANCELLED)
    }

    private fun perform(captured: Int) {
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
        running = false
        cancel()
        detector = null
        overlay.stop()
        PowerRotate.status.value = PowerRotate.Status.OFF
    }
}
