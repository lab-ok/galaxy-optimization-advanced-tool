package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChargeMeterTest {
    /** 4,000mAh 배터리를 [ma]mA로 충전: 5초마다 샘플, 들어간 양만큼 잔량이 오른다. */
    private fun charge(
        acc: ChargeMeter.Accumulator,
        fromLevel: Int,
        toLevel: Int,
        ma: Int,
        capacity: Int = 4000,
        startMs: Long = 0,
        temp: Float = 30f,
        gapAtLevel: Int? = null
    ): Long {
        var t = startMs
        var mah = 0.0
        var level = fromLevel
        while (level < toLevel) {
            t += 5_000
            if (gapAtLevel != null && level == gapAtLevel) {
                t += 60_000
            }
            // 기기가 주는 부호와 상관없이 배터리에는 그만큼 들어간다
            mah += abs(ma) * 5.0 / 3600
            level = fromLevel + (mah * 100 / capacity).toInt()
            acc.add(t, level, ma, temp)
        }
        return t
    }

    @Test
    fun steadyChargeGivesCapacity() {
        val acc = ChargeMeter.Accumulator(0, 30)
        val end = charge(acc, 30, 90, 2000)
        val r = acc.result(end, 4000)
        assertNotNull(r)
        // 잔량이 정수라 몇 % 오차는 있다
        assertTrue("capacity ${r!!.capacityMah}", r.capacityMah in 3880..4120)
        assertTrue(r.span >= ChargeMeter.MIN_SPAN)
    }

    @Test
    fun negativeSignDevicesWorkToo() {
        val acc = ChargeMeter.Accumulator(0, 30)
        val end = charge(acc, 30, 90, -2000)
        val r = acc.result(end, 4000)
        assertNotNull(r)
        assertTrue(r!!.capacityMah in 3880..4120)
    }

    @Test
    fun shortChargeIsNotCounted() {
        val acc = ChargeMeter.Accumulator(0, 50)
        val end = charge(acc, 50, 65, 2000)
        assertNull(acc.result(end, 4000))
    }

    @Test
    fun warmupAndTopAreExcluded() {
        val acc = ChargeMeter.Accumulator(0, 80)
        val end = charge(acc, 80, 100, 2000)
        val r = acc.result(end, 4000)
        // 3분 뒤부터 95%까지만 재서 20%p를 못 채운다
        assertNull(r)
        val live = acc.live()
        assertTrue(live.fromLevel!! >= 80)
        assertTrue(live.toLevel!! <= ChargeMeter.TOP_LEVEL)
    }

    @Test
    fun gapRestartsTheWindow() {
        val acc = ChargeMeter.Accumulator(0, 20)
        val end = charge(acc, 20, 80, 2000, gapAtLevel = 50)
        val r = acc.result(end, 4000)
        assertNotNull(r)
        // 공백 뒤 잔량 상승부터 다시 잰다
        assertTrue("from ${r!!.fromLevel}", r.fromLevel > 50)
        assertTrue(r.capacityMah in 3800..4200)
    }

    @Test
    fun hotChargeIsDropped() {
        val acc = ChargeMeter.Accumulator(0, 20)
        val end = charge(acc, 20, 80, 2000, temp = 47f)
        assertNull(acc.result(end, 4000))
    }

    @Test
    fun implausibleValueIsDropped() {
        // 전류를 1,000배로 잘못 읽으면 설계의 130%를 크게 넘어 버린다
        val acc = ChargeMeter.Accumulator(0, 30)
        var t = 0L
        var level = 30
        while (level < 80) {
            t += 5_000
            if (t % 30_000 == 0L) level++
            acc.add(t, level, 2_000_000, 30f)
        }
        assertNull(acc.result(t, 4000))
    }

    @Test
    fun medianAndUnits() {
        fun r(c: Int) = ChargeMeter.Result(0, 20, 60, c * 40 / 100, c, null)
        assertEquals(4000, ChargeMeter.median(listOf(r(4100), r(3900), r(4000))))
        assertEquals(4050, ChargeMeter.median(listOf(r(4100), r(4000))))
        assertNull(ChargeMeter.median(emptyList()))
        assertEquals(1820, ChargeMeter.toMa(1_820_000, false))
        assertEquals(1820, ChargeMeter.toMa(1820, false))
        assertEquals(15, ChargeMeter.toMa(15_000, true))
        assertNull(ChargeMeter.toMa(0, false))
    }

    @Test
    fun designPrefersTypical() {
        assertEquals(4800, ChargeMeter.parseDesign("    Capacity: 4400, Computed drain: 0, Rated: 4650, Typical: 4800"))
        assertEquals(4650, ChargeMeter.parseDesign("    Capacity: 4400, Rated: 4650"))
        assertNull(ChargeMeter.parseDesign("    Capacity: 4400, Computed drain: 0"))
    }

    @Test
    fun serializationRoundTrips() {
        val res = ChargeMeter.Result(123, 20, 61, 1650, 4024, 33.5f)
        assertEquals(res, ChargeMeter.Result.parse(res.serialize()))
        val live = ChargeMeter.Live(1, 20, 40, 22, 39, 680, -1500)
        assertEquals(live, ChargeMeter.Live.parse(live.serialize()))
        assertEquals(ChargeMeter.Live(1, 20, 21, null, null, 0, null), ChargeMeter.Live.parse(ChargeMeter.Live(1, 20, 21, null, null, 0, null).serialize()))
    }
}
