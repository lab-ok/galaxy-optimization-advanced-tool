package com.labok.goat.data.lab

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRotateTest {
    @Test fun swapsWhenButtonsAreReversed() {
        // 기본: 왼쪽으로 돌린 가로(버튼이 위)와 거꾸로 든 세로에서만 바꾼다
        assertEquals(listOf(false, true, true, false), listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270).map { VolumeRotate.swapFor(it, invert = false) })
        // 반대로: 오른쪽으로 돌린 가로와 거꾸로 든 세로
        assertEquals(listOf(false, false, true, true), listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270).map { VolumeRotate.swapFor(it, invert = true) })
    }
}
