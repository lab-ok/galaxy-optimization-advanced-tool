package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.Gos.Action
import com.labok.goat.data.tweaks.Gos.State
import org.junit.Assert.assertEquals
import org.junit.Test

class GosTest {
    private val gos = "com.samsung.android.game.gos"
    private val legacy = "com.enhance.gameservice"

    @Test
    fun classifiesPackages() {
        assertEquals("못 읽음", State.UNKNOWN, Gos.classify(null))
        assertEquals("GOS 없는 기기", State.NONE, Gos.classify(emptyMap()))
        assertEquals("이전 패키지만 있고 꺼짐", State.OFF, Gos.classify(mapOf(legacy to true)))
        assertEquals("둘 중 하나만 꺼짐이면 켜진 것", State.ON, Gos.classify(mapOf(gos to true, legacy to false)))
        assertEquals(State.ON, Gos.classify(mapOf(gos to false)))
    }

    @Test
    fun reappliesOncePerBoot() {
        assertEquals("원하지 않음", Action.SKIP, Gos.reapplyAction(wanted = false, keep = true, boot = 5, checkedBoot = null, now = State.ON))
        assertEquals("keep 꺼짐", Action.SKIP, Gos.reapplyAction(wanted = true, keep = false, boot = 5, checkedBoot = null, now = State.ON))
        assertEquals("새 부팅에 다시 켜져 있음", Action.DISABLE, Gos.reapplyAction(true, true, boot = 5, checkedBoot = 4, now = State.ON))
        assertEquals("같은 부팅의 두 번째 연결(사용자가 다시 켰어도 존중)", Action.SKIP, Gos.reapplyAction(true, true, boot = 5, checkedBoot = 5, now = State.ON))
        assertEquals("못 읽으면 다음 연결 때 다시", Action.RETRY_LATER, Gos.reapplyAction(true, true, boot = 5, checkedBoot = 4, now = State.UNKNOWN))
        assertEquals("이미 꺼져 있으면 확인만 남김", Action.MARK, Gos.reapplyAction(true, true, boot = 5, checkedBoot = 4, now = State.OFF))
        assertEquals("BOOT_COUNT 없는 기기는 연결마다 확인", Action.DISABLE, Gos.reapplyAction(true, true, boot = null, checkedBoot = 3, now = State.ON))
    }
}
