package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import kotlin.math.floor

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 주사율 그림(시안 8 '글 목록 밀기', 2026-10-23): 두 화면에서 손가락이 같은 글 목록을 같은 속도로 밀어 올린다. 화면은 1초에 정해진 장수만
 * 그리므로 목록은 그린 순간의 자리로만 뚝뚝 옮겨 다닌다. 왼쪽([low]Hz)은 장 사이가 넓어 글줄이 겹쳐 번져 보이고, 오른쪽([high]Hz)은
 * 촘촘해 또렷하다. [selected]와 같은 쪽 화면을 진하게 표시하고, 그 밖의 값(시스템 기본 등)이면 두 화면을 같은 굵기로 둔다.
 * 실제 차이가 눈에 보이도록 그림 속 시간은 느리게 흘린다(밀어 올리기 한 번에 [low]Hz 화면은 8장).
 */
@Composable
internal fun RefreshArt(low: Int, high: Int, selected: Int) {
    val t = artLoop(2600)
    ArtCanvas(150.dp) { c, tm ->
        val phoneW = 92.dp.toPx()
        val phoneH = 118.dp.toPx()
        val gap = 28.dp.toPx()
        val x0 = (size.width - phoneW * 2 - gap) / 2
        // 0~0.7 밀어 올리기, 나머지는 멈춰 있다가 처음 자리로
        val push = ease((t / 0.7f).coerceIn(0f, 1f))
        listOf(low, high).forEachIndexed { i, hz ->
            val on = hz == selected
            val left = x0 + i * (phoneW + gap)
            val top = 4.dp.toPx()
            val ink = if (on) c.ink else c.muted
            drawRoundRect(c.surface, Offset(left, top), Size(phoneW, phoneH), CornerRadius(14.dp.toPx()))
            drawRoundRect(ink.copy(alpha = if (on) 1f else 0.6f), Offset(left, top), Size(phoneW, phoneH), CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
            // 그린 장 수: low는 밀기 한 번에 8장, high는 그 비율만큼
            val frames = 8f * hz / low
            val shown = floor(push * frames) / frames
            val travel = 40.dp.toPx()
            // 번짐: 낮은 주사율은 앞서 그린 두 장이 겹쳐 남아 보인다
            val ghosts = if (hz == low) 2 else 0
            clipRect(left + 3.dp.toPx(), top + 3.dp.toPx(), left + phoneW - 3.dp.toPx(), top + phoneH - 3.dp.toPx()) {
                for (g in ghosts downTo 0) {
                    val p = (shown - g / frames).coerceAtLeast(0f)
                    val alpha = if (g == 0) 1f else 0.28f / g
                    for (k in 0 until 7) {
                        val y = top + 14.dp.toPx() + k * 17.dp.toPx() - travel * p
                        val w = phoneW * (0.62f - (k % 3) * 0.12f)
                        drawRoundRect(c.ink.copy(alpha = (if (on) 0.55f else 0.35f) * alpha), Offset(left + 12.dp.toPx(), y), Size(w, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                }
                // 미는 손가락
                val fy = top + phoneH - 22.dp.toPx() - travel * shown
                if (t < 0.75f) drawCircle(c.ink.copy(alpha = 0.35f), 9.dp.toPx(), Offset(left + phoneW - 20.dp.toPx(), fy))
            }
            artPill(tm, c, "${hz}Hz", Offset(left + phoneW / 2, top + phoneH + 14.dp.toPx()), filled = on)
        }
    }
}
