package com.labok.goat.ui.about

import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.57.1부터는 변경 기록 판마다 영어판을 함께 쓴다(11-11 소장님 「체인지로그도 앞으로는 영문으로」). 최신부터 1.57.1까지 영어가 빠진 판이 있으면 실패한다. */
class ChangelogEnglishTest {
    @Test
    fun latestReleasesHaveEnglish() {
        val from = CHANGELOG.indexOfFirst { it.version == "GOAT 1.57.1" }
        assertTrue("1.57.1 판을 찾지 못함", from >= 0)
        val missing = CHANGELOG.take(from + 1).filter { it.en == null || it.en.sections.isEmpty() }.map { it.version }
        assertTrue("영어 변경 기록이 없는 판: $missing", missing.isEmpty())
    }
}
