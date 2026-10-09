package com.local.folddpifix.ui.help

import com.local.folddpifix.ui.help.GrantGuideContent.Connection
import com.local.folddpifix.ui.help.GrantGuideContent.PcOs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantGuideContentTest {

    private fun commands(c: Connection, os: PcOs) =
        GrantGuideContent.steps(c, os).flatMap { it.commands }

    @Test
    fun everyCombinationEndsWithGrantForThatOs() {
        for (c in Connection.entries) for (os in PcOs.entries) {
            val steps = GrantGuideContent.steps(c, os)
            assertTrue(steps.first().openDevOptions)
            assertEquals("마무리", steps.last().title)
            assertTrue(commands(c, os).contains(GrantGuideContent.grantCommand(os)))
        }
    }

    @Test
    fun windowsUsesDotSlashAdb() {
        val cmds = commands(Connection.WIRELESS, PcOs.WINDOWS)
        assertTrue(cmds.filter { "adb" in it }.all { it.startsWith(".\\adb ") })
        assertEquals(
            ".\\adb shell pm grant com.local.folddpifix android.permission.WRITE_SECURE_SETTINGS",
            GrantGuideContent.grantCommand(PcOs.WINDOWS),
        )
    }

    @Test
    fun wirelessHasPairAndConnectUsbDoesNot() {
        val wireless = commands(Connection.WIRELESS, PcOs.MAC)
        assertTrue(wireless.any { it.startsWith("adb pair ") })
        assertTrue(wireless.any { it.startsWith("adb connect ") })
        val usb = commands(Connection.USB, PcOs.MAC)
        assertFalse(usb.any { " pair " in it || " connect " in it })
        assertTrue(usb.contains("adb devices"))
    }

    @Test
    fun installStepMatchesOs() {
        assertTrue(commands(Connection.USB, PcOs.MAC).contains("brew install --cask android-platform-tools"))
        assertTrue(commands(Connection.USB, PcOs.LINUX).contains("sudo apt install adb"))
        assertFalse(commands(Connection.USB, PcOs.WINDOWS).any { "apt" in it || "brew" in it })
    }
}
