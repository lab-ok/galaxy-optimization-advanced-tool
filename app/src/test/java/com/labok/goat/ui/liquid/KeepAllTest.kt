package com.labok.goat.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAllTest {
    @Test
    fun joinsHangulInsideWordsOnly() {
        assertEquals("씁⁠니⁠다.", keepAll("씁니다."))
        assertEquals("새 버⁠전", keepAll("새 버전"))
        assertEquals("GOAT 1.5", keepAll("GOAT 1.5"))
        // 영문·숫자·닫는 따옴표 뒤 조사는 앞말에 붙는다
        assertEquals("GPL-3.0\u2060으\u2060로", keepAll("GPL-3.0으로"))
        assertTrue(keepAll("'설정'에서").contains("'\u2060에"))
        assertEquals("1.5.0 버⁠전", keepAll("1.5.0 버전"))
    }
}
