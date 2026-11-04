package com.labok.goat.ui.text

import com.labok.goat.testing.TestStrings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공통 문구(res/values/strings_common.xml) 검사. DPI·PPI·권한 같은 표준 용어는 쓰되(지나치게 풀어 쓰지 않는다),
 * 내부 구현 이름(IWindowManager, density 명령 등)은 고급 정보 밖으로 내보내지 않는다.
 */
class CopyTest {

    private val forbidden = listOf("IWindowManager", "-d ", "density", "DPI 보고", "cmd window")

    @Test
    fun everydayCopyHasNoTechnicalTerms() {
        val texts = TestStrings.common.values
        assertTrue("문구가 비어 있다", texts.isNotEmpty())
        texts.forEach { t ->
            forbidden.forEach { word -> assertTrue("'$t'에 '$word'", !t.contains(word)) }
        }
    }

    @Test
    fun everydayCopyIsShort() {
        TestStrings.common.values.forEach { t ->
            assertTrue("'$t'이 너무 길다(${t.length}자)", t.length <= 70)
        }
    }
}
