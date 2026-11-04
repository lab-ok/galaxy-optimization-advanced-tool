package com.labok.goat.ui.text

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문구 리소스화 재발 방지(11-04): 코드에 한국어 문자열이 새로 들어오면 실패한다. 화면 문구는 res/values/strings_*.xml에 둔다.
 * 아직 옮기지 않은 파일은 [BASELINE] 파일의 개수까지만 허용한다(줄기만 해야 한다). 로그·진단·변경 기록·조사 도구는 [EXEMPT].
 */
class HardcodedKoreanTest {
    private val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    @Test
    fun noNewKoreanLiterals() {
        val baseline = listOf(File("src/test/resources/korean_literal_baseline.txt"), File("app/src/test/resources/korean_literal_baseline.txt"))
            .firstOrNull { it.isFile }?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt() }
        val over = root.walkTopDown().filter { it.extension == "kt" }.mapNotNull { f ->
            val path = f.relativeTo(root).path.replace(File.separatorChar, '/')
            if (EXEMPT.any { path.endsWith(it) }) return@mapNotNull null
            val n = count(f.readText())
            val allowed = baseline[path] ?: 0
            if (n > allowed) "$path: 한국어 문자열 ${n}개(허용 ${allowed}개)" else null
        }.toList()
        assertTrue("코드에 한국어 문구가 늘었습니다. res/values/strings_*.xml로 옮기세요:\n" + over.joinToString("\n"), over.isEmpty())
    }

    companion object {
        /** 화면 문구가 아닌 파일: 변경 기록(언어별 파일로 따로), 개발자 조사 도구, 진단 파일. */
        val EXEMPT = listOf(
            "ui/about/Changelog.kt",
            "ui/lab/LabScreen.kt",
            "ui/lab/TentActivity.kt",
            "data/lab/OttFlexTest.kt",
            "data/lab/ShareProbe.kt",
            "data/lab/ApiProbe.kt",
            "data/log/DiagnosticReport.kt"
        )

        private val HANGUL = Regex("[가-힣]")
        private val LITERAL = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

        /** 주석과 로그 줄을 뺀 한국어 문자열 리터럴 수. */
        fun count(src: String): Int {
            val code = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")
            return code.lines().filterNot { "LogRepository" in it || Regex("\\blog\\.add\\(|\\bnote\\(|Log\\.[diwe]\\(").containsMatchIn(it) }
                .sumOf { line -> LITERAL.findAll(line).count { HANGUL.containsMatchIn(it.groupValues[1]) } }
        }
    }
}
