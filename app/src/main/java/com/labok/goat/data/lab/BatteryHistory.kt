package com.labok.goat.data.lab

import com.labok.goat.data.shizuku.ShizukuAccess
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 시스템 배터리 기록(`dumpsys batterystats --history`)으로 마지막 충전 이후의 잔량·화면 켜짐·깨어 있음·충전 흐름을 읽는다.
 * 예전 안드로이드(4~11) 배터리 화면의 기록 그래프와 같은 자료다. 시스템이 늘 기록하므로 GOAT의 15분 기록을 켜 두지 않아도 된다.
 * 셸 권한(DUMP)이 필요해 Shizuku로 읽는다. 읽기만 하며 통계를 지우지 않는다(`--checkin`·`--reset`은 쓰지 않는다).
 *
 * 줄 형식(삼성 실기 출력으로 확인):
 * ```
 *                     0 (19) RESET:TIME: 2022-12-07-01-14-31
 *                     0 (2) 100 status=discharging health=good plug=none ... +running +screen ...
 *           +7m25s445ms (3) 099 -wake_lock -screen stats=0:"screen-state"
 *          +27m23s664ms (17) SHUTDOWN
 *          +27m23s665ms (17) START
 *          +27m23s665ms (19) TIME: 2022-12-07-01-42-21
 * ```
 * 시간은 기록 시작부터의 경과 시간이고, 끈 동안은 빠진다. 그래서 TIME 줄(실제 시각)이 나올 때마다 실제 시각을 다시 맞춘다.
 */
object BatteryHistory {
    /** 상태가 바뀐 한 시점. [afterOff]면 이 시점 직전까지 기기가 꺼져 있었다. */
    data class Point(
        val time: Long,
        val level: Int,
        val screen: Boolean,
        val charging: Boolean,
        val awake: Boolean,
        val afterOff: Boolean = false
    )

    /**
     * 시간대 한 칸. [usedPct]는 충전하지 않은 동안 줄어든 잔량, 그중 [screenPct]는 화면이 켜져 있던 동안 줄어든 몫이다
     * (나머지는 대기). [screenMs]는 화면이 켜져 있던 시간.
     */
    data class Bucket(
        val start: Long,
        val end: Long,
        val usedPct: Float,
        val screenPct: Float,
        val screenMs: Long,
        val charging: Boolean,
        /** 그 칸에서 충전으로 오른 잔량 */
        val chargedPct: Float = 0f
    ) {
        val standbyPct: Float get() = (usedPct - screenPct).coerceAtLeast(0f)
    }

    /**
     * 시스템 기록을 읽는다(작업 스레드). Shizuku가 없거나 읽지 못하면 null.
     * 기록 전체는 수십 MB 글이 될 수 있어, 기기에서 awk로 그래프에 필요한 줄만 남긴다:
     * 시각·꺼짐·켜짐 줄, 충전 상태·화면·깨어 있음이 바뀐 줄, 잔량이 바뀐 줄. awk가 없으면 grep으로 기록 줄만 거른다.
     */
    fun read(now: Long = System.currentTimeMillis()): List<Point>? {
        val out = ShizukuAccess.shell(command())?.out ?: return null
        return parse(out, now = now).takeIf { it.isNotEmpty() }
    }

    /**
     * 문제 신고용 진단 글(작업 스레드): 기록을 읽는 명령의 결과 크기와 앞뒤 줄, 읽은 시점 수, 거르지 않은 원래 출력의 앞부분.
     * 실기에서 그래프가 비는 이유(셸 실패·형식 차이·시각 줄 없음)를 가리기 위한 것이다.
     */
    fun diagnose(now: Long = System.currentTimeMillis()): String = buildString {
        val r = ShizukuAccess.shell(command())
        if (r == null) {
            appendLine("Shizuku 셸을 쓸 수 없음")
            return@buildString
        }
        val lines = r.out.lines()
        appendLine("거른 출력: ${r.out.length}자 ${lines.size}줄 · 종료 ${if (r.ok) "정상" else "실패"}${r.err.takeIf { it.isNotBlank() }?.let { " · 오류 ${it.take(300)}" } ?: ""}")
        val points = parse(r.out, now = now)
        appendLine("읽은 시점: ${points.size}개" + (points.firstOrNull()?.let { " · 처음 ${java.util.Date(it.time)} ${it.level}% · 마지막 ${java.util.Date(points.last().time)} ${points.last().level}%" } ?: ""))
        appendLine("-- 거른 출력 앞 15줄")
        lines.take(15).forEach { appendLine(it.take(200)) }
        appendLine("-- 거른 출력 끝 5줄")
        lines.takeLast(5).forEach { appendLine(it.take(200)) }
        appendLine("-- 원래 출력 앞 40줄")
        ShizukuAccess.shell("dumpsys batterystats --history | head -40")?.out?.lines()?.forEach { appendLine(it.take(200)) }
        appendLine("-- 원래 출력에서 기록 줄 앞 10줄")
        ShizukuAccess.shell("dumpsys batterystats --history | grep -m10 -E '\\([0-9]+\\) |^ +[0-9][0-9]-[0-9][0-9] '")?.out?.lines()?.forEach { appendLine(it.take(200)) }
    }

