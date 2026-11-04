package com.labok.goat.ui.lab

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.lab.BatteryHistory
import com.labok.goat.data.lab.UsageArchive
import com.labok.goat.data.lab.UsageFun
import com.labok.goat.data.lab.UsageTally
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 실험실 사용 통계(11-04 소장님 지정). 시안 1번 '숫자 타일' 위주에 재미(한 줄 요약·칭호·▲▼·재미로 보는 숫자)를 얹었다.
 * 오늘·어제·7일·월·1년·전체를 고르면 저장된 날짜별 기록([UsageArchive])과 오늘의 Android 기록([UsageTally])을 합쳐 센다.
 * 맨 아래에는 예전 실험실 '배터리 모니터링'을 그대로 붙였다(1.50.3에서 흡수).
 */

private enum class StatsRange(val label: String, val compare: String?) {
    TODAY("오늘", "어제 이맘때보다"),
    YESTERDAY("어제", "그저께보다"),
    WEEK("7일", "그 전 7일보다"),
    MONTH("월", "지난달보다"),
    YEAR("1년", "지난해보다"),
    ALL("전체", null)
}

/**
 * 화면에 보여 줄 한 기간: 통계, 비교 기준, 평균 기준([avgRef], 하루짜리 기간에서 '최근 7일 평균'으로 쓴다),
 * 기간 안 흐름(일별·월별 화면 시간), 실제 기간(시작·끝).
 */
private class StatsView(
    val now: UsageTally.Result,
    val base: UsageTally.Result?,
    val avgRef: UsageTally.Result?,
    val trend: List<Pair<String, Long>>,
    val trendTitle: String?,
    val from: Long,
    val to: Long
)

/** 숫자 표의 한 줄: 이름, 값을 꺼내는 법, 표시 방법. */
private enum class Kind { COUNT, TIME, PERCENT }

private class Metric(val label: String, val unit: String, val kind: Kind, val get: (UsageTally.Result) -> Number?)

/** 숫자 표의 묶음(같은 종류끼리 모아 중복 없이 한 번씩만 보여 준다). */
private val GROUPS: List<Pair<String, List<Metric>>> = listOf(
    "화면" to listOf(
        Metric("화면 켠 시간", "", Kind.TIME) { it.screenMs },
        Metric("화면 켬", "번", Kind.COUNT) { it.screenOn },
        Metric("잠금 해제", "번", Kind.COUNT) { it.unlocks }
    ),
    "앱·알림" to listOf(
        Metric("앱 실행", "번", Kind.COUNT) { it.appOpens },
        Metric("알림", "개", Kind.COUNT) { it.notifications }
    ),
    "손으로 한 것" to listOf(
        Metric("펼침", "번", Kind.COUNT) { it.unfolds },
        Metric("화면 회전", "번", Kind.COUNT) { it.rotations },
        Metric("볼륨 버튼", "번", Kind.COUNT) { it.volume }
    ),
    "배터리" to listOf(
        Metric("배터리 사용", "%", Kind.PERCENT) { it.batteryUsed },
        Metric("충전기 꽂음", "번", Kind.COUNT) { it.charges }
    )
)

