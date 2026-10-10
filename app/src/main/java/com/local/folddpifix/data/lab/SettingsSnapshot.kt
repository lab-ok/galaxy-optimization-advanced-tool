package com.local.folddpifix.data.lab

import android.content.Context
import android.net.Uri

/**
 * 실험실: 시스템 설정(secure·global·system) 전체를 읽어 두고, 전후를 비교한다.
 * 삼성 '앱 화면 크게/작게' 같은 기능이 어떤 설정 키에 값을 쓰는지 찾는 데 쓴다.
 * 앱 권한으로 읽을 수 있는 키만 보인다(Android 12부터 일부 숨은 키는 앱에 감춰진다). 표마다 읽은 개수를 함께 남긴다.
 */
object SettingsSnapshot {
    val TABLES = listOf("secure", "global", "system")

    /** 값이 없는(null) 설정을 파일에 적는 표시. 빈 문자열과 구분한다. */
    private const val NULL = "<null>"

    /** 표 이름 → (키 → 값). 읽지 못한 표는 오류 문구를 담은 키 하나로 남긴다. */
    fun take(context: Context): Map<String, Map<String, String?>> = TABLES.associateWith { table ->
        runCatching {
            val out = sortedMapOf<String, String?>()
            context.contentResolver.query(Uri.parse("content://settings/$table"), arrayOf("name", "value"), null, null, null)?.use { c ->
                val n = c.getColumnIndex("name")
                val v = c.getColumnIndex("value")
                while (c.moveToNext()) out[c.getString(n)] = c.getString(v)
            }
            out as Map<String, String?>
        }.getOrElse { mapOf("!error" to "${it.javaClass.simpleName}: ${it.message}") }
    }

    fun serialize(s: Map<String, Map<String, String?>>): String = buildString {
        s.forEach { (table, kv) -> kv.forEach { (k, v) -> appendLine("$table\t$k\t${v?.replace('\n', ' ') ?: NULL}") } }
    }

    fun parse(text: String): Map<String, Map<String, String?>> {
        val out = TABLES.associateWith { mutableMapOf<String, String?>() }.toMutableMap()
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('\t', limit = 3)
            if (p.size == 3) out.getOrPut(p[0]) { mutableMapOf() }[p[1]] = p[2].takeIf { it != NULL }
        }
        return out
    }

    /** 전후 비교 보고서. 추가·삭제·변경된 키와 표마다 읽은 개수. */
    fun diff(before: Map<String, Map<String, String?>>, after: Map<String, Map<String, String?>>): String = buildString {
        appendLine("읽은 개수: " + TABLES.joinToString(" · ") { "$it ${before[it]?.size ?: 0} → ${after[it]?.size ?: 0}" })
        var changes = 0
        TABLES.forEach { t ->
            val a = before[t].orEmpty()
            val b = after[t].orEmpty()
            (a.keys + b.keys).sorted().forEach { k ->
                val x = a[k]
                val y = b[k]
                when {
                    k !in a -> { appendLine("[$t] + $k = $y"); changes++ }
                    k !in b -> { appendLine("[$t] - $k (이전 $x)"); changes++ }
                    x != y -> { appendLine("[$t] * $k : $x → $y"); changes++ }
                }
            }
        }
        if (changes == 0) appendLine("바뀐 설정 키가 없습니다. 앱 권한으로 보이지 않는 곳(삼성 내부 서비스 등)에 저장되는 것으로 보입니다.")
    }
}
