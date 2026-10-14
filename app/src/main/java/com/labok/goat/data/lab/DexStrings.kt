package com.labok.goat.data.lab

import com.labok.goat.data.shizuku.ShizukuAccess
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 시스템 파일 속 코드 이름 찾기(터치패널 진단용). 삼성 플렉스 패널 코드가 폴드8 시스템에 남아 있는지 보려고, 시스템 UI·설정 앱·시스템 서비스 파일의
 * dex 문자열 표에서 [pattern]에 맞는 이름을 뽑는다. 시스템 파일은 Shizuku 셸로 읽는다(`cat`). apk·jar(zip 안의 classes*.dex)와
 * vdex(여러 dex가 이어 붙은 파일) 모두 파일 안의 dex 머리("dex\n0")를 찾아 읽는다.
 */
object DexStrings {
    private const val MAX_BYTES = 160L * 1024 * 1024

    /** [path] 파일에서 [pattern]에 맞는 문자열(최대 [limit]개). 읽지 못하면 이유를 담은 한 줄. 작업 스레드에서 부른다. */
    fun find(path: String, pattern: Regex, limit: Int = 60): List<String> {
        val bytes = read(path) ?: return listOf("(읽지 못함)")
        val found = sortedSetOf<String>()
        val dexes = if (path.endsWith(".apk") || path.endsWith(".jar")) unzipDex(bytes) else listOf(bytes)
        dexes.forEach { d -> dexOffsets(d).forEach { off -> runCatching { strings(d, off) { if (pattern.containsMatchIn(it) && it.length <= 160) found.add(it) } } } }
        return found.take(limit).ifEmpty { listOf("(없음)") }
    }

    private fun read(path: String): ByteArray? = runCatching {
        val p = ShizukuAccess.stream("cat '$path'") ?: return null
        p.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }.also { p.destroy() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun unzipDex(zip: ByteArray): List<ByteArray> = runCatching {
        val list = mutableListOf<ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.startsWith("classes") && e.name.endsWith(".dex")) list.add(z.readBytes())
            }
        }
        list
    }.getOrDefault(emptyList())

    /** 파일 안 dex 머리("dex\n03x\0")의 위치들. */
    internal fun dexOffsets(b: ByteArray): List<Int> {
        val out = mutableListOf<Int>()
        var i = 0
        while (i <= b.size - 8) {
            if (b[i] == 'd'.code.toByte() &&
                b[i + 1] == 'e'.code.toByte() &&
                b[i + 2] == 'x'.code.toByte() &&
                b[i + 3] == '\n'.code.toByte() &&
                b[i + 4] == '0'.code.toByte() &&
                b[i + 7] == 0.toByte()
            ) {
                out.add(i)
                i += 0x70
            } else {
                i++
            }
        }
        return out
    }

    /** dex([base]부터)의 문자열 표를 차례로 [each]에 넘긴다: 머리 0x38에 개수, 0x3C에 위치. 각 항목은 uleb128 길이 + MUTF-8 바이트(0으로 끝남). */
    internal fun strings(b: ByteArray, base: Int, each: (String) -> Unit) {
        val count = u32(b, base + 0x38)
        val idsOff = u32(b, base + 0x3C)
        if (count <= 0 || idsOff <= 0) return
        for (k in 0 until count) {
            var p = base + u32(b, base + idsOff + k * 4)
            if (p !in b.indices) continue
            // uleb128(UTF-16 길이) 건너뛰기
            while (p < b.size && b[p].toInt() and 0x80 != 0) p++
            p++
            var end = p
            while (end < b.size && b[end] != 0.toByte()) end++
            each(String(b, p, end - p, Charsets.UTF_8))
        }
    }

    private fun u32(b: ByteArray, i: Int): Int = if (i < 0 || i + 4 > b.size) 0 else (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)
}
