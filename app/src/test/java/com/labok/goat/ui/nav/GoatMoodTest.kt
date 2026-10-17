package com.labok.goat.ui.nav

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoatMoodTest {
    @Test fun eachPressChangesOnce() {
        val s = GoatMoodState()
        var before = s.mood to s.variant % faceCount(s.mood)
        repeat(20) {
            assertTrue(s.press(1_000L + it * 200L))
            val now = s.mood to s.variant % faceCount(s.mood)
            assertNotEquals(Mood.CALM, s.mood)
            assertNotEquals(before, now)
            before = now
        }
    }
}
