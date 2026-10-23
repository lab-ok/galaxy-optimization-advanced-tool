package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrowAppsTest {
    @Test
    fun onDisablesUniversalResizeThenEnablesPortraitAnd16by9() {
        val cmd = NarrowApps.commands("com.example", on = true, kill = true)
        assertEquals(
            "am compat disable 357141415 com.example; am compat enable 174042980 com.example; " +
                "am compat enable 180326787 com.example; am compat enable 265452344 com.example; am force-stop com.example",
            cmd
        )
    }

    @Test
    fun offResetsEveryChangeItTouched() {
        val cmd = NarrowApps.commands("com.example", on = false, kill = true)
        listOf("174042980", "180326787", "265452344", "357141415").forEach { assertTrue(cmd.contains("am compat reset $it com.example")) }
        assertFalse(cmd.contains("enable"))
        assertTrue(cmd.endsWith("am force-stop com.example"))
    }

    @Test
    fun reapplyKeepsRunningAppsOpen() {
        val cmd = NarrowApps.commands("com.example", on = true, kill = false)
        assertEquals(4, Regex("--no-kill").findAll(cmd).count())
        assertFalse(cmd.contains("force-stop"))
    }
}
