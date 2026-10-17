package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthTest {
    @Test
    fun samsungFields() {
        // 삼성 dumpsys battery에 남는 값(공개된 설명: Asoc = 새 배터리 대비 용량, Usage/100 = 완충 환산 충전 횟수)
        val out = """
            Current Battery Service state:
              level: 85
              mSavedBatteryAsoc: 92
              mSavedBatteryBsoh: 95
              mSavedBatteryUsage: 33248
        """.trimIndent()
        val h = BatteryHealth.parse(out)
        assertEquals(92, h.asoc)
        assertEquals(95, h.bsoh)
        assertEquals(332.48f, h.cycles!!, 0.001f)
    }

    @Test
    fun missingFieldsAreNull() {
        val h = BatteryHealth.parse("Current Battery Service state:\n  level: 0\n  mSavedBatteryAsoc: -1")
        assertNull(h.asoc)
        assertNull(h.cycles)
        assertTrue(h.empty)
    }
}
