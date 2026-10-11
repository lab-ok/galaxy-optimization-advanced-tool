package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.RefreshRate.Choice
import com.labok.goat.data.tweaks.RefreshRate.Values
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRateTest {
    @Test
    fun classifiesSettings() {
        assertEquals("값이 없으면 시스템 기본", Choice.SYSTEM, RefreshRate.classify(Values(null, null, null), 120))
        assertEquals("적응형 그대로", Choice.SYSTEM, RefreshRate.classify(Values(120f, 0f, 1), 120))
        assertEquals("최저가 최고면 항상 최고", Choice.SMOOTH, RefreshRate.classify(Values(120f, 120f, 1), 120))
        assertEquals("최고가 60이면 절전", Choice.SAVER, RefreshRate.classify(Values(60f, 60f, 0), 120))
        assertEquals("최고만 60(최저 없음)도 절전", Choice.SAVER, RefreshRate.classify(Values(60f, null, null), 120))
        assertEquals("최고 90 기기에서 최저 90은 항상 최고", Choice.SMOOTH, RefreshRate.classify(Values(90f, 90f, 1), 90))
        assertEquals("최저 96(최고 120 기기)은 고정이 아니다", Choice.SYSTEM, RefreshRate.classify(Values(120f, 96f, 1), 120))
    }
}
