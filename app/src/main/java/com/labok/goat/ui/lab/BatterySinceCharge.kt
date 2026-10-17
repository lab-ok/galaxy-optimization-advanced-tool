package com.labok.goat.ui.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.BatteryCycles
import com.labok.goat.data.lab.BatteryHistory
import com.labok.goat.data.lab.BatteryMonitor
import com.labok.goat.data.lab.BatteryStatsReader
import com.labok.goat.data.lab.BatteryStatsReader.SinceCharge
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.text.Copy
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 순위에 처음 보여 줄 앱 수. 나머지는 '모두 보기'로 편다. */
private const val TOP_APPS = 10

/** 충전 기록에 보여 줄 최근 충전 수. */
private const val RECENT_CHARGES = 5

private enum class RankTab(val label: String) { APPS("앱별"), PARTS("항목별") }

/**
 * 배터리 모니터링의 시스템 통계·기록 카드들(지금 배터리 카드 아래). 카드마다 한 가지만 보여 준다.
 * 1) 마지막 충전 이후: 쓴 양, 화면 켜짐·대기, 지금처럼 쓰면 얼마나 가는지
 * 2) 시간대별 사용량: 화면 켜짐·대기로 나눈 물기둥([BatteryHistorySection])
 * 3) 충전 기록: 몇 %에서 몇 %까지 얼마나 걸려 충전했는지
 * 4) 사용량 순위: 앱별·항목별 탭
 * 5) 지난 충전 주기와 자동 기록 스위치
 * 시스템 배터리 통계([BatteryStatsReader])와 기록([BatteryHistory])을 Shizuku로 읽는다. 화면을 여는 동안 1분마다 다시 읽는다.
 */
