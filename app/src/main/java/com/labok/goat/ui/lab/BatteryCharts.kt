package com.labok.goat.ui.lab

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.BatteryCycles
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.UiMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 배터리 모니터링의 리퀴드 그래프. 앱의 진행 버튼처럼 잉크 물이 차오르고, 물 앞면은 얌전히 출렁인다.
 * 값이 처음 보이거나 바뀌면 물 스프링(Springs.water)으로 넘치듯 자리를 잡는다. '애니메이션 제거'가 켜져 있으면 움직이지 않는다.
 */

/** 물결 시각(초). 애니메이션을 끄면 0에 멈춘다. */
@Composable
internal fun rememberWaveTime(): Float {
    val reduce = LocalReduceMotion.current
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f * UiMotion.speed }
    }
    return t
}

/** 0에서 [target]까지 물 스프링으로 차오르는 값. [delayMs]만큼 늦게 시작한다(기둥이 차례로 차오르게). */
@Composable
internal fun rememberFill(target: Float, delayMs: Long = 0L): Float {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) {
            a.snapTo(target)
        } else {
            if (a.value == 0f && delayMs > 0) kotlinx.coroutines.delay(delayMs)
            a.animateTo(target, Springs.water())
        }
    }
    return a.value
}

/** 왼쪽부터 [width]까지 찬 물(앞면이 위아래로 출렁임). */
private fun waterPath(width: Float, height: Float, t: Float, amp: Float, step: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(width, 0f)
    var y = 0f
    while (y <= height) {
        lineTo((width + amp * sin(y / (step * 3) + t * 2.2f)).coerceAtLeast(0f), y)
        y += step
    }
    lineTo(width, height)
    lineTo(0f, height)
    close()
}

/**
 * 가로 물 막대: [fraction](0~1)만큼 잉크 물이 찬다. [ghost]가 있으면 그 앞까지를 옅은 물로 먼저 깐다
 * (예: 시간 중 화면 켜짐은 진한 물, 꺼짐까지 합친 전체는 옅은 물).
 */
@Composable
internal fun LiquidBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, ghost: Float? = null, description: String? = null) {
    val c = LocalLiquid.current
    val t = rememberWaveTime()
    val fill = rememberFill(fraction.coerceIn(0f, 1f))
    val back = ghost?.let { rememberFill(it.coerceIn(0f, 1f)) }
    Canvas(modifier.fillMaxWidth().height(height).semantics { description?.let { contentDescription = it } }) {
        val h = size.height
        val r = CornerRadius(h / 2)
        val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, h, r)) }
        drawRoundRect(c.line.copy(alpha = 0.55f), cornerRadius = r)
        clipPath(pill) {
            val amp = (h * 0.18f).coerceAtMost(2.dp.toPx())
            if (back != null && back > 0f) drawPath(waterPath(size.width * back, h, t + 1.3f, amp, 1.5.dp.toPx()), c.ink.copy(alpha = 0.28f))
            if (fill > 0f) drawPath(waterPath(size.width * fill, h, t, amp, 1.5.dp.toPx()), c.ink)
        }
    }
}

/**
 * 배터리 물통: 배터리 모양 안에 지금 잔량([level])만큼 물이 차 있고, 마지막 충전 때 잔량([startLevel])까지는
 * 쓴 만큼 옅은 물 자국으로 남는다. 가운데 글자는 물에 잠긴 부분만 반전색이 된다(물방울·거품은 그리지 않는다).
 */
