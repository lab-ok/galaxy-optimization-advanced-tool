package com.local.folddpifix.ui.art

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.folddpifix.ui.liquid.LiquidColors
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion

/*
 * 사용 방법·안내 그림의 공통 도구: 반복 진행값, 그림판, 글자, 값 알약.
 * 동작 줄이기가 켜져 있으면 진행값은 1(끝 장면)로 멈춘다.
 */

/** 0→1을 [periodMs]마다 반복하는 진행값. */
@Composable
internal fun artLoop(periodMs: Int, reverse: Boolean = false): Float {
    val reduce = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "art").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(periodMs, easing = if (reverse) FastOutSlowInEasing else LinearEasing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart),
        label = "t",
    )
    return if (reduce) 1f else t
}

@Composable
internal fun ArtCanvas(height: Dp = 150.dp, draw: DrawScope.(LiquidColors, TextMeasurer) -> Unit) {
    val c = LocalLiquid.current
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(height)) { draw(c, tm) }
}

/** 가운데 정렬 글자. */
internal fun DrawScope.artLabel(tm: TextMeasurer, text: String, center: Offset, color: Color, sizeSp: Int = 11, bold: Boolean = false) {
    val l = tm.measure(text, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium))
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

/** 값 표시 알약. */
internal fun DrawScope.artPill(tm: TextMeasurer, c: LiquidColors, text: String, center: Offset, filled: Boolean) {
    val l = tm.measure(text, TextStyle(color = if (filled) c.onInk else c.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    val w = l.size.width + 14.dp.toPx(); val h = l.size.height + 6.dp.toPx()
    drawRoundRect(if (filled) c.ink else c.line, Offset(center.x - w / 2, center.y - h / 2), Size(w, h), CornerRadius(h / 2))
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