@Composable
internal fun BatterySinceCharge(level: Int?, charging: Boolean) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsState()
    var stats by remember { mutableStateOf<SinceCharge?>(null) }
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var cycles by remember { mutableStateOf(BatteryCycles.cycles(context)) }
    var history by remember { mutableStateOf<List<BatteryHistory.Point>?>(null) }
    var historyLoaded by remember { mutableStateOf(false) }
    // 시스템 배터리 기록은 통계와 따로 읽는다(기록이 길면 오래 걸려 통계 표시를 늦추지 않게)
    LaunchedEffect(shizuku) {
        if (shizuku != ShizukuAccess.State.READY) return@LaunchedEffect
        while (true) {
            history = withContext(Dispatchers.IO) { BatteryHistory.read() }
            historyLoaded = true
            delay(60_000)
        }
    }
    LaunchedEffect(shizuku) {
        if (shizuku != ShizukuAccess.State.READY) return@LaunchedEffect
        while (true) {
            val s = withContext(Dispatchers.IO) { BatteryStatsReader.read() }
            val n = withContext(Dispatchers.IO) { s?.apps?.associate { it.uid to BatteryStatsReader.uidName(context, it.uid) }.orEmpty() }
            withContext(Dispatchers.IO) {
                s?.let { BatteryCycles.update(context, it) { uid -> n[uid] ?: BatteryStatsReader.uidName(context, uid) } }
            }
            names = n
            cycles = BatteryCycles.cycles(context)
            stats = s
            loaded = true
            delay(60_000)
        }
    }

    val s = stats
    Card("마지막 충전 이후") {
        if (shizuku != ShizukuAccess.State.READY) {
            Text("Shizuku를 연결하면 시스템 배터리 통계로 마지막 충전 이후 사용량과 앱별 순위를 보여 줍니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            return@Card
        }
        if (s == null) {
            if (loaded) Text("시스템 배터리 통계를 불러오지 못했습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            else LiquidLoader("시스템 배터리 통계를 불러오는 중…", Modifier.padding(vertical = 8.dp))
            return@Card
        }
        SinceChargeSummary(s, level, charging)
    }
    if (shizuku != ShizukuAccess.State.READY || s == null) return
    val now = System.currentTimeMillis()

    Card("시간대별 사용량") {
        when (val h = history) {
            null -> if (historyLoaded) Text("시스템 배터리 기록을 불러오지 못했습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            else LiquidLoader("시스템 배터리 기록을 불러오는 중…", Modifier.padding(vertical = 8.dp))
            else -> BatteryHistorySection(h, now, s.startClock)
        }
    }

    history?.let { h ->
        // 충전 기록은 기록 전체에서 찾는다(마지막 충전 이후의 시작이 된 충전도 보이게)
        val charges = remember(h, now / 60_000) { BatteryHistory.charges(h, now).takeLast(RECENT_CHARGES).reversed() }
        Card("충전 기록") {
            if (charges.isEmpty()) {
                Text("시스템 기록에 남은 충전이 아직 없습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                charges.forEach { ChargeRow(it) }
            }
        }
    }

    if (s.apps.isNotEmpty() || s.system.isNotEmpty()) {
        Card("사용량 순위") { UsageRanking(s, names) }
    }

    Card("지난 충전 주기") { PastCycles(cycles) }
}

/** 카드 하나: 제목, 설명(있으면), 내용. 카드 사이는 [Gap.Card]만큼 띄운다. */
@Composable
private fun Card(title: String, desc: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalLiquid.current
    Spacer(Modifier.height(Gap.Card))
    GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
        desc?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(Gap.Item))
        content()
    }
}

/** 마지막 충전 이후 요약: 기준 시각, 배터리 물통, 쓴 양, 화면 켜짐·대기, 지금처럼 쓰면. */
@Composable
private fun ColumnScope.SinceChargeSummary(s: SinceCharge, level: Int?, charging: Boolean) {
    val c = LocalLiquid.current
    val since = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(s.startClock))
    Text("${since}부터 · 배터리 사용 ${duration(s.onBatteryMs)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(if (s.usedPctLow == s.usedPctHigh) "${s.usedPctLow}" else "${s.usedPctLow}~${s.usedPctHigh}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("% 사용", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    }
    if (charging) Text("현재 충전 중입니다. 충전기를 분리하면 시스템이 새로 집계합니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    UseRow("화면 켜짐", s.screenOnMs, s.screenOnPct)
    UseRow("화면 꺼짐(대기)", s.screenOffMs, s.screenOffPct)
    // 이 패턴이면: 지금 잔량으로 몇 시간 더, 완충으로 몇 시간
    BatteryStatsReader.fullChargeMs(s)?.let { full ->
        Spacer(Modifier.height(Gap.Card))
        Text("지금처럼 사용하면", fontWeight = FontWeight.SemiBold, color = c.ink, style = MaterialTheme.typography.bodyMedium)
        if (level != null && !charging) Line("현재 잔량 $level%로", "약 ${duration(full * level / 100)} 더 사용")
        Line("완충 시", "약 ${duration(full)}")
    }
}

/** 충전 한 번: 시각과 걸린 시간, 몇 %→몇 %, 물 막대(진한 물 = 꽂을 때 잔량, 옅은 물 = 충전으로 오른 몫). */
@Composable
private fun ChargeRow(ch: BatteryHistory.Charge) {
    val c = LocalLiquid.current
    val day = SimpleDateFormat("M/d a h:mm", Locale.KOREA)
    val time = SimpleDateFormat("a h:mm", Locale.KOREA)
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).let { it.format(Date(ch.start)) == it.format(Date(ch.end)) }
    val range = "${day.format(Date(ch.start))}~" + if (ch.ongoing) "지금" else if (sameDay) time.format(Date(ch.end)) else day.format(Date(ch.end))
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${ch.startLevel}% → ${ch.endLevel}%", color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text((if (ch.ongoing) "충전 중 · " else "") + duration(ch.durationMs), color = c.ink, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Gap.Tight))
        LiquidBar(ch.startLevel / 100f, height = 8.dp, ghost = ch.endLevel / 100f, description = "${ch.startLevel}%에서 ${ch.endLevel}%까지 충전")
        Spacer(Modifier.height(Gap.Tight))
        val perHour = if (ch.durationMs >= 5 * 60_000L && ch.gained > 0) String.format(Locale.KOREA, " · 시간당 +%.0f%%", ch.gained * 3_600_000f / ch.durationMs) else ""
        Text("$range$perHour", color = c.muted, style = MaterialTheme.typography.labelSmall)
    }
}

