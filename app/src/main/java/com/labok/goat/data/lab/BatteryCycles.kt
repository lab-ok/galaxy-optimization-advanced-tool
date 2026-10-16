package com.labok.goat.data.lab

import android.content.Context
import com.labok.goat.data.lab.BatteryStatsReader.SinceCharge

/**
 * 충전 주기 기록(실험실 배터리 모니터링): 시스템이 '마지막 충전 이후' 통계를 새로 시작하면(충전한 뒤 충전기를 뺌)
 * 그 직전에 읽어 둔 통계를 지난 주기 하나로 요약해 남긴다. 최근 [KEEP]번까지 둔다.
 * 통계는 15분 기록 작업과 배터리 화면이 읽을 때마다 [update]로 넘겨받는다. 그래서 마지막으로 읽은 뒤 충전기를 꽂기 전까지의
 * 사용(최대 15분 남짓)은 빠질 수 있다. 기록은 기기 안(앱 설정 저장소)에만 둔다.
 */
object BatteryCycles {
    private const val PREF = "battery_cycles"
    internal const val KEEP = 10
    private const val TOP = 3

    /** 지난 충전 주기 하나. [apps]는 많이 쓴 앱 이름과 전체 중 몫(0~1), 많이 쓴 순서. */
    data class Cycle(
        val start: Long,
        val onBatteryMs: Long,
        val screenOnMs: Long,
        val usedPct: Int,
        val screenOnPct: Int,
        val screenOffPct: Int,
        val apps: List<Pair<String, Float>>
    )

    /**
     * 방금 읽은 통계 [now]를 넘긴다. 저장해 둔 최신 통계와 시작 시각이 다르면 저장해 둔 것을 지난 주기로 남긴다.
     * [name]은 uid를 표시 이름으로 바꾼다(지난 주기는 앱을 지워도 이름이 남게 이름으로 저장한다).
     */
    fun update(context: Context, now: SinceCharge, name: (Int) -> String) {
        val p = prefs(context)
        val last = p.getString("current", null)?.let(::decodeOne)
        val cur = summarize(now, name)
        val history = if (last != null && last.start != cur.start && last.onBatteryMs > 0) {
            trim(listOf(last) + cycles(context))
        } else {
            null
        }
        val e = p.edit().putString("current", encodeOne(cur))
        if (history != null) e.putString("history", encode(history))
        e.apply()
    }

    /** 지난 주기들, 최근 것부터. */
    fun cycles(context: Context): List<Cycle> = decode(prefs(context).getString("history", null))

    fun clear(context: Context) {
        prefs(context).edit().remove("history").apply()
    }

    internal fun summarize(s: SinceCharge, name: (Int) -> String): Cycle {
        val total = s.computedMah.takeIf { it > 0 } ?: s.apps.sumOf { it.mah }
        val apps = if (total <= 0) emptyList() else s.apps.filter { it.mah > 0 }.take(TOP).map { name(it.uid) to (it.mah / total).toFloat().coerceIn(0f, 1f) }
        return Cycle(s.startClock, s.onBatteryMs, s.screenOnMs, (s.usedPctLow + s.usedPctHigh + 1) / 2, s.screenOnPct, s.screenOffPct, apps)
    }

    internal fun trim(list: List<Cycle>) = list.distinctBy { it.start }.sortedByDescending { it.start }.take(KEEP)

    // 저장 형식: 주기는 줄바꿈으로, 칸은 탭으로, 앱은 칸 안에서 ␞(U+241E)로 나눈다. 앱 이름에 든 탭·줄바꿈·␞는 공백으로 바꾼다

    internal fun encodeOne(c: Cycle): String = listOf(
        c.start, c.onBatteryMs, c.screenOnMs, c.usedPct, c.screenOnPct, c.screenOffPct,
        c.apps.joinToString(SEP) { (n, share) -> n.replace(Regex("[\t\n$SEP]"), " ") + "=" + share }
    ).joinToString("\t")

    internal fun decodeOne(line: String): Cycle? {
        val f = line.split('\t')
        if (f.size != 7) return null
        val apps = f[6].split(SEP).filter { it.isNotEmpty() }.mapNotNull { a ->
            val i = a.lastIndexOf('=')
            if (i <= 0) null else (a.substring(0, i) to (a.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null))
        }
        return Cycle(
            f[0].toLongOrNull() ?: return null,
            f[1].toLongOrNull() ?: return null,
            f[2].toLongOrNull() ?: return null,
            f[3].toIntOrNull() ?: return null,
            f[4].toIntOrNull() ?: return null,
            f[5].toIntOrNull() ?: return null,
            apps
        )
    }

    internal fun encode(list: List<Cycle>) = list.joinToString("\n") { encodeOne(it) }

    internal fun decode(raw: String?): List<Cycle> = raw.orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull(::decodeOne)

    private const val SEP = "␞"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
