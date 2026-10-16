package com.labok.goat.data.lab

import com.labok.goat.data.lab.BatteryCycles.Cycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCyclesTest {
    private fun cycle(start: Long, apps: List<Pair<String, Float>> = listOf("크롬" to 0.25f)) = Cycle(start, 36_000_000L, 10_800_000L, 41, 28, 12, apps)

    @Test
    fun roundTripKeepsNamesWithOddCharacters() {
        val c = cycle(1L, listOf("카카오\t톡" to 0.5f, "a=b" to 0.125f, "x␞y" to 0f))
        val back = BatteryCycles.decodeOne(BatteryCycles.encodeOne(c))!!
        assertEquals(listOf("카카오 톡" to 0.5f, "a=b" to 0.125f, "x y" to 0f), back.apps)
        assertEquals(c.copy(apps = back.apps), back)
        assertEquals(listOf(c.copy(apps = back.apps), cycle(2L)), BatteryCycles.decode(BatteryCycles.encode(listOf(c, cycle(2L)))))
        assertNull(BatteryCycles.decodeOne("1\t2"))
        assertEquals(emptyList<Cycle>(), BatteryCycles.decode(null))
    }

    @Test
    fun trimKeepsNewestTenWithoutDuplicates() {
        val list = (1L..12L).map { cycle(it) } + cycle(12L)
        val t = BatteryCycles.trim(list)
        assertEquals(BatteryCycles.KEEP, t.size)
        assertEquals((12L downTo 3L).toList(), t.map { it.start })
    }

    @Test
    fun summarizeTakesTopThreeAppsByShare() {
        val s = BatteryStatsReader.SinceCharge(
            100L, 36_000_000L, 10_800_000L, 4400, 40, 41, 28, 12, 0.0, 0.0, 1000.0,
            listOf(
                BatteryStatsReader.AppUse(10001, 400.0, 0, 0),
                BatteryStatsReader.AppUse(10002, 200.0, 0, 0),
                BatteryStatsReader.AppUse(10003, 100.0, 0, 0),
                BatteryStatsReader.AppUse(10004, 50.0, 0, 0),
                BatteryStatsReader.AppUse(10005, 0.0, 60_000, 0)
            ),
            emptyMap()
        )
        val c = BatteryCycles.summarize(s) { "앱$it" }
        assertEquals(listOf("앱10001" to 0.4f, "앱10002" to 0.2f, "앱10003" to 0.1f), c.apps)
        assertEquals("아래·위 값의 가운데(반올림)", 41, c.usedPct)
    }
}
