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

    /** 규칙 B: 세로 간격을 글·버튼·묶음의 padding(top = 숫자)로 만들지 않는다(Spacer(Gap.*)만). */
    @Test
    fun noPaddingTopSpacing() {
        val bad = Regex("""^\s*(Text|LiquidButton|Column|Row)\(.*padding\(top = \d""")
        val problems = ui.walkTopDown().filter { it.extension == "kt" && "/art/" !in it.path && "/liquid/" !in it.path }.flatMap { f ->
            // 가로 줄(Row) 안에서 옆 요소와 글줄을 맞추는 미세 정렬(weight를 가진 칸)은 세로 간격이 아니라 제외한다
            f.readLines().mapIndexedNotNull { i, line -> if (bad.containsMatchIn(line) && "weight(" !in line) "${f.relativeTo(ui)}:${i + 1}" else null }
        }.toList()
        assertTrue("간격 규칙 B 위반(padding(top)으로 간격):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * 규칙표: 글(Text) ↔ 조작·그림 사이는 Spacer(Gap.Item)(조작 다음이 새 묶음 제목이면 Gap.Section). 세로로 쌓은 조작 ↔ 조작도 Gap.Item.
     * 가로로 나란한 버튼(Modifier.weight)은 예외. 화면 소스를 줄 단위로 읽어 이웃한 두 요소와 그 사이 Spacer를 본다.
     */
    @Test
    fun textAndControlsSpacing() {
        val control = Regex("""^\s*(LiquidSlider|LiquidChips|LiquidButton|LiquidRadioGroup|LiquidTextField|LiquidLoader|CommandBox|LiquidSwitchRow)\(|^\s*[A-Z]\w*Art\(""")
        val text = Regex("""^\s*Text\(""")
        val spacer = Regex("""^\s*Spacer\(Modifier\.height\((Gap\.\w+)\)\)\s*$""")
        val skip = Regex("""^\s*(var |val |//|$)""")
        fun strip(l: String) = l.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")
        fun end(lines: List<String>, i: Int): Int {
            var depth = 0
            for (j in i until lines.size) {
                val l = strip(lines[j]); depth += l.count { it == '(' } - l.count { it == ')' }
                if (depth <= 0) {
                    if (!l.trimEnd().endsWith("{")) return j
                    var d = 0
                    for (k in j until lines.size) { d += lines[k].count { it == '{' } - lines[k].count { it == '}' }; if (d <= 0) return k }
                }
            }
            return i
        }
        val problems = mutableListOf<String>()
        ui.walkTopDown().filter { it.extension == "kt" && "/art/" !in it.path }.forEach { f ->
            val lines = f.readLines()
            for (i in lines.indices) {
                val isC = control.containsMatchIn(lines[i]); val isT = text.containsMatchIn(lines[i])
                if (!isC && !isT) continue
                val e = end(lines, i)
                var n = e + 1
                while (n < lines.size && skip.containsMatchIn(lines[n])) n++
                var gap: String? = null
                if (n < lines.size) spacer.find(lines[n])?.let { gap = it.groupValues[1]; n++; while (n < lines.size && skip.containsMatchIn(lines[n])) n++ }
                if (n >= lines.size) continue
                val nC = control.containsMatchIn(lines[n]); val nT = text.containsMatchIn(lines[n])
                if (!nC && !nT || (isT && nT)) continue
                val first = lines.subList(i, e + 1).joinToString(" "); val next = lines.subList(n, end(lines, n) + 1).joinToString(" ")
                val want = when {
                    isC && nC && ("weight" in first || "weight" in next) -> continue
                    isC && nC && "LiquidSwitchRow" in first -> "Gap.Section"
                    isC && nT && "c.ink" in next && "c.muted" !in next -> "Gap.Section"
                    else -> "Gap.Item"
                }
                if (gap != want) problems += "${f.relativeTo(ui)}:${i + 1} ${if (isC) "조작" else "글"}→${if (nC) "조작" else "글"} 간격 ${gap ?: "없음"} (규칙: $want)"
            }
        }
        assertTrue("간격 규칙표 위반:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
