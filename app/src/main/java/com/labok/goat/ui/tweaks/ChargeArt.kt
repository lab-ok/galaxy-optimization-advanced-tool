package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.labok.goat.data.tweaks.ChargeLimit.Choice
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import kotlin.math.sin

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 충전 상한 그림: 눕힌 배터리에 번개 표시가 켜지고 물이 20%에서 차오른다.
 * - 끔: 100%까지 찬다. - 최대: [limit]% 점선에서 멈추고 번개가 꺼진다. - 기본: 100%까지 찬 뒤 95%로 살짝 내려가 머문다.
 * 물 표면은 천천히 출렁이고, 아래 알약에 결과를 적는다. [choice]가 null(설정이 없거나 값을 모름)이면
 * 20%에서 100%까지 차는 일반 충전만 옅게 그리고 알약을 강조하지 않는다.
 */
@Composable
internal fun ChargeArt(choice: Choice?, limit: Int) {
    val t = artLoop(3400)
    ArtCanvas(118.dp) { c, tm ->
        val w = 190.dp.toPx(); val h = 64.dp.toPx()
        val left = size.width / 2 - w / 2 - 4.dp.toPx(); val top = 10.dp.toPx()
        val target = when (choice) { Choice.MAXIMUM -> limit; else -> 100 } / 100f
        val rise = ease(seg(t, 0.08f, 0.6f))
        var level = 0.2f + (target - 0.2f) * rise
        // 기본: 100%를 찍은 뒤 95%로 내려와 유지
        if (choice == Choice.BASIC) level -= 0.05f * ease(seg(t, 0.68f, 0.85f))
        val charging = if (choice == Choice.BASIC) t < 0.66f else rise < 1f
        val pad = 5.dp.toPx()
        val r = CornerRadius(12.dp.toPx())
        // 몸체·단자
        drawRoundRect(c.ink, Offset(left, top), Size(w, h), r, style = Stroke(3.dp.toPx()))
        drawRoundRect(c.ink, Offset(left + w + 3.dp.toPx(), top + h * 0.32f), Size(7.dp.toPx(), h * 0.36f), CornerRadius(3.dp.toPx()))
        // 물: 오른쪽 끝이 출렁이는 막대
        val inner = w - pad * 2
        val fillRight = left + pad + inner * level
        clipRect(left + pad, top + pad, left + w - pad, top + h - pad) {
            val p = Path().apply {
                moveTo(left + pad, top + pad); lineTo(fillRight, top + pad)
                val steps = 12
                for (k in 0..steps) {
                    val y = top + pad + (h - 2 * pad) * k / steps
                    lineTo(fillRight + sin(k * 0.9f + t * 12.6f) * 2.dp.toPx(), y)
                }
                lineTo(left + pad, top + h - pad); close()
            }
            drawPath(p, c.ink.copy(alpha = 0.85f))
        }
        // 상한 점선(최대일 때)
        if (choice == Choice.MAXIMUM) {
            val x = left + pad + inner * (limit / 100f)
            drawLine(c.muted, Offset(x, top - 4.dp.toPx()), Offset(x, top + h + 4.dp.toPx()), 1.6f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        // 번개(충전 중일 때만)
        if (charging) {
            val cx = left + w / 2; val cy = top + h / 2; val u = 1.dp.toPx()
            val bolt = Path().apply {
                moveTo(cx + 3 * u, cy - 16 * u); lineTo(cx - 8 * u, cy + 2 * u); lineTo(cx - 1 * u, cy + 2 * u)
                lineTo(cx - 4 * u, cy + 16 * u); lineTo(cx + 8 * u, cy - 3 * u); lineTo(cx + 1 * u, cy - 3 * u); close()
            }
            drawPath(bolt, c.surface)
            drawPath(bolt, c.ink, style = Stroke(1.4f * u))
        }
        val text = when {
            choice == Choice.MAXIMUM -> "${limit}%에서 멈춤"
            choice == Choice.BASIC && t > 0.7f -> "95% 근처로 유지"
            else -> "${(level * 100).toInt()}%"
        }
        artPill(tm, c, text, Offset(size.width / 2, top + h + 22.dp.toPx()), filled = choice != null)
    }
}
