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

    @Test
    fun fineHingeParsesVendorLogLine() {
        assertEquals(47f, FineHinge.parse("I/sensors-hal( 812): handle_sns_client_event:197, [0]lid_angle_fusion ts=123456 ns value [1/ 47/2]")!!, 0f)
        assertNull(FineHinge.parse("I/sensors-hal( 812): something else"))
        assertNull("범위 밖은 버린다", FineHinge.parse("lid_angle_fusion ts=1 ns value [1/ 400/2]"))
    }

    @Test
    fun fineHingeSnapsFlatAndDropsJitter() {
        val s = FineHinge.Smoother()
        assertEquals(172f, s.feed(172f)!!, 0f)
        assertEquals(180f, s.feed(179f)!!, 0f) // 175° 이상은 펼침으로 붙인다
        assertNull(s.feed(177f)) // 그대로 180
        val f = FineHinge.Smoother()
        f.feed(100f)
        assertNull(f.feed(101f)) // 1° 흔들림은 무시
        // 빨리 펼친 뒤 로그가 멈춰도 마지막 값이 바로 반영된다(가운데 값으로 늦추지 않는다)
        assertEquals(150f, f.feed(150f)!!, 0f)
        assertEquals(180f, f.feed(178f)!!, 0f)
    }

    @Test
    fun kkaddakNeedsQuickDipInto130to160() {
        val d = AutoFlex.ClickDetector()
        assertFalse(d.feed(180f, 0))
        assertFalse(d.feed(165f, 100))
        assertFalse(d.feed(150f, 250))
        assertTrue("3초 안에 150°까지 접었다 펴면 까딱", d.feed(180f, 700))
        // 덜 접은 흔들림(165°까지)은 아니다
        assertFalse(d.feed(165f, 1000))
        assertFalse(d.feed(180f, 1300))
        // 더 접은 것(120°까지)은 아니다
        assertFalse(d.feed(150f, 1500))
        assertFalse(d.feed(120f, 1700))
        assertFalse(d.feed(180f, 2100))
        // 3초를 넘기면 아니다
        assertFalse(d.feed(150f, 3000))
        assertFalse(d.feed(150f, 5900))
        assertFalse(d.feed(180f, 6100))
    }

    @Test
    fun hingeHorizontalOnlyWhenAxisFlat() {
        // 힌지 축이 Y: 세워 든 책 모양(중력이 Y)은 세로, 옆으로 돌리면(중력이 X) 가로, 바닥에 눕히면(중력이 Z) 가로
        assertFalse(AutoFlex.isHorizontal(0f, 9.8f, 0f, alongY = true))
        assertTrue(AutoFlex.isHorizontal(9.8f, 0f, 0f, alongY = true))
        assertTrue(AutoFlex.isHorizontal(0f, 1f, 9.7f, alongY = true))
        assertFalse(AutoFlex.isHorizontal(9.8f, 0f, 0f, alongY = false))
    }

    @Test
    fun dexStringTableIsRead() {
        val b = ByteArray(0x90)
        "dex\n035".toByteArray().copyInto(b, 0)
        fun put(i: Int, v: Int) {
            for (k in 0..3) b[i + k] = (v shr (8 * k)).toByte()
        }
        put(0x38, 2)
        put(0x3C, 0x70)
        put(0x70, 0x78)
        put(0x74, 0x80)
        b[0x78] = 3
        "abc".toByteArray().copyInto(b, 0x79)
        b[0x80] = 9
        "FlexPanel".toByteArray().copyInto(b, 0x81)
        assertEquals(listOf(0), DexStrings.dexOffsets(b))
        val got = mutableListOf<String>()
        DexStrings.strings(b, 0) { got.add(it) }
        assertEquals(listOf("abc", "FlexPanel"), got)
    }

    @Test
    fun privacyDotErrorShowsRealReason() {
        val err = "Exception occurred while executing 'put':\njava.lang.SecurityException: Permission denial: writing to settings requires:android.permission.WRITE_DEVICE_CONFIG\n\tat android.os.Parcel"
        assertEquals("SecurityException: Permission denial: writing to settings requires:android.permission.WRITE_DEVICE_CONFIG", PrivacyDot.reason(err))
        assertEquals("뭔가 실패", PrivacyDot.reason("뭔가 실패"))
    }
}
