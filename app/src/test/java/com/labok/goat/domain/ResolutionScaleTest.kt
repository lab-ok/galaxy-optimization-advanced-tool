package com.labok.goat.domain

import com.labok.goat.data.display.DpiManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 해상도 낮추기가 DPI 기능에 거는 비율 경로(화면 키·목표 축소·외부 변경). 해상도를 바꾸지 않은 사용자에게는 아무것도 바뀌지 않아야 한다. */
class ResolutionScaleTest {

    @Test
    fun screenWithoutGoatResolutionIsUnchanged() {
        // 삼성 FHD+처럼 GOAT 밖에서 줄인 크기도 기록이 없으면 그대로 키가 되고 비율 1
        val s = DpiManager.screenOf(1080, 2340, 420f, 420f, base = null)
        assertEquals("1080x2340", s.key)
        assertEquals(1f, s.scale)
    }

    @Test
    fun goatResolutionKeepsBaseKeyAndScale() {
        val s = DpiManager.screenOf(900, 1422, 420f, 420f, base = 1248 to 1972)
        assertEquals("같은 화면으로 본다", "1248x1972", s.key)
        assertEquals(900f / 1248f, s.scale, 1e-4f)
        assertEquals("대각선도 직전 크기 기준", DpiManager.screenOf(1248, 1972, 420f, 420f, null).diagonalInch, s.diagonalInch, 1e-4f)
    }

    @Test
    fun tinyDifferenceAndLargerSizeAreScaleOne() {
        assertEquals("1% 안쪽은 반올림 차이", 1f, DpiManager.screenOf(1240, 1960, 420f, 420f, base = 1248 to 1972).scale)
        // GOAT로 낮춘 뒤 다른 곳에서 더 크게 바꾸면 낡은 기록이라 쓰지 않는다
        val bigger = DpiManager.screenOf(1440, 3120, 500f, 500f, base = 1080 to 2340)
        assertEquals("1440x3120", bigger.key)
        assertEquals(1f, bigger.scale)
    }

    private val plan = DensityPlan(referenceDpi = 360, reference = ScreenPolicy.Role.OUTER, adjust = 0, panels = emptyList())

    @Test
    fun targetIsScaledOnlyWhileLowered() {
        val full = ScreenInfo("1248x1972", 420f, 5.56f)
        assertEquals(360, plan.targetFor(full).dpi)
        val lowered = full.copy(scale = 900f / 1248f)
        val t = plan.targetFor(lowered)
        assertEquals(260, t.dpi)
        assertNotNull(t.note)
        assertTrue(t.note!!.contains("360 → 260"))
    }

    @Test
    fun externalChangeSerializationKeepsScaleAndReadsOldFormat() {
        val c = ExternalChange("1248x1972", ScreenPolicy.Role.OUTER, 260, 270, 1000L, 0.75f)
        assertEquals(c, ExternalChange.parse(c.serialize()))
        val old = ExternalChange.parse("1248x1972|OUTER|360|380|1000")
        assertNotNull(old)
        assertEquals("이전 판(다섯 칸)은 비율 1", 1f, old!!.scale)
        assertNull(ExternalChange.parse("a|b|c"))
    }

    @Test
    fun adoptUndoesScaleForReferenceScreen() {
        // 75%로 낮춘 화면에서 270으로 바뀐 것을 기준으로 삼으면 원래 해상도 기준 360이 된다
        val change = ExternalChange("1248x1972", ScreenPolicy.Role.OUTER, 260, 270, 1000L, 0.75f)
        assertEquals(360 to 0, ExternalChange.adopt(change, plan))
    }
}
