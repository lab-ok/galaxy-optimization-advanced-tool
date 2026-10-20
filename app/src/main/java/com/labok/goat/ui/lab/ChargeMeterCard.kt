package com.labok.goat.ui.lab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.ChargeMeter
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 충전 실측 용량 카드([ChargeMeter]). 켜기 스위치, 충전 중이면 지금 재는 구간(물 막대), 잰 결과(중앙값 큰 숫자·설계 대비·측정값 분포), 최근 측정 목록.
 * 상태는 상주 서비스가 저장해 두고, 화면은 2초마다 다시 읽는다.
 */
@Composable
internal fun ChargeMeterCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var on by remember { mutableStateOf(ChargeMeter.enabled(context)) }
    var live by remember { mutableStateOf(ChargeMeter.live(context)) }
    var results by remember { mutableStateOf(ChargeMeter.results(context)) }
    var design by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        design = withContext(Dispatchers.IO) { ChargeMeter.design(context) }
        while (true) {
            live = ChargeMeter.live(context)
            results = ChargeMeter.results(context)
            delay(2_000)
        }
    }

    Text("충전 실측 용량", fontWeight = FontWeight.SemiBold, color = c.ink)
    Text(
        "충전하는 동안 실제로 들어간 전류를 쌓아, 배터리가 지금 담을 수 있는 양을 직접 잽니다.",
        color = c.muted,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(Gap.Item))
    LiquidSwitchRow("충전할 때 재기", "켜 두면 GOAT 알림이 하나 뜨고, 충전 중에만 5초마다 전류를 읽습니다.", on, true) {
        on = it
        ChargeMeter.setEnabled(context, it)
    }

    val l = live
    if (on && l != null) {
        Spacer(Modifier.height(Gap.Section))
        LivePanel(l)
    }

    val median = ChargeMeter.median(results)
    if (median != null) {
        Spacer(Modifier.height(Gap.Section))
        ResultPanel(median, results, design)
    } else if (on && l == null) {
        Spacer(Modifier.height(Gap.Item))
        Text("아직 잰 값이 없습니다. 잔량이 20%p 넘게 오르도록 한 번 충전하면 첫 값이 나옵니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    }

    Spacer(Modifier.height(Gap.Item))
    Text(
        "충전 시작 3분, 95% 위, 0~45°C 밖은 빼고 잽니다. 잔량은 정수라 측정마다 몇 % 오차가 있어, 3번 이상 잰 중앙값을 보세요.",
        color = c.muted,
        style = MaterialTheme.typography.labelSmall
    )
}

/** 지금 충전에서 재고 있는 구간: 잔량 범위, 들어간 양, 전류, 한 번으로 치기까지 남은 %p를 물 막대로. */
@Composable
private fun LivePanel(l: ChargeMeter.Live) {
    val c = LocalLiquid.current
    Text("지금 재는 중", fontWeight = FontWeight.SemiBold, color = c.ink, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(Gap.Tight))
    if (l.fromLevel == null) {
        Text("충전 중 ${l.level}% · 충전이 자리 잡고 잔량이 오르면 재기 시작합니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val to = l.toLevel ?: l.fromLevel
    Row(verticalAlignment = Alignment.Bottom) {
        Text(String.format(Locale.KOREA, "%,d", l.chargedMah), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("mAh 들어감", color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        Spacer(Modifier.weight(1f))
        Text(
            "${l.fromLevel}% → $to%" + (l.currentMa?.let { String.format(Locale.KOREA, " · %,dmA", abs(it)) } ?: ""),
            color = c.ink,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
    Spacer(Modifier.height(Gap.Item))
    val need = ChargeMeter.MIN_SPAN
    val fill by animateFloatAsState((l.span / need.toFloat()).coerceIn(0f, 1f), tween(600), label = "span")
    LiquidBar(fill, height = 12.dp, description = "한 번으로 치는 ${need}%p 중 ${l.span}%p")
    Spacer(Modifier.height(Gap.Tight))
    Text(
        if (l.span >= need) "이번 충전은 한 번으로 칩니다. 더 오를수록 정확해집니다." else "${need - l.span}%p 더 오르면 이번 충전을 한 번으로 칩니다.",
        color = c.muted,
        style = MaterialTheme.typography.labelSmall
    )
}

/** 잰 결과: 중앙값 큰 숫자, 설계 대비(알 때만), 측정값 분포, 최근 측정 목록, 기록 지우기. */
@Composable
private fun ResultPanel(median: Int, results: List<ChargeMeter.Result>, design: Int?) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(String.format(Locale.KOREA, "%,d", median), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("mAh", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(
            if (results.size >= 3) "${results.size}번 잰 중앙값" else "${results.size}번 잼 · 3번부터 믿을 만함",
            color = c.muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
    if (design != null) {
        val pct = median * 100 / design
        Spacer(Modifier.height(Gap.Tight))
        LiquidBar((median / design.toFloat()).coerceIn(0f, 1f), height = 12.dp, description = "설계 용량의 $pct%")
        Spacer(Modifier.height(Gap.Tight))
        Text(String.format(Locale.KOREA, "설계 %,dmAh의 약 %d%%", design, pct), color = c.ink, style = MaterialTheme.typography.bodySmall)
    }
    if (results.size >= 2) {
        Spacer(Modifier.height(Gap.Item))
        SessionStrip(results, median, design)
    }
    Spacer(Modifier.height(Gap.Item))
    val fmt = remember { SimpleDateFormat("M월 d일", Locale.KOREA) }
    Column {
        results.takeLast(4).reversed().forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${fmt.format(Date(r.time))} · ${r.fromLevel}% → ${r.toLevel}%", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(String.format(Locale.KOREA, "%,dmAh", r.capacityMah), color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(Gap.Item))
    LiquidButton("측정 기록 지우기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = { ChargeMeter.clear(context) })
}

/**
 * 측정값 분포를 가로 눈금 위에 점으로: 측정 하나가 점 하나(마지막 측정은 진하게), 중앙값은 세로 알약, 설계 용량을 알면 100% 눈금.
 * 측정값 차이는 보통 몇 %라 기둥으로는 높이가 거의 같아 보여서, 좁은 범위를 넓게 펼친 눈금을 쓴다.
 */
@Composable
private fun SessionStrip(results: List<ChargeMeter.Result>, median: Int, design: Int?) {
    val c = LocalLiquid.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = c.muted)
    val values = results.map { it.capacityMah }
    // 눈금 범위: 설계를 알면 설계의 %로(측정값과 100%가 함께 보이게 5% 단위로 넓힘), 모르면 측정값 앞뒤로 조금 넓혀 mAh로
    val (lo, hi, ticks) = if (design != null) {
        val minPct = (values.min() * 100 / design).coerceAtMost(100)
        val maxPct = (values.max() * 100 / design).coerceAtLeast(100)
        val a = (minPct - 5) / 5 * 5
        val b = (maxPct + 9) / 5 * 5
        Triple(design * a / 100f, design * b / 100f, (a..b step 5).map { design * it / 100f to "$it%" })
    } else {
        val a = values.min() * 0.97f
        val b = values.max() * 1.03f
        Triple(a, b, listOf(a, (a + b) / 2, b).map { it to String.format(Locale.KOREA, "%,d", it.toInt()) })
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = "측정 ${results.size}번의 분포, 중앙값 ${median}mAh" }
    ) {
        val pad = 12.dp.toPx()
        val trackY = 22.dp.toPx()
        fun x(v: Float) = pad + (size.width - 2 * pad) * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        // 눈금 막대
        drawRoundRect(c.ink.copy(alpha = 0.10f), Offset(pad, trackY - 3.dp.toPx()), Size(size.width - 2 * pad, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        // 측정 범위(가장 작은 값 ~ 가장 큰 값)
        drawRoundRect(
            c.ink.copy(alpha = 0.22f),
            Offset(x(values.min().toFloat()) - 6.dp.toPx(), trackY - 6.dp.toPx()),
            Size(x(values.max().toFloat()) - x(values.min().toFloat()) + 12.dp.toPx(), 12.dp.toPx()),
            CornerRadius(6.dp.toPx())
        )
        ticks.forEach { (v, t) ->
            val tx = x(v)
            val emph = design != null && t == "100%"
            drawLine(c.ink.copy(alpha = if (emph) 0.55f else 0.2f), Offset(tx, trackY + 8.dp.toPx()), Offset(tx, trackY + 13.dp.toPx()), 1.dp.toPx())
            val layout = measurer.measure(if (emph) "설계 $t" else t, label)
            val lx = (tx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(lx, trackY + 16.dp.toPx()))
        }
        // 측정 하나가 점 하나(겹치면 진해진다), 마지막 측정은 진하게
        results.forEachIndexed { i, r ->
            val last = i == results.lastIndex
            drawCircle(c.ink.copy(alpha = if (last) 0.95f else 0.45f), radius = (if (last) 5.5f else 4.5f) * density, center = Offset(x(r.capacityMah.toFloat()), trackY))
        }
        // 중앙값: 세로 알약
        val mx = x(median.toFloat())
        drawRoundRect(c.ink, Offset(mx - 2.dp.toPx(), trackY - 13.dp.toPx()), Size(4.dp.toPx(), 26.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}
