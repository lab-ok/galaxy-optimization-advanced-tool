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

    /** 시간대 한 칸. [usedPct]는 충전하지 않은 동안 줄어든 잔량, [screenMs]는 화면이 켜져 있던 시간. */
    data class Bucket(val start: Long, val end: Long, val usedPct: Float, val screenMs: Long, val charging: Boolean)

    /** 시스템 기록을 읽는다(작업 스레드). Shizuku가 없거나 읽지 못하면 null. */
    fun read(): List<Point>? {
        val out = ShizukuAccess.shell("dumpsys batterystats --history | grep -E '^ +(0|\\+[0-9dhms]+) \\('")?.out ?: return null
        return parse(out).takeIf { it.isNotEmpty() }
    }

    private val LINE = Regex("""^\s*(0|\+[0-9dhms]+)\s+\(\d+\)\s+(.*)$""")
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

    internal fun parse(text: String, zone: TimeZone = TimeZone.getDefault()): List<Point> {
        val fmt = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply { timeZone = zone }
        val out = ArrayList<Point>()
        // 실제 시각 기준점: (경과 시간, 실제 시각)
        var anchorOffset = -1L
        var anchorWall = 0L
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
     * [from]~[now]를 [bucketMs] 칸으로 나눠 칸마다 줄어든 잔량과 화면 켜짐 시간을 더한다. 두 시점 사이에서 줄어든 잔량은
     * 시간 비율대로 나눈다. 충전 중이거나 꺼져 있던 구간은 사용량에 넣지 않는다.
     */
    internal fun buckets(points: List<Point>, now: Long, bucketMs: Long, from: Long = points.firstOrNull()?.time ?: now): List<Bucket> {
        if (points.isEmpty() || now <= from) return emptyList()
        val count = ((now - from + bucketMs - 1) / bucketMs).toInt().coerceAtLeast(1)
        val used = FloatArray(count)
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
                if (a.charging) charging[i] = true
                used[i] += drop * overlap / span
                if (a.screen) screen[i] += overlap
            }
        }
        return List(count) { i -> Bucket(from + i * bucketMs, minOf(from + (i + 1) * bucketMs, now), used[i], screen[i], charging[i]) }
    }

    /**
     * 마지막 충전 이후 충전하지 않은 동안의 평균 소모(%/시간). 꺼져 있던 구간은 뺀다. 쓴 시간이 30분이 안 되거나 줄어든 잔량이 없으면 null.
     */
    internal fun drainPerHour(points: List<Point>, now: Long): Float? {
        if (points.isEmpty()) return null
        var drop = 0
        var span = 0L
        (points.zipWithNext() + listOf(points.last() to points.last().copy(time = now, afterOff = false))).forEach { (a, b) ->
            if (a.charging || b.afterOff || b.time <= a.time) return@forEach
            drop += (a.level - b.level).coerceAtLeast(0)
            span += b.time - a.time
        }
        if (span < 30 * 60_000L || drop <= 0) return null
        return drop * HOUR.toFloat() / span
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
