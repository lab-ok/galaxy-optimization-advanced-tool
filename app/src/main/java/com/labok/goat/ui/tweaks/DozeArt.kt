package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill

private fun easeD(x: Float) = x * x * (3 - 2 * x)

/**
 * 잠자기 모드 그림: 화면이 꺼진 두 폰에 같은 시각에 메시지 세 개가 온다(위 줄 점이 차례로 생긴다).
 * 왼쪽(잠자기 켜짐)은 알림(아래 줄 점)이 바로 오지 않고 끝 무렵 정해진 때에 한꺼번에 오며, 배터리는 조금만 준다.
 * 오른쪽(잠자기 꺼짐)은 메시지가 오자마자 알림이 오는 대신 배터리가 더 많이 준다. [off]면 오른쪽을, 아니면 왼쪽을 진하게 그린다.
 */
@Composable
internal fun DozeArt(off: Boolean) {
    val t = artLoop(4200)
    ArtCanvas(150.dp) { c, tm ->
        val w = size.width / 2 - 24.dp.toPx()
        val h = 78.dp.toPx()
        val top = 16.dp.toPx()
        val sends = listOf(0.12f, 0.34f, 0.56f)
        val batch = 0.82f
        listOf(false, true).forEachIndexed { i, dozeOff ->
            val left = 12.dp.toPx() + i * (size.width / 2)
            val on = dozeOff == off
            val ink = if (on) c.ink else c.muted
            val trackW = w - 22.dp.toPx()
            drawRoundRect(c.line.copy(alpha = 0.5f), Offset(left, top), Size(trackW, h), CornerRadius(8.dp.toPx()))
            val x0 = left + 8.dp.toPx(); val x1 = left + trackW - 8.dp.toPx()
            val sendY = top + h * 0.3f; val getY = top + h * 0.72f
            // 시간 축 두 줄: 위는 보낸 때, 아래는 알림이 온 때
            drawLine(c.line, Offset(x0, sendY), Offset(x1, sendY), 1.dp.toPx())
            drawLine(c.line, Offset(x0, getY), Offset(x1, getY), 1.dp.toPx())
            // 지금 시각 막대
            val now = (t / 0.92f).coerceAtMost(1f)
            val nx = x0 + (x1 - x0) * now
            drawLine(ink.copy(alpha = 0.35f), Offset(nx, top + 6.dp.toPx()), Offset(nx, top + h - 6.dp.toPx()), 1.5f.dp.toPx(), StrokeCap.Round)
            // 잠자기 켜짐: 몰아서 오는 때(점선)
            if (!dozeOff) drawLine(
                c.muted.copy(alpha = 0.5f), Offset(x0 + (x1 - x0) * batch, top + 6.dp.toPx()), Offset(x0 + (x1 - x0) * batch, top + h - 6.dp.toPx()),
                1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            sends.forEachIndexed { k, s ->
                if (now >= s) drawCircle(ink, 4.dp.toPx() * easeD(((now - s) / 0.05f).coerceIn(0f, 1f)), Offset(x0 + (x1 - x0) * s, sendY))
                // 알림: 꺼짐은 보내자마자, 켜짐은 몰아서 오는 때에 조금씩 비켜 한꺼번에
                val at = if (dozeOff) s + 0.03f else batch
                val gx = if (dozeOff) s + 0.03f else batch + (k - 1) * 0.04f
                if (now >= at) drawCircle(ink, 4.dp.toPx() * easeD(((now - at) / 0.05f).coerceIn(0f, 1f)), Offset(x0 + (x1 - x0) * gx, getY))
            }
            // 배터리: 잠자기 꺼짐이 더 많이 준다
            val bx = left + trackW + 12.dp.toPx()
            val bTop = top + 8.dp.toPx(); val bBottom = top + h - 8.dp.toPx()
            drawRoundRect(c.line, Offset(bx - 5.dp.toPx(), bTop), Size(10.dp.toPx(), bBottom - bTop), CornerRadius(3.dp.toPx()), style = Stroke(1.5f.dp.toPx()))
            drawRoundRect(c.line, Offset(bx - 2.5f.dp.toPx(), bTop - 3.dp.toPx()), Size(5.dp.toPx(), 3.dp.toPx()), CornerRadius(1.dp.toPx()))
            val level = 0.9f - now * (if (dozeOff) 0.55f else 0.18f)
            val fillTop = bBottom - (bBottom - bTop - 3.dp.toPx()) * level
            drawRoundRect(ink, Offset(bx - 3.dp.toPx(), fillTop), Size(6.dp.toPx(), bBottom - 1.5f.dp.toPx() - fillTop), CornerRadius(2.dp.toPx()))
            artPill(tm, c, if (dozeOff) "알림 바로" else "알림 몰아서", Offset(left + trackW / 2, top + h + 16.dp.toPx()), filled = on)
            artLabel(tm, if (dozeOff) "잠자기 꺼짐" else "잠자기 켜짐", Offset(left + trackW / 2, top + h + 37.dp.toPx()), ink, bold = on)
        }
    }
}
