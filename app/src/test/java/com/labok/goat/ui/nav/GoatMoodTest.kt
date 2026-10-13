package com.labok.goat.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoatMoodTest {
    @Test fun keepsAlternatingAngryAndCry() {
        val s = GoatMoodState()
        val seen = (0 until 30).map {
            s.press(1_000L + it * 200L)
            s.mood
        }
        assertEquals(Mood.SQUINT, seen[3])
        assertEquals(Mood.CRY, seen[4]) // 5번째
        assertEquals(Mood.ANGRY, seen[9]) // 10번째
        assertEquals(Mood.CRY, seen[14]) // 15번째: 다시 울음
        assertEquals(Mood.ANGRY, seen[19])
        assertEquals(Mood.CRY, seen[24])
    }

    @Test fun pauseStartsOver() {
        val s = GoatMoodState()
        repeat(6) { s.press(1_000L + it * 200L) }
        assertEquals(Mood.CRY, s.mood)
        s.press(1_000L + 5 * 200L + GoatMoodState.STREAK_GAP_MS + 1)
        assertEquals(Mood.SQUINT, s.mood)
    }

    @Test fun signalsEachSwitch() {
        val s = GoatMoodState()
        var angry = 0
        var cry = 0
        repeat(30) {
            if (s.press(1_000L + it * 200L)) angry++
            if (s.startedCrying) cry++
        }
        // 30번: 울음 5·15·25번째, 화남 10·20·30번째에 시작
        assertTrue(angry == 3 && cry == 3)
    }
}
