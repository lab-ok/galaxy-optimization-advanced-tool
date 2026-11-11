package com.labok.goat

import com.labok.goat.testing.ModuleSources
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 영어 번역 검사(11-11 영어 지원): 한국어 문구 파일(values-ko)마다 같은 이름의 영어 파일(values, 기본 언어)이 있고,
 * 모든 문구가 빠짐없이 번역됐으며, 서식 인자(%1$s 등)가 같아야 한다. 인자가 어긋나면 그 언어에서 앱이 죽는다.
 */
class TranslationTest {
    private class Res(val names: Map<String, String>)

    private fun read(f: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private val args = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx]""")

    private fun pairs(): List<Pair<File, File>> = ModuleSources.modules.flatMap { m ->
        val ko = File(m, "src/main/res/values-ko")
        ko.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().map { it to File(m, "src/main/res/values/${it.name}") }
    }

    @Test
    fun everyKoreanStringHasEnglish() {
        val problems = pairs().flatMap { (ko, en) ->
            if (!en.isFile) return@flatMap listOf("${en.path}: 영어 파일 없음")
            val k = read(ko)
            val e = read(en)
            (k.keys - e.keys).map { "${en.name}: 번역 없음 $it" } + (e.keys - k.keys).map { "${en.name}: 한국어에 없는 이름 $it" }
        }
        assertTrue("영어 번역이 빠졌습니다:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun formatArgumentsMatch() {
        val problems = pairs().filter { it.second.isFile }.flatMap { (ko, en) ->
            val k = read(ko)
            val e = read(en)
            k.mapNotNull { (name, text) ->
                val et = e[name] ?: return@mapNotNull null
                val ka = args.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()
                val ea = args.findAll(et.replace("%%", "")).map { it.value }.sorted().toList()
                "${en.name}: $name 한국어 $ka / 영어 $ea".takeIf { ka != ea }
            }
        }
        assertTrue("서식 인자가 다릅니다:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
