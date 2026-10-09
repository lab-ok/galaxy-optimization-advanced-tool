package com.local.folddpifix.ui.text

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일반 화면 문구(Copy) 검사. DPI·PPI·권한 같은 표준 용어는 쓰되(지나치게 풀어 쓰지 않는다),
 * 내부 구현 이름(IWindowManager, density 명령 등)은 고급 정보 밖으로 내보내지 않는다.
 */
class CopyTest {

    private val forbidden = listOf("IWindowManager", "-d ", "density", "DPI 보고", "cmd window")

    @Test
    fun everydayCopyHasNoTechnicalTerms() {
        val texts = Copy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
        assertTrue("문구가 비어 있다", texts.isNotEmpty())
        texts.forEach { t ->
            forbidden.forEach { word -> assertTrue("'$t'에 '$word'", !t.contains(word)) }
        }
    }

    @Test
    fun everydayCopyIsShort() {
        Copy::class.java.declaredFields.filter { it.type == String::class.java }.forEach {
            it.isAccessible = true
            val t = it.get(null) as String
            assertTrue("'$t'이 너무 길다(${t.length}자)", t.length <= 70)
        }
    }
}
