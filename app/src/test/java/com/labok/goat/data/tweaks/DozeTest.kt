package com.labok.goat.data.tweaks

import org.junit.Assert.assertEquals
import org.junit.Test

class DozeTest {
    @Test fun classifyReadsBothModes() {
        assertEquals(Doze.State.OFF, Doze.classify("0", "0"))
        assertEquals(Doze.State.ON, Doze.classify("1", "1"))
        assertEquals(Doze.State.ON, Doze.classify("0", "1")) // 가벼운 잠자기만 켜져 있어도 켜진 것
        assertEquals(Doze.State.UNKNOWN, Doze.classify(null, "0"))
        assertEquals(Doze.State.UNKNOWN, Doze.classify("Error", "0"))
        assertEquals(Doze.State.OFF, Doze.classify("0\n", " 0 "))
    }

    @Test fun reapplyOncePerBoot() {
        assertEquals(Doze.Action.SKIP, Doze.reapplyAction(false, 5, null, Doze.State.ON))
        assertEquals(Doze.Action.SKIP, Doze.reapplyAction(true, 5, 5, Doze.State.ON)) // 이번 부팅에 이미 확인
        assertEquals(Doze.Action.DISABLE, Doze.reapplyAction(true, 6, 5, Doze.State.ON)) // 재부팅으로 다시 켜짐
        assertEquals(Doze.Action.MARK, Doze.reapplyAction(true, 6, 5, Doze.State.OFF))
        assertEquals(Doze.Action.RETRY_LATER, Doze.reapplyAction(true, 6, 5, Doze.State.UNKNOWN))
        assertEquals(Doze.Action.DISABLE, Doze.reapplyAction(true, null, null, Doze.State.ON)) // 부팅 횟수 없는 기기
    }
}
