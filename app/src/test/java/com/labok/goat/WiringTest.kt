package com.labok.goat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 기능 사이 연결이 끊기지 않았는지 소스로 확인한다(11-11 소장님 "다시는 이런 일이 안 생기게, 구조화").
 * 1.49.0에서 상단바 제스처 서비스를 지울 때 그 안에 있던 상단바 숨기기의 입력란 감지(yieldForInput 호출)가 함께 지워졌고,
 * 1.55.3에서 서비스를 되살리며 다른 창 읽기 플래그를 빠뜨렸다. 둘 다 빌드·단위 테스트가 통과해 실기에서야 드러났다.
 * - 고아 함수: 어디서도 부르지 않는 공개 함수(지운 기능이 쓰던 진입점이 남은 흔적)
 * - 매니페스트: 선언한 구성요소의 클래스가 있는지, 서비스·리시버 클래스가 선언됐는지
 * - 기능 계약: 파일을 넘나드는 필수 연결을 [CONTRACTS]에 적어 두고 확인한다. 기능을 고치거나 지울 때 이 표도 함께 고친다
 */
class WiringTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private val main = File(app, "src/main/java")
    private val sources = main.walkTopDown().filter { it.extension == "kt" }.associateWith { it.readText() }
    // 이 파일은 계약 문자열에 함수 이름을 담고 있어 쓰임 세기에서 뺀다
    private val tests = File(app, "src/test/java").walkTopDown().filter { it.extension == "kt" && it.name != "WiringTest.kt" }.map { it.readText() }.toList()
    private val manifest = File(app, "src/main/AndroidManifest.xml").readText()

    private fun rel(f: File) = f.relativeTo(main).path.substringAfter("com/labok/goat/")

    @Test
    fun noOrphanFunctions() {
        // 주석에 이름만 남은 것은 쓰임이 아니다(1.49.0 뒤 yieldForInput은 주석에만 남아 있었다)
        val all = (sources.values + tests).joinToString("\n") { stripComments(it) }
        val fn = Regex("""^[ \t]*((?:[a-z]+ |@\w+ )*)fun (?:[\w.<>?]+\.)?(\w+)\(""", RegexOption.MULTILINE)
        val orphans = sources.flatMap { (f, text) ->
            fn.findAll(text).mapNotNull { m ->
                val mods = m.groupValues[1]
                val name = m.groupValues[2]
                if ("override" in mods || "private" in mods) return@mapNotNull null
                val uses = Regex("""\b${Regex.escape(name)}\b""").findAll(all).count()
                "${rel(f)}: $name".takeIf { uses <= 1 }
            }.toList()
        }.filterNot { it in ORPHAN_BASELINE }
        assertTrue("어디서도 부르지 않는 공개 함수가 생겼습니다(지운 기능의 호출부가 함께 사라졌는지 확인):\n" + orphans.joinToString("\n"), orphans.isEmpty())
    }

    private fun stripComments(code: String) = code.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("""//[^\n]*"""), "")

    @Test
    fun manifestComponentsHaveClasses() {
        // activity-alias(바로가기 아이콘)는 클래스가 없는 별칭이라 뺀다
        val names = Regex("""<(?:application|activity|service|receiver|provider)\s+android:name="\.([\w.$]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        val missing = names.filterNot { name ->
            val outer = name.substringBefore('$')
            val file = File(main, "com/labok/goat/" + outer.replace('.', '/') + ".kt")
            val simple = outer.substringAfterLast('.')
            val inFile = file.takeIf { it.isFile }?.readText()
                ?: sources.entries.firstOrNull { (f, _) -> f.parentFile?.path.orEmpty().replace('/', '.').endsWith(outer.substringBeforeLast('.', "")) && Regex("""(class|object) $simple\b""").containsMatchIn(sources[f]!!) }?.value
            inFile != null && (!name.contains('$') || Regex("""class ${name.substringAfter('$')}\b""").containsMatchIn(inFile))
        }
        assertTrue("매니페스트에 있지만 클래스가 없는 구성요소:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun servicesAndReceiversAreDeclared() {
        val kinds = Regex("""class (\w+)\s*(?:\([^)]*\))?\s*:\s*(AccessibilityService|Service|BroadcastReceiver|TileService)\(\)""")
        val undeclared = sources.flatMap { (_, text) -> kinds.findAll(text).map { it.groupValues[1] }.toList() }
            .filterNot { Regex("""android:name="[^"]*[.$]$it"""").containsMatchIn(manifest) }
        assertTrue("매니페스트에 선언하지 않은 서비스·리시버:\n" + undeclared.joinToString("\n"), undeclared.isEmpty())
    }

    @Test
    fun featureContracts() {
        val broken = CONTRACTS.filterNot { c ->
            val f = File(app, c.file)
            f.isFile && c.mustContain.all { it in f.readText() }
        }.map { "${it.file}: ${it.mustContain} — ${it.why}" }
        assertTrue("기능 계약이 깨졌습니다:\n" + broken.joinToString("\n"), broken.isEmpty())
    }

    private class Contract(val file: String, val mustContain: List<String>, val why: String)

    companion object {
        /** 지금 남아 있는 고아 함수(이 목록을 늘리지 않는다. 지우거나 쓰게 되면 여기서도 뺀다). */
        private val ORPHAN_BASELINE = setOf(
            "data/lab/VoiceRedirect.kt: targetEnabled",
            "data/lab/TentMode.kt: launchComponent"
        )

        private val CONTRACTS = listOf(
            Contract(
                "src/main/java/com/labok/goat/data/lab/StatusBarInputService.kt",
                listOf("StatusBarHider.yieldForInput("),
                "상단바 숨기기 중 입력란 첫 누름에 키보드(1.27.0, 1.49.0에서 빠져 회귀)"
            ),
            Contract(
                "src/main/res/xml/status_bar_input_service.xml",
                listOf("android:accessibilityFlags=\"flagRetrieveInteractiveWindows\"", "typeViewClicked", "canRetrieveWindowContent=\"true\""),
                "숨기기 창이 활성 창인 동안 다른 앱 창의 입력란을 읽으려면 필요(1.55.3 실기에서 빠져 동작 안 함)"
            ),
            Contract(
                "src/main/java/com/labok/goat/data/lab/StatusBarHider.kt",
                listOf("StatusBarInputService.setEnabled(app, true)", "StatusBarInputService.setEnabled(app, false)"),
                "상단바 숨기기를 켜고 끌 때 입력 도우미도 함께 켜고 끈다"
            ),
            Contract(
                "src/main/java/com/labok/goat/data/shizuku/ShizukuAccess.kt",
                listOf("StatusBarHider.onShizukuLost(", "StatusBarHider.onShizukuReady("),
                "Shizuku가 끊기면 상단바 숨기기가 키보드를 막지 않게(1.55.0)"
            )
        )
    }
}
