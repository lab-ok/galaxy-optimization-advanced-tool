package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
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
    val haptic = LocalHapticFeedback.current
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
    val bubbleLift = remember { Animatable(0f) }

    LaunchedEffect(active) {
        kotlinx.coroutines.coroutineScope {
            launch { bubbleSize.animateTo(if (active) 46f else 10f, Springs.water(reduce)) }
            launch { bubbleLift.animateTo(if (active) 1f else 0f, Springs.water(reduce)) }
        }
    }

    val span = (range.last - range.first).coerceAtLeast(1)
    val trackY = with(density) { 46.dp.toPx() }
    val edge = with(density) { 30.dp.toPx() }
    fun xOf(v: Int) = edge + (v - range.first).toFloat() / span * (width - 2 * edge)
    fun valueAt(x: Float): Int {
        val f = ((x - edge) / (width - 2 * edge)).coerceIn(0f, 1f)
        return (range.first + f * span).roundToInt()
    }

    // 손잡이: 값 자리로 끌려가는 연체 물방울. 잡고 있는 동안은 납작하고 넓게(60×24dp), 놓으면 52×30dp로 돌아온다.
    val tw = with(density) { (if (active) 60 else 52).dp.toPx() }
    val tht = with(density) { (if (active) 24 else 30).dp.toPx() }
    LaunchedEffect(value, width, active) {
        if (width > 0f) blob.moveTo(xOf(value), trackY, tw, tht, animate = !reduce)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(66.dp)
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
                awaitEachGesture {
                    val down = awaitFirstDown()
                    active = true
                    var v = valueAt(down.position.x)
                    if (v != current) { change(v); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    horizontalDrag(down.id) { e ->
                        val nv = valueAt(e.position.x)
                        if (nv != v) {
                            v = nv
                            change(nv)
                            if (nv % 5 == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        e.consume()
                    }
                    active = false
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
        // 손잡이 + 값 방울(goo 층: 떨어지는 순간 목처럼 이어진다)
        Canvas(Modifier.matchParentSize().goo(c.ink, 7.dp)) {
            blob.frame
            val cx = blob.blob.cx
            drawPath(blob.blob.path(path), c.ink)
            val bs = bubbleSize.value.dp.toPx()
            val by = trackY - bubbleLift.value * 34.dp.toPx()
            drawOval(c.ink, Offset(cx - bs / 2, by - bs / 2), Size(bs, bs))
        }
        // 값 글자(방울 위에서 반전). 글자 크기를 재서 손잡이·방울의 정확한 가운데에 놓는다.
        Canvas(Modifier.matchParentSize()) {
            if (width <= 0f) return@Canvas
            blob.frame
            val lift = bubbleLift.value
            val cx = blob.blob.cx
            val cy = trackY - lift * 34.dp.toPx()
            val layout = measurer.measure(
                valueText(value),
                TextStyle(color = c.onInk, fontSize = (12 + 5 * lift).sp, fontWeight = FontWeight.Bold),
            )
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
    }
}
