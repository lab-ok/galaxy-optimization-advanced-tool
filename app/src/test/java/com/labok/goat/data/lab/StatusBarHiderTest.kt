package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarHiderTest {
    @Test
    fun foldSecondDisplayNullFocusIsSkipped() {
        // 실기(SM-F971N, Android 17): 쓰지 않는 화면의 null 줄이 먼저 나온다
        val out = """
              mCurrentFocus=null
              mCurrentFocus=Window{a0898ab u0 com.labok.goat/com.labok.goat.ui.MainActivity}
                  mInputShown=false
        """.trimIndent()
        assertEquals(true, StatusBarFocus.parse(out).app)
    }

    @Test
    fun keyboardOrSystemWindowWaits() {
        val ime = "mCurrentFocus=Window{1 u0 com.a/com.a.Main}\nmInputShown=false\nmInputShown=true"
        assertEquals(false, StatusBarFocus.parse(ime).app)
        val shade = "mCurrentFocus=Window{2 u0 NotificationShade}\nmInputShown=false"
        assertEquals(false, StatusBarFocus.parse(shade).app)
        assertEquals(false, StatusBarFocus.parse("mCurrentFocus=null\nmInputShown=false").app)
    }
}
