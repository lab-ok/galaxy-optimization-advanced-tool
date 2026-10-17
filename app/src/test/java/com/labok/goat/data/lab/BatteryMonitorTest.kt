package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMonitorTest {
    @Test
    fun currentIsMilliampsWithSignFromChargingState() {
        // µA로 주는 기기(절댓값 10,000 초과)는 mA로 바꾸고, 부호는 충전 상태에 맞춘다
        assertEquals(-450, BatteryMonitor.normalizeCurrent(450_000, charging = false))
        assertEquals(1200, BatteryMonitor.normalizeCurrent(-1_200_000, charging = true))
        // 삼성처럼 mA로 주는 기기
        assertEquals(-380, BatteryMonitor.normalizeCurrent(-380, charging = false))
        assertEquals(2100, BatteryMonitor.normalizeCurrent(2100, charging = true))
        assertNull(BatteryMonitor.normalizeCurrent(0, charging = false))
        assertNull(BatteryMonitor.normalizeCurrent(Int.MIN_VALUE, charging = false))
    }
}
