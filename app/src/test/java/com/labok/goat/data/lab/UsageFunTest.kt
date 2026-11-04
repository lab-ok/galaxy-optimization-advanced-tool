package com.labok.goat.data.lab

import com.labok.goat.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsageFunTest {
    // 문구가 리소스로 옮겨져 JVM 테스트에서는 xml을 직접 읽어 넣는다
    @Before
    fun strings() = TestStrings.install()

    private fun result(unlocks: Int = 0, screenMs: Long = 0, notifications: Int = 0, hourly: LongArray = LongArray(24), unfolds: Int? = null) = UsageTally.Result(
        usageAccess = true, screenOn = 10, screenMs = screenMs, hourlyMs = hourly, unlocks = unlocks, appOpens = 20,
        notifications = notifications, rotations = 0, unfolds = unfolds, boots = 0, charges = null, chargeStartSum = 0,
        volume = null, apps = mapOf("com.kakao.talk" to 5), notis = emptyMap(), batteryUsed = null, batteryScreen = null, batteryCharged = null
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

    @Test
    fun unfoldIgnoresDpi() {
        // 폴드8: 바깥 화면 360dpi에서 sw 554dp(554x873), 안쪽 337dpi에서 sw 877dp(877x1162). 둘 다 500dp가 넘어도 펼침으로 센다
        assertEquals(UsageTally.Shape.UNFOLDED, UsageTally.shapeChange(554, 554, 873, 1, 877, 877, 1162, 1))
        assertEquals(UsageTally.Shape.FOLDED, UsageTally.shapeChange(877, 877, 1162, 1, 554, 554, 873, 1))
        // 회전: 가장 짧은 변 그대로, 가로·세로 맞바뀜
        assertEquals(UsageTally.Shape.ROTATED, UsageTally.shapeChange(554, 554, 873, 1, 554, 897, 530, 2))
        // 글자 크기·배율만 바뀐 것(비율 같음)은 세지 않는다
        assertEquals(UsageTally.Shape.SAME, UsageTally.shapeChange(554, 554, 873, 1, 700, 700, 1103, 1))
    }
}
