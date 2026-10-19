package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.BatteryHistory
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 마지막 충전 이후 시간대별 사용량(시안 7 '켜짐·대기 나눈 기둥'). 시스템 배터리 기록([BatteryHistory])으로 그린다.
 * 기둥 높이는 그 시간대에 쓴 잔량, 아래 진한 물은 화면을 켜고 쓴 몫, 위 옅은 물은 화면이 꺼진 채(대기) 줄어든 몫이다.
 * 대기 중에 배터리가 새는 시간대가 바로 보이게 하는 것이 목적이다. 기둥을 누르면 그 시간대의 자세한 값을 보여 준다.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatteryHistorySection(all: List<BatteryHistory.Point>, now: Long, since: Long?) {
    val c = LocalLiquid.current
    // 시스템 기록은 통계 기준(마지막 충전 이후)보다 앞에서 시작할 수 있다(실기: 기록 10/16 18:24, 통계 10/17 00:29).
    // 카드 제목과 맞게 기준 시각부터 자른다
    val points = remember(all, since) { BatteryHistory.since(all, since) }
    if (points.size < 2) {
        Text("시스템 배터리 기록이 아직 충분하지 않습니다. 잠시 사용한 뒤 다시 열어 주세요.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val start = points.first().time
    val bucketMs = BatteryHistory.bucketMs(now - start)
    val buckets = remember(points, now / 60_000) { BatteryHistory.buckets(points, now, bucketMs, from = floorHour(start)) }
    var selected by remember(buckets.size) { mutableStateOf<Int?>(null) }
    LiquidBucketColumns(buckets, selected, { selected = it })
    Spacer(Modifier.height(Gap.Item))
    // 범례: 기둥의 세 가지 물(가장 옅은 기둥은 충전한 시간대)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(c.ink, "화면 켜짐에 쓴 양")
        Legend(c.ink.copy(alpha = 0.3f), "대기(화면 꺼짐)에 쓴 양")
        Legend(c.ink.copy(alpha = 0.12f), "충전한 시간대")
    }
    Spacer(Modifier.height(Gap.Item))
    val hourFmt = remember { SimpleDateFormat("a h시", Locale.KOREA) }
    val pick = selected?.let { buckets.getOrNull(it) }
    if (pick == null) {
        Text("기둥 하나가 ${bucketMs / 3_600_000}시간입니다. 누르면 그 시간대 사용량이 나옵니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    } else {
        val range = "${hourFmt.format(Date(pick.start))}~${hourFmt.format(Date(pick.end)).substringAfter(' ')}"
        Text(
            "$range · ${pctText(pick.usedPct)} 사용" + if (pick.chargedPct >= 0.5f) {
                " · 충전 +${pick.chargedPct.roundToInt()}%"
            } else if (pick.charging) {
                " · 충전"
            } else {
                ""
            },
            color = c.ink,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "화면 켜짐에 ${pctText(pick.screenPct)} · 대기에 ${pctText(pick.standbyPct)} · 화면 켜짐 ${duration(pick.screenMs)}",
            color = c.muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = LocalLiquid.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Canvas(Modifier.width(14.dp).height(14.dp)) { drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx())) }
        Text(label, color = c.ink, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun floorHour(t: Long): Long {
    val zone = TimeZone.getDefault()
    val off = zone.getOffset(t)
    return (t + off) / 3_600_000L * 3_600_000L - off
}

private fun pctText(v: Float) = when {
    v <= 0f -> "0%"
    v < 1f -> "1% 미만"
    else -> "${v.roundToInt()}%"
}

/**
 * 켜짐·대기 나눈 물기둥. 기둥 전체는 옅은 물(그 칸에서 쓴 잔량), 그 아래 진한 물이 화면 켜짐 몫이며 진한 물 윗면이 출렁인다.
 * 충전한 칸은 바닥까지 아주 옅은 물을 깐다. 기둥은 왼쪽부터 차례로 차오른다.
 */
@Composable
private fun LiquidBucketColumns(buckets: List<BatteryHistory.Bucket>, selected: Int?, onSelect: (Int?) -> Unit) {
    val c = LocalLiquid.current
    val t = rememberWaveTime()
    if (buckets.isEmpty()) return
    val top = (ceil(buckets.maxOf { it.usedPct } / 5f) * 5f).coerceAtLeast(5f)
    val fills = buckets.mapIndexed { i, b -> rememberFill(b.usedPct / top, delayMs = 30L * i) }
    val measurer = rememberTextMeasurer()
    val hourFmt = remember { SimpleDateFormat("a h시", Locale.KOREA) }
    val small = TextStyle(fontFamily = GalaxyFont, fontSize = 10.sp, color = c.muted)
    val labelEvery = ((buckets.size + 3) / 4).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "시간대별 배터리 사용량 그래프. 기둥을 누르면 그 시간대 사용량을 보여 줍니다." }
            .pointerInput(buckets.size, selected) {
                detectTapGestures { p ->
                    val axisW = 32.dp.toPx()
                    val i = (p.x / ((size.width - axisW) / buckets.size)).toInt().coerceIn(0, buckets.size - 1)
                    onSelect(if (i == selected) null else i)
                }
            }
    ) {
        val labelH = 16.dp.toPx()
        val axisW = 32.dp.toPx()
        val chartH = size.height - labelH
        val chartW = size.width - axisW
        val slotW = chartW / buckets.size
        val colW = (slotW * 0.64f).coerceAtMost(20.dp.toPx())
        listOf(1f, 0.5f).forEach { f ->
            val y = chartH * (1f - f)
            drawLine(c.line, Offset(0f, y), Offset(chartW, y), 1.dp.toPx())
            val v = measurer.measure("${(top * f).roundToInt()}%", small)
            drawText(v, topLeft = Offset(chartW + 4.dp.toPx(), (y - v.size.height / 2f).coerceAtLeast(0f)))
        }
        drawLine(c.line, Offset(0f, chartH), Offset(chartW, chartH), 1.dp.toPx())
        buckets.forEachIndexed { i, b ->
            val x = slotW * i + (slotW - colW) / 2
            val dim = selected != null && selected != i
            val ink = if (dim) c.ink.copy(alpha = 0.3f) else c.ink
            val r = CornerRadius(colW / 2.6f)
            if (b.charging) drawRoundRect(c.ink.copy(alpha = if (dim) 0.05f else 0.12f), topLeft = Offset(x, 0f), size = Size(colW, chartH), cornerRadius = r)
            val colH = chartH * fills[i].coerceIn(0f, 1f)
            if (colH > 0.5f) {
                val y0 = chartH - colH
                val col = Path().apply { addRoundRect(RoundRect(x, y0, x + colW, chartH, r, r, CornerRadius.Zero, CornerRadius.Zero)) }
                drawPath(col, c.ink.copy(alpha = 0.3f * (if (dim) 0.5f else 1f)))
                val onShare = if (b.usedPct > 0f) (b.screenPct / b.usedPct).coerceIn(0f, 1f) else 0f
                val onH = colH * onShare
                if (onH > 0.5f) {
                    val surface = chartH - onH
                    val amp = 1.4.dp.toPx().coerceAtMost(onH / 3f)
                    val water = Path().apply {
                        moveTo(x, chartH)
                        var dx = 0f
                        while (dx <= colW) {
                            lineTo(x + dx, surface + amp * sin(dx / (colW / 3f) + t * 2.2f + i * 0.7f))
                            dx += 1.5.dp.toPx()
                        }
                        lineTo(x + colW, chartH)
                        close()
                    }
                    clipPath(col) { drawPath(water, ink) }
                }
            }
            if (i % labelEvery == 0) {
                val d = measurer.measure(hourFmt.format(Date(b.start)), small)
                drawText(d, topLeft = Offset((slotW * i).coerceAtMost(chartW - d.size.width), size.height - d.size.height))
            }
        }
    }
}
