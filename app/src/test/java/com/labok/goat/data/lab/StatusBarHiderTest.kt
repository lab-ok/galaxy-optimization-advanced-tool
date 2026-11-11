package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarHiderTest {
    @Test
    fun lastTouchUpFromRecentQueue() {
        val out = """
              RecentQueue: length=3
                MotionEvent(deviceId=-1, eventTime=1, source=TOUCHSCREEN, displayId=0, action=DOWN, flags=0x0, pointers=[0: (100.0, 200.0)]), policyFlags=0x6b000000, age=900ms
                MotionEvent(deviceId=-1, eventTime=1, source=TOUCHSCREEN, displayId=1, action=UP, flags=0x0, pointers=[0: (640.5, 903.0)]), policyFlags=0x6b000000, age=800ms
                KeyEvent(deviceId=-1, action=UP, keyCode=BACK(4)), age=100ms
        """.trimIndent()
        val tap = StatusBarFocus.lastTouchUp(out)!!
        assertEquals(listOf(1, 640, 903), listOf(tap.display, tap.x, tap.y))
        // 오래된 누름은 다시 누르지 않는다
        assertEquals(null, StatusBarFocus.lastTouchUp(out.replace("age=800ms", "age=9000ms")))
        assertEquals(null, StatusBarFocus.lastTouchUp("RecentQueue: length=0"))
    }

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

    @Test
    fun failedShowRequestsFromOtherAppsOnly() {
        val out = """
                #4458 TYPE_HIDE - STATUS_FAIL - org.chromium.webview_shell:2d2e7b5 (0ms):
                #4558 TYPE_SHOW - STATUS_FAIL - org.chromium.webview_shell:629b5189 (1ms):
                #4560 TYPE_SHOW - STATUS_FAIL - com.labok.goat:4e742 (0ms):
        """.trimIndent()
        assertEquals(4558, StatusBarFocus.lastFailedShow(out))
        assertEquals(0, StatusBarFocus.lastFailedShow(""))
    }
}
