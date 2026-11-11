package com.labok.goat.testing

import com.labok.goat.R
import com.labok.goat.ui.text.Copy
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM 단위 테스트용 문구 읽기: res/values/strings*.xml을 직접 읽어 [Copy.lookup]에 넣는다(앱이 없어 리소스를 못 읽으므로).
 * 테스트에서 `@Before fun strings() = TestStrings.install()`로 쓴다.
 */
object TestStrings {
    /** 리소스는 :core에 모여 있다(11-11 모듈 분리). 기능 모듈이 따로 가진 문구도 함께 읽는다. */
    private val repo: File by lazy { listOf(File(".."), File(".")).first { File(it, "core/src/main/res/values").isDirectory } }
    private val dir: File by lazy { File(repo, "core/src/main/res/values") }
    private val featureDirs: List<File> by lazy {
        File(repo, "feature").listFiles().orEmpty().map { File(it, "src/main/res/values") }.filter { it.isDirectory }
    }

    /** 리소스 이름 → 문구(기본 언어, 모든 strings*.xml). */
    val all: Map<String, String> by lazy {
        (listOf(dir) + featureDirs).flatMap { d -> d.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().toList() }
            .fold(HashMap()) { m, f -> m.apply { putAll(read(f)) } }
    }

    /** 공통 문구(strings_common.xml, 예전 Copy.kt 상수)만. */
    val common: Map<String, String> by lazy { read(File(dir, "strings_common.xml")) }

    private fun read(f: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to unescape(n.textContent)
        }
    }

    private val byId: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    fun install() {
        Copy.lookup = { id -> all[byId[id]] ?: error("문구 없음: id=$id") }
    }

    /** Android 문자열 리소스의 이스케이프(\', \", \\n, \\)를 푼다. */
    internal fun unescape(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> b.append('\n')
                    't' -> b.append('\t')
                    'u' -> {
                        b.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> b.append(n)
                }
                i += 2
            } else {
                b.append(ch)
                i++
            }
        }
        return b.toString()
    }
}
