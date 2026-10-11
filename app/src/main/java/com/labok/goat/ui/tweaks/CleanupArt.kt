package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.art.deviceFrame

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 기본 앱 정리 그림: 폰(외부 화면 비율)의 앱 격자에서 기본 앱 셋이 차례로 흑백으로 작아지며 잠들고(z), 오른쪽 스위치가 꺼진다.
 * 잠시 뒤 스위치를 다시 켜면 하나가 깨어나 원래대로 돌아온다(지우지 않으니 언제든 되돌린다).
 */
@Composable
internal fun CleanupArt() {
    val t = artLoop(4200)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(170.dp) { c, tm ->
        val h = 132.dp.toPx(); val w = h * aspect
        val tl = Offset(size.width * 0.32f - w / 2, 14.dp.toPx())
        val screen = deviceFrame(c, tl, Size(w, h))
        val cell = screen.width / 3
        // 끌 앱: (줄, 칸) 셋. 마지막 하나는 다시 켠다.
        val targets = listOf(0 to 2, 1 to 0, 2 to 1)
        for (r in 0..3) for (col in 0..2) {
            val k = targets.indexOf(r to col)
            val sleep = if (k < 0) 0f else {
                val off = ease(seg(t, 0.12f + k * 0.12f, 0.24f + k * 0.12f))
                val back = if (k == 2) ease(seg(t, 0.7f, 0.82f)) else 0f
                off * (1 - back)
            }
            val center = Offset(screen.left + cell * (col + 0.5f), screen.top + cell * 0.6f + r * cell * 0.92f)
            val s = cell * 0.58f * (1 - 0.25f * sleep)
            val col0 = if (k < 0) c.muted.copy(alpha = 0.55f) else c.ink
            drawRoundRect(
                if (sleep > 0f) c.line.copy(alpha = 0.4f + 0.6f * sleep) else col0,
                center - Offset(s / 2, s / 2), Size(s, s), CornerRadius(s * 0.26f),
            )
            if (sleep > 0.6f) artLabel(tm, "z", center + Offset(s * 0.55f, -s * 0.55f), c.muted.copy(alpha = (sleep - 0.6f) / 0.4f), 9, bold = true)
        }
        // 오른쪽: 마지막 앱의 스위치(꺼졌다가 다시 켜진다)
        val k = 2
        val off = ease(seg(t, 0.12f + k * 0.12f, 0.24f + k * 0.12f)) * (1 - ease(seg(t, 0.7f, 0.82f)))
        val sx = size.width * 0.74f; val sy = size.height * 0.42f
        val sw = 46.dp.toPx(); val sh = 24.dp.toPx()
        drawRoundRect(if (off > 0.5f) c.line else c.ink, Offset(sx - sw / 2, sy - sh / 2), Size(sw, sh), CornerRadius(sh / 2))
        drawCircle(c.surface, sh * 0.38f, Offset(sx + (sw / 2 - sh / 2) * (1 - 2 * off), sy))
        artLabel(tm, "게임 런처", Offset(sx, sy - 26.dp.toPx()), c.ink, bold = true)
        artPill(tm, c, if (off > 0.5f) "꺼 둠" else "켜짐", Offset(sx, sy + 30.dp.toPx()), filled = off <= 0.5f)
        artLabel(tm, "지우지 않고 꺼 둠", Offset(sx, size.height - 14.dp.toPx()), c.muted)
    }
}
