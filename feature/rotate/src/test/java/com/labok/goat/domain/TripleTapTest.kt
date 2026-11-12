package com.labok.goat.domain

import org.junit.Assert.*
import org.junit.Test

class TripleTapTest {
    private fun detector() = TripleTap(1500, 12f, 500)
    private fun tap(d: TripleTap, at: Long, hold: Long = 50): Boolean { d.down(100f, 100f, at); return d.up(100f, 100f, at + hold) }
    @Test fun exactlyThreeCompletedTapsFireOnceAndSixFireTwice() {
        val d = detector()
        assertFalse(tap(d, 0)); assertFalse(tap(d, 200)); assertTrue(tap(d, 400))
        assertFalse(tap(d, 600)); assertFalse(tap(d, 800)); assertTrue(tap(d, 1000))
    }
    @Test fun slowTapsRestartInsteadOfMatching() {
        val d = detector()
        tap(d, 0); tap(d, 1000); assertFalse(tap(d, 2000))
        assertEquals(1, d.count); assertEquals(TripleTap.Decision.WINDOW_EXPIRED, d.decision)
    }
    @Test fun boundaryIsInclusive() { val d = detector(); tap(d, 0); tap(d, 500); assertTrue(tap(d, 1500)) }
    @Test fun swipeBreaksTheSequenceEvenIfFingerReturnsToItsStartingPoint() {
        val d = detector(); tap(d, 0); tap(d, 200)
        d.down(100f, 100f, 400); d.move(100f, 60f)
        assertFalse(d.up(100f, 100f, 450)); assertEquals(0, d.count)
        assertFalse(tap(d, 600))
    }
    @Test fun longHoldIsNotATap() {
        val d = detector(); tap(d, 0); tap(d, 200)
        assertFalse(tap(d, 400, 500)); assertEquals(0, d.count)
    }
    @Test fun systemCancellationAndMultitouchResetTheSequence() {
        val d = detector(); tap(d, 0); tap(d, 200); d.down(100f, 100f, 400); d.cancel()
        assertFalse(d.up(100f, 100f, 450)); assertFalse(tap(d, 600))
    }
    @Test fun backwardsClockCannotMatch() {
        val d = detector(); tap(d, 1000); tap(d, 1200); assertFalse(tap(d, 300))
        assertEquals(1, d.count)
    }
    @Test fun smallFingerMovementStillCounts() {
        val d = detector(); tap(d, 0); tap(d, 200); d.down(100f, 100f, 400)
        assertTrue(d.up(105f, 104f, 450))
    }
}
