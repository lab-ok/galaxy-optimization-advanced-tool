package com.labok.goat.domain

import com.labok.goat.domain.ShakeRotatePolicy.Detector
import com.labok.goat.domain.ShakeRotatePolicy.Peak
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 흔들기 판정 시험. 실제 센서처럼 50Hz(20ms 간격)로 값을 넣는다.
 * 손목 흔들기는 3~5Hz 사인파(세로로 든 폰의 x축), 걷기는 2Hz 위아래 흔들림, 탭은 한 샘플짜리 충격으로 흉내 낸다.
 */
class ShakeRotatePolicyTest {
    private val dt = 20L

    /** 세로로 든 채 [restMs] 동안 가만히. */
    private fun Detector.rest(from: Long, restMs: Long): Long {
        var t = from
        while (t < from + restMs) {
            feed(0f, 9.8f, 0f, t)
            t += dt
        }
        return t
    }

    /**
     * 손목으로 x축을 따라 [hz]로 [amp] 세기만큼 [ms] 동안 흔든다. [gyro]가 있으면 같은 리듬으로 손목 회전(rad/s)도 넣는다.
     * 판정된 순간이 있으면 true.
     */
    private fun Detector.shake(amp: Float, hz: Float = 4f, ms: Long = 700, gyro: Float? = null, from: Long = rest(0, 1000)): Boolean {
        var t = from
        var shook = false
        while (t < from + ms) {
            val ph = 2 * PI * hz * (t - from) / 1000.0
            gyro?.let { feedGyro(0f, 0f, (it * cos(ph)).toFloat(), t) }
            shook = feed((amp * sin(ph)).toFloat(), 9.8f, 0f, t) || shook
            t += dt
        }
        return shook
    }

    private fun detector(level: Int) = Detector(ShakeRotatePolicy.thresholdOf(level), ShakeRotatePolicy.gyroMinOf(level))

    @Test
    fun levelsMapBothWays() {
        assertTrue("단계가 높을수록 예민(기준이 낮다)", ShakeRotatePolicy.thresholdOf(ShakeRotatePolicy.MAX_LEVEL) < ShakeRotatePolicy.thresholdOf(1))
        for (l in ShakeRotatePolicy.MIN_LEVEL..ShakeRotatePolicy.MAX_LEVEL) {
            assertEquals(l, ShakeRotatePolicy.levelOf(ShakeRotatePolicy.thresholdOf(l)))
        }
    }

    @Test
    fun continuousShakeIsCountedAtEverySensitivity() {
        // 예전 판정은 계속 흔들면 흔들림이 끝나지 않아(세기가 기준 절반 아래로 안 떨어짐) 높은 민감도에서 오히려 못 잡았다
        for (level in listOf(1, 8, 17, 24, 30)) {
            val amp = ShakeRotatePolicy.thresholdOf(level) * 1.6f
            assertTrue("민감도 $level: 기준보다 센 연속 흔들기", detector(level).shake(amp, gyro = 4f))
        }
    }

    @Test
    fun maxSensitivityCatchesGentleShake() {
        assertTrue("가장 예민하면 아주 가볍게(2.5m/s²) 흔들어도 된다", detector(ShakeRotatePolicy.MAX_LEVEL).shake(2.5f, gyro = 1f))
    }

    @Test
    fun lowSensitivityNeedsStrongShake() {
        assertFalse(detector(1).shake(12f, gyro = 4f))
        assertTrue(detector(1).shake(30f, gyro = 4f))
    }

    @Test
    fun detectsQuicklyWithinAboutHalfASecond() {
        // 4Hz면 125ms마다 방향이 바뀐다: 세 번째 휘두름은 시작 뒤 0.5초 안에 잡혀야 한다
        val d = detector(ShakeRotatePolicy.DEFAULT_LEVEL)
        val from = d.rest(0, 1000)
        var t = from
        var at = -1L
        while (t < from + 1000 && at < 0) {
            val ph = 2 * PI * 4 * (t - from) / 1000.0
            d.feedGyro(0f, 0f, (4 * cos(ph)).toFloat(), t)
            if (d.feed((15 * sin(ph)).toFloat(), 9.8f, 0f, t)) at = t - from
            t += dt
        }
        assertTrue("판정 시각 ${at}ms", at in 1..500)
        assertEquals(3, d.lastPattern.size)
    }

