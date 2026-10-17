package com.labok.goat.data.lab

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHistoryTest {
    private val utc = TimeZone.getTimeZone("UTC")

    // 삼성 기기 실제 출력(공개 예시)에서 기록 줄만 남기고 따옴표 안 문자열을 지운 것
    private val sample = javaClass.classLoader!!.getResource("batterystats_history_sample.txt")!!.readText()

    @Test
    fun offsetParsesAllUnits() {
        assertEquals(0L, BatteryHistory.offsetMs("0"))
        assertEquals(101L, BatteryHistory.offsetMs("+101ms"))
        assertEquals(7 * 60_000L + 25_445L, BatteryHistory.offsetMs("+7m25s445ms"))
        assertEquals(2 * 3_600_000L + 46 * 60_000L + 1_315L, BatteryHistory.offsetMs("+2h46m01s315ms"))
        assertEquals(86_400_000L + 3_600_000L, BatteryHistory.offsetMs("+1d1h"))
    }

    @Test
    fun sampleGivesLevelScreenAndOffGap() {
        val p = BatteryHistory.parse(sample, utc)
        assertTrue(p.size > 20)
        // RESET:TIME 2022-12-07 01:14:31(UTC)부터 시작하고 잔량은 100에서 83까지 내려간다
        assertEquals(1670375671000L, p.first().time)
        assertEquals(100, p.first().level)
        assertEquals(83, p.last().level)
        // 7분 25초 뒤 처음으로 화면이 꺼진다
        val off = p.first { !it.screen }
        assertEquals(1670375671000L + 7 * 60_000L + 25_445L, off.time)
        assertEquals(99, off.level)
        // 재부팅(SHUTDOWN→START→TIME) 뒤 첫 시점은 꺼짐 뒤로 표시하고 새 TIME으로 시각을 맞춘다
        val boot = p.first { it.afterOff }
        assertEquals(1670377341000L + (27 * 60_000L + 29_417L) - (27 * 60_000L + 23_665L), boot.time)
        assertTrue(p.zipWithNext().all { (a, b) -> b.time >= a.time })
    }

    @Test
    fun bucketsSplitDropAndScreenTime() {
        val h = 3_600_000L
        val pts = listOf(
            BatteryHistory.Point(0, 100, screen = true, charging = false, awake = true),
            BatteryHistory.Point(h / 2, 96, screen = false, charging = false, awake = false),
            BatteryHistory.Point(2 * h, 90, screen = false, charging = true, awake = true),
            BatteryHistory.Point(3 * h, 100, screen = false, charging = false, awake = false)
        )
        val b = BatteryHistory.buckets(pts, now = 4 * h, bucketMs = h)
        assertEquals(4, b.size)
        // 0~0.5h 4% + 0.5~2h 6%를 시간 비율로: 1칸 4+2=6, 2칸 4
        assertEquals(6f, b[0].usedPct, 0.01f)
        assertEquals(4f, b[1].usedPct, 0.01f)
        assertEquals(h / 2, b[0].screenMs)
        assertEquals(true, b[2].charging)
        assertEquals(0f, b[2].usedPct, 0.01f)
    }

    @Test
    fun bucketWidthKeepsBarsUnder24() {
        val h = 3_600_000L
        assertEquals(h, BatteryHistory.bucketMs(20 * h))
        assertEquals(2 * h, BatteryHistory.bucketMs(30 * h))
        assertEquals(6 * h, BatteryHistory.bucketMs(100 * h))
    }

    @Test
    fun spansCloseAtPowerOff() {
        val pts = listOf(
            BatteryHistory.Point(0, 100, screen = true, charging = false, awake = true),
            BatteryHistory.Point(10, 99, screen = true, charging = false, awake = true),
            BatteryHistory.Point(50, 98, screen = true, charging = false, awake = true, afterOff = true),
            BatteryHistory.Point(60, 98, screen = false, charging = false, awake = true)
        )
        assertEquals(listOf(0L until 10L, 50L until 60L), BatteryHistory.spans(pts, 100) { it.screen })
    }

    @Test
    fun drainSkipsChargingAndOff() {
        val h = 3_600_000L
        val pts = listOf(
            BatteryHistory.Point(0, 100, screen = true, charging = false, awake = true),
            BatteryHistory.Point(h, 90, screen = false, charging = true, awake = true),
            BatteryHistory.Point(2 * h, 100, screen = false, charging = false, awake = false),
            BatteryHistory.Point(5 * h, 95, screen = false, charging = false, awake = false, afterOff = true)
        )
        // 0~1h에 10%, 5~6h에 0% → 10%/2h(충전 1~2h, 꺼짐 2~5h는 뺀다)
        assertEquals(5f, BatteryHistory.drainPerHour(pts, 6 * h)!!, 0.01f)
    }
}
