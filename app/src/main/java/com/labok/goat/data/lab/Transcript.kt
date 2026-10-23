package com.labok.goat.data.lab

/**
 * 실험실 '전사문 저장': 삼성 음성 녹음(Galaxy AI 전사)이 공유한 전사문에서 화자를 찾고,
 * 사용자가 고른 화자를 '나', 나머지를 모두 '상대'로 바꾼 글을 만든다. 화면·저장은 [com.labok.goat.ui.lab.TranscriptShareActivity].
 *
 * 전사문 형식은 앱 판마다 조금씩 달라 아래 모양을 모두 받는다(시간은 m:ss, mm:ss, h:mm:ss).
 * - 화자 줄 + 다음 줄부터 말: "화자 1 00:05", "화자 1 (00:05)", "00:05 화자 1", "[00:05] 라준혁", 시간 없는 "화자 1"
 * - 한 줄에 화자와 말: "화자 1: 안녕하세요", "[00:05] 화자 1: 안녕하세요"
 * 이름을 바꾼 화자(예: 라준혁)는 같은 이름이 두 번 이상 나오거나, 문서의 확실한 화자 줄과 같은 모양이고 시간이 앞 화자보다
 * 뒤일 때만 화자로 본다("참고: …", "회의 10:30" 같은 보통 문장을 화자로 오해하지 않게).
 */
object Transcript {
    data class Turn(val speaker: String, val time: String?, val text: String)

    data class Parsed(val preamble: String, val turns: List<Turn>) {
        /** 처음 나온 순서대로 화자 이름. */
        val speakers: List<String> get() = turns.map { it.speaker }.distinct()

        fun count(speaker: String) = turns.count { it.speaker == speaker }

        /** 화자를 고르는 화면에 보여 줄 그 화자의 첫 말(앞부분). */
        fun preview(speaker: String, max: Int = 40): String {
            val t = turns.firstOrNull { it.speaker == speaker && it.text.isNotBlank() }?.text.orEmpty().replace('\n', ' ')
            return if (t.length > max) t.take(max).trimEnd() + "…" else t
        }
    }

    const val ME = "나"
    const val OTHER = "상대"

    private const val TIME = """\d{1,2}:\d{2}(?::\d{2})?"""
    private const val NAME = """[^\s:：()\[\]][^:：()\[\]]{0,23}?"""
    private val generic = Regex("""^(화자|참석자|발언자|Speaker)\s*\d+$""", RegexOption.IGNORE_CASE)
    private val nameThenTime = Regex("""^\s*($NAME)\s*[(\[]?\s*($TIME)\s*[)\]]?\s*$""")
    private val timeThenName = Regex("""^\s*[(\[]?($TIME)[)\]]?\s+($NAME)\s*$""")
    private val nameOnly = Regex("""^\s*($NAME)\s*$""")
    private val inline = Regex("""^\s*(?:[(\[]?($TIME)[)\]]?\s+)?($NAME)\s*(?:[(\[]?\s*($TIME)\s*[)\]]?)?\s*[:：]\s*(.+)$""")

    /** 화자 줄 하나. [form]은 시간 위치(0 = 시간 없음, 1 = 이름 뒤, 2 = 이름 앞). */
    private data class Header(val name: String, val time: String?, val form: Int)

    private fun seconds(t: String) = t.split(':').fold(0) { acc, v -> acc * 60 + (v.toIntOrNull() ?: 0) }

    private fun validName(n: String) = n.any { it.isLetter() }

    private fun isGeneric(n: String) = generic.matches(n.trim())

    /** 화자를 하나도 못 찾으면 null(전사문이 아니거나 화자 표시가 없는 글). */
    fun parse(text: String): Parsed? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // 이름을 바꾼 화자의 "이름: 말" 줄은 같은 이름이 두 번 이상 나와야 화자로 본다
        val inlineNames = lines.mapNotNull { l -> inline.matchEntire(l)?.groupValues?.get(2)?.trim()?.takeIf(::validName) }
            .groupingBy { it }.eachCount()
        fun inlineOk(n: String) = isGeneric(n) || (inlineNames[n] ?: 0) >= 2