    @Test
    fun walkingAndSlowTiltAreNotShakes() {
        val d = detector(ShakeRotatePolicy.DEFAULT_LEVEL)
        var t = d.rest(0, 1000)
        // 걷기: 2Hz 위아래 3m/s², 5초
        repeat(250) {
            val ph = 2 * PI * 2 * it * dt / 1000.0
            d.feedGyro(0.3f, 0f, 0f, t)
            assertFalse("걷기", d.feed(0f, 9.8f + (3 * sin(ph)).toFloat(), 0f, t))
            t += dt
        }
        // 2초에 걸쳐 천천히 가로로 눕히기
        repeat(100) { i ->
            val a = Math.toRadians(90.0 * i / 99)
            assertFalse("천천히 돌리기", d.feed((9.8 * sin(a)).toFloat(), (9.8 * cos(a)).toFloat(), 0f, t))
            t += dt
        }
    }

    @Test
    fun tapsInOneDirectionAreNotShakes() {
        // 뒷면을 같은 쪽으로 톡톡톡톡: 세기는 충분해도 방향이 바뀌지 않아 한 번으로 남는다
        val d = detector(ShakeRotatePolicy.DEFAULT_LEVEL)
        var t = d.rest(0, 1000)
        repeat(4) {
            assertFalse(d.feed(0f, 9.8f, 25f, t))
            t += dt
            t = d.rest(t, 140)
        }
    }

    @Test
    fun shakeWithoutWristRotationIsRejectedWhenGyroExists() {
        // 기준(약 8.7)을 겨우 넘는 회전 없는 왕복: 차량·책상 진동
        assertFalse("회전 없는 약한 왕복 진동(차량 등)", detector(ShakeRotatePolicy.DEFAULT_LEVEL).shake(10f, gyro = 0.1f))
        assertTrue("손목이 돌면 흔들기", detector(ShakeRotatePolicy.DEFAULT_LEVEL).shake(15f, gyro = 4f))
    }

    @Test
    fun slowBigShakeIsAShake() {
        // 1.2Hz로 천천히 크게 흔들기(팔): 손목 회전이 적어도 세기가 크면 흔들기다
        assertTrue("천천히 크게(손목 회전 적음)", detector(ShakeRotatePolicy.DEFAULT_LEVEL).shake(18f, hz = 1.2f, ms = 2000, gyro = 0.4f))
        assertTrue("천천히, 손목도 돌림", detector(ShakeRotatePolicy.DEFAULT_LEVEL).shake(12f, hz = 1.2f, ms = 2000, gyro = 3f))
    }

    @Test
    fun verySlowSwayIsNotAShake() {
        // 0.5Hz로 아주 천천히 좌우: 휘두름 사이가 너무 길다
        assertFalse(detector(ShakeRotatePolicy.MAX_LEVEL).shake(12f, hz = 0.5f, ms = 3000, gyro = 3f))
    }

    @Test
    fun knocksAndBouncesAreNotShakes() {
        // 바닥에 부딪혀 튕기듯 방향이 바뀌는 한 샘플짜리 충격들(회전 없음)
        val d = detector(ShakeRotatePolicy.MAX_LEVEL)
        d.feedGyro(0f, 0f, 0.1f, 0)
        var t = d.rest(0, 1000)
        repeat(6) { i ->
            assertFalse(d.feed(0f, 9.8f, if (i % 2 == 0) 25f else -25f, t))
            t += dt
            t = d.rest(t, 120)
        }
    }

    @Test
    fun dropIsIgnored() {
        // 떨어뜨림: 자유 낙하(0.3초) 뒤 바닥에서 크게 튕김
        val d = detector(ShakeRotatePolicy.MAX_LEVEL)
        var t = d.rest(0, 1000)
        repeat(15) {
            assertFalse(d.feed(0f, 0.5f, 0f, t))
            t += dt
        }
        repeat(6) { i ->
            assertFalse("떨어뜨린 뒤 튕김", d.feed(if (i % 2 == 0) 30f else -30f, 9.8f, 0f, t))
            t += 100
        }
    }

