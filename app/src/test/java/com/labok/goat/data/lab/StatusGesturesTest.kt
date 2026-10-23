package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusGesturesTest {
    @Test
    fun zonesSplitFourTwoFour() {
        val w = 1000f
        assertEquals(StatusGestures.Zone.LEFT, StatusGestures.zone(0f, w))
        assertEquals(StatusGestures.Zone.LEFT, StatusGestures.zone(399f, w))
        assertEquals(StatusGestures.Zone.CENTER, StatusGestures.zone(400f, w))
        assertEquals(StatusGestures.Zone.CENTER, StatusGestures.zone(600f, w))
        assertEquals(StatusGestures.Zone.RIGHT, StatusGestures.zone(601f, w))
        assertEquals(StatusGestures.Zone.RIGHT, StatusGestures.zone(999f, w))
    }
}
