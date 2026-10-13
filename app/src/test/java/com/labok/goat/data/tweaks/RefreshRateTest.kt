package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.RefreshRate.Choice
import com.labok.goat.data.tweaks.RefreshRate.Values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun planFixesBothLimitsAndSamsungModeOnlyOnSamsung() {
        assertEquals(
            listOf("settings put system peak_refresh_rate 120.0", "settings put system min_refresh_rate 120.0", "settings put secure refresh_rate_mode 1"),
            RefreshRate.plan(Choice.SMOOTH, 120, samsung = true, original = null)
        )
        assertEquals(
            "삼성이 아니면 refresh_rate_mode를 건드리지 않는다",
            listOf("settings put system peak_refresh_rate 60.0", "settings put system min_refresh_rate 60.0"),
            RefreshRate.plan(Choice.SAVER, 120, samsung = false, original = null)
        )
    }

    @Test
    fun planRestoresOriginalAndDeletesKeysThatDidNotExist() {
        assertEquals(
            listOf("settings put system peak_refresh_rate 120.0", "settings delete system min_refresh_rate", "settings put secure refresh_rate_mode 1"),
            RefreshRate.plan(Choice.SYSTEM, 120, samsung = true, original = Values(120f, null, 1))
        )
        assertEquals(
            "처음부터 키가 없던 기기는 모두 지운다",
            listOf("settings delete system peak_refresh_rate", "settings delete system min_refresh_rate"),
            RefreshRate.plan(Choice.SYSTEM, 120, samsung = false, original = Values(null, null, null))
        )
        assertTrue("GOAT로 바꾼 적 없으면 시스템 기본은 아무것도 쓰지 않는다", RefreshRate.plan(Choice.SYSTEM, 120, samsung = true, original = null).isEmpty())
    }
}
