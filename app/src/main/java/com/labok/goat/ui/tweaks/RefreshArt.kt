package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import kotlin.math.floor

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 주사율 그림: 같은 공이 두 줄에서 같은 속도로 왼쪽에서 오른쪽으로 갔다가 돌아온다. 화면은 1초에 정해진 장수만 그리므로
 * 공은 '그린 순간'의 자리로만 뚝뚝 옮겨 다니고, 지나온 자리가 잔상으로 남는다. 위 줄([low]Hz)은 장 사이가 넓어 끊겨 보이고,
 * 아래 줄([high]Hz)은 촘촘해 매끄럽다. [selected]가 high면 아래 줄을, low면 위 줄을 진하게 표시한다.
 * 실제 차이를 눈으로 볼 수 있게 그림 속 시간은 느리게(1장 = 실제 [low]Hz 한 장의 6배) 흘린다.
 */
@Composable
internal fun RefreshArt(low: Int, high: Int, selected: Int) {
    val t = artLoop(2400)
    ArtCanvas(132.dp) { c, tm ->
        val left = 28.dp.toPx(); val right = size.width - 92.dp.toPx()
        val r = 8.dp.toPx()
        listOf(low, high).forEachIndexed { i, hz ->
            val y = (26 + i * 60).dp.toPx()
            val on = hz == selected
            // 가는 길
            drawRoundRect(c.line, Offset(left - r, y - 1.dp.toPx()), Size(right - left + 2 * r, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            // 장 수: 왕복 한 번에 low는 12장, high는 그 비율만큼 더 많이 그린다
            val frames = (12f * hz / low).coerceAtLeast(2f)
            fun pos(p: Float): Float { val k = if (p < 0.5f) p * 2 else (1 - p) * 2; return left + (right - left) * ease(k) }
            val step = floor(t * frames) / frames
            // 잔상: 앞서 그린 네 장(옅어지며)
            for (g in 4 downTo 1) {
                val p = step - g / frames
                if (p < 0f) continue
                drawCircle(c.ink.copy(alpha = (if (on) 0.16f else 0.09f) * (5 - g) / 4f), r, Offset(pos(p), y))
            }
            drawCircle(if (on) c.ink else c.muted, r, Offset(pos(step), y))
            artPill(tm, c, "${hz}Hz", Offset(size.width - 52.dp.toPx(), y), filled = on)
            artLabel(tm, "1초에 ${hz}장", Offset(size.width - 52.dp.toPx(), y + 20.dp.toPx()), if (on) c.ink else c.muted, sizeSp = 10)
        }
    }
}
