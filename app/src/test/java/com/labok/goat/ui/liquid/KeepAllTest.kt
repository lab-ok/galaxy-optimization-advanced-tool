package com.labok.goat.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAllTest {
    @Test
    fun joinsHangulInsideWordsOnly() {
        assertEquals("씁⁠니⁠다.", keepAll("씁니다."))
        assertEquals("새 버⁠전", keepAll("새 버전"))
        assertEquals("GOAT 1.5", keepAll("GOAT 1.5"))
        assertEquals("1.5.0 버⁠전", keepAll("1.5.0 버전"))
    }
}