    private fun command(): String {
        // 두 형식의 기록 줄: 옛 형식 '  +1m02s003ms (2) 099 …', 새 형식(Format: 2) '  10-16 18:24:22.406 099 …'. 둘 다 세 번째 칸이 잔량이다
        val awk = "awk '/^ +(0|\\+[0-9dhms]+) \\(|^ +[0-9][0-9]-[0-9][0-9] [0-9][0-9]:/{ if (\$0 ~ /TIME:|SHUTDOWN|START|status=|[+-]screen( |\$)|[+-]running( |\$)/) " +
            "{print; last=\$3; next} if (\$3!=last) {print; last=\$3} }'"
        val grep = "grep -E '^ +(0|\\+[0-9dhms]+) \\(|^ +[0-9][0-9]-[0-9][0-9] [0-9][0-9]:'"
        return "if command -v awk >/dev/null; then dumpsys batterystats --history | $awk; else dumpsys batterystats --history | $grep; fi"
    }

    private val LINE = Regex("""^\s*(0|\+[0-9dhms]+)\s+\(\d+\)\s+(.*)$""")
    /** 새 형식(Android 16 이후 삼성 실기 'Battery History [Format: 2]'): 줄 앞이 실제 시각(월-일 시:분:초.밀리초)이다. */
    private val LINE2 = Regex("""^\s*(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(.*)$""")
    private val PART = Regex("""(\d+)(ms|d|h|m|s)""")

    /** "+1h02m03s456ms" → 밀리초. */
    internal fun offsetMs(s: String): Long = PART.findAll(s).sumOf { m ->
        val n = m.groupValues[1].toLong()
        when (m.groupValues[2]) {
            "d" -> n * 86_400_000L
            "h" -> n * 3_600_000L
            "m" -> n * 60_000L
            "s" -> n * 1000L
            else -> n
        }
    }

    /**
     * [now]가 있으면 시각 줄(RESET:TIME·TIME)이 없는 기록도 읽는다: 오래된 기록 파일이 밀려나 시각 줄이 빠진 경우,
     * 마지막 기록을 지금으로 보고 거꾸로 맞춘다(마지막 기록은 길어야 몇 분 전이다).
     */
    internal fun parse(text: String, zone: TimeZone = TimeZone.getDefault(), now: Long? = null): List<Point> {
        if (text.lineSequence().any { LINE2.containsMatchIn(it) }) return parse2(text, zone, now)
        val fmt = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply { timeZone = zone }
        val out = ArrayList<Point>()
        // 실제 시각 기준점: (경과 시간, 실제 시각)
        var anchorOffset = -1L
        var anchorWall = 0L
        if (now != null && text.lineSequence().none { LINE.find(it)?.groupValues?.get(2)?.contains("TIME:") == true }) {
            text.lineSequence().mapNotNull { LINE.find(it) }.lastOrNull()?.let {
                anchorOffset = offsetMs(it.groupValues[1])
                anchorWall = now
            }
        }
        var level = -1
        var screen = false
        var charging = false
        var awake = false
        var off = false
        for (raw in text.lineSequence()) {
            val m = LINE.find(raw) ?: continue
            val offset = offsetMs(m.groupValues[1])
            val rest = m.groupValues[2].trim()
            when {
                rest.startsWith("RESET:TIME:") || rest.startsWith("TIME:") -> {
                    val t = rest.substringAfter("TIME:").trim().take(19)
                    runCatching { fmt.parse(t)?.time }.getOrNull()?.let {
                        anchorOffset = offset
                        anchorWall = it
                    }
                    continue
                }
                rest.startsWith("SHUTDOWN") -> {
                    off = true
                    continue
                }
                rest.startsWith("START") -> continue
            }
            if (anchorOffset < 0) continue
            val lv = rest.take(3).toIntOrNull() ?: continue
            val tokens = rest.split(' ')
            var s = screen
            var c = charging
            var a = awake
            for (tk in tokens) {
                when {
                    tk == "+screen" -> s = true
                    tk == "-screen" -> s = false
                    tk == "+running" -> a = true
                    tk == "-running" -> a = false
                    tk.startsWith("status=") -> c = tk.removePrefix("status=").let { it == "charging" || it == "full" }
                }
            }
            if (lv == level && s == screen && c == charging && a == awake && !off) continue
            val wall = anchorWall + (offset - anchorOffset)
            out += Point(wall, lv, s, c, a, afterOff = off && out.isNotEmpty())
            level = lv
            screen = s
            charging = c
            awake = a
            off = false
        }
        return out
    }

