package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.IconBlacklist.Slot
import com.labok.goat.ui.liquid.Text

/**
 * 상태 표시줄 아이콘 그림(실험실 아이콘 숨기기): 줄마다 숨길 아이콘이 상태 표시줄에서 어떻게 생겼는지 보여 준다.
 * 메뉴 아이콘과 같은 24 격자·둥근 선으로 그리고, VoLTE처럼 글자 아이콘은 글자로 쓴다.
 */
@Composable
internal fun StatusBarGlyph(slot: Slot, color: Color, size: Dp = 22.dp) {
    if (slot == Slot.VOLTE) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text("VoLTE", color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        return
    }
    Canvas(Modifier.size(size)) {
        val s = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (slot) {
            Slot.ALARM -> alarm(color, s)
            Slot.VOLUME -> vibrate(color, s)
            Slot.BLUETOOTH -> bluetooth(color, s)
            Slot.NFC -> nfc(color, s)
            Slot.HOTSPOT -> hotspot(color, s)
            Slot.ROTATE -> rotate(color, s)
            Slot.HEADSET -> headset(color, s)
            Slot.LOCATION -> location(color, s)
            Slot.DATA_SAVER -> dataSaver(color, s)
            Slot.VPN -> vpn(color, s)
            Slot.ZEN -> zen(color, s)
            Slot.VOLTE -> Unit
        }
    }
}

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x), u(y))

private fun DrawScope.alarm(c: Color, s: Stroke) {
    drawCircle(c, u(7.2f), p(12f, 13f), style = s)
    drawLine(c, p(12f, 13f), p(12f, 9.4f), s.width, StrokeCap.Round)
    drawLine(c, p(12f, 13f), p(14.6f, 14.6f), s.width, StrokeCap.Round)
    drawLine(c, p(3.6f, 6.4f), p(6.6f, 3.6f), s.width, StrokeCap.Round)
    drawLine(c, p(20.4f, 6.4f), p(17.4f, 3.6f), s.width, StrokeCap.Round)
}

private fun DrawScope.vibrate(c: Color, s: Stroke) {
    drawRoundRect(c, p(8f, 4f), Size(u(8f), u(16f)), CornerRadius(u(1.6f)), style = s)
    drawLine(c, p(4.6f, 8.5f), p(4.6f, 15.5f), s.width, StrokeCap.Round)
    drawLine(c, p(19.4f, 8.5f), p(19.4f, 15.5f), s.width, StrokeCap.Round)
    drawLine(c, p(1.8f, 10.5f), p(1.8f, 13.5f), s.width, StrokeCap.Round)
    drawLine(c, p(22.2f, 10.5f), p(22.2f, 13.5f), s.width, StrokeCap.Round)
}

private fun DrawScope.bluetooth(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(6.5f), u(7.5f))
        lineTo(u(17f), u(16.5f))
        lineTo(u(12f), u(21f))
        lineTo(u(12f), u(3f))
        lineTo(u(17f), u(7.5f))
        lineTo(u(6.5f), u(16.5f))
    }
    drawPath(path, c, style = s)
}

private fun DrawScope.nfc(c: Color, s: Stroke) {
    drawRoundRect(c, p(3.5f, 3.5f), Size(u(17f), u(17f)), CornerRadius(u(3f)), style = s)
    val n = Path().apply {
        moveTo(u(8.5f), u(16f))
        lineTo(u(8.5f), u(8f))
        lineTo(u(15.5f), u(16f))
        lineTo(u(15.5f), u(8f))
    }
    drawPath(n, c, style = s)
}

private fun DrawScope.hotspot(c: Color, s: Stroke) {
    drawCircle(c, u(1.6f), p(12f, 13f))
    drawArc(c, 210f, 120f, false, p(7f, 8f), Size(u(10f), u(10f)), style = s)
    drawArc(c, 210f, 120f, false, p(3f, 4f), Size(u(18f), u(18f)), style = s)
    drawLine(c, p(12f, 15f), p(12f, 21f), s.width, StrokeCap.Round)
}

private fun DrawScope.rotate(c: Color, s: Stroke) {
    drawRoundRect(c, p(8f, 6f), Size(u(8f), u(12f)), CornerRadius(u(1.4f)), style = s)
    drawArc(c, 200f, 120f, false, p(2.5f, 2.5f), Size(u(19f), u(19f)), style = s)
    drawLine(c, p(19.6f, 5.4f), p(19.4f, 8.4f), s.width, StrokeCap.Round)
    drawLine(c, p(19.4f, 8.4f), p(16.6f, 7.6f), s.width, StrokeCap.Round)
}

private fun DrawScope.headset(c: Color, s: Stroke) {
    drawArc(c, 180f, 180f, false, p(4f, 4f), Size(u(16f), u(16f)), style = s)
    drawRoundRect(c, p(3.5f, 12.5f), Size(u(4f), u(7f)), CornerRadius(u(1.4f)), style = s)
    drawRoundRect(c, p(16.5f, 12.5f), Size(u(4f), u(7f)), CornerRadius(u(1.4f)), style = s)
}

private fun DrawScope.location(c: Color, s: Stroke) {
    val pin = Path().apply {
        moveTo(u(12f), u(21.5f))
        cubicTo(u(6.5f), u(15f), u(5f), u(12f), u(5f), u(9.5f))
        cubicTo(u(5f), u(5.4f), u(8.2f), u(2.5f), u(12f), u(2.5f))
        cubicTo(u(15.8f), u(2.5f), u(19f), u(5.4f), u(19f), u(9.5f))
        cubicTo(u(19f), u(12f), u(17.5f), u(15f), u(12f), u(21.5f))
        close()
    }
    drawPath(pin, c, style = s)
    drawCircle(c, u(2.4f), p(12f, 9.5f), style = s)
}

private fun DrawScope.dataSaver(c: Color, s: Stroke) {
    drawArc(c, -60f, 300f, false, p(3.5f, 3.5f), Size(u(17f), u(17f)), style = s)
    drawLine(c, p(12f, 8.5f), p(12f, 15.5f), s.width, StrokeCap.Round)
    drawLine(c, p(8.5f, 12f), p(15.5f, 12f), s.width, StrokeCap.Round)
}

private fun DrawScope.vpn(c: Color, s: Stroke) {
    drawCircle(c, u(4f), p(7.5f, 12f), style = s)
    drawLine(c, p(11.5f, 12f), p(21f, 12f), s.width, StrokeCap.Round)
    drawLine(c, p(18f, 12f), p(18f, 15.5f), s.width, StrokeCap.Round)
    drawLine(c, p(21f, 12f), p(21f, 15f), s.width, StrokeCap.Round)
}

private fun DrawScope.zen(c: Color, s: Stroke) {
    drawCircle(c, u(8.5f), p(12f, 12f), style = s)
    drawLine(c, p(7.5f, 12f), p(16.5f, 12f), s.width * 1.3f, StrokeCap.Round)
}
