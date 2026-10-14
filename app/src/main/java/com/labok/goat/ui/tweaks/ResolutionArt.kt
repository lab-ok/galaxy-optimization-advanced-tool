package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.text.Copy
import kotlin.math.hypot

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 해상도 그림: 같은 크기의 동그라미를 두 돋보기 칸에 픽셀로 그린다. 왼쪽(원래)은 픽셀이 촘촘해 가장자리가 매끈하고,
 * 오른쪽(변 [percent]%)은 픽셀이 굵어 계단이 보이는 대신 그리는 점 수가 줄어든다(알약에 점 수 비율, 선택지 이름과 같은 기준).
 * 동그라미가 천천히 숨 쉬듯 커졌다 작아져 계단이 움직이는 것이 보인다.
 */
@Composable
internal fun ResolutionArt(percent: Int) {
    val t = artLoop(2800, reverse = true)
    val p = percent.coerceIn(40, 100) / 100f
    ArtCanvas(170.dp) { c, tm ->
        val box = 112.dp.toPx()
        val top = 8.dp.toPx()
        listOf(0.28f to 1f, 0.72f to p).forEachIndexed { i, (fx, scale) ->
            val left = size.width * fx - box / 2
            val on = i == 1
            drawRoundRect(c.line.copy(alpha = 0.6f), Offset(left, top), Size(box, box), CornerRadius(12.dp.toPx()))
            // 픽셀 크기: 원래 칸을 16칸으로 나누고, 해상도를 낮추면 그 비율만큼 굵어진다
            val cell = box / (16f * scale)
            val r = box * (0.30f + 0.06f * ease(t))
            val cx = left + box / 2
            val cy = top + box / 2
            // 칸을 가운데에서부터 대칭으로 놓아 동그라미가 한쪽으로 치우치지 않게 한다
            val half = (r / cell).toInt() + 1
            for (j in -half until half) {
                for (i in -half until half) {
                    val px = cx + i * cell
                    val py = cy + j * cell
                    if (hypot(px + cell / 2 - cx, py + cell / 2 - cy) <= r) {
                        drawRect(if (on) c.ink else c.muted, Offset(px + 0.6f, py + 0.6f), Size(cell - 1.2f, cell - 1.2f))
                    }
                }
            }
            drawRoundRect(if (on) c.ink else c.line, Offset(left, top), Size(box, box), CornerRadius(12.dp.toPx()), style = Stroke(1.5f.dp.toPx()))
            artPill(tm, c, if (i == 0) "점 100%" else "점 ${Copy.resPoints(percent)}%", Offset(left + box / 2, top + box + 16.dp.toPx()), filled = on)
            artLabel(tm, if (i == 0) "원래 해상도" else "낮춘 해상도", Offset(left + box / 2, top + box + 36.dp.toPx()), if (on) c.ink else c.muted, bold = on)
        }
    }
}