@Composable
internal fun UsageStatsScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.TODAY) }
    // 월·1년 보기에서 몇 달·몇 해 전을 보는지(0 = 이번)
    var back by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<StatsView?>(null) }
    var batteryOpen by remember { mutableStateOf(false) }
    LaunchedEffect(range, back) {
        view = null
        view = withContext(Dispatchers.IO) { load(context, range, back) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(bottom = Gap.Item)) {
            LiquidChips(options = StatsRange.entries, selected = range, label = { it.label }, onSelect = {
                range = it
                back = 0
            })
        }
        if (range == StatsRange.MONTH || range == StatsRange.YEAR) {
            PeriodPicker(periodLabel(range, back), canNext = back > 0, onPrev = { back++ }, onNext = { if (back > 0) back-- })
            Spacer(Modifier.height(Gap.Item))
        }
        val v = view
        if (v == null) {
            Spacer(Modifier.height(Gap.Section))
            LiquidLoader(Copy.LAB_STATS_LOADING)
            return@Column
        }
        val r = v.now
        val b = v.base
        if (!r.usageAccess) {
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text(Copy.LAB_STATS_NO_ACCESS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.LAB_STATS_NO_ACCESS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Gap.Card))
        }
        // 1. 요약: 기간(언제부터 언제까지), 가장 눈에 띄는 것 두 줄, 칭호
        val lines = UsageFun.headline(r, b)
        val titles = UsageFun.titles(r, r.days)
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.ink)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(spanLabel(v.from, v.to, r.days), color = c.onInk.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            if (lines.isEmpty()) Text("아직 기록이 없습니다", color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            lines.forEach { Text(it, color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
            if (titles.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Item))
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                    titles.forEach { t ->
                        Text(
                            t.name,
                            color = c.ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.onInk).padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Gap.Card))
        // 2. 숫자 표: 값, 평균, 비교 기준 대비 변화를 한 줄에
        val avgNote = if (r.days > 1) "하루 평균" else v.avgRef?.let { "최근 ${it.days}일 평균" }
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("숫자로 보기", fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(listOfNotNull(avgNote, range.compare?.let { "▲▼ $it" }).joinToString(" · "), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            GROUPS.forEach { (group, metrics) ->
                val shown = metrics.filter { it.get(r) != null }
                if (shown.isEmpty()) return@forEach
                Spacer(Modifier.height(Gap.Item))
                Text(group, color = c.muted, style = MaterialTheme.typography.labelSmall)
                shown.forEach { m -> MetricRow(m, r, b, v.avgRef) }
            }
            val missing = buildList {
                if (r.volume == null) add(Copy.LAB_STATS_VOLUME_NEED)
                if (r.charges == null) add(Copy.LAB_STATS_CHARGE_NEED)
            }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Item))
                Text(missing.joinToString(" "), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        // 3. 흐름: 날마다·달마다 화면 켠 시간, 평균선과 가장 많이 쓴 때
        if (v.trend.size > 1 && v.trendTitle != null) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text(v.trendTitle, fontWeight = FontWeight.SemiBold, color = c.ink)
                val active = v.trend.filter { it.second > 0 }
                if (active.isNotEmpty()) {
                    val peak = active.maxBy { it.second }
                    Text("평균 ${UsageFun.duration(active.sumOf { it.second } / active.size)} · 가장 많이 쓴 때 ${peak.first} ${UsageFun.duration(peak.second)}", color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(Gap.Item))
                TrendBars(v.trend)
            }
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Text(if (r.days > 1) "시간대별 화면 켠 시간 · 하루 평균" else "시간대별 화면 켠 시간", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(Gap.Item))
            HourBars(if (r.days > 1) LongArray(24) { r.hourlyMs[it] / r.days } else r.hourlyMs)
        }
        // 4. 발견: 숫자끼리 엮어서 나온 것
        val topApps = remember(r) { UsageTally.top(context, r.apps, 5) }
        val topNotis = remember(r) { UsageTally.top(context, r.notis, 3) }
        val facts = UsageFun.facts(r, topApps.firstOrNull(), topNotis.firstOrNull())
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("발견", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Tight))
                facts.forEach { Text("• $it", color = c.ink, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        // 5. 앱 순위: 실행과 알림을 한 카드에
        if (topApps.isNotEmpty() || topNotis.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                if (topApps.isNotEmpty()) {
                    Text("많이 연 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.height(Gap.Tight))
                    RankList(topApps, "번")
                }
                if (topNotis.isNotEmpty()) {
                    if (topApps.isNotEmpty()) Spacer(Modifier.height(Gap.Item))
                    Text("알림이 많이 온 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.height(Gap.Tight))
                    RankList(topNotis, "개")
                }
            }
        }
        // 6. 배터리 자세히(예전 배터리 모니터링). 접어 두고 필요할 때 연다
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth).clickable { batteryOpen = !batteryOpen }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Copy.LAB_STATS_BATTERY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(if (batteryOpen) "접기" else "펼치기", color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(Copy.LAB_STATS_BATTERY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (batteryOpen) {
            Spacer(Modifier.height(Gap.Card))
            Box(Modifier.widthIn(max = ContentMaxWidth)) { BatterySection() }
        }
        if (range != StatsRange.TODAY && range != StatsRange.YESTERDAY) {
            Spacer(Modifier.height(Gap.Item))
            Text(Copy.LAB_STATS_ARCHIVE_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
    }
}

/** 숫자 표 한 줄: 이름 · 값(굵게), 아래에 평균과 변화. */
@Composable
private fun MetricRow(m: Metric, r: UsageTally.Result, base: UsageTally.Result?, avgRef: UsageTally.Result?) {
    val c = LocalLiquid.current
    val value = m.get(r) ?: return
    // 여러 날이면 이 기간의 하루 평균, 하루짜리면 최근 며칠의 하루 평균
    val avg = if (r.days > 1) perDay(value, r.days) else avgRef?.let { ref -> m.get(ref)?.let { perDay(it, ref.days) } }
    val change = base?.let { m.get(it) }?.let { delta(m.kind, value, it) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(m.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        avg?.let { Text("평균 " + format(m.kind, it, m.unit), color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 10.dp)) }
        Text(format(m.kind, value, m.unit), color = c.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        Text(
            change ?: "",
            color = c.ink.copy(alpha = if (change?.startsWith("▲") == true) 1f else 0.5f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 52.dp)
        )
    }
}

private fun perDay(v: Number, days: Int): Double = v.toDouble() / days.coerceAtLeast(1)

private fun format(kind: Kind, v: Number, unit: String): String = when (kind) {
    Kind.TIME -> UsageFun.duration(v.toLong())
    Kind.PERCENT -> "${v.toDouble().roundToInt()}$unit"
    Kind.COUNT -> {
        val d = v.toDouble()
        val one = (d * 10).roundToInt() / 10.0
        (if (d >= 10 || one == one.roundToInt().toDouble()) "${one.roundToInt()}" else "$one") + unit
    }
}

/** 기준 대비 변화: ▲·▼와 차이. 같거나 아주 작으면 null. */
private fun delta(kind: Kind, now: Number, before: Number): String? {
    val d = now.toDouble() - before.toDouble()
    return when (kind) {
        Kind.TIME -> minutesDelta(now.toLong(), before.toLong())
        else -> {
            val n = d.roundToInt()
            when {
                n > 0 -> "▲$n"
                n < 0 -> "▼${-n}"
                else -> null
            }
        }
    }
}

/** 요약 맨 윗줄: 실제 기간(언제부터 언제까지)과 날 수. */
private fun spanLabel(from: Long, to: Long, days: Int): String {
    val f = Calendar.getInstance().apply { timeInMillis = from }
    val t = Calendar.getInstance().apply { timeInMillis = to }
    fun md(c: Calendar) = "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일"
    val sameDay = f.get(Calendar.YEAR) == t.get(Calendar.YEAR) && f.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "${f.get(Calendar.YEAR)}년 ${md(f)}"
    val start = "${f.get(Calendar.YEAR)}년 ${md(f)}"
    val end = (if (t.get(Calendar.YEAR) != f.get(Calendar.YEAR)) "${t.get(Calendar.YEAR)}년 " else "") + md(t)
    return "$start ~ $end · 기록 ${days}일"
}

/** 기간 하나를 센다(작업 스레드): 지난날은 저장된 기록, 오늘은 새로 센다. */
private fun load(context: Context, range: StatsRange, back: Int): StatsView {
    UsageArchive.sync(context)
    val foldable = DeviceKind.isFoldable(context)
    val now = System.currentTimeMillis()
    val today = UsageTally.dayStart()
    val battery = if (ShizukuAccess.status.value == ShizukuAccess.State.READY) BatteryHistory.read(now) else null
    val todayResult by lazy { UsageTally.read(context, today, now, foldable, battery) }

    /** [day] 앞 7일(저장된 날만)의 합. 하루짜리 기간의 '최근 7일 평균'에 쓴다. 저장된 날이 없으면 null. */
    fun recent(day: Long): UsageTally.Result? = UsageTally.merge(UsageArchive.between(context, day - 7 * DAY, day - 1).map { it.second })
    fun period(from: Long, to: Long): UsageTally.Result {
        val days = UsageArchive.between(context, from, minOf(to, today - 1)).map { it.second }
        val withToday = if (to >= today) days + todayResult else days
        return UsageTally.merge(withToday) ?: UsageTally.read(context, from, minOf(to, now), foldable)
    }
    return when (range) {
        StatsRange.TODAY -> StatsView(todayResult, UsageTally.read(context, today - DAY, today - DAY + (now - today), foldable), recent(today), emptyList(), null, today, now)
        StatsRange.YESTERDAY -> StatsView(period(today - DAY, today - 1), period(today - 2 * DAY, today - DAY - 1), recent(today - DAY), emptyList(), null, today - DAY, today - 1)
        StatsRange.WEEK -> {
            val from = UsageTally.dayStart(6)
            StatsView(period(from, now), period(UsageTally.dayStart(13), from - 1), null, dailyTrend(context, from, now) { todayResult }, "날마다 화면 켠 시간", from, now)
        }
        StatsRange.MONTH -> {
            val (from, to) = monthRange(back)
            val (pf, pt) = monthRange(back + 1)
            StatsView(period(from, minOf(to, now)), period(pf, pt), null, dailyTrend(context, from, minOf(to, now)) { todayResult }, "날마다 화면 켠 시간", from, minOf(to, now))
        }
        StatsRange.YEAR -> {
            val (from, to) = yearRange(back)
            val (pf, pt) = yearRange(back + 1)
            val start = maxOf(from, UsageArchive.firstDay(context) ?: today)
            StatsView(period(from, minOf(to, now)), period(pf, pt), null, monthlyTrend(from, minOf(to, now), ::period), "달마다 화면 켠 시간", start, minOf(to, now))
        }
        StatsRange.ALL -> {
            val first = UsageArchive.firstDay(context) ?: today
            StatsView(period(first, now), null, null, monthlyTrend(first, now, ::period), "달마다 화면 켠 시간", first, now)
        }
    }
}

/** 날마다의 화면 켠 시간(라벨은 일). 오늘은 [today]로 새로 센 값을 쓴다. */
private fun dailyTrend(context: Context, from: Long, to: Long, today: () -> UsageTally.Result): List<Pair<String, Long>> {
    val stored = UsageArchive.between(context, from, to).toMap()
    val todayStart = UsageTally.dayStart()
    val cal = Calendar.getInstance().apply { timeInMillis = from }
    val out = ArrayList<Pair<String, Long>>()
    while (cal.timeInMillis <= to) {
        val t = cal.timeInMillis
        val ms = if (t >= todayStart) today().screenMs else stored[UsageTally.dayKey(t)]?.screenMs ?: 0L
        out += "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}" to ms
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return out
}

/** 달마다의 화면 켠 시간(라벨은 월). */
private fun monthlyTrend(from: Long, to: Long, period: (Long, Long) -> UsageTally.Result): List<Pair<String, Long>> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = from
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val out = ArrayList<Pair<String, Long>>()
    while (cal.timeInMillis <= to) {
        val start = maxOf(cal.timeInMillis, from)
        val label = "${cal.get(Calendar.MONTH) + 1}월"
        cal.add(Calendar.MONTH, 1)
        out += label to period(start, minOf(cal.timeInMillis - 1, to)).screenMs
    }
    return out
}

/** [back]달 전 달의 1일 0시 ~ 말일 끝. */
private fun monthRange(back: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = UsageTally.dayStart()
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, -back)
    }
    val from = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return from to cal.timeInMillis - 1
}

/** [back]해 전 해의 1월 1일 ~ 12월 31일 끝. */
private fun yearRange(back: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = UsageTally.dayStart()
        set(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.YEAR, -back)
    }
    val from = cal.timeInMillis
    cal.add(Calendar.YEAR, 1)
    return from to cal.timeInMillis - 1
}

private fun periodLabel(range: StatsRange, back: Int): String {
    val cal = Calendar.getInstance()
    return if (range == StatsRange.MONTH) {
        cal.add(Calendar.MONTH, -back)
        "${cal.get(Calendar.YEAR)}년 ${cal.get(Calendar.MONTH) + 1}월"
    } else {
        cal.add(Calendar.YEAR, -back)
        "${cal.get(Calendar.YEAR)}년"
    }
}

private const val DAY = 86_400_000L

/** 이전·다음 달(해) 고르기. */
@Composable
private fun PeriodPicker(label: String, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = LocalLiquid.current
    Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onPrev).padding(horizontal = 16.dp))
        Text(label, color = c.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("›", color = c.ink.copy(alpha = if (canNext) 1f else 0.25f), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = canNext, onClick = onNext).padding(horizontal = 16.dp))
    }
}