        // 시간이 붙은 화자 줄도 이름을 바꾼 화자는 두 번 이상 나와야 한다("내일 3:30"처럼 시간으로 끝나는 말을 화자로 오해하지 않게)
        fun headerOf(line: String) = nameThenTime.matchEntire(line)?.let { Header(it.groupValues[1].trim(), it.groupValues[2], 1) }
            ?: timeThenName.matchEntire(line)?.let { Header(it.groupValues[2].trim(), it.groupValues[1], 2) }
            ?: nameOnly.matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf(::isGeneric)?.let { Header(it, null, 0) }
        val headers = lines.mapNotNull(::headerOf).filter { validName(it.name) }
        val headerNames = headers.groupingBy { it.name }.eachCount()
        fun sure(h: Header) = isGeneric(h.name) || (headerNames[h.name] ?: 0) >= 2
        // 확실한 화자 줄이 두 개 이상인 모양(시간 위치)이면, 같은 모양에 시간이 앞 화자보다 뒤인 줄은 한 번만 말한 화자로도 받는다
        val form = headers.filter(::sure).groupingBy { it.form }.eachCount().filterValues { it >= 2 }.keys
        var lastSec = -1
        fun headerOk(h: Header): Boolean {
            if (!validName(h.name)) return false
            val sec = h.time?.let(::seconds)
            val ok = sure(h) || (h.form in form && sec != null && sec >= lastSec && h.name.length <= 12)
            if (ok && sec != null) lastSec = sec
            return ok
        }

        val preamble = StringBuilder()
        val turns = mutableListOf<Turn>()
        var speaker: String? = null
        var time: String? = null
        val body = StringBuilder()
        fun flush() {
            val s = speaker ?: return
            turns += Turn(s, time, body.toString().trim())
            body.clear()
        }
        for (line in lines) {
            val header = headerOf(line)
            if (header != null && headerOk(header)) {
                flush()
                speaker = header.name
                time = header.time
                continue
            }
            val m = inline.matchEntire(line)
            val name = m?.groupValues?.get(2)?.trim()
            if (m != null && name != null && validName(name) && inlineOk(name)) {
                flush()
                // 다음 줄이 이어지면(줄바꿈된 말) 이 화자의 말에 붙인다
                speaker = name
                time = m.groupValues[1].ifEmpty { m.groupValues[3] }.ifEmpty { null }
                body.appendLine(m.groupValues[4])
                continue
            }
            if (speaker != null) {
                body.appendLine(line)
            } else if (turns.isEmpty()) {
                preamble.appendLine(line)
            }
        }
        flush()
        if (turns.isEmpty()) return null
        return Parsed(preamble.toString().trim(), turns)
    }

    /** [me]가 한 말은 '나', 나머지 화자는 모두 '상대'로 바꾼 글. 시간은 그대로 둔다. */
    fun convert(parsed: Parsed, me: String): String = buildString {
        if (parsed.preamble.isNotEmpty()) append(parsed.preamble).append("\n\n")
        parsed.turns.forEachIndexed { i, t ->
            if (i > 0) append("\n\n")
            append(if (t.speaker == me) ME else OTHER)
            t.time?.let { append(" (").append(it).append(')') }
            append('\n').append(t.text)
        }
        append('\n')
    }

    /** 공유받은 파일 바이트를 글로: BOM이 있으면 그 인코딩, 없으면 UTF-16 흔적(0 바이트)을 보고, 아니면 UTF-8. */
    fun decode(bytes: ByteArray): String {
        fun b(i: Int) = bytes[i].toInt() and 0xff
        return when {
            bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 4 && bytes.take(64).withIndex().count { (i, v) -> i % 2 == 1 && v.toInt() == 0 } > bytes.take(64).size / 4 ->
                String(bytes, Charsets.UTF_16LE)
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    /** 저장할 파일 이름: 받은 이름(확장자 뺌)에 '(나·상대)'를 붙인다. 파일 이름에 못 쓰는 글자는 바꾼다. */
    fun fileName(base: String?, fallback: String): String {
        val name = base?.substringBeforeLast('.')?.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_")?.trim()?.take(60)?.ifEmpty { null } ?: fallback
        return "$name (나·상대).txt"
    }
}
