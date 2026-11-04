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

/** 화면에 보여 줄 한 기간의 통계와 비교 기준, 기간 안 흐름(일별·월별 화면 시간). */
private class StatsView(val now: UsageTally.Result, val base: UsageTally.Result?, val trend: List<Pair<String, Long>>, val trendTitle: String?)

@Composable
internal fun UsageStatsScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.TODAY) }
    // 월·1년 보기에서 몇 달·몇 해 전을 보는지(0 = 이번)
    var back by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<StatsView?>(null) }
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
        // 맨 위: 한 줄 요약과 칭호
        val lines = UsageFun.headline(r, b)
        val titles = UsageFun.titles(r, r.days)
        if (lines.isNotEmpty()) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.ink)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(headlineLabel(range, back, r.days), color = c.onInk.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
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
                    Text(titles.joinToString(" · ") { "${it.name}: ${it.why}" }, color = c.onInk.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Gap.Tight))
                }
            }
            Spacer(Modifier.height(Gap.Card))
        }
        val tiles = buildList {
            r.unfolds?.let { add(StatCell("펼친 횟수", "$it", "번", UsageFun.delta(it, b?.unfolds))) }
            add(StatCell("잠금 해제", "${r.unlocks}", "번", UsageFun.delta(r.unlocks, b?.unlocks)))
            add(StatCell("화면 켬", "${r.screenOn}", "번", UsageFun.delta(r.screenOn, b?.screenOn)))
            add(StatCell("화면 켠 시간", UsageFun.duration(r.screenMs), "", b?.let { minutesDelta(r.screenMs, it.screenMs) }))
            add(StatCell("앱 실행", "${r.appOpens}", "번", UsageFun.delta(r.appOpens, b?.appOpens)))
            add(StatCell("알림", "${r.notifications}", "개", UsageFun.delta(r.notifications, b?.notifications)))
            add(StatCell("화면 회전", "${r.rotations}", "번", UsageFun.delta(r.rotations, b?.rotations)))
            add(StatCell("충전기 꽂음", r.charges?.toString() ?: "-", if (r.charges != null) "번" else "", UsageFun.delta(r.charges, b?.charges)))
            add(StatCell("볼륨 버튼", r.volume?.toString() ?: "-", if (r.volume != null) "번" else "", UsageFun.delta(r.volume, b?.volume)))
            r.batteryUsed?.let { add(StatCell("배터리 사용", "${it.toInt()}", "%", b?.batteryUsed?.let { x -> UsageFun.delta(it.toInt(), x.toInt()) })) }
            if (r.boots > 0) add(StatCell("재부팅", "${r.boots}", "번", null))
        }
        range.compare?.let {
            Text(it, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(bottom = Gap.Tight))
        }
        Column(Modifier.widthIn(max = ContentMaxWidth), verticalArrangement = Arrangement.spacedBy(Gap.Item)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.Item)) {
                    row.forEach { StatTile(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        val notes = buildList {
            if (r.charges == null) add(Copy.LAB_STATS_CHARGE_NEED)
            if (r.volume == null) add(Copy.LAB_STATS_VOLUME_NEED)
            if (range != StatsRange.TODAY && range != StatsRange.YESTERDAY) add(Copy.LAB_STATS_ARCHIVE_NOTE)
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Item))
            Text(notes.joinToString("\n"), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
        if (v.trend.size > 1 && v.trendTitle != null) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text(v.trendTitle, fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                TrendBars(v.trend)
            }
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Text(if (r.days > 1) "시간대별 화면 켠 시간(모두 더한 값)" else "시간대별 화면 켠 시간", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(Gap.Item))
            HourBars(r.hourlyMs)
        }
        val topApps = remember(r) { UsageTally.top(context, r.apps, 5) }
        val topNotis = remember(r) { UsageTally.top(context, r.notis, 3) }
        val facts = UsageFun.facts(r, topApps.firstOrNull(), topNotis.firstOrNull())
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("재미로 보는 숫자", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                facts.forEach { Text("• $it", color = c.ink, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (topApps.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("많이 연 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                RankList(topApps, "번")
            }
        }
        if (topNotis.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("알림이 많이 온 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                RankList(topNotis, "개")
            }
        }
        // 예전 '배터리 모니터링': 지금 배터리와 마지막 충전 이후(기간과 상관없이 지금 기준)
        Spacer(Modifier.height(Gap.Section))
        Text(Copy.LAB_STATS_BATTERY_TITLE, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        Spacer(Modifier.height(Gap.Item))
        Box(Modifier.widthIn(max = ContentMaxWidth)) { BatterySection() }
    }
}

/** 기간 하나를 센다(작업 스레드): 지난날은 저장된 기록, 오늘은 새로 센다. */
private fun load(context: Context, range: StatsRange, back: Int): StatsView {
    UsageArchive.sync(context)
    val foldable = DeviceKind.isFoldable(context)
    val now = System.currentTimeMillis()
    val today = UsageTally.dayStart()
    val battery = if (ShizukuAccess.status.value == ShizukuAccess.State.READY) BatteryHistory.read(now) else null
    val todayResult by lazy { UsageTally.read(context, today, now, foldable, battery) }
    fun period(from: Long, to: Long): UsageTally.Result {
        val days = UsageArchive.between(context, from, minOf(to, today - 1)).map { it.second }
        val withToday = if (to >= today) days + todayResult else days
        return UsageTally.merge(withToday) ?: UsageTally.read(context, from, minOf(to, now), foldable)
    }
    return when (range) {
        StatsRange.TODAY -> StatsView(todayResult, UsageTally.read(context, today - DAY, today - DAY + (now - today), foldable), emptyList(), null)
        StatsRange.YESTERDAY -> StatsView(period(today - DAY, today - 1), period(today - 2 * DAY, today - DAY - 1), emptyList(), null)
        StatsRange.WEEK -> {
            val from = UsageTally.dayStart(6)
            StatsView(period(from, now), period(UsageTally.dayStart(13), from - 1), dailyTrend(context, from, now) { todayResult }, "날마다 화면 켠 시간")
        }
        StatsRange.MONTH -> {
            val (from, to) = monthRange(back)
            val (pf, pt) = monthRange(back + 1)
            StatsView(period(from, minOf(to, now)), period(pf, pt), dailyTrend(context, from, minOf(to, now)) { todayResult }, "날마다 화면 켠 시간")
        }
        StatsRange.YEAR -> {
            val (from, to) = yearRange(back)
            val (pf, pt) = yearRange(back + 1)
            StatsView(period(from, minOf(to, now)), period(pf, pt), monthlyTrend(from, minOf(to, now), ::period), "달마다 화면 켠 시간")
        }
        StatsRange.ALL -> {
            val first = UsageArchive.firstDay(context) ?: today
            StatsView(period(first, now), null, monthlyTrend(first, now, ::period), "달마다 화면 켠 시간")
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
        out += "${cal.get(Calendar.DAY_OF_MONTH)}일" to ms
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

private fun headlineLabel(range: StatsRange, back: Int, days: Int): String = when (range) {
    StatsRange.TODAY, StatsRange.YESTERDAY -> range.label
    StatsRange.WEEK -> "이번 7일"
    StatsRange.MONTH, StatsRange.YEAR -> periodLabel(range, back) + " · 기록 ${days}일"
    StatsRange.ALL -> "모든 기록 · ${days}일"
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

/** 숫자 칸 하나: 이름, 값, 단위, 기준 대비 변화(▲·▼). */
private data class StatCell(val label: String, val value: String, val unit: String, val delta: String?)

@Composable
private fun StatTile(cell: StatCell, modifier: Modifier) {
    val c = LocalLiquid.current
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cell.label, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            cell.delta?.let { Text(it, color = c.ink.copy(alpha = if (it.startsWith("▲")) 1f else 0.55f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(cell.value, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            if (cell.unit.isNotEmpty()) Text(" ${cell.unit}", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 3.dp))
        }
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
