package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabTweaksTest {
    @Test
    fun airplaneRadiosKeepAndRestoreKeepsCellular() {
        val bt = AirplaneRadios.Radio.BLUETOOTH
        assertFalse("기본값이면 블루투스도 꺼진다", AirplaneRadios.kept(null, bt))
        val kept = AirplaneRadios.edit(null, bt, keep = true)
        assertEquals("cell,wifi,nfc,wimax", kept)
        assertTrue(AirplaneRadios.kept(kept, bt))
        assertEquals("cell,wifi,nfc,wimax,bluetooth", AirplaneRadios.edit(kept, bt, keep = false))
        assertEquals("셀룰러는 늘 남긴다", "cell,wifi", AirplaneRadios.edit("wifi", AirplaneRadios.Radio.NFC, keep = true))
    }

    @Test
    fun iconBlacklistKeepsUnknownSlots() {
        val alarm = IconBlacklist.Slot.ALARM
        assertEquals("rotate,alarm_clock", IconBlacklist.edit("rotate", alarm, hide = true))
        assertTrue(IconBlacklist.hidden("rotate,alarm_clock", alarm))
        assertEquals("rotate", IconBlacklist.edit("rotate,alarm_clock", alarm, hide = false))
        assertNull("다 지우면 키를 지운다", IconBlacklist.edit("alarm_clock", alarm, hide = false))
        assertEquals("같은 것을 두 번 넣지 않는다", "alarm_clock", IconBlacklist.edit("alarm_clock", alarm, hide = true))
    }

    @Test
    fun autoFlexRangeIsInclusive() {
        assertTrue(AutoFlex.inRange(75f, 75, 115))
        assertTrue(AutoFlex.inRange(115f, 75, 115))
        assertFalse(AutoFlex.inRange(74.9f, 75, 115))
        assertFalse(AutoFlex.inRange(180f, 75, 115))
    }
}
