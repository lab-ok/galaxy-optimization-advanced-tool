package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHealthTest {
    @Test
    fun kernelCapacityFirst() {
        // charge_full 3,900,000µAh / charge_full_design 4,500,000µAh → 86%
        val sh = "3900000 4500000 \n  mSavedBatteryAsoc: 92\n  Capacity: 4400, Computed drain: 0"
        val h = BatteryHealth.compute(sh, null, null, null)
        assertEquals(BatteryHealth.Source.KERNEL, h.source)
        assertEquals(3900, h.actualMah)
        assertEquals(4500, h.designMah)
        assertEquals(86, h.percent)
    }

    @Test
    fun samsungAsocTimesDesignWhenKernelBlocked() {
        // 커널 값이 없거나 설계 값을 두 번 넣은 경우(같은 값)는 쓰지 않고, 삼성 수명값 × 전력 프로필 설계 용량
        val sh = "4400000 4400000 \n  mSavedBatteryAsoc: 92\n  mSavedBatteryUsage: 33248\n    Capacity: 4400, Computed drain: 1200, actual drain: 1300"
        val h = BatteryHealth.compute(sh, null, null, null)
        assertEquals(BatteryHealth.Source.SAMSUNG, h.source)
        assertEquals(4048, h.actualMah)
        assertEquals(92, h.percent)
        assertEquals(332.48f, h.cycles!!, 0.001f)
    }

    @Test
    fun chargeCounterEstimate() {
        // 남은 전하량 3,000,000µAh, 잔량 80% → 완충 3,750mAh / 설계 4,400 → 85%
        val h = BatteryHealth.compute("\n  Capacity: 4400, Computed drain: 0", 3_000_000, 80, 120)
        assertEquals(BatteryHealth.Source.ESTIMATE, h.source)
        assertEquals(3750, h.actualMah)
        assertEquals(85, h.percent)
        assertEquals(120f, h.cycles!!, 0.001f)
        // 잔량이 낮으면 추정하지 않는다
        assertNull(BatteryHealth.compute("\n  Capacity: 4400", 900_000, 20, null).percent)
    }
}