    /**
     * 새 형식 해석. 시각이 줄마다 있지만 연도가 없어서 RESET:TIME 줄의 연도(없으면 [now]의 연도)에서 시작하고,
     * 월이 줄어들면(12월 → 1월) 해를 넘긴 것으로 본다.
     */
    private fun parse2(text: String, zone: TimeZone, now: Long?): List<Point> {
        val cal = java.util.Calendar.getInstance(zone)
        var year = text.lineSequence().mapNotNull { Regex("""RESET:TIME: (\d{4})-""").find(it)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
            ?: cal.apply { timeInMillis = now ?: System.currentTimeMillis() }.get(java.util.Calendar.YEAR)
        var lastMonth = -1
        val out = ArrayList<Point>()
        var level = -1
        var screen = false
        var charging = false
        var awake = false
        var off = false
        for (raw in text.lineSequence()) {
            val m = LINE2.find(raw) ?: continue
            val g = m.groupValues
            val month = g[1].toInt()
            if (lastMonth in 1..12 && month < lastMonth) year++
            lastMonth = month
            cal.clear()
            cal.set(year, month - 1, g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())
            val wall = cal.timeInMillis + g[6].toLong()
            val rest = g[7].trim()
            when {
                rest.startsWith("RESET:TIME:") || rest.startsWith("TIME:") || rest.startsWith("START") -> continue
                rest.startsWith("SHUTDOWN") -> {
                    off = true
                    continue
                }
            }
            val lv = rest.take(3).trim().toIntOrNull() ?: continue
            var s = screen
            var c = charging
            var a2 = awake
            for (tk in rest.split(' ')) {
                when {
                    tk == "+screen" -> s = true
                    tk == "-screen" -> s = false
                    tk == "+running" -> a2 = true
                    tk == "-running" -> a2 = false
                    tk.startsWith("status=") -> c = tk.removePrefix("status=").let { it == "charging" || it == "full" }
                }
            }
            if (lv == level && s == screen && c == charging && a2 == awake && !off) continue
            out += Point(wall, lv, s, c, a2, afterOff = off && out.isNotEmpty())
            level = lv
            screen = s
            charging = c
            awake = a2
            off = false
        }
        return out
    }

    /** 충전 한 번: [startLevel]%에서 [endLevel]%까지 [start]~[end] 동안. [ongoing]이면 아직 충전 중이다. */
    data class Charge(val start: Long, val end: Long, val startLevel: Int, val endLevel: Int, val ongoing: Boolean) {
        val gained: Int get() = endLevel - startLevel
        val durationMs: Long get() = end - start
    }

    /**
     * 기록에서 충전한 구간들(오래된 것부터). 충전기를 꽂은 때부터 뺀 때까지를 한 번으로 보고, 끝 잔량은 그 사이 가장 높은 잔량이다.
     * 잠깐 꽂았다 뺀 것(1분 미만이면서 오른 잔량이 없는 것)은 뺀다.
     */
    internal fun charges(points: List<Point>, now: Long): List<Charge> {
        val out = ArrayList<Charge>()
        var start: Point? = null
        var top = 0
        fun close(end: Long, ongoing: Boolean) {
            val s0 = start ?: return
            if (end - s0.time >= 60_000L || top > s0.level) out += Charge(s0.time, end, s0.level, maxOf(top, s0.level), ongoing)
            start = null
        }
        points.forEach { p ->
            if (p.afterOff && start != null) close(p.time, false)
            if (p.charging) {
                if (start == null) {
                    start = p
                    top = p.level
                } else {
                    top = maxOf(top, p.level)
                }
            } else {
                close(p.time, false)
            }
        }
        close(now, true)
        return out
    }

    /** [from] 이후만 남긴다. 그 앞의 마지막 상태는 [from] 시각의 시점으로 옮겨 시작 상태로 쓴다. [from]이 null이면 그대로. */
    internal fun since(points: List<Point>, from: Long?): List<Point> {
        if (from == null) return points
        val before = points.lastOrNull { it.time <= from }
        val after = points.filter { it.time > from }
        return listOfNotNull(before?.copy(time = from, afterOff = false)) + after
    }

    /**
     * [from]~[now]를 [bucketMs] 칸으로 나눠 칸마다 줄어든 잔량과 화면 켜짐 시간을 더한다. 두 시점 사이에서 줄어든 잔량은
     * 시간 비율대로 나눈다. 충전 중이거나 꺼져 있던 구간은 사용량에 넣지 않는다.
     */
    internal fun buckets(points: List<Point>, now: Long, bucketMs: Long, from: Long = points.firstOrNull()?.time ?: now): List<Bucket> {
        if (points.isEmpty() || now <= from) return emptyList()
        val count = ((now - from + bucketMs - 1) / bucketMs).toInt().coerceAtLeast(1)
        val used = FloatArray(count)
        val usedOn = FloatArray(count)
        val charged = FloatArray(count)
        val screen = LongArray(count)
        val charging = BooleanArray(count)
        // 마지막 시점은 지금까지 이어진다
        val spans = points.zipWithNext() + listOf(points.last() to points.last().copy(time = now, afterOff = false))
        for ((a, b) in spans) {
            if (b.time <= a.time || b.afterOff) continue
            val drop = if (a.charging) 0 else (a.level - b.level).coerceAtLeast(0)
            val span = (b.time - a.time).toFloat()
            val first = ((a.time - from) / bucketMs).toInt().coerceIn(0, count - 1)
            val last = ((b.time - 1 - from) / bucketMs).toInt().coerceIn(0, count - 1)
            for (i in first..last) {
                val s0 = from + i * bucketMs
                val overlap = minOf(b.time, s0 + bucketMs) - maxOf(a.time, s0)
                if (overlap <= 0) continue
                if (a.charging) {
                    charging[i] = true
                    charged[i] += (b.level - a.level).coerceAtLeast(0) * overlap / span
                }
                used[i] += drop * overlap / span
                if (a.screen) {
                    usedOn[i] += drop * overlap / span
                    screen[i] += overlap
                }
            }
        }
        return List(count) { i -> Bucket(from + i * bucketMs, minOf(from + (i + 1) * bucketMs, now), used[i], usedOn[i], screen[i], charging[i], charged[i]) }
    }

    /** 칸 길이: 마지막 충전 이후가 길면 칸을 넓혀 기둥이 [maxBars]개를 넘지 않게 한다(1·2·3·4·6·12시간). */
    internal fun bucketMs(spanMs: Long, maxBars: Int = 24): Long {
        val hours = listOf(1L, 2L, 3L, 4L, 6L, 12L)
        return (hours.firstOrNull { spanMs <= it * HOUR * maxBars } ?: 24L) * HOUR
    }

    /** [pick]이 참인 구간들(시작·끝). 마지막 구간은 [now]까지 이어진다. */
    internal fun spans(points: List<Point>, now: Long, pick: (Point) -> Boolean): List<LongRange> {
        val out = ArrayList<LongRange>()
        var start = -1L
        points.forEachIndexed { i, p ->
            val on = pick(p)
            // 꺼져 있던 동안은 끊는다: 앞 구간을 꺼지기 전 마지막 시점에서 닫는다
            if (p.afterOff && start >= 0) {
                out += start until points[i - 1].time.coerceAtLeast(start + 1)
                start = -1
            }
            if (on && start < 0) start = p.time
            if (!on && start >= 0) {
                out += start until p.time
                start = -1
            }
        }
        if (start >= 0) out += start until now
        return out
    }

    private const val HOUR = 3_600_000L
}
