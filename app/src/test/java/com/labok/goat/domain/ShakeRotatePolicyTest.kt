package com.labok.goat.domain

import com.labok.goat.domain.ShakeRotatePolicy.Detector
import com.labok.goat.domain.ShakeRotatePolicy.Peak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 흔들기 판정 시험. 실제 센서처럼 50Hz(20ms 간격)로 값을 넣는다.
 * 손목 흔들기는 3~5Hz 사인파(세로로 든 폰의 x축), 걷기는 2Hz 위아래 흔들림, 탭은 한 샘플짜리 충격으로 흉내 낸다.
 */
class ShakeRotatePolicyTest {
    private val dt = 20L

    /** 세로로 든 채 [restMs] 동안 가만히. */
    private fun Detector.rest(from: Long, restMs: Long): Long {
        var t = from
        while (t < from + restMs) { feed(0f, 9.8f, 0f, t); t += dt }
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
        assertTrue("단계가 높을수록 예민(기준이 낮다)", ShakeRotatePolicy.thresholdOf(20) < ShakeRotatePolicy.thresholdOf(1))
        for (l in ShakeRotatePolicy.MIN_LEVEL..ShakeRotatePolicy.MAX_LEVEL) {
            assertEquals(l, ShakeRotatePolicy.levelOf(ShakeRotatePolicy.thresholdOf(l)))
        }
    }

    @Test
    fun continuousShakeIsCountedAtEverySensitivity() {
        // 예전 판정은 계속 흔들면 흔들림이 끝나지 않아(세기가 기준 절반 아래로 안 떨어짐) 높은 민감도에서 오히려 못 잡았다
        for (level in listOf(1, 8, 14, 20)) {
            val amp = ShakeRotatePolicy.thresholdOf(level) * 1.6f
            assertTrue("민감도 $level: 기준보다 센 연속 흔들기", detector(level).shake(amp, gyro = 4f))
        }
    }

    @Test
    fun maxSensitivityCatchesGentleShake() {
        assertTrue("민감도 20이면 가볍게(5m/s²) 흔들어도 된다", detector(20).shake(5f, gyro = 2f))
    }

    @Test
    fun lowSensitivityNeedsStrongShake() {
        assertFalse(detector(1).shake(12f, gyro = 4f))
        assertTrue(detector(1).shake(30f, gyro = 4f))
    }

    @Test
    fun detectsQuicklyWithinAboutHalfASecond() {
        // 4Hz면 125ms마다 방향이 바뀐다: 세 번째 휘두름은 시작 뒤 0.5초 안에 잡혀야 한다
        val d = detector(14)
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
        val d = detector(14)
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
        val d = detector(14)
        var t = d.rest(0, 1000)
        repeat(4) {
            assertFalse(d.feed(0f, 9.8f, 25f, t)); t += dt
            t = d.rest(t, 140)
        }
    }

    @Test
    fun shakeWithoutWristRotationIsRejectedWhenGyroExists() {
        assertFalse("회전 없는 왕복 진동(차량 등)", detector(14).shake(15f, gyro = 0.1f))
        assertTrue("손목이 돌면 흔들기", detector(14).shake(15f, gyro = 4f))
    }

    @Test
    fun slowSwayIsNotAShake() {
        // 1Hz로 천천히 좌우: 휘두름 사이가 너무 길다
        assertFalse(detector(20).shake(12f, hz = 1f, ms = 1500, gyro = 3f))
    }

    @Test
    fun calibrateUsesTypicalStrongSwing() {
        assertNull("한 번만 흔들면 다시", ShakeRotatePolicy.calibrate(listOf(20f)))
        assertNull("너무 약하면 다시", ShakeRotatePolicy.calibrate(listOf(1f, 1.5f, 1f)))
        val d = Detector(ShakeRotatePolicy.CALIBRATE_MIN)
        d.shake(16f, gyro = 4f, ms = 1500)
        val level = ShakeRotatePolicy.calibrate(d.peaks.map { it.force })!!
        assertTrue("맞춘 세기로 흔들면 돌아간다(단계 $level)", detector(level).shake(16f, gyro = 4f))
        assertTrue("조금 약하게 흔들어도 돌아간다", detector(level).shake(12f, gyro = 4f))
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
