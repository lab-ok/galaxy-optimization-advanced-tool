package com.labok.goat.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoatMoodTest {
    @Test fun changesOnceAndStays() {
        val s = GoatMoodState()
        assertTrue(s.press(1_000L))
        val face = s.mood to s.variant
        assertNotEquals(Mood.CALM, s.mood)
        // 이어서 여러 번 눌러도 그대로
        (1 until 20).forEach { assertFalse(s.press(1_000L + it * 200L)) }
        assertEquals(face, s.mood to s.variant)
    }

    @Test fun pauseChangesOnceMore() {
        val s = GoatMoodState()
        s.press(1_000L)
        val first = s.mood to s.variant % faceCount(s.mood)
        assertTrue(s.press(1_000L + GoatMoodState.STREAK_GAP_MS + 1))
        assertNotEquals(first, s.mood to s.variant % faceCount(s.mood))
    }
}
