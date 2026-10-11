package com.labok.goat.data.tweaks

import org.junit.Assert.assertEquals
import org.junit.Test

class ForceDarkTest {
    @Test
    fun reappliesOncePerBootOnlyWhenOff() {
        assertEquals(false, ForceDark.shouldReapply(wanted = false, keep = true, boot = 3, checkedBoot = 2, forced = false))
        assertEquals(false, ForceDark.shouldReapply(wanted = true, keep = false, boot = 3, checkedBoot = 2, forced = false))
        assertEquals("재부팅으로 꺼짐", true, ForceDark.shouldReapply(true, true, boot = 3, checkedBoot = 2, forced = false))
        assertEquals("이미 켜져 있음", false, ForceDark.shouldReapply(true, true, boot = 3, checkedBoot = 2, forced = true))
        assertEquals("같은 부팅에 개발자 옵션으로 끈 것은 존중", false, ForceDark.shouldReapply(true, true, boot = 3, checkedBoot = 3, forced = false))
        // GOAT로 켠 부팅은 setForced가 checkedBoot를 그 부팅으로 남기므로, 같은 부팅에 앱 밖에서 끄고 프로세스가 다시 떠도 켜지 않는다
        assertEquals("GOAT로 바꾼 부팅", false, ForceDark.shouldReapply(true, true, boot = 7, checkedBoot = 7, forced = false))
        assertEquals("못 읽으면 다음 연결 때", null, ForceDark.shouldReapply(true, true, boot = 3, checkedBoot = 2, forced = null))
    }
}
