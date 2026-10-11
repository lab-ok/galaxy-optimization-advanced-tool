package com.labok.goat.domain

import com.labok.goat.domain.ShakeRotatePolicy.Detector
import com.labok.goat.domain.ShakeRotatePolicy.Peak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeRotatePolicyTest {
    @Test
    fun levelsMapBothWays() {
        assertTrue("단계가 높을수록 예민(기준이 낮다)", ShakeRotatePolicy.thresholdOf(20) < ShakeRotatePolicy.thresholdOf(1))
        for (l in ShakeRotatePolicy.MIN_LEVEL..ShakeRotatePolicy.MAX_LEVEL) {
            assertEquals(l, ShakeRotatePolicy.levelOf(ShakeRotatePolicy.thresholdOf(l)))
        }
    }

    /** 세로로 든 채 가만히 있다가, 기기 x축으로 [amp]만큼 [count]번 휙휙(좌우 번갈아) 흔든다. 판정되면 true. */
    private fun Detector.flicks(amp: Float, count: Int, gapMs: Long = 150, startMs: Long = 1000): Boolean {
        repeat(30) { feed(0f, 9.8f, 0f, it * 20L) }
        var t = startMs
        var shook = false
        repeat(count) { i ->
            val dir = if (i % 2 == 0) 1 else -1
            shook = feed(amp * dir, 9.8f, 0f, t) || shook
            shook = feed(0f, 9.8f, 0f, t + 40) || shook  // 흔들림 하나가 끝남
            t += gapMs
        }
        return shook
    }

    @Test
    fun swingsAgainstGravityCountToo() {
        // 중력 반대(아래→위로 -y)로 흔든 것도 센다: 예전처럼 크기에서 중력을 빼면 거의 0이 되던 경우
        val d = Detector(ShakeRotatePolicy.thresholdOf(ShakeRotatePolicy.DEFAULT_LEVEL))
        repeat(30) { d.feed(0f, 9.8f, 0f, it * 20L) }
        var shook = false
        var t = 1000L
        repeat(3) { i ->
            // 아래로 빠르게(중력 쪽 크기가 줄어듦) ↔ 위로
            shook = d.feed(0f, if (i % 2 == 0) 9.8f - 14f else 9.8f + 14f, 0f, t) || shook
            shook = d.feed(0f, 9.8f, 0f, t + 40) || shook
            t += 150
        }
        assertTrue(shook)
    }

    @Test
    fun tapsInOneDirectionAreNotShakes() {
        // 뒷면을 같은 쪽으로 톡톡톡: 세기는 충분해도 왕복이 아니라 아니다
        val d = Detector(ShakeRotatePolicy.thresholdOf(ShakeRotatePolicy.DEFAULT_LEVEL))
        repeat(30) { d.feed(0f, 9.8f, 0f, it * 20L) }
        var t = 1000L
        repeat(4) {
            assertFalse(d.feed(0f, 9.8f, 20f, t))
            assertFalse(d.feed(0f, 9.8f, 0f, t + 40))
            t += 150
        }
    }

    @Test
    fun gyroMustShowRotationWhenAvailable() {
        val level = ShakeRotatePolicy.DEFAULT_LEVEL
        val still = Detector(ShakeRotatePolicy.thresholdOf(level), ShakeRotatePolicy.gyroMinOf(level))
        still.feedGyro(0f, 0f, 0.1f, 900)
        assertFalse("회전 없는 왕복 충격은 아니다", still.flicks(15f, 3))
        val wrist = Detector(ShakeRotatePolicy.thresholdOf(level), ShakeRotatePolicy.gyroMinOf(level))
        wrist.feedGyro(0f, 0f, 0.1f, 900)
        wrist.feedGyro(0f, 0f, 3f, 1100)
        assertTrue("손목이 돌면 흔들기", wrist.flicks(15f, 3))
    }

    @Test
    fun lightFlicksShakeAtDefault() {
        val d = Detector(ShakeRotatePolicy.thresholdOf(ShakeRotatePolicy.DEFAULT_LEVEL))
        assertTrue("기본 단계에서는 가볍게 휙휙 흔들어도 돌아간다", d.flicks(13f, 3))
        assertEquals(3, d.lastPattern.size)
    }

    @Test
    fun walkingAndSlowTiltAreNotShakes() {
        val d = Detector(ShakeRotatePolicy.thresholdOf(ShakeRotatePolicy.DEFAULT_LEVEL))
        var t = 0L
        repeat(300) { i ->
            assertFalse("걷는 정도의 흔들림", d.feed(0f, 9.8f + if (i % 25 < 3) 3.5f else 0f, 0f, t))
            t += 20
        }
        // 천천히 가로로 눕히기(2초에 걸쳐)
        repeat(100) { i ->
            val a = Math.toRadians(90.0 * i / 99)
            assertFalse("천천히 돌리기", d.feed((9.8 * Math.sin(a)).toFloat(), (9.8 * Math.cos(a)).toFloat(), 0f, t))
            t += 20
        }
    }

    @Test
    fun twoFlicksOrSlowFlicksAreNotEnough() {
        assertFalse(Detector(ShakeRotatePolicy.thresholdOf(14)).flicks(20f, 2))
        assertFalse("너무 띄엄띄엄", Detector(ShakeRotatePolicy.thresholdOf(14)).flicks(20f, 3, gapMs = 600))
    }

    @Test
    fun levelChangesWhatCounts() {
        assertFalse(Detector(ShakeRotatePolicy.thresholdOf(1)).flicks(13f, 3))
        assertTrue(Detector(ShakeRotatePolicy.thresholdOf(20)).flicks(7f, 3))
    }

    @Test
    fun calibrateUsesTypicalStrongSwing() {
        assertNull("한 번만 흔들면 다시", ShakeRotatePolicy.calibrate(listOf(20f)))
        assertNull("너무 약하면 다시", ShakeRotatePolicy.calibrate(listOf(2f, 3f, 1f)))
        val level = ShakeRotatePolicy.calibrate(listOf(18f, 20f, 22f, 5f))!!
        // 가운데(20)의 60% = 12 근처 기준
        assertEquals(ShakeRotatePolicy.levelOf(12f), level)
        assertTrue("그 세기로 흔들면 돌아간다", Detector(ShakeRotatePolicy.thresholdOf(level)).flicks(18f, 3))
    }

    @Test
    fun vibrationFollowsShakeRhythm() {
        // 휙 … 휘힉: 첫 흔들림 뒤 쉬었다가 빠르게 두 번
        val v = ShakeRotatePolicy.vibrationOf(listOf(Peak(0, 20f), Peak(300, 10f), Peak(380, 15f)))
        assertEquals(listOf(0, 300, 80), v.map { it.first })
        assertEquals(1f, v[0].second, 0.001f)
        assertTrue("약한 흔들림은 약한 진동", v[1].second < v[2].second && v[2].second < v[0].second)
        assertEquals("간격은 최대값을 넘지 않는다", ShakeRotatePolicy.MAX_GAP_MS, ShakeRotatePolicy.vibrationOf(listOf(Peak(0, 1f), Peak(5000, 1f)))[1].first)
    }
}
