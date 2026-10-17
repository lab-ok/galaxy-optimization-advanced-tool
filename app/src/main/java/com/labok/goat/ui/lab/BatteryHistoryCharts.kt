package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 마지막 충전 이후 배터리 기록 그래프(예전 안드로이드 배터리 화면의 기록 그래프를 리퀴드로 다시 그림).
 * 위: 잔량 물(선 아래를 물로 채우고, 지금 끝은 출렁임) + 지금 패턴대로 쓰면 0%가 되는 때까지 옅은 점선 예측.
 * 가운데: 같은 시간축의 상태 줄(충전·화면 켜짐·깨어 있음).
 * 아래: 시간대별 사용량 물기둥(누르면 그 시간대의 사용량과 화면 켜짐 시간).
 */

/**
 * 마지막 충전 이후 그래프 묶음. [remainMs]는 지금 잔량으로 더 쓸 수 있는 예상 시간(없으면 예측을 그리지 않는다).
 */
@Composable
internal fun BatteryHistorySection(points: List<BatteryHistory.Point>, now: Long, remainMs: Long?) {
    val c = LocalLiquid.current
    if (points.size < 2) {
        Text("시스템 배터리 기록이 아직 충분하지 않습니다. 잠시 사용한 뒤 다시 열어 주세요.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val start = points.first().time
    val end = remainMs?.takeIf { !points.last().charging && it > 0 }?.let { now + it }
    // 예측이 너무 길면(완충 직후 등) 지금까지 시간의 2배까지만 보여 준다
    val axisEnd = end?.coerceAtMost(now + (now - start) * 2) ?: now
    LiquidLevelHistory(points, start, now, axisEnd, end)
    Spacer(Modifier.height(Gap.Tight))
    StateStrips(points, start, now, axisEnd)
    Spacer(Modifier.height(Gap.Card))
    Text("시간대별 사용량", fontWeight = FontWeight.SemiBold, color = c.ink)
    val bucketMs = BatteryHistory.bucketMs(now - start)
    val buckets = remember(points, now / 60_000) { BatteryHistory.buckets(points, now, bucketMs, from = floorHour(start)) }
    var selected by remember(buckets.size) { mutableStateOf<Int?>(null) }
    Spacer(Modifier.height(Gap.Item))
    LiquidBucketColumns(buckets, selected, { selected = it })
    Spacer(Modifier.height(Gap.Item))
    val hourFmt = remember { SimpleDateFormat("a h시", Locale.KOREA) }
    val pick = selected?.let { buckets.getOrNull(it) }
    val line = if (pick == null) {
        "기둥 하나가 ${bucketMs / 3_600_000}시간입니다. 기둥을 누르면 그 시간대에 쓴 양과 화면 켜짐 시간을 볼 수 있습니다."
    } else {
        val range = "${hourFmt.format(Date(pick.start))}~${hourFmt.format(Date(pick.end)).substringAfter(' ')}"
        listOfNotNull(
            range,
            "${pctText(pick.usedPct)} 사용",
            "화면 켜짐 ${duration(pick.screenMs)}",
            "충전".takeIf { pick.charging }
        ).joinToString(" · ")
    }
    Text(line, color = if (pick == null) c.muted else c.ink, style = MaterialTheme.typography.bodySmall)
}

private fun floorHour(t: Long): Long {
    val zone = java.util.TimeZone.getDefault()
    val off = zone.getOffset(t)
    return (t + off) / 3_600_000L * 3_600_000L - off
}

private fun pctText(v: Float) = when {
    v <= 0f -> "0%"
    v < 1f -> "1% 미만"
    else -> "${v.roundToInt()}%"
}

/** 잔량 물 그래프. 가로는 [start]~[axisEnd], 세로는 0~100%. 충전 구간은 선을 굵게, 꺼져 있던 구간은 끊는다. */
@Composable
private fun LiquidLevelHistory(points: List<BatteryHistory.Point>, start: Long, now: Long, axisEnd: Long, empty: Long?) {
    val c = LocalLiquid.current
    val t = rememberWaveTime()
    val rise = rememberFill(1f)
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontFamily = GalaxyFont, fontSize = 10.sp, color = c.muted)
    val timeFmt = remember { SimpleDateFormat("M/d a h:mm", Locale.KOREA) }
    val last = points.last()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "마지막 충전 이후 배터리 잔량 그래프. 지금 ${last.level}%" }
    ) {
        val labelH = 16.dp.toPx()
        val axisW = 32.dp.toPx()
        val w = size.width - axisW
        val h = size.height - labelH
        val span = (axisEnd - start).coerceAtLeast(1L).toFloat()
        fun x(time: Long) = (time - start) / span * w
        fun y(level: Int) = h - level / 100f * h * rise
        listOf(0, 50, 100).forEach { lv ->
            val yy = h - lv / 100f * h
            drawLine(c.line, Offset(0f, yy), Offset(w, yy), 1.dp.toPx())
            val m = measurer.measure("$lv%", small)
            drawText(m, topLeft = Offset(w + 4.dp.toPx(), (yy - m.size.height / 2f).coerceIn(0f, h - m.size.height)))
        }
        // 꺼져 있던 구간으로 나눈 조각마다 물을 채우고 선을 긋는다
        val pieces = ArrayList<MutableList<BatteryHistory.Point>>()
        points.forEach { p -> if (p.afterOff || pieces.isEmpty()) pieces += mutableListOf(p) else pieces.last() += p }
        pieces.last() += last.copy(time = now, afterOff = false)
        pieces.forEach { piece ->
            if (piece.size < 2) return@forEach
            // 잔량은 다음 시점까지 그대로였다가 바뀌므로 계단 모양으로 잇는다
            val line = Path().apply {
                moveTo(x(piece.first().time), y(piece.first().level))
                piece.zipWithNext().forEach { (a, b) ->
                    lineTo(x(b.time), y(a.level))
                    lineTo(x(b.time), y(b.level))
                }
            }
            val water = Path().apply {
                addPath(line)
                lineTo(x(piece.last().time), h)
                lineTo(x(piece.first().time), h)
                close()
            }
            drawPath(water, c.ink.copy(alpha = 0.22f))
            drawPath(line, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            piece.zipWithNext().forEach { (a, b) ->
                if (a.charging) drawLine(c.ink.copy(alpha = 0.45f), Offset(x(a.time), y(a.level)), Offset(x(b.time), y(a.level)), 6.dp.toPx(), StrokeCap.Round)
            }
        }
        // 지금: 잔량 끝에 숨 쉬듯 커졌다 작아지는 물방울
        val nx = x(now)
        val ny = y(last.level)
        drawCircle(c.ink.copy(alpha = 0.25f), (6f + 3f * sin(t * 2.2f)).dp.toPx(), Offset(nx, ny))
        drawCircle(c.ink, 4.dp.toPx(), Offset(nx, ny))
        // 예측: 지금 잔량에서 0%까지 옅은 점선과 옅은 물
        if (empty != null && axisEnd > now) {
            val ex = x(empty.coerceAtMost(axisEnd))
            val ey = if (empty <= axisEnd) h else y((last.level * (1f - (axisEnd - now).toFloat() / (empty - now))).roundToInt())
            val ghost = Path().apply {
                moveTo(nx, ny)
                lineTo(ex, ey)
                lineTo(ex, h)
                lineTo(nx, h)
                close()
            }
            drawPath(ghost, c.ink.copy(alpha = 0.08f))
            drawLine(c.ink.copy(alpha = 0.6f), Offset(nx, ny), Offset(ex, ey), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
        // 가로 눈금: 시작·지금·예상 0%
        val s = measurer.measure(timeFmt.format(Date(start)), small)
        drawText(s, topLeft = Offset(0f, size.height - s.size.height))
        val n = measurer.measure("지금", small.copy(color = c.ink, fontWeight = FontWeight.SemiBold))
        drawText(n, topLeft = Offset((nx - n.size.width / 2f).coerceIn(s.size.width + 4.dp.toPx(), w - n.size.width), size.height - n.size.height))
        if (empty != null && empty <= axisEnd && axisEnd > now) {
            // 오늘이면 시각만, 다른 날이면 날짜까지 적는다
            val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).let { it.format(Date(empty)) == it.format(Date(now)) }
            val e = measurer.measure(timeFmt.format(Date(empty)).let { if (sameDay) it.substringAfter(' ') else it }, small)
            drawText(e, topLeft = Offset((x(empty) - e.size.width).coerceAtLeast(nx + n.size.width / 2f + 4.dp.toPx()), size.height - e.size.height))
        }
    }
}

/** 상태 줄: 같은 시간축에 충전·화면 켜짐·깨어 있음 구간을 물 알약으로 칠한다(예전 안드로이드 배터리 그래프 아래 줄들). */
@Composable
private fun StateStrips(points: List<BatteryHistory.Point>, start: Long, now: Long, axisEnd: Long) {
    val c = LocalLiquid.current
    val rows = listOf<Pair<String, (BatteryHistory.Point) -> Boolean>>(
        "충전" to { it.charging },
        "화면 켜짐" to { it.screen },
        "깨어 있음" to { it.awake }
    )
    val fill = rememberFill(1f)
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { (name, pick) ->
            val spans = remember(points, now / 60_000) { BatteryHistory.spans(points, now, pick) }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.weight(1f).height(8.dp).semantics { contentDescription = "$name 구간 ${spans.size}개" }) {
                    val w = size.width
                    val span = (axisEnd - start).coerceAtLeast(1L).toFloat()
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(c.line.copy(alpha = 0.5f), size = Size(w * ((now - start) / span), size.height), cornerRadius = r)
                    spans.forEach { s ->
                        val x0 = (s.first - start) / span * w
                        val x1 = ((s.last + 1 - start) / span * w).coerceAtLeast(x0 + 1.5f)
                        drawRoundRect(c.ink, topLeft = Offset(x0, 0f), size = Size((x1 - x0) * fill, size.height), cornerRadius = r)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(name, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), maxLines = 1)
            }
        }
    }
}

/** 시간대별 사용량 물기둥. 기둥 높이는 그 칸에서 쓴 잔량, 진한 물 앞면이 출렁인다. 충전한 칸은 바닥까지 옅은 물을 깐다. */
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
                val col = Path().apply { addRoundRect(RoundRect(x, y0 - 2.dp.toPx(), x + colW, chartH, r, r, CornerRadius.Zero, CornerRadius.Zero)) }
                val amp = 1.4.dp.toPx().coerceAtMost(colH / 3f)
                val water = Path().apply {
                    moveTo(x, chartH)
                    var dx = 0f
                    while (dx <= colW) {
                        lineTo(x + dx, y0 + amp * sin(dx / (colW / 3f) + t * 2.2f + i * 0.7f))
                        dx += 1.5.dp.toPx()
                    }
                    lineTo(x + colW, chartH)
                    close()
                }
                clipPath(col) { drawPath(water, ink) }
            }
            if (i % labelEvery == 0) {
                val d = measurer.measure(hourFmt.format(Date(b.start)), small)
                drawText(d, topLeft = Offset((slotW * i).coerceAtMost(chartW - d.size.width), size.height - d.size.height))
            }
        }
    }
}
