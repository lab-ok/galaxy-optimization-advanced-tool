package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldBlenderTest {
    private fun solid(c: Int, n: Int = 16) = IntArray(n) { c }

    @Test
    fun firstTargetShowsImmediately() {
        val b = FieldBlender()
        b.setTarget(solid(0xFF102030.toInt()), 4, 4)
        val out = IntArray(16)
        assertFalse(b.step(16f, out))
        assertEquals(0xFF102030.toInt(), out[0])
    }

    @Test
    fun newTargetContinuesFromShownStateInsteadOfRestarting() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF000000.toInt()), 4, 4)
        b.step(16f, out)
        b.setTarget(solid(0xFFFF0000.toInt()), 4, 4)
        repeat(3) { b.step(33f, out) }
        val midRed = out[0] ushr 16 and 0xFF
        assertTrue("중간까지 와 있어야 한다: $midRed", midRed in 60..250)
        // 새 목표(파랑)가 와도 빨강이 0으로 되돌아가지 않고 지금 상태에서 이어 간다
        b.setTarget(solid(0xFF0000FF.toInt()), 4, 4)
        b.step(1f, out)
        val red = out[0] ushr 16 and 0xFF
        assertTrue("지금 상태에서 이어야 한다: $red vs $midRed", red >= midRed - 10)
    }

    @Test
    fun convergesAndStops() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF000000.toInt()), 4, 4)
        b.setTarget(solid(0xFFFFFFFF.toInt()), 4, 4)
        var frames = 0
        while (b.step(16f, out) && frames < 200) frames++
        assertEquals(0xFFFFFFFF.toInt(), out[0])
        assertTrue("장면 전환은 0.6초 안에 끝나야 한다: $frames 프레임", frames * 16 < 600)
    }

    @Test
    fun bigChangeIsFasterThanSmallChange() {
        assertTrue(FieldBlender.tauFor(0.3f) < FieldBlender.tauFor(0.01f))
    }

    @Test
    fun fadeOutClears() {
        val b = FieldBlender()
        val out = IntArray(16)
        b.setTarget(solid(0xFF808080.toInt()), 4, 4)
        b.fadeOut()
        while (b.step(16f, out)) Unit
        assertTrue(b.isClear())
        assertEquals(0, out[0])
    }
}
