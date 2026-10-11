package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import kotlin.math.sin

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * GOS 그림: 같은 게임을 켠 두 화면의 프레임 그래프가 왼쪽에서 오른쪽으로 그려진다.
 * 왼쪽(GOS 켜짐)은 조금 지나면 GOS가 성능을 묶어 그래프가 아래(제한선)로 내려가고 온도계는 조금만 오른다.
 * 오른쪽(GOS 꺼짐)은 그래프가 끝까지 높게 유지되는 대신 온도계가 더 높이 오른다. [off]면 오른쪽을, 아니면 왼쪽을 진하게
 * 표시하고, [none](GOS가 없는 기기)이면 어느 쪽도 강조하지 않는다.
 */
@Composable
internal fun GosArt(off: Boolean, none: Boolean = false) {
    val t = artLoop(3600)
    ArtCanvas(150.dp) { c, tm ->
        val w = size.width / 2 - 24.dp.toPx()
        val h = 78.dp.toPx()
        val top = 16.dp.toPx()
        listOf(false, true).forEachIndexed { i, gosOff ->
            val left = 12.dp.toPx() + i * (size.width / 2)
            val on = !none && gosOff == off
            val ink = if (on) c.ink else c.muted
            val graphW = w - 22.dp.toPx()
            drawRoundRect(c.line.copy(alpha = 0.5f), Offset(left, top), Size(graphW, h), CornerRadius(8.dp.toPx()))
            // 제한선(GOS가 묶는 높이)
            val capY = top + h * 0.45f
            if (!gosOff) drawLine(c.muted.copy(alpha = 0.6f), Offset(left + 4.dp.toPx(), capY), Offset(left + graphW - 4.dp.toPx(), capY), 1.dp.toPx())
            // 프레임 그래프: 진행 t만큼만 그린다. GOS 켜짐은 0.35 지점부터 제한선으로 내려간다.
            val path = Path()
            val n = 48
            val shown = (n * ease((t / 0.8f).coerceAtMost(1f))).toInt().coerceAtLeast(1)
            for (k in 0..shown) {
                val x = k / n.toFloat()
                val wobble = sin(x * 31f) * 0.03f + sin(x * 13f) * 0.02f
                val level = if (gosOff) 0.86f else 0.86f - 0.3f * ease(((x - 0.35f) / 0.25f).coerceIn(0f, 1f))
                val px = left + 6.dp.toPx() + (graphW - 12.dp.toPx()) * x
                val py = top + h * (1 - level - wobble)
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            clipRect(left, top, left + graphW, top + h) { drawPath(path, ink, style = Stroke(2.2f.dp.toPx(), cap = StrokeCap.Round)) }
            // 온도계: GOS 꺼짐이 더 높이 오른다
            val tx = left + graphW + 12.dp.toPx()
            val tubeTop = top + 6.dp.toPx(); val tubeBottom = top + h - 14.dp.toPx()
            drawRoundRect(c.line, Offset(tx - 3.dp.toPx(), tubeTop), Size(6.dp.toPx(), tubeBottom - tubeTop), CornerRadius(3.dp.toPx()))
            val heat = ease((t / 0.8f).coerceAtMost(1f)) * (if (gosOff) 0.9f else 0.45f)
            val fillTop = tubeBottom - (tubeBottom - tubeTop) * heat
            drawRoundRect(ink, Offset(tx - 3.dp.toPx(), fillTop), Size(6.dp.toPx(), tubeBottom - fillTop), CornerRadius(3.dp.toPx()))
            drawCircle(ink, 6.dp.toPx(), Offset(tx, tubeBottom + 3.dp.toPx()))
            val fps = if (gosOff) "높은 성능" else "성능 제한"
            artPill(tm, c, fps, Offset(left + graphW / 2, top + h + 16.dp.toPx()), filled = on)
            artLabel(tm, if (gosOff) "GOS 꺼짐" else "GOS 켜짐", Offset(left + graphW / 2, top + h + 37.dp.toPx()), ink, bold = on)
        }
    }
}