@Composable
internal fun LiquidBatteryTank(level: Int, startLevel: Int?, charging: Boolean, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val t = rememberWaveTime()
    val fill = rememberFill(level / 100f)
    val ghost = startLevel?.let { rememberFill(it.coerceIn(level, 100) / 100f) }
    val measurer = rememberTextMeasurer()
    val label = if (charging) "충전 중 $level%" else "$level%"
    Canvas(modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "배터리 잔량 $level%" + (startLevel?.let { ", 마지막 충전 시 $it%" } ?: "") }) {
        val nub = 6.dp.toPx()
        val w = size.width - nub - 3.dp.toPx()
        val h = size.height
        val r = CornerRadius(14.dp.toPx())
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, r)) }
        drawRoundRect(c.surface, size = Size(w, h), cornerRadius = r)
        // 배터리 꼭지
        drawRoundRect(c.line, topLeft = Offset(w + 3.dp.toPx(), h * 0.32f), size = Size(nub, h * 0.36f), cornerRadius = CornerRadius(3.dp.toPx()))
        val water = waterPath(w * fill, h, t, 3.dp.toPx(), 2.dp.toPx())
        clipPath(body) {
            if (ghost != null && ghost > fill) drawPath(waterPath(w * ghost, h, t + 1.1f, 2.dp.toPx(), 2.dp.toPx()), c.ink.copy(alpha = 0.18f))
            drawPath(water, c.ink)
        }
        drawRoundRect(c.line, size = Size(w, h), cornerRadius = r, style = Stroke(1.dp.toPx()))
        val style = TextStyle(fontFamily = GalaxyFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val outside = measurer.measure(label, style.copy(color = c.ink))
        val inside = measurer.measure(label, style.copy(color = c.onInk))
        val at = Offset((w - outside.size.width) / 2f, (h - outside.size.height) / 2f)
        drawText(outside, topLeft = at)
        clipPath(water) { drawText(inside, topLeft = at) }
    }
}

/**
 * 지난 충전 주기 기둥 그래프(오래된 것 왼쪽 → 최근 오른쪽). 기둥 높이는 그 주기처럼 쓰면 완충으로 가는 시간(완충 환산),
 * 진한 물은 그중 화면 켜짐 몫, 옅은 물은 꺼짐(대기) 몫이다. 기둥은 왼쪽부터 차례로 차오른다.
 */
@Composable
internal fun LiquidCycleColumns(cycles: List<BatteryCycles.Cycle>, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val t = rememberWaveTime()
    val list = cycles.filter { it.usedPct >= 3 && it.onBatteryMs > 0 }.sortedBy { it.start }
    if (list.isEmpty()) return
    val hours = list.map { it.onBatteryMs * 100.0 / it.usedPct / 3_600_000.0 }
    val top = (hours.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val fills = list.mapIndexed { i, _ -> rememberFill((hours[i] / top).toFloat(), delayMs = 70L * i) }
    val measurer = rememberTextMeasurer()
    val fmt = remember { SimpleDateFormat("M/d", Locale.KOREA) }
    val small = TextStyle(fontFamily = GalaxyFont, fontSize = 11.sp, color = c.muted)
    val value = TextStyle(fontFamily = GalaxyFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "충전 주기 ${list.size}회의 완충 환산 시간 그래프" }) {
        val labelH = 16.dp.toPx()
        val valueH = 16.dp.toPx()
        val chartH = size.height - labelH - valueH
        val slot = size.width / list.size
        val colW = (slot * 0.56f).coerceAtMost(34.dp.toPx())
        list.forEachIndexed { i, cy ->
            val x = slot * i + (slot - colW) / 2
            val colH = chartH * fills[i]
            val y0 = valueH + chartH - colH
            val r = CornerRadius(colW / 2.6f)
            val col = Path().apply { addRoundRect(RoundRect(x, y0, x + colW, valueH + chartH, r, r, CornerRadius.Zero, CornerRadius.Zero)) }
            // 물기둥: 옅은 물(전체) 위에 화면 켜짐 몫만큼 진한 물이 바닥부터 찬다. 진한 물 위쪽 면이 출렁인다
            drawPath(col, c.ink.copy(alpha = 0.28f))
            val onShare = (cy.screenOnMs.toFloat() / cy.onBatteryMs).coerceIn(0f, 1f)
            val surface = valueH + chartH - colH * onShare
            val amp = 1.6.dp.toPx()
            val dark = Path().apply {
                moveTo(x, valueH + chartH)
                var dx = 0f
                while (dx <= colW) {
                    lineTo(x + dx, surface + amp * sin(dx / (colW / 3f) + t * 2.2f + i))
                    dx += 1.5.dp.toPx()
                }
                lineTo(x + colW, valueH + chartH)
                close()
            }
            clipPath(col) { drawPath(dark, c.ink) }
            val v = measurer.measure("${hours[i].roundToInt()}h", value)
            drawText(v, topLeft = Offset(x + (colW - v.size.width) / 2, (y0 - v.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            val d = measurer.measure(fmt.format(Date(cy.start)), small)
            drawText(d, topLeft = Offset(x + (colW - d.size.width) / 2, size.height - d.size.height))
        }
    }
}
