package com.labok.goat.ui.lab

import androidx.compose.foundation.layout.Column
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
import com.labok.goat.data.lab.BatteryStatsReader
import com.labok.goat.data.lab.BatteryStatsReader.SinceCharge
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.Gap
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

/**
 * 배터리 모니터링의 '마지막 충전 이후' 카드 내용: 쓴 양, 화면 켜짐·꺼짐 시간과 소모, 이 패턴의 예상 시간, 앱별 순위, 항목별 소모.
 * 시스템 배터리 통계를 Shizuku로 읽는다([BatteryStatsReader]). 화면을 여는 동안 1분마다 다시 읽는다.
 */
@Composable
internal fun BatterySinceCharge(level: Int?, charging: Boolean) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsState()
    var stats by remember { mutableStateOf<SinceCharge?>(null) }
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var cycles by remember { mutableStateOf(BatteryCycles.cycles(context)) }
    var history by remember { mutableStateOf<List<BatteryHistory.Point>?>(null) }
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
            history = withContext(Dispatchers.IO) { BatteryHistory.read() }
            stats = s
            loaded = true
            delay(60_000)
        }
    }

    Text("마지막 충전 이후", fontWeight = FontWeight.SemiBold, color = c.ink)
    val s = stats
    if (shizuku != ShizukuAccess.State.READY) {
        Text("Shizuku를 연결하면 시스템 배터리 통계로 마지막 충전 이후 사용량과 앱별 순위를 보여 줍니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    if (s == null) {
        Text(if (loaded) "시스템 배터리 통계를 불러오지 못했습니다." else "시스템 배터리 통계를 불러오는 중…", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val since = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(s.startClock))
    Text("${since}부터 · 배터리 사용 ${duration(s.onBatteryMs)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    // 배터리 물통: 지금 잔량만큼 물이 차 있고, 마지막 충전 때 잔량까지는 쓴 만큼 옅은 물 자국이 남는다
    if (level != null) {
        val start = if (charging) null else (level + (s.usedPctLow + s.usedPctHigh + 1) / 2).coerceAtMost(100)
        LiquidBatteryTank(level, start, charging)
        Spacer(Modifier.height(Gap.Item))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(if (s.usedPctLow == s.usedPctHigh) "${s.usedPctLow}" else "${s.usedPctLow}~${s.usedPctHigh}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("% 사용", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    }
    if (charging) Text("현재 충전 중입니다. 충전기를 분리하면 시스템이 새로 집계합니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    // 마지막 충전 이후 기록 그래프(시스템 배터리 기록): 잔량 흐름과 0%까지의 예측, 충전·화면 켜짐·깨어 있음, 시간대별 사용량
    history?.let { h ->
        Spacer(Modifier.height(Gap.Card))
        Text("마지막 충전 이후 기록", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text("점선은 지금처럼 사용할 때 0%가 되는 예상 시점입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        // 예측은 그래프와 같은 자료(마지막 충전 이후 평균 소모)로 계산한다
        val now = System.currentTimeMillis()
        val remain = if (level != null && !charging) BatteryHistory.drainPerHour(h, now)?.let { (level / it * 3_600_000L).toLong() } else null
        BatteryHistorySection(h, now, remain)
    }
    Spacer(Modifier.height(Gap.Item))
    UseRow("화면 켜짐", s.screenOnMs, s.screenOnPct)
    UseRow("화면 꺼짐(대기)", s.screenOffMs, s.screenOffPct)
    // 시간과 소모를 화면 켜짐(진한 물)·꺼짐(빈 칸) 몫으로 나눠 물 막대로 보여 준다
    if (s.onBatteryMs > 0) {
        Spacer(Modifier.height(Gap.Item))
        val timeOn = s.screenOnMs.toFloat() / s.onBatteryMs
        SplitBar("시간", timeOn)
        val drained = s.screenOnPct + s.screenOffPct
        if (drained > 0) {
            Spacer(Modifier.height(Gap.Tight))
            SplitBar("사용량", s.screenOnPct.toFloat() / drained)
        }
    }

    // 이 패턴이면: 완충으로 몇 시간, 그중 화면 켜짐 몇 시간, 지금 잔량으로 몇 시간 더
    BatteryStatsReader.fullChargeMs(s)?.let { full ->
        Spacer(Modifier.height(Gap.Item))
        Text("지금처럼 사용하면", fontWeight = FontWeight.SemiBold, color = c.ink, style = MaterialTheme.typography.bodyMedium)
        Line("완충 시", "약 ${duration(full)}")
        if (s.onBatteryMs > 0) Line("그중 화면 켜짐", "약 ${duration(full * s.screenOnMs / s.onBatteryMs)}")
        if (level != null && !charging) Line("현재 잔량 $level%로", "약 ${duration(full * level / 100)} 더 사용")
    }

    if (s.apps.isNotEmpty()) {
        Spacer(Modifier.height(Gap.Card))
        Text("앱별 사용량", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text("시스템이 추정한 사용량 순입니다. 막대는 전체 사용량에서 그 앱이 차지하는 비율입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        val total = s.computedMah.takeIf { it > 0 } ?: s.apps.sumOf { it.mah }.coerceAtLeast(1.0)
        val list = if (showAll) s.apps else s.apps.take(TOP_APPS)
        list.forEach { a ->
            val share = (a.mah / total).toFloat().coerceIn(0f, 1f)
            val detail = listOfNotNull(
                a.frontMs.takeIf { it >= 60_000 }?.let { "포그라운드 ${duration(it)}" },
                a.backMs.takeIf { it >= 60_000 }?.let { "백그라운드 ${duration(it)}" }
            ).joinToString(" · ")
            ShareRow(names[a.uid] ?: "uid ${a.uid}", share, detail)
        }
        if (s.apps.size > TOP_APPS) {
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(if (showAll) "상위 ${TOP_APPS}개만 보기" else "모두 보기(${s.apps.size}개)", modifier = Modifier.fillMaxWidth(), primary = false, onClick = { showAll = !showAll })
        }
    }

    if (s.system.isNotEmpty()) {
        Spacer(Modifier.height(Gap.Card))
        Text("항목별 사용량", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text("기기 전체 사용량을 항목별로 나눈 값입니다. 앱 사용량도 포함됩니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        val total = s.computedMah.takeIf { it > 0 } ?: s.system.values.sum()
        s.system.entries.sortedByDescending { it.value }.forEach { (k, v) ->
            ShareRow(BatteryStatsReader.systemLabel(k), (v / total).toFloat().coerceIn(0f, 1f), "")
        }
    }

    Spacer(Modifier.height(Gap.Card))
    PastCycles(cycles)
}

/** 지난 충전 주기(최근 것부터): 날짜, 배터리로 쓴 시간·화면 켜짐·쓴 양, 완충 환산, 많이 쓴 앱 셋. */
@Composable
private fun PastCycles(cycles: List<BatteryCycles.Cycle>) {
    val c = LocalLiquid.current
    Text("지난 충전 주기", fontWeight = FontWeight.SemiBold, color = c.ink)
    if (cycles.isEmpty()) {
        Text("충전 후 충전기를 분리하면 직전 주기가 여기에 기록됩니다(최근 ${BatteryCycles.KEEP}회). 15분 기록을 켜 두면 빠짐없이 기록됩니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    Text("충전할 때마다 직전까지의 사용량을 요약합니다. 완충 환산은 그 주기처럼 사용할 때 100%를 모두 쓰는 데 걸리는 시간입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    // 기둥 높이는 완충 환산 시간, 진한 물은 그중 화면 켜짐 몫
    LiquidCycleColumns(cycles)
    val fmt = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)
    cycles.forEach { cy ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("${fmt.format(Date(cy.start))}부터", color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val full = if (cy.usedPct >= 3) " · 완충 환산 ${duration(cy.onBatteryMs * 100 / cy.usedPct)}" else ""
            Text("배터리 사용 ${duration(cy.onBatteryMs)} · 화면 켜짐 ${duration(cy.screenOnMs)} · ${cy.usedPct}% 사용$full", color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (cy.apps.isNotEmpty()) Text(cy.apps.joinToString(" · ") { (n, share) -> "$n ${percent(share)}" }, color = c.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "시간 · 켜짐 30% · 꺼짐 70%" 한 줄과 물 막대(진한 물 = 화면 켜짐 몫). */
@Composable
private fun SplitBar(name: String, onShare: Float) {
    val c = LocalLiquid.current
    val on = (onShare * 100).roundToInt().coerceIn(0, 100)
    Text("$name · 화면 켜짐 $on% · 꺼짐 ${100 - on}%", color = c.muted, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(Gap.Tight))
    LiquidBar(onShare, height = 12.dp, description = "$name 중 화면 켜짐 $on%")
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

/** 이름·몫(%)·막대, 아래에 작은 설명 한 줄. */
@Composable
private fun ShareRow(name: String, share: Float, detail: String) {
    val c = LocalLiquid.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(percent(share), color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(Gap.Tight))
        LiquidBar(share, height = 6.dp, description = "$name ${percent(share)}")
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
