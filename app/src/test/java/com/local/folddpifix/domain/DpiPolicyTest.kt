package com.local.folddpifix.domain

import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.domain.DpiPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiPolicyTest {

    @Test
    fun validateAcceptsRangeBoundaries() {
        assertEquals(DpiPolicy.Validation.Ok(240), DpiPolicy.validate("240"))
        assertEquals(DpiPolicy.Validation.Ok(640), DpiPolicy.validate(" 640 "))
        assertEquals(DpiPolicy.Validation.Ok(420), DpiPolicy.validate("420"))
    }

    @Test
    fun validateRejectsInvalidInput() {
        listOf(null, "", "  ", "abc", "4.2", "-420", "239", "641", "99999").forEach {
            assertTrue("입력 '$it'", DpiPolicy.validate(it) is DpiPolicy.Validation.Error)
        }
    }

    private fun decide(
        automatic: Boolean = true,
        autoEnabled: Boolean = true,
        hasPermission: Boolean = true,
        target: Int? = 420,
        current: Int? = 480,
        times: List<Long> = emptyList(),
        now: Long = 1_000_000L,
    ) = DpiPolicy.decide(automatic, autoEnabled, hasPermission, target, current, times, now)

    @Test
    fun decisionOrder() {
        assertEquals(Decision.SKIP_DISABLED, decide(autoEnabled = false, hasPermission = false))
        assertEquals(Decision.NO_PERMISSION, decide(hasPermission = false))
        assertEquals(Decision.NO_TARGET, decide(target = null))
        assertEquals(Decision.NO_TARGET, decide(target = 100))
        assertEquals(Decision.UNKNOWN_CURRENT, decide(current = null))
        assertEquals(Decision.ALREADY_OK, decide(current = 420))
        assertEquals(Decision.APPLY, decide())
    }

    @Test
    fun manualApplyIgnoresAutoSwitch() {
        assertEquals(Decision.APPLY, decide(automatic = false, autoEnabled = false))
    }

    @Test
    fun rateLimitCountsOnlyRecentWindow() {
        val now = 1_000_000L
        val recent = List(DpiPolicy.MAX_APPLIES_PER_WINDOW) { now - 1_000L * it }
        assertEquals(Decision.RATE_LIMITED, decide(times = recent, now = now))
        assertEquals(Decision.APPLY, decide(automatic = false, times = recent, now = now))
        val old = recent.map { it - DpiPolicy.APPLY_WINDOW_MS }
        assertEquals(Decision.APPLY, decide(times = old, now = now))
        assertEquals(0, DpiPolicy.pruneApplyTimes(old, now).size)
    }

    @Test
    fun parsesWmDensityOutput() {
        val s = DpiManager.parseWmDensity("Physical density: 480\nOverride density: 420\n")!!
        assertEquals(480, s.physical)
        assertEquals(420, s.override)
        assertEquals(420, s.current)
        val noOverride = DpiManager.parseWmDensity("Physical density: 420")!!
        assertEquals(null, noOverride.override)
        assertEquals(420, noOverride.current)
    }
}
