package com.labok.goat.domain

import com.labok.goat.domain.ShakeRotatePolicy.Detector
import com.labok.goat.domain.ShakeRotatePolicy.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeRotatePolicyTest {
    @Test
    fun orientationFollowsGravity() {
        assertEquals(0, ShakeRotatePolicy.orientationOf(0f, 9.8f))
        assertEquals(2, ShakeRotatePolicy.orientationOf(0f, -9.8f))
        assertEquals(1, ShakeRotatePolicy.orientationOf(9.8f, 0f))
        assertEquals(3, ShakeRotatePolicy.orientationOf(-9.8f, 0f))
        assertNull("바닥에 눕히면 방향을 정하지 않는다", ShakeRotatePolicy.orientationOf(0.5f, 1f))
    }

    @Test
    fun nextTurnsToHeldOrToggles() {
        assertEquals("가로로 들고 흔들면 가로", 1, ShakeRotatePolicy.next(0, 1))
        assertEquals("세로로 들고 흔들면 세로", 0, ShakeRotatePolicy.next(3, 0))
        assertEquals("이미 그 방향이면 세로↔가로", 1, ShakeRotatePolicy.next(0, 0))
        assertEquals(0, ShakeRotatePolicy.next(1, 1))
        assertEquals("방향을 모르면 세로↔가로", 1, ShakeRotatePolicy.next(0, null))
        assertEquals(0, ShakeRotatePolicy.next(3, null))
    }

    @Test
    fun threeStrongPeaksMakeAShake() {
        val d = Detector(Sensitivity.NORMAL)
        // 가로로 든 채 가만히
        repeat(20) { d.feed(9.8f, 0f, 0f, it * 20L) }
        var t = 400L
        var shook = false
        repeat(3) { i ->
            shook = d.feed(if (i % 2 == 0) 28f else -26f, 0f, 0f, t)
            t += 150
        }
        assertTrue(shook)
        assertEquals("흔들기 전 방향을 기억한다", 1, d.heldBefore)
        assertFalse("판정 직후에는 쉰다", d.feed(30f, 0f, 0f, t + 100))
    }

    @Test
    fun walkingOrWeakMovesAreNotShakes() {
        val d = Detector(Sensitivity.NORMAL)
        var t = 0L
        repeat(200) { i ->
            assertFalse(d.feed(0f, 9.8f + if (i % 10 == 0) 6f else 0f, 0f, t))
            t += 20
        }
        // 강한 흔들림이 두 번뿐이면 아니다
        assertFalse(d.feed(0f, 30f, 0f, t)); t += 150
        assertFalse(d.feed(0f, -26f, 0f, t))
        // 너무 띄엄띄엄이면 아니다
        assertFalse(d.feed(0f, 30f, 0f, t + 2000))
    }

    @Test
    fun sensitivityChangesThreshold() {
        val low = Detector(Sensitivity.LOW)
        val high = Detector(Sensitivity.HIGH)
        var lowShook = false
        var highShook = false
        repeat(3) { i ->
            val v = if (i % 2 == 0) 22f else -21f
            lowShook = low.feed(0f, v, 0f, 1000L + i * 150)
            highShook = high.feed(0f, v, 0f, 1000L + i * 150)
        }
        assertFalse(lowShook)
        assertTrue(highShook)
    }
}
