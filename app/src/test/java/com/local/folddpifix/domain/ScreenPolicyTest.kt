package com.local.folddpifix.domain

import com.local.folddpifix.domain.ScreenPolicy.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenPolicyTest {

    private val outer = ScreenInfo("904x2316", 402f, 6.2f)
    private val inner = ScreenInfo("1812x2176", 373f, 7.6f)
    private val both = listOf(outer, inner)

    @Test
    fun classifiesBySizeOnlyWhenBothKnown() {
        assertNull(ScreenPolicy.roleOf(listOf(outer), outer.key))
        assertEquals(Role.OUTER, ScreenPolicy.roleOf(both, outer.key))
        assertEquals(Role.INNER, ScreenPolicy.roleOf(both, inner.key))
        assertNull(ScreenPolicy.roleOf(both, "1x1"))
    }

    @Test
    fun physicalSizeIsPreserved() {
        val t = ScreenPolicy.effectiveTarget(420, Role.OUTER, both, inner)
        assertEquals(390, t.dpi) // 420 × 373 / 402 = 389.7
        assertEquals(Role.INNER, t.role)
        assertNotNull(t.note)
        // 1dp의 실제 길이(density/ppi)가 두 화면에서 1% 안으로 같다.
        val outerLen = 420f / outer.ppi
        val innerLen = t.dpi / inner.ppi
        assertEquals(1.0, (innerLen / outerLen).toDouble(), 0.01)
    }

    @Test
    fun referenceScreenUsesTargetAsIs() {
        assertEquals(420, ScreenPolicy.effectiveTarget(420, Role.OUTER, both, outer).dpi)
        assertEquals(380, ScreenPolicy.effectiveTarget(380, Role.INNER, both, inner).dpi)
        // 내부 기준이면 외부가 보정된다: 380 × 402 / 373 = 409.5
        assertEquals(410, ScreenPolicy.effectiveTarget(380, Role.INNER, both, outer).dpi)
    }

    @Test
    fun fallsBackWithoutOtherScreen() {
        val t = ScreenPolicy.effectiveTarget(420, Role.OUTER, listOf(inner), inner)
        assertEquals(420, t.dpi)
        assertNull(t.role)
        assertEquals(420, ScreenPolicy.effectiveTarget(420, Role.OUTER, both, null).dpi)
    }

    @Test
    fun extremeRatioIsBlocked() {
        // 픽셀이 더 많은(내부) 화면인데 PPI가 비정상적으로 낮다 → 비율 0.37, 적용 보류.
        val odd = ScreenInfo("1800x2400", 150f, 20f)
        val t = ScreenPolicy.effectiveTarget(400, Role.OUTER, listOf(outer, odd), odd)
        assertEquals(Role.INNER, t.role)
        assertNotNull(t.blocked)
    }

    @Test
    fun ignoresImplausiblePpi() {
        val bogus = ScreenInfo("1x2", 0f, 1f)
        assertNull(ScreenPolicy.roleOf(listOf(outer, bogus), outer.key))
    }

    @Test
    fun watchStopsOnlyWhenBothScreensConfirmed() {
        assertEquals(false, ScreenPolicy.allConfirmed(listOf(outer), setOf(outer.key)))
        assertEquals(false, ScreenPolicy.allConfirmed(both, setOf(outer.key)))
        assertEquals(true, ScreenPolicy.allConfirmed(both, setOf(outer.key, inner.key)))
    }

    @Test
    fun encodeDecodeAndRemember() {
        assertEquals(both, ScreenPolicy.decode(ScreenPolicy.encode(both)))
        assertEquals(emptyList<ScreenInfo>(), ScreenPolicy.decode(null))
        val updated = ScreenPolicy.remember(both, outer.copy(ppi = 401f))
        assertEquals(2, updated.size)
        assertEquals(401f, updated.last().ppi)
    }
}
