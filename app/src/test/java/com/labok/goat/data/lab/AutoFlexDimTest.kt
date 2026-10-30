package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
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

class AutoFlexModeDefaultTest {
    @Test
    fun onlyYoutubeCameraGalleryDefaultToFlex() {
        assertEquals(AutoFlex.MODE_FLEX, AutoFlex.defaultMode("com.google.android.youtube"))
        assertEquals(AutoFlex.MODE_FLEX, AutoFlex.defaultMode("com.sec.android.app.camera"))
        assertEquals(AutoFlex.MODE_FLEX, AutoFlex.defaultMode("com.sec.android.gallery3d"))
        assertEquals(AutoFlex.MODE_OFF, AutoFlex.defaultMode("com.kakao.talk"))
        assertEquals(AutoFlex.MODE_OFF, AutoFlex.defaultMode("com.android.chrome"))
    }
}
