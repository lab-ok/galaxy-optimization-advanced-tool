package com.local.folddpifix.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDensityPolicyTest {

    @Test
    fun onlySamsungStepsAndDefaultAreAllowed() {
        AppDensityPolicy.STEPS.forEach { assertTrue(AppDensityPolicy.isAllowed(it)) }
        assertTrue(AppDensityPolicy.isAllowed(0))
        listOf(366, 400, 500, 1, -1).forEach { assertFalse("$it", AppDensityPolicy.isAllowed(it)) }
    }

    @Test
    fun stepsMoveToNeighbourFromAnyBase() {
        // 기본(시스템 473)에서: 작게 → 450, 크게 → 480
        assertEquals(450, AppDensityPolicy.next(473, -1))
        assertEquals(480, AppDensityPolicy.next(473, 1))
        // 단계 위에서는 바로 옆 단계로
        assertEquals(360, AppDensityPolicy.next(420, -1))
        assertEquals(450, AppDensityPolicy.next(420, 1))
        // 기본 버튼은 늘 0
        assertEquals(0, AppDensityPolicy.next(320, 0))
    }

    @Test
    fun noStepBeyondEnds() {
        assertNull(AppDensityPolicy.next(320, -1))
        assertNull(AppDensityPolicy.next(510, 1))
        assertNull(AppDensityPolicy.next(300, -1))
    }

    @Test
    fun samsungReports320As360AndWeCorrectIt() {
        assertEquals(320, AppDensityPolicy.shown(360, 320))
        assertEquals(360, AppDensityPolicy.shown(360, null))
        assertEquals(360, AppDensityPolicy.shown(360, 360))
        assertEquals(480, AppDensityPolicy.shown(480, 320))
        assertEquals(0, AppDensityPolicy.shown(0, 320))
    }
}
