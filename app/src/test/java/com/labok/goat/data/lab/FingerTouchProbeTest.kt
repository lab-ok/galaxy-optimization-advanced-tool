package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class FingerTouchProbeTest {
    @Test
    fun readsDeviceNamesAndEventsInGeteventFormat() {
        // 실기 getevent -pl / -lt 형식(에뮬레이터에는 입력 장치가 없어 형식으로만 확인한다)
        val pl = """
            add device 1: /dev/input/event3
              name:     "gpio_keys"
              events:
                KEY (0001): KEY_POWER
            add device 2: /dev/input/event5
              name:     "sec_touchscreen"
        """.trimIndent()
        assertEquals(mapOf("/dev/input/event3" to "gpio_keys", "/dev/input/event5" to "sec_touchscreen"), FingerTouchProbe.deviceNames(pl))
        val m = FingerTouchProbe.EVENT.find("[   12345.678901] /dev/input/event3: EV_KEY       KEY_WAKEUP           DOWN")!!
        assertEquals(listOf("/dev/input/event3", "EV_KEY", "KEY_WAKEUP", "DOWN"), m.groupValues.drop(1))
    }
}
