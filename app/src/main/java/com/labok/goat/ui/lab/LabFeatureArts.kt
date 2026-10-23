package com.labok.goat.ui.lab

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

private fun seg(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))

/**
 * 비행기 모드 무선 그림(시안 2 '토글과 남는 무선', 2026-10-23): 비행기 모드 스위치가 켜지면 아래 세 무선 가운데 켜 두기로 고른 것만
 * 그대로 남고 나머지는 흐려진다. [kept]는 Wi-Fi·블루투스·NFC 순으로 켜 두는지(지금 설정 그대로 보여 준다).
 */
@Composable
internal fun AirplaneArt(kept: List<Boolean>, labels: List<String>) {
    val t = artLoop(3200)
    ArtCanvas(132.dp) { c, tm ->
        // 0.1~0.35 켜짐, 0.75~0.95 꺼짐
        val k = if (t < 0.6f) seg(t, 0.1f, 0.35f) else 1f - seg(t, 0.75f, 0.95f)
        val w = 150.dp.toPx()
        val h = 36.dp.toPx()
        val left = (size.width - w) / 2
        val top = 4.dp.toPx()
        drawRoundRect(lerp(c.line, c.ink, k), Offset(left, top), Size(w, h), CornerRadius(h / 2))
        val knob = h / 2 - 4.dp.toPx()
        drawCircle(c.onInk, knob, Offset(left + h / 2 + (w - h) * k, top + h / 2))
        plane(Offset(size.width / 2, top + h / 2), 0.9f, lerp(c.muted, c.onInk, k))
        val y = top + h + 40.dp.toPx()
        val gap = size.width / 4
        kept.forEachIndexed { i, keep ->
            val col = if (keep) c.ink else lerp(c.ink, c.line, k)
            val x = gap * (i + 1)
            when (i) {
                0 -> wifi(Offset(x, y), col)
                1 -> bluetooth(Offset(x, y), col)
                else -> nfc(Offset(x, y), col)
            }
            artLabel(tm, labels.getOrElse(i) { "" }, Offset(x, y + 30.dp.toPx()), col, sizeSp = 10)
        }
    }
}

/**
 * 상태 표시줄 그림(시안 3 '물방울로 떨어짐', 2026-10-23): 상단바에 늘어선 아이콘 가운데 숨길 것들이 물방울로 녹아 떨어져 사라지고,
 * 잠시 뒤 다시 맺힌다.
 */
