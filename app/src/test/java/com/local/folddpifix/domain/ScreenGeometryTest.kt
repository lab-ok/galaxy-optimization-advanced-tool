package com.local.folddpifix.domain

import com.local.folddpifix.domain.ScreenGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    @Test
    fun fold8InnerIsLandscapeFold7InnerIsPortrait() {
        // Fold8: 내부 1848x2448, 외부 1248x1972 → 내부는 가로로 긴 화면
        assertEquals(2448f to 1848f, ScreenGeometry.orientedSize("1848x2448", "1248x1972", isInner = true))
        // Fold7: 내부 1968x2184, 외부 1080x2520 → 내부는 세로로 조금 긴 화면
        assertEquals(1968f to 2184f, ScreenGeometry.orientedSize("1968x2184", "1080x2520", isInner = true))
        // 외부는 항상 세로
        assertEquals(1248f to 1972f, ScreenGeometry.orientedSize("1248x1972", null, isInner = false))
    }

    @Test
    fun dpToMmMatchesPhysicalFormula() {
        // 160dpi·160ppi 화면에서 160dp = 1인치 = 25.4mm
        assertEquals(25.4f, ScreenGeometry.dpToMm(160, 160, 160f), 0.001f)
    }
}

class RowsTest {
    @Test
    fun matchedScreensFitAboutTheSameRows() {
        // 실기기 값: 외부 360dpi(세로 1972px), 내부 339dpi(가로 화면, 세로 1848px)
        val outer = ScreenGeometry.rowsFor(1972f, 360)
        val inner = ScreenGeometry.rowsFor(1848f, 339)
        assertEquals(outer, inner, outer * 0.02f)
    }
}

class CommandDisplayTest {
    @Test
    fun longWordsGoOnTheirOwnLine() {
        val shown = com.local.folddpifix.ui.components.displayCommand(
            ".\\adb shell pm grant com.local.folddpifix android.permission.WRITE_SECURE_SETTINGS",
        )
        assertEquals(".\\adb shell pm grant\ncom.local.folddpifix\nandroid.permission.WRITE_SECURE_SETTINGS", shown)
    }
}
