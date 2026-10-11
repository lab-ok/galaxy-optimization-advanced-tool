package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.labok.goat.data.tweaks.StatusIcons.Icon
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/** 그림에 그리는 아이콘(왼쪽부터). 와이파이·배터리는 늘 남는다. */
private val SHOWN = listOf(Icon.NFC, Icon.ALARM, Icon.BLUETOOTH, Icon.VOLUME, Icon.LOCATION)

/**
 * 상태 표시줄 그림: 위 줄은 지금 상태 표시줄(시계, 아이콘 다섯, 와이파이, 배터리). 0.35~0.6에 [hidden] 아이콘이
 * 톡 오그라들어 사라지고, 남은 아이콘이 오른쪽으로 모여 빈자리를 메운다. 끝에서 다시 나타나 반복한다.
 * 숨길 아이콘을 아직 고르지 않았으면 NFC·알람으로 보여 준다.
 */
@Composable
internal fun StatusIconsArt(hidden: Set<Icon>) {
    val t = artLoop(3000)
    val gone = SHOWN.filter { it in hidden }.ifEmpty { listOf(Icon.NFC, Icon.ALARM) }.toSet()
    ArtCanvas(112.dp) { c, tm ->
        val barH = 34.dp.toPx()
        val top = 18.dp.toPx()
        val left = 12.dp.toPx(); val right = size.width - 12.dp.toPx()
        // 상태 표시줄 바탕(폰 화면 윗부분)
        drawRoundRect(c.ink, Offset(left, top), Size(right - left, barH + 26.dp.toPx()), CornerRadius(18.dp.toPx()))
        drawRect(c.surface, Offset(left + 6.dp.toPx(), top + barH), Size(right - left - 12.dp.toPx(), 26.dp.toPx()))
        val cy = top + barH / 2
        artLabel(tm, "12:30", Offset(left + 34.dp.toPx(), cy), c.surface, sizeSp = 13, bold = true)
        // 숨김 진행: 0.35~0.6에 사라지고 0.9~1에 다시 나타난다
        val out = ease(seg(t, 0.35f, 0.6f)) * (1 - ease(seg(t, 0.9f, 1f)))
        val cell = 22.dp.toPx()
        // 오른쪽부터: 배터리, 와이파이, 그다음 아이콘들(사라지는 만큼 칸이 줄어 남은 것이 오른쪽으로 모인다)
        var x = right - 24.dp.toPx()
        battery(c.surface, Offset(x, cy)); x -= cell
        wifi(c.surface, Offset(x, cy)); x -= cell
        for (icon in SHOWN.reversed()) {
            val k = if (icon in gone) 1 - out else 1f
            if (k > 0.01f) scale(k, Offset(x, cy)) { glyph(icon, c.surface, Offset(x, cy)) }
            x -= cell * k
        }
        artLabel(tm, if (out > 0.5f) "숨긴 뒤" else "숨기기 전", Offset(size.width / 2, top + barH + 13.dp.toPx()), c.muted, sizeSp = 10)
    }
}

/** 아이콘 하나(지름 약 14dp, 가운데 [o]). */
private fun DrawScope.glyph(icon: Icon, col: Color, o: Offset) {
    val u = 1.dp.toPx()
    val s = Stroke(1.6f * u, cap = StrokeCap.Round)
    when (icon) {
        Icon.NFC -> {
            drawRoundRect(col, Offset(o.x - 6 * u, o.y - 6 * u), Size(12 * u, 12 * u), CornerRadius(3 * u), style = s)
            val p = Path().apply { moveTo(o.x - 2.5f * u, o.y + 3 * u); lineTo(o.x - 2.5f * u, o.y - 3 * u); lineTo(o.x + 2.5f * u, o.y + 3 * u); lineTo(o.x + 2.5f * u, o.y - 3 * u) }
            drawPath(p, col, style = s)
        }
        Icon.ALARM -> {
            drawCircle(col, 5.5f * u, o, style = s)
            drawLine(col, o, o + Offset(0f, -3 * u), s.width, StrokeCap.Round)
            drawLine(col, o, o + Offset(2.5f * u, 1.5f * u), s.width, StrokeCap.Round)
        }
        Icon.BLUETOOTH -> {
            val p = Path().apply {
                moveTo(o.x - 4 * u, o.y - 3 * u); lineTo(o.x + 4 * u, o.y + 3 * u); lineTo(o.x, o.y + 6.5f * u); lineTo(o.x, o.y - 6.5f * u)
                lineTo(o.x + 4 * u, o.y - 3 * u); lineTo(o.x - 4 * u, o.y + 3 * u)
            }
            drawPath(p, col, style = s)
        }
        Icon.VOLUME -> {
            val p = Path().apply {
                moveTo(o.x - 6 * u, o.y - 2.5f * u); lineTo(o.x - 3 * u, o.y - 2.5f * u); lineTo(o.x + 1.5f * u, o.y - 6 * u)
                lineTo(o.x + 1.5f * u, o.y + 6 * u); lineTo(o.x - 3 * u, o.y + 2.5f * u); lineTo(o.x - 6 * u, o.y + 2.5f * u); close()
            }
            drawPath(p, col)
            drawLine(col, o + Offset(4 * u, -3 * u), o + Offset(7 * u, 3 * u), s.width, StrokeCap.Round)
            drawLine(col, o + Offset(7 * u, -3 * u), o + Offset(4 * u, 3 * u), s.width, StrokeCap.Round)
        }
        else -> {
            // 위치: 핀
            val p = Path().apply {
                moveTo(o.x, o.y + 6.5f * u)
                cubicTo(o.x - 7 * u, o.y - 1 * u, o.x - 4.5f * u, o.y - 6.5f * u, o.x, o.y - 6.5f * u)
                cubicTo(o.x + 4.5f * u, o.y - 6.5f * u, o.x + 7 * u, o.y - 1 * u, o.x, o.y + 6.5f * u); close()
            }
            drawPath(p, col, style = s)
            drawCircle(col, 1.6f * u, o + Offset(0f, -2 * u))
        }
    }
}

private fun DrawScope.wifi(col: Color, o: Offset) {
    val u = 1.dp.toPx()
    val s = Stroke(1.6f * u, cap = StrokeCap.Round)
    for (r in listOf(3.5f, 6.5f, 9.5f)) drawArc(col, 225f, 90f, false, Offset(o.x - r * u, o.y + 4 * u - r * u), Size(2 * r * u, 2 * r * u), style = s)
    drawCircle(col, 1.4f * u, o + Offset(0f, 4 * u))
}

private fun DrawScope.battery(col: Color, o: Offset) {
    val u = 1.dp.toPx()
    drawRoundRect(col, Offset(o.x - 8 * u, o.y - 4.5f * u), Size(15 * u, 9 * u), CornerRadius(2.5f * u), style = Stroke(1.4f * u))
    drawRoundRect(col, Offset(o.x - 6 * u, o.y - 2.5f * u), Size(9 * u, 5 * u), CornerRadius(1 * u))
    drawRoundRect(col, Offset(o.x + 7.5f * u, o.y - 2 * u), Size(1.6f * u, 4 * u), CornerRadius(0.8f * u))
}
