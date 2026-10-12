package com.labok.goat.ui.liquid

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.launch

/**
 * 액체 슬라이더(원본 liquid_range): 끄는 동안 손잡이가 납작해지고(jelly), 값 방울이 손잡이에서
 * 떨어져 위로 맺히며 큰 글자로 값을 보여 준다. 손을 떼면 방울이 손잡이로 다시 빨려 든다.
 * 값은 끄는 동안 [onValueChange]로, 손을 뗄 때 [onValueChangeFinished]로 알린다.
 */
@Composable
fun LiquidSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueText: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** true면 채움이 가운데(0)에서 값 쪽으로 뻗는다(미세조정처럼 ± 값). */
    centered: Boolean = false,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val density = LocalDensity.current
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val current by rememberUpdatedState(value)

    var width by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    var active by remember { mutableStateOf(false) }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    val bubbleSize = remember { Animatable(10f) }
    // 세밀 조절(모든 조절바): 붙들고 가만히 있으면 손잡이에서 확대 패널이 퍼져 나와 앞뒤 [FINE_HALF]칸을 조절바 전체 폭으로 보여 준다
    var fineBase by remember { mutableStateOf<Int?>(null) }
    val fineK = remember { Animatable(0f) }
    LaunchedEffect(fineBase != null) { fineK.animateTo(if (fineBase != null) 1f else 0f, Springs.water(reduce)) }
    val bubbleLift = remember { Animatable(0f) }

    LaunchedEffect(active) {
        kotlinx.coroutines.coroutineScope {
            launch { bubbleSize.animateTo(if (active) 46f else 10f, Springs.water(reduce)) }
            launch { bubbleLift.animateTo(if (active) 1f else 0f, Springs.water(reduce)) }
        }
    }

    val span = (range.last - range.first).coerceAtLeast(1)
    // 잡으면 값 방울이 트랙 위로 떠오르므로 위쪽에 그만큼(26dp) 자리를 둔다. 방울 중심은 예전과 같은 높이(12dp)에 온다.
    val trackY = with(density) { 38.dp.toPx() }
    // 양 끝 여백: 가장 넓은 값 글자의 손잡이도 조절바 밖으로 잘리지 않게 그 절반 이상을 둔다
    val widest = remember(range) {
        range.maxOf { measurer.measure(valueText(it), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)).size.width }
    }
    val edge = with(density) { maxOf(30.dp.toPx(), (widest + 32.dp.toPx()) / 2 + 2.dp.toPx()) }
    fun xOf(v: Int) = edge + (v - range.first).toFloat() / span * (width - 2 * edge)
    /** 세밀 조절 중 값 [v]의 x(확대 눈금 위치). */
    // 확대 칸 수: 앞뒤 FINE_HALF칸, 범위가 좁으면 그 절반(최소 1)
    val fineHalf = minOf(FINE_HALF, maxOf(1, span / 2))
    fun xFine(base: Int, v: Int) = edge + (v - (base - fineHalf)).toFloat() / (2 * fineHalf) * (width - 2 * edge)
    fun valueAt(x: Float): Int {
        val f = ((x - edge) / (width - 2 * edge)).coerceIn(0f, 1f)
        return (range.first + f * span).roundToInt()
    }

    // 손잡이: 연체 물방울. 잡고 있는 동안은 손가락 위치를 바짝 따라가고(구동력 7배) 납작하고 넓게(60×24dp) 된다.
    // 뒤쪽 면은 늦게 따라오므라 빨리 끌수록 길게 늘어난다. 놓으면 값 자리로 가서 52×30dp로 돌아온다.
    var dragX by remember { mutableFloatStateOf(-1f) }
    // 손잡이 너비는 글자 너비를 따라 늘어난다(글자가 방울 밖으로 튀어나오지 않게): 쉴 때 12sp 글자 + 양옆 12dp
    val restText = measurer.measure(valueText(value), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)).size.width
    val tw = with(density) { maxOf((if (active) 60 else 52).dp.toPx(), restText + 24.dp.toPx() + if (active) 8.dp.toPx() else 0f) }
    val tht = with(density) { (if (active) 24 else 30).dp.toPx() }
    LaunchedEffect(value, width, active, dragX) {
        if (width <= 0f) return@LaunchedEffect
        blob.blob.gain = if (active) 7f else 1f
        val x = if (active && dragX >= 0f) dragX.coerceIn(edge, width - edge) else xOf(value)
        blob.moveTo(x, trackY, tw, tht, animate = !reduce)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(HEIGHT.dp)
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { target ->
                    change(target.roundToInt().coerceIn(range)); finish(); true
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                val slop = 5.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragX = down.position.x
                    active = true
                    haptics.press()
                    var v = valueAt(down.position.x)
                    if (v != current) change(v)
                    // 손가락을 떼는 순간 접촉점이 살짝 밀려 값이 1 바뀌는 것을 막는다: 직전에 머물던 값(stable)을 기억해 두고,
                    // 떼기 직전 [LIFT_JITTER_MS] 안에 1만큼만 바뀌었으면 그 값으로 되돌린다.
                    var stable = v
                    var changedAt = 0L
                    var stillX = down.position.x
                    var stillSince = down.uptimeMillis
                    var anchorX = 0f
                    var upAt = down.uptimeMillis
                    fun set(nv: Int, at: Long) {
                        if (nv == v) return
                        if (at - changedAt >= LIFT_JITTER_MS) stable = v
                        changedAt = at; v = nv; change(nv); haptics.tick()
                    }
                    fun openFine(x: Float) {
                        if (fineBase != null) return
                        fineBase = v; anchorX = x; haptics.snap()
                    }
                    while (true) {
                        // 가만히 붙들고 있으면 이벤트가 오지 않으므로 시간으로도 확인한다
                        val ev = withTimeoutOrNull(FINE_HOLD_MS) { awaitPointerEvent() }
                        if (ev == null) { openFine(stillX); continue }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        upAt = ch.uptimeMillis
                        if (!ch.pressed) break
                        val x = ch.position.x
                        if (kotlin.math.abs(x - stillX) > slop) { stillX = x; stillSince = ch.uptimeMillis }
                        else if (ch.uptimeMillis - stillSince >= FINE_HOLD_MS) openFine(x)
                        val base = fineBase
                        if (base == null) {
                            dragX = x
                            set(valueAt(x), ch.uptimeMillis)
                        } else {
                            // 세밀 조절: 조절바 전체 폭이 앞뒤 FINE_HALF칸. 같은 손 움직임으로 훨씬 조금씩 바뀐다.
                            val perPx = (2f * fineHalf) / (width - 2 * edge)
                            // 패널이 보여 주는 앞뒤 fineHalf칸 안에서만 움직인다(더 가려면 떼고 다시 붙든다)
                            val nv = (base + ((x - anchorX) * perPx).roundToInt()).coerceIn(base - fineHalf, base + fineHalf).coerceIn(range)
                            set(nv, ch.uptimeMillis)
                            dragX = xFine(base, v)
                        }
                        ch.consume()
                    }
                    if (v != stable && kotlin.math.abs(v - stable) == 1 && upAt - changedAt < LIFT_JITTER_MS) change(stable)
                    fineBase = null
                    active = false
                    dragX = -1f
                    finish()
                }
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        // 트랙(유리판 층, goo 없음)
        Canvas(Modifier.matchParentSize()) {
            val th = 8.dp.toPx()
            val trackColor = if (c.dark) c.muted.copy(alpha = 0.35f) else c.line
            drawRoundRect(trackColor, Offset(edge / 2, trackY - th / 2), Size(width - edge, th), CornerRadius(th / 2))
            blob.frame
            // 채움 끝은 손잡이 방울 중심을 따라가 손잡이와 어긋나지 않는다.
            val cx = if (blob.blob.ready) blob.blob.cx else xOf(value)
            val from = if (centered) xOf((range.first + range.last) / 2) else edge / 2
            val l = minOf(from, cx)
            val r = maxOf(from, cx)
            drawRoundRect(c.ink.copy(alpha = 0.85f), Offset(l, trackY - th / 2), Size(r - l, th), CornerRadius(th / 2))
        }
        // 손잡이 + 값 방울(goo 층: 떨어지는 순간 목처럼 이어진다). goo 층은 자기 크기 밖을 잘라 내므로,
        // 떠오른 방울이 잘리지 않게 이 층만 위아래로 OVERFLOW만큼 크게 두고 그만큼 내려 그린다.
        Canvas(Modifier.fillMaxWidth().requiredHeight((HEIGHT + 2 * OVERFLOW).dp).goo(c.ink, 7.dp)) {
            blob.frame
            translate(top = OVERFLOW.dp.toPx()) {
                val cx = blob.blob.cx
                drawPath(blob.blob.path(path), c.ink)
                // 세밀 조절 패널: 손잡이 자리에서 양옆으로 퍼져 조절바 폭만큼 늘어나는 잉크 알약(goo라 손잡이와 이어진다)
                if (fineK.value > 0.01f) {
                    val k = fineK.value
                    val ph = 22.dp.toPx()
                    val full = width - edge
                    val pw = blob.blob.let { tw + (full - tw) * k }
                    val px = (blob.blob.cx * (1 - k) + (width / 2) * k)
                    drawRoundRect(c.ink, Offset(px - pw / 2, trackY - ph / 2), Size(pw, ph), CornerRadius(ph / 2))
                }
                val bs = bubbleSize.value.dp.toPx()
                // 떠오른 값 방울도 17sp 글자 너비에 맞춰 옆으로 늘어난다(둥근 알약)
                val liftText = measurer.measure(valueText(value), TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)).size.width
                val bw = maxOf(bs, (liftText + 20.dp.toPx()) * (bs / 46.dp.toPx()))
                val by = trackY - bubbleLift.value * LIFT.dp.toPx()
                drawRoundRect(c.ink, Offset(cx - bw / 2, by - bs / 2), Size(bw, bs), CornerRadius(bs / 2))
            }
        }
        // 세밀 조절 눈금(패널 위, 반전색): 앞뒤 FINE_HALF칸, 5칸마다 길게
        val base = fineBase
        if (base != null || fineK.value > 0.01f) Canvas(Modifier.matchParentSize()) {
            val b = base ?: value
            val k = fineK.value
            for (i in -fineHalf..fineHalf) {
                val vv = b + i
                if (vv !in range) continue
                val x = xFine(b, vv)
                val long = vv % 5 == 0 || fineHalf < 5
                val h = (if (long) 10 else 5).dp.toPx() * k
                drawLine(c.onInk.copy(alpha = 0.75f * k), Offset(x, trackY - h / 2), Offset(x, trackY + h / 2), 1.5.dp.toPx())
            }
        }
        // 값 글자(방울 위에서 반전). 글자 크기를 재서 손잡이·방울의 정확한 가운데에 놓는다.
        Canvas(Modifier.matchParentSize()) {
            if (width <= 0f) return@Canvas
            blob.frame
            val lift = bubbleLift.value
            val cx = blob.blob.cx
            val cy = trackY - lift * LIFT.dp.toPx()
            val layout = measurer.measure(
                valueText(value),
                TextStyle(color = c.onInk, fontSize = (12 + 5 * lift).sp, fontWeight = FontWeight.Bold),
            )
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
    }
}

/** 잡았을 때 값 방울이 트랙 위로 떠오르는 높이(dp). */
private const val LIFT = 26
/** 조절바 높이(dp). */
private const val HEIGHT = 56
/** goo 층을 조절바 위아래로 더 키우는 양(dp). */
private const val OVERFLOW = 30
/** 떼기 직전 이 시간(ms) 안에 1만큼 바뀐 값은 손가락이 들리며 밀린 것으로 보고 되돌린다. */
private const val LIFT_JITTER_MS = 90L
/** 이 시간(ms) 동안 거의 움직이지 않고 붙들고 있으면 세밀 조절을 연다. */
private const val FINE_HOLD_MS = 750L
/** 세밀 조절 패널이 보여 주는 칸 수(앞뒤). */
private const val FINE_HALF = 10
