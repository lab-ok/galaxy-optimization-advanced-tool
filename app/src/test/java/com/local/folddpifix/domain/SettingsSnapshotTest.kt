package com.local.folddpifix.domain

import com.local.folddpifix.data.lab.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSnapshotTest {
    @Test
    fun nullAndEmptySurviveRoundTripWithoutFalseChanges() {
        val snap = mapOf("secure" to mapOf("a" to null, "b" to "", "c" to "1"), "global" to emptyMap(), "system" to emptyMap())
        val back = SettingsSnapshot.parse(SettingsSnapshot.serialize(snap))
        assertEquals(snap["secure"], back["secure"])
        assertTrue(SettingsSnapshot.diff(back, snap).contains("바뀐 설정 키가 없습니다"))
    }

    @Test
    fun reportsChangedKey() {
        val a = mapOf("secure" to mapOf("x" to "1"), "global" to emptyMap<String, String?>(), "system" to emptyMap())
        val b = mapOf("secure" to mapOf("x" to "2"), "global" to emptyMap<String, String?>(), "system" to emptyMap())
        assertTrue(SettingsSnapshot.diff(a, b).contains("[secure] * x : 1 → 2"))
    }
}