/** 화면 켠 시간의 기준 대비 변화(분). 1분 미만 차이는 표시하지 않는다. */
private fun minutesDelta(now: Long, before: Long): String? {
    val d = (now - before) / 60_000
    return when {
        d >= 1 -> "▲${UsageFun.duration(d * 60_000)}"
        d <= -1 -> "▼${UsageFun.duration(-d * 60_000)}"
        else -> null
    }
}

/** 0~23시 막대. 진하고 높을수록 그 시간에 화면을 오래 켰다. */
@Composable
private fun HourBars(hourly: LongArray) {
    Bars(hourly.toList(), (0 until 24).map { if (it % 6 == 0) "${it}시" else "" })
}

/** 기간 안 흐름(일별·월별) 막대. 라벨이 많으면 몇 칸 걸러 쓴다. */
@Composable
private fun TrendBars(items: List<Pair<String, Long>>) {
    val every = ((items.size + 7) / 8).coerceAtLeast(1)
    Bars(items.map { it.second }, items.mapIndexed { i, p -> if (i % every == 0) p.first else "" })
}

@Composable
private fun Bars(values: List<Long>, labels: List<String>) {
    val c = LocalLiquid.current
    val measurer = rememberTextMeasurer()
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val label = TextStyle(fontFamily = GalaxyFont, fontSize = (10f / fontScale).sp, color = c.muted)
        val chartH = size.height - 16.dp.toPx()
        val slot = size.width / values.size
        val gap = (slot * 0.2f).coerceAtMost(3.dp.toPx())
        values.forEachIndexed { i, v ->
            val k = v.toFloat() / max
            val barH = (chartH * k).coerceAtLeast(if (v > 0) 2.dp.toPx() else 0f)
            drawRoundRect(c.line.copy(alpha = 0.45f), Offset(i * slot, 0f), Size(slot - gap, chartH), CornerRadius(2.dp.toPx()))
            if (barH > 0f) drawRoundRect(c.ink.copy(alpha = 0.35f + 0.65f * k), Offset(i * slot, chartH - barH), Size(slot - gap, barH), CornerRadius(2.dp.toPx()))
            if (labels[i].isNotEmpty()) {
                val l = measurer.measure(labels[i], label)
                drawText(l, topLeft = Offset((i * slot).coerceAtMost(size.width - l.size.width), size.height - l.size.height))
            }
        }
    }
}

/** 순위 목록: 번호, 이름, 막대, 횟수. */
@Composable
private fun RankList(items: List<Pair<String, Int>>, unit: String) {
    val c = LocalLiquid.current
    val max = items.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(Gap.Tight)) {
        items.forEachIndexed { i, (name, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = c.ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                Text(name, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.width(80.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.ink))
                }
                Text("$n$unit", color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 52.dp).padding(start = 6.dp))
            }
        }
    }
}
