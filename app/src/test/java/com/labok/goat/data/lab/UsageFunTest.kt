package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageFunTest {
    private fun result(unlocks: Int = 0, screenMs: Long = 0, notifications: Int = 0, hourly: LongArray = LongArray(24), unfolds: Int? = null) =
        UsageTally.Result(
            usageAccess = true, screenOn = 10, screenMs = screenMs, hourlyMs = hourly, unlocks = unlocks, appOpens = 20,
            notifications = notifications, rotations = 0, unfolds = unfolds, boots = 0, charges = null, chargeStartAvg = null,
            volume = null, topApps = listOf("카카오톡" to 5), topNotifications = emptyList()
        )

    @Test
    fun deltaShowsDirection() {
        assertEquals("▲12", UsageFun.delta(37, 25))
        assertEquals("▼8", UsageFun.delta(74, 82))
        assertNull(UsageFun.delta(5, 5))
        assertNull(UsageFun.delta(5, null))
    }

    @Test
    fun hourNames() {
        assertEquals("새벽 2시대", UsageFun.hourName(2))
        assertEquals("낮 12시대", UsageFun.hourName(12))
        assertEquals("저녁 8시대", UsageFun.hourName(20))
    }

    @Test
    fun headlinePutsBiggestRiseFirst() {
        val now = result(unlocks = 74, notifications = 200, unfolds = 40)
        val before = result(unlocks = 70, notifications = 50, unfolds = 38)
        assertEquals("알림을 200개 받았습니다", UsageFun.headline(now, before).first())
    }

    @Test
    fun titlesUseDailyAverage() {
        val hourly = LongArray(24).also { it[1] = 40 * 60_000L }
        val week = result(unlocks = 7 * 90, screenMs = 7 * 7 * 3_600_000L, hourly = hourly)
        val names = UsageFun.titles(week, 7).map { it.name }
        assertTrue("잠금 해제 달인" in names)
        assertTrue("화면 지킴이" in names)
        assertTrue("올빼미" !in names)
    }
}
