package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrowAppsTest {
    @Test
    fun tallTurnsOffResizeAndPortraitOnlyThenEnables16by9() {
        val cmd = NarrowApps.commands("com.example", NarrowApps.Ratio.TALL, kill = true)
        assertEquals(
            "am compat disable 357141415 com.example; am compat disable 203647190 com.example; " +
                "am compat enable 174042980 com.example; am compat enable 265452344 com.example; am compat enable 180326787 com.example; " +
                "am compat reset 180326845 com.example; am force-stop com.example",
            cmd
        )
    }

    @Test
    fun mediumClearsTheOtherRatio() {
        val cmd = NarrowApps.commands("com.example", NarrowApps.Ratio.MEDIUM, kill = true)
        assertTrue(cmd.contains("am compat enable 180326845 com.example"))
        assertTrue(cmd.contains("am compat reset 180326787 com.example"))
    }

    @Test
    fun offResetsEveryChangeItTouched() {
        val cmd = NarrowApps.commands("com.example", null, kill = true)
        listOf("174042980", "180326787", "180326845", "265452344", "357141415", "203647190").forEach { assertTrue(it, cmd.contains("am compat reset $it com.example")) }
        assertFalse(cmd.contains("enable"))
        assertTrue(cmd.endsWith("am force-stop com.example"))
    }

    @Test
    fun reapplyKeepsRunningAppsOpen() {
        val cmd = NarrowApps.commands("com.example", NarrowApps.Ratio.TALL, kill = false)
        assertEquals(6, Regex("--no-kill").findAll(cmd).count())
        assertFalse(cmd.contains("force-stop"))
    }

    @Test
    fun parsesWmSizeWithAndWithoutOverride() {
        assertEquals((1248 to 1972) to null, NarrowApps.parseSize("Physical size: 1248x1972"))
        assertEquals((1248 to 1972) to (1080 to 1706), NarrowApps.parseSize("Physical size: 1248x1972\nOverride size: 1080x1706"))
        assertNull(NarrowApps.parseSize("error"))
    }
}
