package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.ChargeLimit.Choice
import com.labok.goat.data.tweaks.ChargeLimit.Found
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeLimitTest {
    @Test
    fun writesOnlyToTheTableWhereTheKeyWasFound() {
        assertEquals(
            listOf("settings put global protect_battery 1", "settings put global battery_protection_threshold 90"),
            ChargeLimit.plan(Found("global", 0, 85), Choice.MAXIMUM, 90),
        )
        assertEquals("상한 키가 없는 이전 One UI는 방식만", listOf("settings put system protect_battery 1"), ChargeLimit.plan(Found("system", 0, null), Choice.MAXIMUM, 90))
        assertEquals("최대가 아니면 상한은 쓰지 않는다", listOf("settings put global protect_battery 3"), ChargeLimit.plan(Found("global", 1, 85), Choice.BASIC, 90))
    }

    @Test
    fun limitSnapsToSupportedSteps() {
        assertEquals(80, ChargeLimit.nearest(70))
        assertEquals(85, ChargeLimit.nearest(86))
        assertEquals(95, ChargeLimit.nearest(100))
    }

    @Test
    fun deviceCareAutoMaximumCountsAsMaximum() {
        assertEquals(Choice.MAXIMUM, ChargeLimit.choiceOf(2))
        assertEquals(Choice.BASIC, ChargeLimit.choiceOf(3))
        assertEquals(Choice.OFF, ChargeLimit.choiceOf(0))
        assertEquals("모르는 값은 단정하지 않는다", null, ChargeLimit.choiceOf(7))
    }
}
