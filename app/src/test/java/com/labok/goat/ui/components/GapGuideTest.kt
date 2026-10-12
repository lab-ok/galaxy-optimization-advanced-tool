package com.labok.goat.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 화면 간격 가이드라인([Gap]) 검사: 화면 소스에서 세로 간격·카드 여백을 숫자 dp로 직접 쓰지 않는다.
 * 새 화면을 만들 때도 Gap 값만 쓰게 해 카드 사이·안쪽 여백·글 간격이 화면마다 달라지지 않게 한다.
 */
class GapGuideTest {
    private val ui = File("src/main/java/com/labok/goat/ui")

    private val rules = listOf(
        Regex("""Spacer\(Modifier\.height\(\d+(\.\d+)?\.dp\)\)""") to "세로 Spacer는 Gap 값을 쓴다",
        Regex("""verticalArrangement = Arrangement\.spacedBy\(\d+(\.\d+)?\.dp\)""") to "목록 세로 간격은 Gap.Card",
        Regex("""GlassCard\(padding = \d+(\.\d+)?\.dp""") to "카드 안쪽 여백은 Gap.CardPadding(기본값)",
    )

    @Test
    fun screensUseGapTokens() {
        assertTrue("UI 소스를 찾지 못함: ${ui.absolutePath}", ui.isDirectory)
        val problems = ui.walkTopDown().filter { it.extension == "kt" && it.name != "Gap.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                rules.firstOrNull { (re, _) -> re.containsMatchIn(line) }?.let { (_, why) -> "${f.relativeTo(ui)}:${i + 1} $why" }
            }
        }.toList()
        assertTrue("간격 가이드라인 위반:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** 규칙 2: 설명 글(muted) 두 개 사이에 Spacer를 넣지 않는다(같은 덩어리는 줄 간격으로만 잇는다). */
    @Test
    fun noSpacerBetweenDescriptions() {
        val spacer = Regex("""^\s*Spacer\(Modifier\.height\(Gap\.\w+\)\)\s*$""")
        fun desc(line: String) = line.trimStart().startsWith("Text(") && "c.muted" in line
        val problems = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val lines = f.readLines()
            (1 until lines.size - 1).filter { i -> spacer.matches(lines[i]) && desc(lines[i - 1]) && desc(lines[i + 1]) }
                .map { i -> "${f.relativeTo(ui)}:${i + 1} 설명 문단 사이 Spacer" }
        }.toList()
        assertTrue("간격 규칙 2 위반:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
