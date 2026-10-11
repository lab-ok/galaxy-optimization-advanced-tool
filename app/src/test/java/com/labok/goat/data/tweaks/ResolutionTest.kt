package com.labok.goat.data.tweaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionTest {
    @Test
    fun wqhdGetsStandardWidthsKeepingAspect() {
        val o = Resolution.optionsFor(1440 to 3120)
        assertEquals("맨 앞은 원래 크기", Resolution.Option(1440, 3120, null, 100), o.first())
        assertEquals("720(50%)은 너무 흐려 55% 아래라 뺀다", listOf(1080, 900), o.drop(1).map { it.short })
        assertEquals("화면비 유지·짝수", 2340, o[1].long)
        assertTrue(o.all { it.short % 2 == 0 && it.long % 2 == 0 })
    }

    @Test
    fun fhdPlusAlreadyLoweredByOneUiShowsOnlySmaller() {
        // 삼성 설정으로 FHD+(1080)로 줄여 둔 기기: 1080은 다시 나오지 않는다
        val o = Resolution.optionsFor(1080 to 2340)
        assertEquals(listOf(1080, 900, 720), o.map { it.short })
        assertEquals(83, o[1].percent)
    }

    @Test
    fun smallScreensFallBackToRatios() {
        val o = Resolution.optionsFor(720 to 1600)
        assertEquals("표준 폭이 없으면 85%·75%", listOf(720, 612, 540), o.map { it.short })
    }
}
