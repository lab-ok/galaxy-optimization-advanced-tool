package com.labok.goat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeLargeScreenTest {
    @Test
    fun foldCoverIsSmallInnerIsLarge() {
        assertFalse("폴드 외부 화면(1080px, 420dpi ≈ 411dp)", ShakeRotatePolicy.isLarge(1080, 420))
        assertTrue("폴드 내부 화면(1812px, 420dpi ≈ 690dp)", ShakeRotatePolicy.isLarge(1812, 420))
        assertTrue("태블릿(1600px, 320dpi = 800dp)", ShakeRotatePolicy.isLarge(1600, 320))
        assertTrue("경계 600dp", ShakeRotatePolicy.isLarge(1200, 320))
        assertFalse("밀도를 못 읽으면 작은 화면", ShakeRotatePolicy.isLarge(1812, 0))
    }
}
