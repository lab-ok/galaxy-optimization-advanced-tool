package com.labok.goat.data.lab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFlexDimTest {
    @Test
    fun dimsOnlyWhenNearlyClosedButNotShut() {
        assertTrue(AutoFlex.dimWanted(30f))
        assertTrue(AutoFlex.dimWanted(49f))
    }

    @Test
    fun fullyClosedCoverScreenKeepsAutoBrightness() {
        assertFalse(AutoFlex.dimWanted(0f))
        assertFalse(AutoFlex.dimWanted(15f))
    }

    @Test
    fun openEnoughKeepsAutoBrightness() {
        assertFalse(AutoFlex.dimWanted(50f))
        assertFalse(AutoFlex.dimWanted(180f))
    }
}
