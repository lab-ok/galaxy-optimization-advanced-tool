package com.labok.goat.domain

import org.junit.Assert.*
import org.junit.Test

class PowerTriplePressTest {
    private val detector = PowerTriplePress()
    private fun event(time: Long, value: String, device: String = "/dev/input/event1") = detector.feed(PowerTriplePress.Event(device, time, value))
    private fun press(time: Long): PowerTriplePress.Result {
        event(time, "DOWN")
        return event(time + 50, "UP")
    }

    @Test fun threeCompleteShortPressesToggleExactlyOnce() {
        assertEquals(PowerTriplePress.Result.NONE, press(100))
        assertEquals(PowerTriplePress.Result.NONE, press(400))
        assertEquals(PowerTriplePress.Result.TRIPLE, press(700))
        assertEquals(PowerTriplePress.Result.NONE, press(900))
    }

    @Test fun sixPressesToggleTwice() {
        val results = (0L..5L).map { press(100 + it * 200) }
        assertEquals(2, results.count { it == PowerTriplePress.Result.TRIPLE })
    }

    @Test fun slowPressesDoNotToggle() {
        press(100)
        press(800)
        assertEquals(PowerTriplePress.Result.NONE, press(1700))
    }

    @Test fun totalWindowIncludesThirdRelease() {
        press(100)
        press(800)
        event(1500, "DOWN")
        assertEquals(PowerTriplePress.Result.NONE, event(1650, "UP"))
    }

    @Test fun longPressAndRepeatResetTheSequence() {
        press(100)
        event(300, "DOWN")
        assertEquals(PowerTriplePress.Result.NONE, event(900, "UP"))
        press(1000)
        press(1100)
        event(1200, "DOWN")
        event(1250, "REPEAT")
        assertEquals(PowerTriplePress.Result.NONE, event(1300, "UP"))
        assertEquals(PowerTriplePress.Result.NONE, press(1400))
    }

    @Test fun duplicateDownAndUnpairedReleaseAreNotExtraPresses() {
        event(50, "UP")
        event(100, "DOWN")
        event(110, "DOWN")
        event(120, "DOWN")
        assertEquals(PowerTriplePress.Result.NONE, event(150, "UP"))
        assertEquals(PowerTriplePress.Result.NONE, press(300))
        assertEquals(PowerTriplePress.Result.TRIPLE, press(500))
    }

    @Test fun differentInputDevicesCannotCompleteTheSameSequence() {
        press(100)
        press(300)
        event(500, "DOWN", "/dev/input/event2")
        assertEquals(PowerTriplePress.Result.NONE, event(550, "UP", "/dev/input/event2"))
    }

    @Test fun aNewSequenceCapturesANewDirection() {
        assertEquals(PowerTriplePress.Result.FIRST_DOWN, event(100, "DOWN"))
        event(150, "UP")
        assertEquals(PowerTriplePress.Result.FIRST_DOWN, event(2000, "DOWN"))
    }

    @Test fun parsesOnlyActualPowerKeyEventsAndKernelTimestamps() {
        val parsed = PowerTriplePress.parse("[  1234.567890] /dev/input/event2: EV_KEY       KEY_POWER            DOWN")!!
        assertEquals(1234567L, parsed.timeMs)
        assertEquals("/dev/input/event2", parsed.device)
        assertEquals("DOWN", parsed.value)
        assertNull(PowerTriplePress.parse("[ 1234.567890] /dev/input/event2: EV_KEY KEY_VOLUMEUP DOWN"))
        assertNull(PowerTriplePress.parse("add device 1: /dev/input/event2"))
        assertNull(PowerTriplePress.parse("[invalid] /dev/input/event2: EV_KEY KEY_POWER DOWN"))
    }

    @Test fun screenOffSleepCannotCompressSlowPressesIntoATriple() {
        // Kernel uptime advances less than elapsed time while the phone sleeps between presses.
        for ((kernel, elapsed) in listOf(100L to 100L, 200L to 1000L, 300L to 2000L)) {
            detector.feed(PowerTriplePress.Event("/dev/input/event1", kernel, "DOWN"), elapsed)
            assertEquals(PowerTriplePress.Result.NONE, detector.feed(PowerTriplePress.Event("/dev/input/event1", kernel + 50, "UP"), elapsed + 50))
        }
    }

}
