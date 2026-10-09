package com.local.folddpifix.domain

import com.local.folddpifix.domain.ScreenPolicy.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PanelSpecTest {

    // Fold4·5 해상도, 시스템 보고 PPI는 일부러 실제와 다르게 둔다.
    private val outer = ScreenInfo("904x2316", 428f, 5.8f)
    private val inner = ScreenInfo("1812x2176", 404f, 7.0f)

    @Test
    fun knownPanelUsesManufacturerDiagonal() {
        val r = PanelSpec.resolve(outer)
        assertEquals(PanelSpec.Source.SPEC, r.source)
        assertEquals(6.2f, r.screen.diagonalInch)
        assertEquals(401.0, r.screen.ppi.toDouble(), 0.1)
        assertEquals(428f, r.reported.ppi)
    }

    // 실기기 SM-F971N 진단 로그 값: 켜진 디스플레이는 두 화면 모두 428.2를 보고, 꺼진 내부는 404.2를 보고.
    private val f971Outer = ScreenInfo("1248x1972", 428.2f, 5.45f)
    private val f971Inner = ScreenInfo("1848x2448", 428.2f, 7.16f)

    @Test
    fun identicalActiveReadingsPreferInactiveReading() {
        val all = listOf(f971Outer, f971Inner)
        val inner = PanelSpec.resolve(f971Inner, inactivePpi = 404.2f, all = all)
        assertEquals(PanelSpec.Source.INACTIVE, inner.source)
        assertEquals(404.2f, inner.screen.ppi)
        assertEquals(7.59, inner.screen.diagonalInch.toDouble(), 0.01)
        // 외부는 꺼진 상태 값이 아직 없으면 켜졌을 때 값을 쓴다.
        assertEquals(PanelSpec.Source.REPORTED, PanelSpec.resolve(f971Outer, null, all).source)
    }

    @Test
    fun distinctActiveReadingsKeepActiveReading() {
        val a = ScreenInfo("1000x2000", 420f, 5.3f)
        val b = ScreenInfo("1800x2200", 380f, 7.5f)
        assertEquals(PanelSpec.Source.REPORTED, PanelSpec.resolve(b, inactivePpi = 300f, all = listOf(a, b)).source)
    }

    @Test
    fun f971TargetMatchesUserObservation() {
        val all = listOf(f971Outer, f971Inner)
        val plan = DensityPlan(
            referenceDpi = 360, reference = Role.OUTER, adjust = 0,
            panels = listOf(PanelSpec.resolve(f971Outer, null, all), PanelSpec.resolve(f971Inner, 404.2f, all)),
        )
        // 360 × 404.2 / 428.2 = 339.8 → 340 (사용자가 335~338을 선호 → 미세조정 -3 정도)
        assertEquals(340, plan.targetFor(f971Inner).dpi)
        assertEquals(337, plan.copy(adjust = -3).targetFor(f971Inner).dpi)
    }

    private fun plan(adjust: Int = 0) = DensityPlan(
        referenceDpi = 360,
        reference = Role.OUTER,
        adjust = adjust,
        panels = listOf(outer, inner).map { PanelSpec.resolve(it, null, listOf(outer, inner)) },
    )

    @Test
    fun specBasedTargetForFold5() {
        // 360 × 372.6 / 401.0 = 334.46 → 334 (보고값 기준이면 340)
        assertEquals(334, plan().targetFor(inner).dpi)
        assertEquals(360, plan().targetFor(outer).dpi)
    }

    @Test
    fun adjustAppliesOnlyToCorrectedScreen() {
        assertEquals(331, plan(adjust = -3).targetFor(inner).dpi)
        assertEquals(360, plan(adjust = -3).targetFor(outer).dpi)
    }

    @Test
    fun targetUsesLearnedPanelNotReportedValue() {
        assertEquals(334, plan().targetFor(inner.copy(ppi = 428f)).dpi)
    }

    @Test
    fun rowsListBothScreens() {
        val rows = plan().rows()
        assertEquals(listOf(Role.OUTER, Role.INNER), rows.map { it.role })
        assertEquals(listOf(360, 334), rows.map { it.dpi })
    }

    /** 진단 로그 재현: 대각선이 뒤바뀌어 외부가 7.6", 내부가 5.5"로 계산된 상태. */
    @Test
    fun swappedDiagonalsDoNotFlipRolesAndAreBlocked() {
        val badOuter = ScreenInfo("1248x1972", 307.1f, 7.6f)
        val badInner = ScreenInfo("1848x2448", 557.7f, 5.5f)
        val screens = listOf(badOuter, badInner)
        // 역할은 픽셀 수로 정한다: 대각선이 뒤바뀌어도 외부/내부가 바뀌지 않는다.
        assertEquals(Role.OUTER, ScreenPolicy.roleOf(screens, badOuter.key))
        assertEquals(Role.INNER, ScreenPolicy.roleOf(screens, badInner.key))
        // 비율 1.82는 비정상 → 적용 보류(이전에는 640으로 잘려 적용됐다).
        val t = ScreenPolicy.effectiveTarget(360, Role.OUTER, screens, badInner)
        assertNotNull(t.blocked)
        // 기준 화면은 그대로 적용한다.
        assertNull(ScreenPolicy.effectiveTarget(360, Role.OUTER, screens, badOuter).blocked)
    }

    @Test
    fun outOfRangeResultIsBlockedNotClamped() {
        val a = ScreenInfo("1000x2000", 420f, 5.3f)
        val b = ScreenInfo("1800x2200", 330f, 8.6f)
        val t = ScreenPolicy.effectiveTarget(300, Role.OUTER, listOf(a, b), b)
        assertEquals("보정 결과는 범위 밖", 236, ScreenPolicy.scale(300, 420f, 330f))
        assertNotNull(t.blocked)
    }

    @Test
    fun unknownPanelFallsBackToReported() {
        val odd = ScreenInfo("1000x2000", 420f, 5.3f)
        assertEquals(PanelSpec.Source.REPORTED, PanelSpec.resolve(odd).source)
        assertNull(PanelSpec.ppi("bad", 6f))
    }
}
