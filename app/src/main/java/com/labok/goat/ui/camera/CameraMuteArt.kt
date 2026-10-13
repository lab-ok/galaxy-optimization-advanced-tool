package com.labok.goat.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * 촬영음 그림: 셔터를 누르면 왼쪽(끄기 전)은 '찰칵' 소리 물결이 퍼지고, 오른쪽(끈 뒤)은 물결 대신 줄 그은 스피커만 남는다.
 */
@Composable
internal fun ShutterArt() {
    val t = artLoop(2600)
    ArtCanvas(150.dp) { c, tm ->
        val flash = seg(t, 0.3f, 0.36f) - seg(t, 0.4f, 0.55f)
        val wave = seg(t, 0.32f, 0.9f)
        listOf(0.27f to false, 0.73f to true).forEach { (fx, muted) ->
            val center = Offset(size.width * fx, size.height * 0.48f)
            val w = 76.dp.toPx()
            val h = 52.dp.toPx()
            val tl = Offset(center.x - w / 2, center.y - h / 2)
            // 카메라 몸체·렌즈·셔터 버튼
            drawRoundRect(c.ink, Offset(tl.x + w * 0.62f, tl.y - 7.dp.toPx()), Size(w * 0.2f, 9.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(c.ink, tl, Size(w, h), CornerRadius(10.dp.toPx()))
            drawCircle(c.surface, h * 0.32f, center)
            drawCircle(c.ink, h * 0.2f, center)
            if (flash > 0f) drawCircle(c.surface.copy(alpha = flash), h * 0.2f, center)
            // 소리: 끄기 전엔 물결, 끈 뒤엔 줄 그은 스피커
            val sx = center.x + w / 2 + 16.dp.toPx()
            val s = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
            if (!muted) {
                for (k in 0..2) {
                    val r = (8 + k * 8).dp.toPx() * (0.6f + 0.4f * wave)
                    val a = (1f - wave) * (1f - k * 0.25f)
                    if (wave > 0f) drawArc(c.ink.copy(alpha = a), -40f, 80f, false, Offset(sx - r, center.y - r), Size(r * 2, r * 2), style = s)
                }
            } else {
                // 스피커(네모 + 나팔)와 그 위를 가로지르는 사선
                val u = 1.dp.toPx()
                val horn = Path().apply {
                    moveTo(sx - 2 * u, center.y - 4 * u)
                    lineTo(sx + 2 * u, center.y - 4 * u)
                    lineTo(sx + 9 * u, center.y - 10 * u)
                    lineTo(sx + 9 * u, center.y + 10 * u)
                    lineTo(sx + 2 * u, center.y + 4 * u)
                    lineTo(sx - 2 * u, center.y + 4 * u)
                    close()
                }
                drawPath(horn, c.ink)
                drawLine(c.surface, Offset(sx - 6 * u, center.y - 12 * u), Offset(sx + 13 * u, center.y + 12 * u), s.width * 2.2f, StrokeCap.Round)
                drawLine(c.ink, Offset(sx - 6 * u, center.y - 12 * u), Offset(sx + 13 * u, center.y + 12 * u), s.width, StrokeCap.Round)
            }
            artPill(tm, c, if (muted) "무음" else "찰칵", Offset(center.x, size.height - 14.dp.toPx()), filled = muted)
        }
        artLabel(tm, "끄기 전", Offset(size.width * 0.27f, 12.dp.toPx()), c.muted)
        artLabel(tm, "끈 뒤(무음 모드)", Offset(size.width * 0.73f, 12.dp.toPx()), c.ink, bold = true)
    }
}
