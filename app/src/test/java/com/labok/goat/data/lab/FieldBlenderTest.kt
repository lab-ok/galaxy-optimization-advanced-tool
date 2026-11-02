package com.labok.goat.data.lab

import kotlin.math.abs
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldBlenderTest {
    private fun solid(c: Int, n: Int = 16) = IntArray(n) { c }

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun firstTargetShowsImmediately() {
        val b = FieldBlender()
        b.setTarget(solid(0xFF102030.toInt()), 4, 4, 0)
        val out = IntArray(16)
        assertFalse(b.step(16f, out))
        assertEquals(0xFF102030.toInt(), out[0])
    }

    @Test
    fun newTargetContinuesFromShownStateInsteadOfRestarting() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF000000.toInt()), 4, 4, 0)
        b.step(16f, out)
        b.setTarget(solid(0xFFFF0000.toInt()), 4, 4, 250)
        repeat(6) { b.step(16f, out) }
        val midRed = out[0] ushr 16 and 0xFF
        assertTrue("중간까지 와 있어야 한다: $midRed", midRed in 20..250)
        // 새 목표(파랑)가 와도 빨강이 0으로 되돌아가지 않고 지금 상태에서 이어 간다
        b.setTarget(solid(0xFF0000FF.toInt()), 4, 4, 500)
        b.step(1f, out)
        val red = out[0] ushr 16 and 0xFF
        assertTrue("지금 상태에서 이어야 한다: $red vs $midRed", red >= midRed - 10)
    }

    @Test
    fun stillScreenConvergesAndStops() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF000000.toInt()), 4, 4, 0)
        b.setTarget(solid(0xFFFFFFFF.toInt()), 4, 4, 250)
        var frames = 0
        while (b.step(16f, out) && frames < 400) frames++
        assertEquals(0xFFFFFFFF.toInt(), out[0])
        assertTrue("정지 화면은 목표에 닿고 멈춰야 한다: $frames 프레임", frames < 120)
        assertFalse(b.active)
    }

    @Test
    fun bigChangeIsFasterThanSmallChange() {
        assertTrue(FieldBlender.omegaFor(250f, 0.3f) > FieldBlender.omegaFor(250f, 0.005f))
    }

    @Test
    fun omegaFollowsSampleInterval() {
        assertTrue(FieldBlender.omegaFor(250f, 0.05f) > FieldBlender.omegaFor(1000f, 0.05f))
    }

    @Test
    fun fadeOutClears() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF808080.toInt()), 4, 4, 0)
        b.fadeOut()
        var n = 0
        while (b.step(16f, out) && n < 400) n++
        assertTrue(b.isClear())
        assertEquals(0, out[0])
    }

    /** 한 시퀀스 결과: 거의 멈춘 프레임 비율(속도가 평균의 25% 미만)과 속도 변동계수(작을수록 일정한 흐름). */
    private class Motion(val stall: Float, val cv: Float) {
        override fun toString() = "멈춤 %.3f · 속도 변동 %.2f".format(stall, cv)
    }

    private fun motion(deltas: List<Float>): Motion {
        val mean = deltas.average().toFloat()
        val sd = kotlin.math.sqrt(deltas.map { (it - mean) * (it - mean) }.average()).toFloat()
        return Motion(deltas.count { it < mean * 0.25f } / deltas.size.toFloat(), sd / mean)
    }

    /**
     * 가상 프레임 시퀀스: 화면이 꾸준히 밝아지는 영상(샘플마다 +16)을 [arrivals] 시각에 받고 16ms마다 그린다.
     * 반환: 움직이는 구간(세 번째 샘플부터 마지막 샘플까지)의 프레임별 변화량.
     */
    private fun deltasNew(arrivals: List<Long>): List<Float> {
        val b = FieldBlender()
        val out = IntArray(16)
        var t = 0L
        var k = 0
        var prev = -1f
        val d = ArrayList<Float>()
        while (t <= arrivals.last()) {
            while (k < arrivals.size && arrivals[k] <= t) {
                b.setTarget(solid(gray(20 + 16 * k)), 4, 4, arrivals[k])
                k++
            }
            b.step(16f, out)
            val v = b.shownBlue(0)
            if (t >= arrivals[2] && prev >= 0f) d.add(abs(v - prev))
            prev = v
            t += 16
        }
        return d
    }

    /** 같은 시퀀스를 예전 방식(지수 감쇠 τ 80~220ms, 1.42.9~11)으로. 비교용. */
    private fun deltasOld(arrivals: List<Long>): List<Float> {
        var shown = 20f
        var tau = 120f
        var target = 20f
        var t = 0L
        var k = 0
        var prev = -1f
        val d = ArrayList<Float>()
        while (t <= arrivals.last()) {
            while (k < arrivals.size && arrivals[k] <= t) {
                target = (20 + 16 * k).toFloat()
                val dd = abs(target - shown) / 255f
                tau = if (dd >= 0.12f) 80f else 220f - 100f * (dd / 0.12f)
                k++
            }
            shown += (target - shown) * (1f - exp(-16f / tau))
            if (t >= arrivals[2] && prev >= 0f) d.add(abs(shown - prev))
            prev = shown
            t += 16
        }
        return d
    }

    private fun regular(ms: Long) = (0 until 14).map { it * ms }

    @Test
    fun noStallBetweenSamplesAtRegularIntervals() {
        for (ms in listOf(250L, 500L, 1000L)) {
            val now = motion(deltasNew(regular(ms)))
            val old = motion(deltasOld(regular(ms)))
            println("간격 ${ms}ms: 새 방식 $now · 예전 $old")
            assertTrue("간격 ${ms}ms에서 멈춤이 남았다: $now", now.stall < 0.05f)
            assertTrue("예전보다 흐름이 일정해야 한다: $now vs $old", now.cv < old.cv && now.stall <= old.stall)
        }
    }

    @Test
    fun noStallWithIrregularIntervals() {
        val arrivals = listOf(0L, 250, 480, 900, 1100, 1350, 1900, 2150, 2400, 2700, 3300, 3550, 3800, 4100)
        val now = motion(deltasNew(arrivals))
        val old = motion(deltasOld(arrivals))
        println("불규칙 간격: 새 방식 $now · 예전 $old")
        assertTrue("불규칙 간격에서 멈춤이 남았다: $now", now.stall < 0.12f)
        assertTrue("예전보다 흐름이 일정해야 한다: $now vs $old", now.cv < old.cv && now.stall <= old.stall)
    }
}