@Composable
internal fun StatusDropArt() {
    val t = artLoop(3400)
    ArtCanvas(132.dp) { c, _ ->
        val barW = size.width * 0.8f
        val left = (size.width - barW) / 2
        val top = 8.dp.toPx()
        val barH = 26.dp.toPx()
        drawRoundRect(c.surface, Offset(left, top), Size(barW, barH), CornerRadius(9.dp.toPx()))
        drawRoundRect(c.ink.copy(alpha = 0.7f), Offset(left + 10.dp.toPx(), top + 10.dp.toPx()), Size(28.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        val drop = setOf(1, 2, 4)
        val step = 22.dp.toPx()
        val cy = top + barH / 2
        for (i in 0 until 6) {
            val x = left + barW - 18.dp.toPx() - (5 - i) * step
            if (i in drop) {
                // 떨어짐(0.15~0.6)과 다시 맺힘(0.8~1)
                val start = 0.15f + i * 0.05f
                val fall = seg(t, start, start + 0.4f)
                val back = seg(t, 0.82f, 0.98f)
                if (fall < 0.02f || back > 0f) {
                    glyph(i, Offset(x, cy), c.ink.copy(alpha = if (back > 0f) back else 1f))
                } else {
                    val y = cy + fall * 80.dp.toPx()
                    val r = 6.dp.toPx() * (1f - fall * 0.5f)
                    // 물방울: 위가 뾰족하게 늘어난다
                    val p = Path().apply {
                        moveTo(x, y - r * 2.2f)
                        cubicTo(x + r, y - r, x + r, y + r, x, y + r)
                        cubicTo(x - r, y + r, x - r, y - r, x, y - r * 2.2f)
                    }
                    drawPath(p, c.ink.copy(alpha = 1f - fall * 0.8f))
                }
            } else {
                glyph(i, Offset(x, cy), c.ink)
            }
        }
    }
}

private fun lerp(a: Color, b: Color, k: Float) = Color(
    a.red + (b.red - a.red) * k,
    a.green + (b.green - a.green) * k,
    a.blue + (b.blue - a.blue) * k,
    a.alpha + (b.alpha - a.alpha) * k
)

private fun DrawScope.plane(c: Offset, s: Float, col: Color) {
    val u = 1.dp.toPx() * s
    val pts = listOf(16f to 0f, 4f to 3f, -6f to 12f, -9f to 12f, -2f to 3f, -10f to 3f, -13f to 8f, -15f to 8f, -13f to 0f, -15f to -8f, -13f to -8f, -10f to -3f, -2f to -3f, -9f to -12f, -6f to -12f, 4f to -3f)
    val p = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(c.x + x * u, c.y + y * u) else lineTo(c.x + x * u, c.y + y * u) }
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.wifi(c: Offset, col: Color) {
    val s = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
    listOf(20f, 13f, 6f).forEach { r ->
        val rr = r.dp.toPx()
        drawArc(col, 225f, 90f, false, Offset(c.x - rr, c.y + 8.dp.toPx() - rr), Size(rr * 2, rr * 2), style = s)
    }
    drawCircle(col, 2.5.dp.toPx(), Offset(c.x, c.y + 8.dp.toPx()))
}

private fun DrawScope.bluetooth(c: Offset, col: Color) {
    val u = 1.dp.toPx()
    val p = Path().apply {
        moveTo(c.x - 7 * u, c.y - 7 * u)
        lineTo(c.x + 7 * u, c.y + 7 * u)
        lineTo(c.x, c.y + 14 * u)
        lineTo(c.x, c.y - 14 * u)
        lineTo(c.x + 7 * u, c.y - 7 * u)
        lineTo(c.x - 7 * u, c.y + 7 * u)
    }
    drawPath(p, col, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.nfc(c: Offset, col: Color) {
    val u = 1.dp.toPx()
    val s = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
    drawRoundRect(col, Offset(c.x - 11 * u, c.y - 11 * u), Size(22 * u, 22 * u), CornerRadius(5 * u), style = s)
    drawArc(col, -60f, 120f, false, Offset(c.x - 9 * u, c.y - 6 * u), Size(12 * u, 12 * u), style = s)
    drawArc(col, 120f, 120f, false, Offset(c.x - 3 * u, c.y - 6 * u), Size(12 * u, 12 * u), style = s)
}

/** 상단바 아이콘 자리표시(알람·무음·블루투스·NFC·위치·VoLTE를 단순한 모양으로). */
private fun DrawScope.glyph(i: Int, c: Offset, col: Color) {
    val u = 1.dp.toPx()
    val s = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
    when (i % 6) {
        0 -> {
            drawCircle(col, 6.5f * u, c, style = s)
            drawLine(col, c, Offset(c.x, c.y - 4 * u), 1.8f * u, StrokeCap.Round)
            drawLine(col, c, Offset(c.x + 3 * u, c.y), 1.8f * u, StrokeCap.Round)
        }
        1 -> {
            drawArc(col, 180f, 180f, false, Offset(c.x - 6 * u, c.y - 6 * u), Size(12 * u, 12 * u), style = s)
            drawLine(col, Offset(c.x - 6 * u, c.y + 5 * u), Offset(c.x + 6 * u, c.y - 5 * u), 1.8f * u, StrokeCap.Round)
        }
        2 -> {
            val p = Path().apply {
                moveTo(c.x - 4 * u, c.y - 4 * u)
                lineTo(c.x + 4 * u, c.y + 4 * u)
                lineTo(c.x, c.y + 8 * u)
                lineTo(c.x, c.y - 8 * u)
                lineTo(c.x + 4 * u, c.y - 4 * u)
                lineTo(c.x - 4 * u, c.y + 4 * u)
            }
            drawPath(p, col, style = s)
        }
        3 -> drawRoundRect(col, Offset(c.x - 6 * u, c.y - 6 * u), Size(12 * u, 12 * u), CornerRadius(3 * u), style = s)
        4 -> {
            drawCircle(col, 4.5f * u, Offset(c.x, c.y - 2 * u), style = s)
            drawLine(col, Offset(c.x, c.y + 2.5f * u), Offset(c.x, c.y + 7 * u), 1.8f * u, StrokeCap.Round)
        }
        else -> {
            for (k in 0 until 3) {
                val a = (k * 120f + 30f) * PI.toFloat() / 180f
                drawCircle(col, 1.6f * u, Offset(c.x + 5 * u * cos(a), c.y + 5 * u * sin(a)))
            }
        }
    }
}
