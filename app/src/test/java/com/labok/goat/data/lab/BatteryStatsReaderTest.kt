package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryStatsReaderTest {
    // AOSP 체크인 형식(redroid 14에서 받은 줄 모양)에 실제 사용처럼 값을 넣은 예
    private val sample = """
        9,0,l,bt,2,36000000,30000000,605203789,605203789,1791555461738,25200000,20000000,4400,0,0,0
        9,0,l,m,10800000,0,0,0,0,0,10800000,0,9,0,0,0,0,0,0,0,0,0,0,0,0
        9,0,l,dc,40,41,28,12,1760.5,520.2,0,0,0,0
        9,0,l,pws,4400,1700.4,1700,1800
        9,0,l,pwi,scrn,700.1,1,0,0
        9,0,l,pwi,cell,150.0,1,0,0
        9,0,l,pwi,camera,0,1,0,0
        9,0,l,pwi,???,3.0,1,0,0
        9,10234,l,pwi,uid,420.5,0,300.0,0
        9,10111,l,pwi,uid,80.25,0,10.0,0
        9,1000,l,pwi,uid,60.0,0,0,0
        9,10234,l,fg,5400000,12
        9,10234,l,st,5400000,600000,0,1200000,0,0,0
        9,10300,l,fg,60000,1
        9,10111,l,i,uid,10111,com.example
        garbage line
    """.trimIndent()

    @Test
    fun parsesSinceChargeSummary() {
        val s = BatteryStatsReader.parse(sample)!!
        assertEquals(1791555461738L, s.startClock)
        assertEquals(36_000_000L, s.onBatteryMs)
        assertEquals(10_800_000L, s.screenOnMs)
        assertEquals(25_200_000L, s.screenOffMs)
        assertEquals(4400, s.capacityMah)
        assertEquals(40, s.usedPctLow)
        assertEquals(41, s.usedPctHigh)
        assertEquals(28, s.screenOnPct)
        assertEquals(12, s.screenOffPct)
        assertEquals(1760.5, s.usedMah, 0.01)
        assertEquals(1700.4, s.computedMah, 0.01)
        assertEquals("0 mAh·이름표 ??? 항목은 뺀다", mapOf("scrn" to 700.1, "cell" to 150.0), s.system)
    }

    @Test
    fun appsSortedByPowerWithFrontAndBackTimes() {
        val apps = BatteryStatsReader.parse(sample)!!.apps
        assertEquals(listOf(10234, 10111, 1000, 10300), apps.map { it.uid })
        val top = apps.first()
        assertEquals(420.5, top.mah, 0.01)
        assertEquals(5_400_000L, top.frontMs)
        assertEquals("포그라운드 서비스 + 백그라운드", 1_800_000L, top.backMs)
        assertEquals("소모량 기록이 없어도 화면에 띄운 앱은 남긴다", 60_000L, apps.last().frontMs)
    }

    @Test
    fun missingBatteryLineIsNull() {
        assertNull(BatteryStatsReader.parse("9,0,l,m,1,2,3"))
        assertNull(BatteryStatsReader.parse(""))
    }

    @Test
    fun estimates() {
        val s = BatteryStatsReader.parse(sample)!!
        // 10시간에 40.5%p → 완충으로 약 24.7시간
        assertEquals(88_888_888L, BatteryStatsReader.fullChargeMs(s))
        assertEquals(28 / 3.0, BatteryStatsReader.perHour(28, 10_800_000L)!!, 0.001)
        assertNull("30분 미만은 계산하지 않는다", BatteryStatsReader.perHour(1, 20 * 60_000L))
        assertNull("쓴 양 3%p 미만은 계산하지 않는다", BatteryStatsReader.fullChargeMs(s.copy(usedPctLow = 1, usedPctHigh = 2)))
    }

    @Test
    fun uidLabels() {
        assertNull(BatteryStatsReader.systemUidLabel(10234))
        assertNull("다른 사용자 앱도 앱", BatteryStatsReader.systemUidLabel(1_010_234))
        assertEquals("Android 시스템", BatteryStatsReader.systemUidLabel(1000))
        assertEquals("화면", BatteryStatsReader.systemLabel("scrn"))
        assertEquals("unknown", BatteryStatsReader.systemLabel("unknown"))
    }
}
