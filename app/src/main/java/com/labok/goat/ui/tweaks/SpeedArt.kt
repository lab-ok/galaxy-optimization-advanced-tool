package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.realTimeLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.art.deviceFrame
import com.labok.goat.ui.liquid.LiquidColors

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 애니메이션 속도 그림: 두 폰(외부 화면 비율)에서 같은 앱 아이콘을 누르면 창이 아이콘에서 화면 가득 열린다.
 * 왼쪽은 기본(1배), 오른쪽은 고른 값([scale])이라 더 빨리(또는 0이면 바로) 다 열린다. 아래 알약에 걸린 시간을 적는다.
 */
@Composable
internal fun SpeedArt(scale: Float) {
    // 시스템 애니메이션 배율이 이 그림에도 걸리면 고른 배율이 두 번 적용돼 보이므로 실제 시간으로 움직인다
    val t = realTimeLoop(2600)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(170.dp) { c, tm ->
        val h = 112.dp.toPx(); val w = h * aspect
        val top = 12.dp.toPx()
        listOf(0.3f to 1f, 0.7f to scale).forEachIndexed { i, (fx, s) ->
            val tl = Offset(size.width * fx - w / 2, top)
            val screen = deviceFrame(c, tl, Size(w, h), emphasis = if (i == 1) 1f else 0.6f)
            appGrid(c, screen)
            // 0.15초에 누르고, 1배면 0.36초 동안 열린다(그림 속도). 0이면 바로.
            val open = when {
                t < 0.15f -> 0f
                s <= 0f -> 1f
                else -> ease(((t - 0.15f) / (0.36f * s)).coerceIn(0f, 1f))
            }
            // 다 열린 모습을 잠깐 보여 준 뒤 닫혀(사라져) 다음 반복을 준비한다
            val close = ((t - 0.78f) / 0.14f).coerceIn(0f, 1f)
            if (t >= 0.12f && close < 1f) window(c, screen, open * (1 - close))
            val ms = (300 * s).toInt()
            artPill(tm, c, if (s <= 0f) "바로" else "${ms}ms", Offset(screen.center.x, top + h + 16.dp.toPx()), filled = i == 1)
            artLabel(tm, if (i == 0) "기본" else AnimationSpeed.label(s), Offset(screen.center.x, top + h + 36.dp.toPx()), if (i == 1) c.ink else c.muted, bold = i == 1)
        }
    }
}

/** 화면 속 앱 아이콘 격자(3×3). 가운데 아이콘이 누를 앱. */
private fun DrawScope.appGrid(c: LiquidColors, screen: Rect) {
    val cell = screen.width / 3
    for (r in 0..2) for (col in 0..2) {
        val center = Offset(screen.left + cell * (col + 0.5f), screen.top + screen.height * 0.22f + r * cell)
        drawRoundRect(if (r == 1 && col == 1) c.ink else c.muted.copy(alpha = 0.55f), center - Offset(cell * 0.3f, cell * 0.3f), Size(cell * 0.6f, cell * 0.6f), CornerRadius(cell * 0.16f))
    }
}

/** 가운데 아이콘에서 화면 가득 열리는 앱 창([p] 0→1). */
private fun DrawScope.window(c: LiquidColors, screen: Rect, p: Float) {
    val cell = screen.width / 3
    val from = Rect(Offset(screen.center.x, screen.top + screen.height * 0.22f + cell) - Offset(cell * 0.3f, cell * 0.3f), Size(cell * 0.6f, cell * 0.6f))
    val l = from.left + (screen.left - from.left) * p
    val tp = from.top + (screen.top - from.top) * p
    val r = from.right + (screen.right - from.right) * p
    val b = from.bottom + (screen.bottom - from.bottom) * p
    drawRoundRect(c.ink, Offset(l, tp), Size(r - l, b - tp), CornerRadius(cell * 0.16f * (1 - p) + 2.dp.toPx() * p))
    if (p > 0.6f) {
        val a = (p - 0.6f) / 0.4f
        drawRoundRect(c.surface.copy(alpha = a), Offset(l + (r - l) * 0.12f, tp + (b - tp) * 0.12f), Size((r - l) * 0.76f, (b - tp) * 0.1f), CornerRadius(2.dp.toPx()))
        drawRoundRect(c.surface.copy(alpha = a * 0.6f), Offset(l + (r - l) * 0.12f, tp + (b - tp) * 0.3f), Size((r - l) * 0.5f, (b - tp) * 0.06f), CornerRadius(2.dp.toPx()))
    }
}
