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
        // 첫 칸 6% 중 화면이 켜진 0~0.5h의 4%가 화면 켜짐 몫, 나머지 2%는 대기
        assertEquals(4f, b[0].screenPct, 0.01f)
        assertEquals(2f, b[0].standbyPct, 0.01f)
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
    fun deviceFilterKeepsEveryPoint() {
        // 기기에서 awk로 거른 출력(read()와 같은 식)으로 읽어도 결과가 같아야 한다
        val filtered = javaClass.classLoader!!.getResource("batterystats_history_sample_filtered.txt")!!.readText()
        assertEquals(BatteryHistory.parse(sample, utc), BatteryHistory.parse(filtered, utc))
    }

    @Test
    fun missingTimeLinesAnchorToNow() {
        // 오래된 기록 파일이 밀려나 시각 줄이 없으면 마지막 기록을 지금으로 본다
        val text = "  +1h00m00s000ms (2) 090 +screen\n  +2h00m00s000ms (2) 080 -screen"
        val p = BatteryHistory.parse(text, utc, now = 10_000_000L)
        assertEquals(listOf(10_000_000L - 3_600_000L, 10_000_000L), p.map { it.time })
        assertEquals(emptyList<BatteryHistory.Point>(), BatteryHistory.parse(text, utc))
    }

    @Test
    fun format2AbsoluteTimeLines() {
        // Android 16 이후 삼성 실기 형식(Battery History [Format: 2]): 줄 앞이 실제 시각, 연도는 RESET:TIME에서 가져온다
        val text = """
            Battery History [Format: 2] (35% used, 1438KB used of 4096KB, 1368 strings using 86KB):
              12-31 23:50:00.000 RESET:TIME: 2026-12-31-23-50-00-000
              12-31 23:50:00.000 100 status=discharging health=good plug=none temp=280 +running +screen
                             Details: cpu=141280u+172830s
              12-31 23:55:00.500 099 -screen -running
              01-01 00:10:00.000 098 stats=0:""
              01-01 00:20:00.000 SHUTDOWN
              01-01 00:30:00.000 START
              01-01 00:30:01.000 098 status=charging +running
        """.trimIndent()
        val p = BatteryHistory.parse(text, utc)
        assertEquals(4, p.size)
        assertEquals(1798761000000L, p[0].time) // 2026-12-31 23:50:00 UTC
        assertEquals(true, p[0].screen)
        assertEquals(1798761300500L, p[1].time)
        assertEquals(false, p[1].screen)
        // 해를 넘겨 2027-01-01이 된다
        assertEquals(1798762200000L, p[2].time)
        assertEquals(true, p[3].afterOff)
        assertEquals(true, p[3].charging)
    }

    @Test
    fun sinceCutsAtStatsStart() {
        val pts = listOf(
            BatteryHistory.Point(0, 100, screen = true, charging = false, awake = true),
            BatteryHistory.Point(10, 90, screen = false, charging = false, awake = true),
            BatteryHistory.Point(30, 80, screen = true, charging = false, awake = true)
        )
        // 기준 20: 그 앞 마지막 상태(90%)가 20에서 시작 상태가 된다
        assertEquals(listOf(20L to 90, 30L to 80), BatteryHistory.since(pts, 20).map { it.time to it.level })
        assertEquals(pts, BatteryHistory.since(pts, null))
    }

    @Test
    fun chargesFromPlugToUnplug() {
        val m = 60_000L
        val pts = listOf(
            BatteryHistory.Point(0, 50, screen = false, charging = false, awake = true),
            BatteryHistory.Point(10 * m, 48, screen = false, charging = true, awake = true),
            BatteryHistory.Point(40 * m, 70, screen = false, charging = true, awake = true),
            BatteryHistory.Point(70 * m, 85, screen = false, charging = false, awake = true),
            BatteryHistory.Point(80 * m, 84, screen = false, charging = true, awake = true),
            BatteryHistory.Point(95 * m, 90, screen = false, charging = true, awake = true)
        )
        val c = BatteryHistory.charges(pts, now = 100 * m)
        assertEquals(2, c.size)
        // 48%에 꽂아 30분 뒤 70%, 뺄 때(70분) 85%
        assertEquals(BatteryHistory.Charge(10 * m, 70 * m, 48, 70, false), c[0])
        assertEquals(true, c[1].ongoing)
        assertEquals(84, c[1].startLevel)
        assertEquals(90, c[1].endLevel)
        // 칸에도 충전으로 오른 양이 들어간다
        val b = BatteryHistory.buckets(pts, now = 100 * m, bucketMs = 60 * m, from = 0)
        // 0~60분 칸: 10~40분 +22%, 40~70분 +15% 중 20분 몫 +10% → 32%
        assertEquals(32f, b[0].chargedPct, 0.01f)
    }
}
