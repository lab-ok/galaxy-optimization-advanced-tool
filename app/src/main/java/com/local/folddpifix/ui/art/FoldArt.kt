package com.local.folddpifix.ui.art

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import com.local.folddpifix.ui.liquid.rememberBreath
import com.local.folddpifix.ui.liquid.Springs

/**
 * 상태 카드의 폴드 기기 그림. [unfolded]가 바뀌면 기기가 실제처럼 펼쳐지거나 접힌다(물 스프링).
 * 켜진 화면 안에는 같은 크기의 아이콘 줄이 보이고, 화면 가장자리에 물빛 테두리가 천천히 숨 쉰다.
 */
@Composable
fun FoldDeviceArt(
    unfolded: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    coverAspect: Float = FoldGeometry.COVER_ASPECT,
    innerAspect: Float = FoldGeometry.INNER_ASPECT,
    /** true면 테두리 숨쉬기를 멈춘다(사용 중이 아닌 화면). */
    still: Boolean = false,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val open = remember { Animatable(if (unfolded) 1f else 0f) }
    LaunchedEffect(unfolded) { open.animateTo(if (unfolded) 1f else 0f, Springs.water(reduce)) }
    val breathe = rememberBreath(unfolded)
    val glow = if (reduce || still) 0f else breathe

    // 폴드는 접었을 때와 펼쳤을 때 기기 높이가 같다. 화면 가로세로비(기기 실제 해상도)로 폭만 달라진다.
    val boxW = height * (innerAspect + 0.12f)
    Box(modifier.width(boxW).height(height)) {
        Canvas(Modifier.width(boxW).height(height)) {
            val t = open.value
            val h = size.height * 0.96f
            val bezel = h * 0.035f
            val coverW = (h - bezel * 2) * coverAspect + bezel * 2
            val innerW = (h - bezel * 2) * innerAspect + bezel * 2
            val w = coverW + (innerW - coverW) * t
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            val screen = deviceFrame(c, Offset(left, top), Size(w, h), glow = glow)
            val sl = screen.left
            val st = screen.top
            val sw = screen.width
            val sh = screen.height
            // 접히는 선(펼칠수록 드러남)
            if (t > 0.05f) {
                val cx = left + w / 2
                drawLine(c.ink.copy(alpha = 0.18f * t), Offset(cx, st + sh * 0.04f), Offset(cx, st + sh * 0.96f), 1.5.dp.toPx())
            }
            iconRows(Offset(sl, st), Size(sw, sh), h * 0.085f, c.ink.copy(alpha = 0.55f))
        }
    }
}

/** 화면 안 아이콘 격자와 글자 줄. 아이콘 크기는 화면 크기와 관계없이 같다(= 두 화면 크기를 맞춘 상태). */
internal fun DrawScope.iconRows(topLeft: Offset, area: Size, s: Float, color: Color, rows: Int = 3) {
    if (s < 1f) return
    val gap = s * 0.55f
    var y = topLeft.y + gap * 1.3f
    repeat(rows) {
        var x = topLeft.x + gap
        while (x + s <= topLeft.x + area.width - gap * 0.6f) {
            drawRoundRect(color, Offset(x, y), Size(s, s), CornerRadius(s * 0.3f))
            x += s + gap
        }
        y += s + gap
    }
    val lineH = s * 0.28f
    repeat(2) { i ->
        if (y + lineH > topLeft.y + area.height - gap) return
        val w = (area.width - gap * 2) * (if (i == 1) 0.55f else 0.9f)
        drawRoundRect(color.copy(alpha = color.alpha * 0.6f), Offset(topLeft.x + gap, y), Size(w, lineH), CornerRadius(lineH / 2))
        y += lineH * 2.2f
    }
}