    @Test
    fun calibrateUsesTypicalStrongSwing() {
        assertNull("한 번만 흔들면 다시", ShakeRotatePolicy.calibrate(listOf(20f)))
        assertNull("너무 약하면 다시", ShakeRotatePolicy.calibrate(listOf(0.3f, 0.6f, 0.4f)))
        val d = Detector(ShakeRotatePolicy.CALIBRATE_MIN)
        d.shake(16f, gyro = 4f, ms = 1500)
        val level = ShakeRotatePolicy.calibrate(d.peaks.map { it.force })!!
        assertTrue("맞춘 세기로 흔들면 돌아간다(단계 $level)", detector(level).shake(16f, gyro = 4f))
        assertTrue("조금 약하게 흔들어도 돌아간다", detector(level).shake(12f, gyro = 4f))
    }

    @Test
    fun vibrationFollowsShakeRhythm() {
        // 휙 … 휘힉: 첫 흔들림 뒤 쉬었다가 빠르게 두 번
        val v = ShakeRotatePolicy.vibrationOf(listOf(Peak(0, 20f), Peak(300, 6f), Peak(380, 14f)))
        assertEquals("흔든 간격 그대로", listOf(0, 300, 80), v.map { it.gap })
        assertEquals("가장 센 것만 또렷한 클릭", listOf(true, false, false), v.map { it.accent })
        assertEquals(1f, v[0].scale, 0.001f)
        assertTrue("약한 휘두름은 약한 진동", v[1].scale < v[2].scale)
        assertTrue("세기는 세 단계", v.all { it.scale in setOf(0.5f, 0.7f, 1f) })
    }

    @Test
    fun vibrationBeatsStayDistinct() {
        val v = ShakeRotatePolicy.vibrationOf(listOf(Peak(0, 10f), Peak(20, 10f), Peak(5000, 10f)))
        assertEquals("너무 붙으면 50ms로 띄운다", ShakeRotatePolicy.MIN_GAP_MS, v[1].gap)
        assertEquals("너무 뜸하면 줄인다", ShakeRotatePolicy.MAX_GAP_MS, v[2].gap)
    }

    @Test
    fun rhythmFromCalibrationTightensTiming() {
        // 4Hz로 흔든 사람: 휘두름 간격 약 125ms → 기다리는 간격은 300ms로 줄어든다
        val d = Detector(ShakeRotatePolicy.CALIBRATE_MIN)
        d.shake(16f, gyro = 4f, ms = 1500)
        val rhythm = ShakeRotatePolicy.rhythmOf(d.peaks)!!
        assertTrue("리듬 ${rhythm}ms", rhythm in 100..160)
        val gap = ShakeRotatePolicy.maxGapFor(rhythm)
        assertTrue("기다리는 간격 ${gap}ms", gap in 300L..360L)
        val level = ShakeRotatePolicy.DEFAULT_LEVEL
        val tuned = Detector(ShakeRotatePolicy.thresholdOf(level), ShakeRotatePolicy.gyroMinOf(level), gap)
        assertTrue("자기 리듬으로 흔들면 된다", tuned.shake(15f, hz = 4f, gyro = 4f))
        val slowTuned = Detector(ShakeRotatePolicy.thresholdOf(level), ShakeRotatePolicy.gyroMinOf(level), gap)
        assertFalse("훨씬 느린 왕복은 그 사람의 흔들기가 아니다", slowTuned.shake(15f, hz = 1.2f, ms = 2000, gyro = 4f))
        assertEquals("리듬을 모르면 기본값", ShakeRotatePolicy.Detector.MAX_GAP_MS, ShakeRotatePolicy.maxGapFor(null))
    }

    @Test
    fun fastTurnReadsHeldDirectionFromGravity() {
        assertEquals("세로로 세워 든 폰", 0, ShakeRotatePolicy.rotationOf(0f, 9.8f))
        assertEquals("왼쪽으로 눕힌 폰(위가 왼쪽)", 1, ShakeRotatePolicy.rotationOf(9.8f, 0f))
        assertEquals("오른쪽으로 눕힌 폰(위가 오른쪽)", 3, ShakeRotatePolicy.rotationOf(-9.8f, 0f))
        assertEquals("조금 기울여도 세로", 0, ShakeRotatePolicy.rotationOf(3f, 8f))
        assertNull("책상에 거의 눕힘", ShakeRotatePolicy.rotationOf(1f, 2f))
        assertNull("45° 비스듬함은 애매", ShakeRotatePolicy.rotationOf(6f, 6f))
        assertNull("거꾸로는 시스템에 맡김", ShakeRotatePolicy.rotationOf(0f, -9.8f))
    }
}
