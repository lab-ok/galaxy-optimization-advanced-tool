package com.labok.goat.data.lab

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

class NarrowBarTest {
    @Test
    fun marginMatchesMeasuredCoverLetterbox() {
        // 에뮬레이터(1248x1972, 내비게이션 막대 108px)에서 좁힌 앱 폭은 1049px였다 → 여백 199px
        assertEquals(199, NarrowBar.margin(1248, 1972, 108))
        // 제스처 내비게이션(막대 없음): Aspectly가 폴드8 커버에서 잰 1111px 앞뒤
        assertEquals(1248 - 1109, NarrowBar.margin(1248, 1972, 0))
    }

    @Test
    fun noMarginWhenScreenIsAlreadyNarrow() {
        assertEquals(0, NarrowBar.margin(900, 2400, 0))
    }

    @Test
    fun mediumRatioLeavesNoMarginOnCover() {
        // 3:2(1.5)는 커버(1972/1248≈1.58)보다 넓어 여백이 생기지 않는다
        assertEquals(0, NarrowBar.margin(1248, 1972, 0, 1.5f))
    }

    @Test
    fun swipeGoesThroughTheAppMiddle() {
        // 폭 1248, 여백 200: 앱 폭 1048
        assertEquals(524f, NarrowBar.appCenterX(NarrowApps.Side.LEFT, 1248, 200), 0.01f)
        assertEquals(724f, NarrowBar.appCenterX(NarrowApps.Side.RIGHT, 1248, 200), 0.01f)
        assertEquals(624f, NarrowBar.appCenterX(NarrowApps.Side.CENTER, 1248, 200), 0.01f)
    }

    @Test
    fun slotSitsOppositeTheApp() {
        assertEquals(Gravity.END to 200, NarrowBar.slot(NarrowApps.Side.LEFT, 200))
        assertEquals(Gravity.START to 200, NarrowBar.slot(NarrowApps.Side.RIGHT, 200))
        assertEquals(Gravity.END to 100, NarrowBar.slot(NarrowApps.Side.CENTER, 200))
    }
}
