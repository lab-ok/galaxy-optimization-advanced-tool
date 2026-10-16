package com.labok.goat.data.lab

import com.labok.goat.data.lab.BatteryMonitor.HOUR_MS
import com.labok.goat.data.lab.BatteryMonitor.Sample
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

    @Test
    fun samplesRoundTripAndOldOnesAreDropped() {
        val now = 100 * HOUR_MS
        val list = listOf(Sample(now - 50 * HOUR_MS, 90, false), Sample(now - HOUR_MS, 80, true), Sample(now, 79, false))
        assertEquals(list, BatteryMonitor.decode(BatteryMonitor.encode(list)))
        assertEquals("48시간 넘은 기록은 버린다", 2, BatteryMonitor.trim(list, now).size)
        assertEquals(emptyList<Sample>(), BatteryMonitor.decode(null))
        assertEquals("깨진 줄은 건너뛴다", 1, BatteryMonitor.decode("1,2,0;x;3,4").size)
    }

    @Test
    fun drainSkipsChargingAndGaps() {
        val now = 100 * HOUR_MS
        val q = HOUR_MS / 4
        val list = listOf(
            Sample(now - 8 * q, 80, false),
            Sample(now - 7 * q, 79, false),
            Sample(now - 6 * q, 78, false), // 30분에 2% → 4%/h
            Sample(now - 5 * q, 85, true), // 충전 구간은 뺀다
            Sample(now - 4 * q, 90, true),
            Sample(now - 3 * q, 90, false),
            Sample(now - 2 * q, 88, false) // 15분에 2%
        )
        // 감소 4% / 45분 = 약 5.33%/h
        assertEquals(5.33f, BatteryMonitor.drainPerHour(list, now)!!, 0.01f)
        assertNull("쓸 수 있는 구간이 30분 미만이면 계산하지 않는다", BatteryMonitor.drainPerHour(list.take(2), now))
    }

    @Test
    fun slotEndIsNextEvenHourInLocalTime() {
        val seoul = java.util.TimeZone.getTimeZone("Asia/Seoul") // UTC+9
        // 서울 13:30(UTC 04:30) → 14:00(UTC 05:00)
        val now = 4 * HOUR_MS + HOUR_MS / 2
        assertEquals(5 * HOUR_MS, BatteryMonitor.slotEnd(now, seoul))
        // 서울 14:00 정각은 다음 칸(14~16시)에 들어간다
        assertEquals(7 * HOUR_MS, BatteryMonitor.slotEnd(5 * HOUR_MS, seoul))
    }

    @Test
    fun slotsSplitDropByTimeAndMarkCharging() {
        val end = 100 * HOUR_MS
        val q = HOUR_MS / 4
        val list = listOf(
            // 마지막 칸 바로 앞 칸(96~98h)과 마지막 칸(98~100h)에 걸친 1시간에 4% → 2%씩
            Sample(end - 2 * HOUR_MS - 2 * q, 80, false),
            Sample(end - 2 * HOUR_MS + 2 * q, 76, false),
            // 마지막 칸 안에서 충전(사용량에 넣지 않음)
            Sample(end - 3 * q, 90, true),
            Sample(end - q, 95, true)
        )
        val s = BatteryMonitor.slots(list, end)
        assertEquals(12, s.size)
        assertEquals(end - 24 * HOUR_MS, s.first().start)
        assertEquals(2f, s[10].usedPct, 0.01f)
        assertEquals(2f, s[11].usedPct, 0.01f)
        assertEquals(true, s[11].charging)
        assertEquals(false, s[10].charging)
        assertEquals("기록이 없는 칸", false, s[0].recorded)
        assertEquals(true, s[10].recorded)
    }

    @Test
    fun slotsSkipLongGaps() {
        val end = 100 * HOUR_MS
        // 3시간 빈 기록 사이의 감소는 어느 칸에도 넣지 않는다
        val s = BatteryMonitor.slots(listOf(Sample(end - 4 * HOUR_MS, 80, false), Sample(end - HOUR_MS, 60, false)), end)
        assertEquals(0f, s.sumOf { it.usedPct.toDouble() }.toFloat(), 0.001f)
    }
}
