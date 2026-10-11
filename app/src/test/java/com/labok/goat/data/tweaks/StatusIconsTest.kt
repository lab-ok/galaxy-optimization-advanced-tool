package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.StatusIcons.Icon
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusIconsTest {
    @Test
    fun parsesSystemValue() {
        assertEquals(emptyList<String>(), StatusIcons.parse(null))
        assertEquals("시스템이 null 글자로 돌려주는 경우", emptyList<String>(), StatusIcons.parse("null"))
        assertEquals(listOf("rotate", "nfc"), StatusIcons.parse(" rotate,,nfc,rotate "))
    }

    @Test
    fun togglingKeepsUnknownSlots() {
        // 다른 앱이 넣은 이름(clock)은 숨기고 보일 때 모두 그대로 둔다
        val hidden = StatusIcons.toggled(listOf("clock"), Icon.VOLUME, hide = true)
        assertEquals(listOf("clock", "volume", "mute"), hidden)
        assertEquals(setOf(Icon.VOLUME), StatusIcons.hiddenIn(hidden))
        assertEquals(listOf("clock"), StatusIcons.toggled(hidden, Icon.VOLUME, hide = false))
    }

    @Test
    fun anySlotNameCountsAsHidden() {
        // 기기마다 이름이 달라 하나만 있어도 숨긴 것으로 본다(예: 삼성 volte)
        assertEquals(setOf(Icon.VOLTE), StatusIcons.hiddenIn(listOf("volte")))
    }
}
