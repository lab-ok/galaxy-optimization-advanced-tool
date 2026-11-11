package com.labok.goat.ui.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 진행 버튼: 쉴 때는 [LiquidButton]이고, [progress]가 있으면(0..1) 업데이트 진행 통처럼 버튼 안에 왼쪽부터 잉크 물이 차오른다.
 * 물 앞면은 얌전히 출렁이고 작은 물방울이 튀며, 가운데 글자([status]와 퍼센트)는 물에 잠긴 부분만 반전색이 된다.
 */
@Composable
fun LiquidProgressButton(
    text: String,
    progress: Float?,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    disabledHint: String? = null
) {
    if (progress == null) {
        LiquidButton(text, onClick = onClick, modifier = modifier, primary = primary, enabled = enabled, disabledHint = disabledHint)
        return
    }
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val fill by animateFloatAsState(progress.coerceIn(0f, 1f), Springs.water(reduce), label = "progressFill")
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f * UiMotion.speed }
    }
    val measurer = rememberTextMeasurer()
    // 퍼센트 숫자는 실제 진행값을 그대로 쓴다. 물과 같은 스프링 값을 쓰면 단계 글자가 6/6까지 가도 숫자가 5~27%에 머물러
    // 멈춘 것처럼 보였다(1.11.4·1.17.5 전수 점검, 실험실 조사 도구). 물 높이만 스프링으로 따라간다
    val pct = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    val label = "$status · $pct%"
    Box(
        modifier
            .fillMaxWidth()
            .height(LiquidShape.control + 4.dp)
            .semantics {
                contentDescription = status
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val h = size.height
            val r = CornerRadius(h / 2)
            val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, h, r)) }
            drawRoundRect(c.surface, cornerRadius = r)
            // 물: 왼쪽부터 fill만큼, 앞면은 위아래로 얌전히 출렁인다
            val front = size.width * fill
            val amp = 2.dp.toPx()
            val water = Path().apply {
                moveTo(0f, 0f)
                lineTo(front, 0f)
                var y = 0f
                while (y <= h) {
                    lineTo((front + amp * sin(y / 6.dp.toPx() + t * 2.2f)).coerceAtLeast(0f), y)
                    y += 2.dp.toPx()
                }
                lineTo(0f, h)
                close()
            }
            clipPath(pill) {
                drawPath(water, c.ink)
                // 앞면에서 튀는 작은 물방울 둘
                if (fill in 0.03f..0.97f) {
                    for (i in 0..1) {
                        val k = (t * 0.5f + i * 0.5f) % 1f
                        val arc = 4f * k * (1f - k)
                        drawCircle(c.ink, 2.6.dp.toPx() * (1f - k * 0.5f), Offset(front + 4.dp.toPx() + k * 10.dp.toPx(), h / 2 - arc * 9.dp.toPx() + i * 3.dp.toPx()))
                    }
                }
            }
            drawRoundRect(c.line, cornerRadius = r, style = Stroke(1.dp.toPx()))
            // 글자: 물 밖은 잉크색, 물 안은 반전색
            // 글이 길면(좁은 화면) 버튼 안에 들어가게 글자를 줄인다(최소 11sp)
            var style = TextStyle(fontFamily = GalaxyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val room = size.width - 32.dp.toPx()
            val w15 = measurer.measure(label, style).size.width
            if (w15 > room) style = style.copy(fontSize = (15f * room / w15).coerceAtLeast(11f).sp)
            val outside = measurer.measure(label, style.copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = room.toInt().coerceAtLeast(1)))
            val inside = measurer.measure(label, style.copy(color = c.onInk), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = room.toInt().coerceAtLeast(1)))
            val at = Offset((size.width - outside.size.width) / 2f, (h - outside.size.height) / 2f)
            drawText(outside, topLeft = at)
            clipPath(water) { drawText(inside, topLeft = at) }
        }
    }
}
