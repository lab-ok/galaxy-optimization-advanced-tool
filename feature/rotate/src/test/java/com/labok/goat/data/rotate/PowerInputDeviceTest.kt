package com.labok.goat.data.rotate

import org.junit.Assert.*
import org.junit.Test

class PowerInputDeviceTest {
    @Test fun skipsTouchscreenAndFindsPowerOnAWrappedKeyLine() {
        assertEquals(listOf("/dev/input/event8"), PowerInputDevice.findAll("""
            add device 1: /dev/input/event2
              name: "touchscreen"
              events:
                KEY (0001): BTN_TOUCH
                ABS (0003): ABS_MT_POSITION_X
            add device 2: /dev/input/event8
              name: "gpio-keys"
              events:
                KEY (0001): KEY_VOLUMEUP KEY_VOLUMEDOWN
                            KEY_POWER
              input props:
                <none>
        """.trimIndent()))
    }

    @Test fun includesMultiplePowerSourcesWithoutIncludingTouchDevices() {
        assertEquals(listOf("/dev/input/event0", "/dev/input/event12"), PowerInputDevice.findAll("""
            add device 1: /dev/input/event0
              KEY (0001): KEY_POWER
            add device 2: /dev/input/event12
              KEY (0001): KEY_ESC KEY_POWER
        """.trimIndent()))
    }

    @Test fun ignoresNamesOtherEventTypesAndSimilarKeyLabels() {
        assertEquals(emptyList<String>(), PowerInputDevice.findAll("""
            add device 1: /dev/input/event1
              name: "KEY_POWER"
              events:
                KEY (0001): KEY_POWER2 KEY_POWERON
                ABS (0003): KEY_POWER
              input props:
                KEY_POWER
            add device 2: /dev/input/event2
              KEY (0001): BTN_TOUCH
        """.trimIndent()))
    }

    @Test fun rejectsInvalidDevicePathsAndDoesNotReuseThePreviousDevice() {
        assertEquals(emptyList<String>(), PowerInputDevice.findAll("""
            add device 1: /dev/input/event1
              KEY (0001): KEY_VOLUMEUP
            add device 2: /dev/input/event2;reboot
              KEY (0001): KEY_POWER
            add device 3: /tmp/event3
              KEY (0001): KEY_POWER
        """.trimIndent()))
    }

    @Test fun missingOrInaccessiblePowerDeviceHasNoFallback() {
        assertEquals(emptyList<String>(), PowerInputDevice.findAll(""))
        assertEquals(emptyList<String>(), PowerInputDevice.findAll("could not open /dev/input/event0, Permission denied"))
        assertEquals(emptyList<String>(), PowerInputDevice.findAll("""
            add device 1: /dev/input/event1
              KEY (0001): KEY_VOLUMEUP KEY_VOLUMEDOWN
        """.trimIndent()))
    }

    @Test fun acceptsExtraWhitespaceAndWindowsLineEndings() {
        assertEquals(listOf("/dev/input/event21"), PowerInputDevice.findAll(
            "add device 1: /dev/input/event21\r\n    KEY   (0001): KEY_POWER  \r\n"
        ))
    }
}
