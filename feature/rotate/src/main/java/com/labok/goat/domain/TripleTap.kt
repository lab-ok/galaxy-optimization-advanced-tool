package com.labok.goat.domain

import kotlin.math.hypot

/** Counts completed single-finger taps. Swipes, cancellation and holds break a sequence. */
class TripleTap(private val windowMs: Long, private val slop: Float, private val holdMs: Long) {
    enum class Decision { FIRST, SECOND, TRIPLE, WINDOW_EXPIRED, CANCELLED }
    var count = 0; private set
    var decision: Decision? = null; private set
    private var firstAt = 0L
    private var downAt: Long? = null
    private var x = 0f
    private var y = 0f

    fun down(px: Float, py: Float, now: Long) {
        if (downAt != null) cancel()
        downAt = now; x = px; y = py
    }
    fun move(px: Float, py: Float) {
        if (downAt != null && hypot(px - x, py - y) > slop) cancel()
    }
    fun up(px: Float, py: Float, now: Long): Boolean {
        move(px, py)
        val start = downAt ?: run { decision = Decision.CANCELLED; return false }
        downAt = null
        if (now < start || now - start >= holdMs) { cancel(); return false }
        val expired = count > 0 && (now < firstAt || now - firstAt > windowMs)
        if (expired) count = 0
        if (count == 0) firstAt = now
        count++
        decision = when {
            count == 3 -> Decision.TRIPLE
            expired -> Decision.WINDOW_EXPIRED
            count == 1 -> Decision.FIRST
            else -> Decision.SECOND
        }
        if (count != 3) return false
        count = 0
        return true
    }
    fun cancel() { downAt = null; count = 0; decision = Decision.CANCELLED }
}
