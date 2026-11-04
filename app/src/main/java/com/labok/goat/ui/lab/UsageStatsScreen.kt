package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.lab.UsageTally
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 실험실 사용 통계(11-04 소장님 지정). 시안 1번 '숫자 타일'에 6번 '순위표'의 앱 순위를 붙였다.
 * 오늘·어제·7일을 고르면 [UsageTally]가 Android 기록에서 다시 센다.
 */

private enum class StatsRange(val label: String) { TODAY("오늘"), YESTERDAY("어제"), WEEK("7일") }

@Composable
internal fun UsageStatsScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.TODAY) }
    var result by remember { mutableStateOf<UsageTally.Result?>(null) }
    LaunchedEffect(range) {
        result = null
        val (from, to) = when (range) {
            StatsRange.TODAY -> UsageTally.dayStart() to System.currentTimeMillis()
            StatsRange.YESTERDAY -> UsageTally.dayStart(1) to UsageTally.dayStart() - 1
            StatsRange.WEEK -> UsageTally.dayStart(6) to System.currentTimeMillis()
        }
        result = withContext(Dispatchers.IO) { UsageTally.read(context, from, to, DeviceKind.isFoldable(context)) }
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
            LiquidChips(options = StatsRange.entries, selected = range, label = { it.label }, onSelect = { range = it })
        }
        val r = result
        if (r == null) {
            Spacer(Modifier.height(Gap.Section))
            LiquidLoader(Copy.LAB_STATS_LOADING)
            return@Column
        }
        if (!r.usageAccess) {
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text(Copy.LAB_STATS_NO_ACCESS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.LAB_STATS_NO_ACCESS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Gap.Card))
        }
        val tiles = buildList {
            r.unfolds?.let { add(Triple("펼친 횟수", "$it", "번")) }
            add(Triple("잠금 해제", "${r.unlocks}", "번"))
            add(Triple("화면 켬", "${r.screenOn}", "번"))
            add(Triple("화면 켠 시간", statsDuration(r.screenMs), ""))
            add(Triple("앱 실행", "${r.appOpens}", "번"))
            add(Triple("알림", "${r.notifications}", "개"))
            add(Triple("화면 회전", "${r.rotations}", "번"))
            add(Triple("충전기 꽂음", r.charges?.toString() ?: "-", if (r.charges != null) "번" else ""))
            add(Triple("볼륨 버튼", r.volume?.toString() ?: "-", if (r.volume != null) "번" else ""))
            if (r.boots > 0) add(Triple("재부팅", "${r.boots}", "번"))
        }
        Column(Modifier.widthIn(max = ContentMaxWidth), verticalArrangement = Arrangement.spacedBy(Gap.Item)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.Item)) {
                    row.forEach { (label, value, unit) -> StatTile(label, value, unit, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        val notes = buildList {
            if (r.chargeStartAvg != null) add("충전기는 평균 ${r.chargeStartAvg}%에서 꽂았습니다.")
            if (r.charges == null) add(Copy.LAB_STATS_CHARGE_NEED)
            if (r.volume == null) add(Copy.LAB_STATS_VOLUME_NEED)
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Item))
            Text(notes.joinToString("\n"), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Text("시간대별 화면 켠 시간", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(Gap.Item))
            HourBars(r.hourlyMs)
        }
        if (r.topApps.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("많이 연 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                RankList(r.topApps, "번")
            }
        }
        if (r.topNotifications.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text("알림이 많이 온 앱", fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(Modifier.height(Gap.Item))
                RankList(r.topNotifications, "개")
            }
        }
    }
}

/** 큰 숫자 칸 하나. */
@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    val c = LocalLiquid.current
    GlassCard(modifier = modifier) {
        Text(label, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            if (unit.isNotEmpty()) Text(" $unit", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** 0~23시 막대. 진하고 높을수록 그 시간에 화면을 오래 켰다. */
@Composable
private fun HourBars(hourly: LongArray) {
    val c = LocalLiquid.current
    val measurer = rememberTextMeasurer()
    val max = (hourly.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val label = TextStyle(fontFamily = GalaxyFont, fontSize = (10f / fontScale).sp, color = c.muted)
        val chartH = size.height - 16.dp.toPx()
        val slot = size.width / 24f
        hourly.forEachIndexed { h, ms ->
            val k = ms.toFloat() / max
            val barH = (chartH * k).coerceAtLeast(if (ms > 0) 2.dp.toPx() else 0f)
            drawRoundRect(c.line.copy(alpha = 0.45f), Offset(h * slot + 1.dp.toPx(), 0f), Size(slot - 3.dp.toPx(), chartH), CornerRadius(2.dp.toPx()))
            if (barH > 0f) {
                drawRoundRect(c.ink.copy(alpha = 0.35f + 0.65f * k), Offset(h * slot + 1.dp.toPx(), chartH - barH), Size(slot - 3.dp.toPx(), barH), CornerRadius(2.dp.toPx()))
            }
        }
        listOf(0, 6, 12, 18).forEach { h ->
            val l = measurer.measure("${h}시", label)
            drawText(l, topLeft = Offset(h * slot, size.height - l.size.height))
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

private fun statsDuration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}
