package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop

/**
 * 애니메이션 속도 그림: 위는 기본(1배), 아래는 고른 값([scale])으로 같은 창이 열린다. 값이 작을수록 아래 창이 먼저 다 열린다.
 * 0이면 아래 창은 움직임 없이 바로 열려 있다.
 */
@Composable
internal fun SpeedArt(scale: Float) {
    val t = artLoop(2400)
    ArtCanvas(120.dp) { c, tm ->
        fun lane(y: Float, s: Float, label: String, bold: Boolean) {
            val p = if (s <= 0f) 1f else (t / (0.7f * s)).coerceIn(0f, 1f)
            val e = p * p * (3 - 2 * p)
            val left = 92.dp.toPx(); val full = size.width - left - 16.dp.toPx()
            val h = 30.dp.toPx()
            drawRoundRect(c.line, Offset(left, y - h / 2), Size(full, h), CornerRadius(8.dp.toPx()))
            drawRoundRect(c.ink, Offset(left, y - h / 2), Size((full * e).coerceAtLeast(h), h), CornerRadius(8.dp.toPx()))
            artLabel(tm, label, Offset(46.dp.toPx(), y), if (bold) c.ink else c.muted, bold = bold)
        }
        lane(size.height * 0.3f, 1f, "기본", false)
        lane(size.height * 0.72f, scale, com.labok.goat.data.tweaks.AnimationSpeed.label(scale), true)
    }
}