/** 사용량 순위: 앱별·항목별 탭. 앱 막대는 진한 물이 포그라운드, 옅은 물이 백그라운드 몫(사용 시간 비율로 나눈 추정)이다. */
@Composable
private fun UsageRanking(s: SinceCharge, names: Map<Int, String>) {
    val c = LocalLiquid.current
    var tab by remember { mutableStateOf(if (s.apps.isNotEmpty()) RankTab.APPS else RankTab.PARTS) }
    var showAll by remember { mutableStateOf(false) }
    LiquidChips(RankTab.entries.toList(), tab, { it.label }, { tab = it })
    Spacer(Modifier.height(Gap.Item))
    when (tab) {
        RankTab.APPS -> {
            Text("진한 부분은 포그라운드, 연한 부분은 백그라운드입니다(사용 시간 비율로 나눈 추정).", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            val total = s.computedMah.takeIf { it > 0 } ?: s.apps.sumOf { it.mah }.coerceAtLeast(1.0)
            val list = if (showAll) s.apps else s.apps.take(TOP_APPS)
            list.forEach { a ->
                val share = (a.mah / total).toFloat().coerceIn(0f, 1f)
                val time = a.frontMs + a.backMs
                val front = if (time > 0) a.frontMs.toFloat() / time else 1f
                val detail = listOfNotNull(
                    a.frontMs.takeIf { it >= 60_000 }?.let { "포그라운드 ${duration(it)} · ${percent(share * front)}" },
                    a.backMs.takeIf { it >= 60_000 }?.let { "백그라운드 ${duration(it)} · ${percent(share * (1 - front))}" }
                ).joinToString("  ")
                ShareRow(names[a.uid] ?: "uid ${a.uid}", share, detail, dark = share * front)
            }
            if (s.apps.size > TOP_APPS) {
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(if (showAll) "상위 ${TOP_APPS}개만 보기" else "모두 보기(${s.apps.size}개)", modifier = Modifier.fillMaxWidth(), primary = false, onClick = { showAll = !showAll })
            }
        }
        RankTab.PARTS -> {
            Text("기기 전체 사용량을 부품별로 나눈 값입니다(앱 사용량 포함).", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            val total = s.computedMah.takeIf { it > 0 } ?: s.system.values.sum()
            s.system.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                val share = (v / total).toFloat().coerceIn(0f, 1f)
                ShareRow(BatteryStatsReader.systemLabel(k), share, "", dark = share)
            }
        }
    }
}

/** 지난 충전 주기(최근 것부터): 날짜, 배터리로 쓴 시간·화면 켜짐·쓴 양, 완충 환산, 많이 쓴 앱 셋. */
@Composable
private fun PastCycles(cycles: List<BatteryCycles.Cycle>) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var auto by remember { mutableStateOf(BatteryMonitor.logging(context)) }
    // 시스템 통계는 충전할 때마다 새로 시작해 지난 주기가 지워진다. 이 화면을 열지 않은 채 충전하면 놓치므로, 15분마다 확인해 남긴다
    LiquidSwitchRow(Copy.LAB_BATTERY_LOG, Copy.LAB_BATTERY_LOG_BODY, auto, true) { want ->
        auto = want
        BatteryMonitor.setLogging(context, want)
    }
    Spacer(Modifier.height(Gap.Item))
    if (cycles.isEmpty()) {
        Text("충전 후 충전기를 분리하면 직전 주기가 여기에 기록됩니다(최근 ${BatteryCycles.KEEP}회).", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    Text("기둥 높이는 그 주기처럼 쓸 때 100%를 다 쓰는 데 걸리는 시간이고, 진한 물은 그중 화면 켜짐입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    // 기둥 높이는 완충 환산 시간, 진한 물은 그중 화면 켜짐 몫
    LiquidCycleColumns(cycles)
    val fmt = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)
    // 기둥 그래프에 이미 다 보이므로 글 목록은 최근 세 번만 펼쳐 두고 나머지는 '모두 보기'로 편다
    var all by remember { mutableStateOf(false) }
    (if (all) cycles else cycles.take(3)).forEach { cy ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("${fmt.format(Date(cy.start))}부터", color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("배터리 사용 ${duration(cy.onBatteryMs)} · 화면 켜짐 ${duration(cy.screenOnMs)} · ${cy.usedPct}% 사용", color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (cy.apps.isNotEmpty()) Text(cy.apps.joinToString(" · ") { (n, share) -> "$n ${percent(share)}" }, color = c.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (cycles.size > 3) {
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(if (all) "최근 3번만 보기" else "모두 보기(${cycles.size}번)", modifier = Modifier.fillMaxWidth(), primary = false, onClick = { all = !all })
    }
}

@Composable
private fun UseRow(name: String, ms: Long, pct: Int) {
    val per = BatteryStatsReader.perHour(pct, ms)
    Line(name, "${duration(ms)} · $pct%" + (per?.let { String.format(Locale.KOREA, " · 시간당 %.1f%%", it) } ?: ""))
}

@Composable
private fun Line(name: String, value: String) {
    val c = LocalLiquid.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 이름·몫(%)·막대(옅은 물 = 전체 몫, 진한 물 = 그중 [dark]), 아래에 작은 설명 한 줄. */
@Composable
private fun ShareRow(name: String, share: Float, detail: String, dark: Float = share) {
    val c = LocalLiquid.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(percent(share), color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(Gap.Tight))
        LiquidBar(dark, height = 6.dp, ghost = share, description = "$name ${percent(share)}")
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Tight))
            Text(detail, color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun percent(share: Float): String = if (share > 0f && share < 0.01f) "1% 미만" else "${(share * 100).roundToInt()}%"

/** "3시간 5분", "45분", "1분 미만". */
internal fun duration(ms: Long): String {
    val min = ms / 60_000
    val h = min / 60
    val m = min % 60
    return when {
        min < 1 -> "1분 미만"
        h == 0L -> "${m}분"
        m == 0L -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}
