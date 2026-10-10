package com.local.folddpifix.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * PC ↔ 폰 연결 그림(공통 기기 틀). 두 기기를 가운데에 가까이 모아 둔다.
 * - USB: PC 받침대 옆에서 폰 아래 단자까지 늘어진 케이블, 그 위로 신호 방울이 오간다.
 * - 무선: 두 기기 사이 위쪽에 와이파이 부채꼴이 차례로 켜지고, 신호 방울이 공중으로 오간다.
 * 연결이 끝나면(권한 부여 완료) 폰 화면에 체크가 뜬다.
 */
@Composable
internal fun ConnectionIllustration(wireless: Boolean, done: Boolean) {
    val c = com.local.folddpifix.ui.liquid.LocalLiquid.current
    val reduce = com.local.folddpifix.ui.liquid.LocalReduceMotion.current
    val t0 by rememberInfiniteTransition(label = "conn").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "t",
    )
    val t = if (reduce) 0.5f else t0
    val shape = LocalDeviceShape.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(104.dp),
    ) {
        val h = size.height
        val pcW = h * 1.05f
        val phH = h * 0.82f
        val phW = phH * shape.cover
        val gap = h * 0.72f
        val left = (size.width - (pcW + gap + phW)) / 2
        val pcCenter = Offset(left + pcW / 2, h * 0.46f)
        monitorFrame(c, pcCenter, pcW)
        val phTop = Offset(left + pcW + gap, h * 0.06f)
        val phone = deviceFrame(c, phTop, Size(phW, phH))
        if (done) {
            val cx = phone.center.x
            val cy = phone.center.y
            val k = phone.width
            drawLine(c.ink, Offset(cx - k * 0.22f, cy), Offset(cx - k * 0.04f, cy + k * 0.18f), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c.ink, Offset(cx - k * 0.04f, cy + k * 0.18f), Offset(cx + k * 0.26f, cy - k * 0.2f), 3.dp.toPx(), StrokeCap.Round)
        }
        // 신호가 오가는 위치: 0→1 가고 1→0 온다
        val p = if (t < 0.5f) t * 2 else 2 - t * 2
        val ease = p * p * (3 - 2 * p)
        if (wireless) {
            // 두 기기 사이 위쪽의 와이파이 부채꼴
            val cx = left + pcW + gap / 2
            val cy = h * 0.52f
            for (k in 0..2) {
                val r = h * (0.12f + 0.11f * k)
                val on = ((t * 3).toInt() % 3) >= k
                drawArc(
                    c.ink.copy(alpha = if (on) 0.85f else 0.18f),
                    startAngle = -135f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(c.ink, 3.dp.toPx(), Offset(cx, cy))
        } else {
            // PC 받침대 옆 → 폰 아래 단자로 늘어진 케이블(2차 베지어)
            val a = Offset(pcCenter.x + pcW * 0.24f, h * 0.9f)
            val b = Offset(phone.center.x, phTop.y + phH + 4.dp.toPx())
            val ctrl = Offset((a.x + b.x) / 2, h * 1.02f)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(a.x, a.y); quadraticBezierTo(ctrl.x, ctrl.y, b.x, b.y)
            }
            drawPath(path, c.ink.copy(alpha = 0.55f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            // 단자
            drawRoundRect(c.ink, Offset(b.x - 5.dp.toPx(), b.y - 4.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()), CornerRadius(2.dp.toPx()))
            // 케이블 위 신호 방울
            val u = ease
            val x = (1 - u) * (1 - u) * a.x + 2 * (1 - u) * u * ctrl.x + u * u * b.x
            val y = (1 - u) * (1 - u) * a.y + 2 * (1 - u) * u * ctrl.y + u * u * b.y
            drawCircle(c.ink, 4.5.dp.toPx(), Offset(x, y))
        }
    }
}

/**
 * 원리 그림: 커버(촘촘한 화면)와 메인(덜 촘촘한 화면)에 같은 아이콘을 그린다.
 * [corrected]가 false면 같은 DPI(메인 쪽이 더 크게 보임), true면 보정 DPI(같은 크기)로 그린다.
 * 차이가 눈에 보이도록 실제보다 과장해서 그린다.
 */
@Composable
internal fun DensityIllustration(corrected: Float) {
    val lc = com.local.folddpifix.ui.liquid.LocalLiquid.current
    val glyph = lc.ink.copy(alpha = 0.55f)
    val shape = LocalDeviceShape.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        // 기기 실제 화면비(앱이 알아낸 해상도, 모르면 폴드8). 높이는 같게 두고 폭만 화면비대로.
        val coverIn = Size(shape.cover, 1f)
        val mainIn = Size(shape.inner, 1f)
        val scale = size.height / maxOf(coverIn.height, mainIn.height) * 0.9f
        val gap = size.width * 0.06f
        val coverPx = Size(coverIn.width * scale, coverIn.height * scale)
        val mainPx = Size(mainIn.width * scale, mainIn.height * scale)
        val startX = (size.width - coverPx.width - gap - mainPx.width) / 2
        val coverTop = Offset(startX, (size.height - coverPx.height) / 2)
        val mainTop = Offset(startX + coverPx.width + gap, (size.height - mainPx.height) / 2)

        // 같은 DPI일 때 메인 아이콘이 커 보이는 비율(실제 약 1.08배)을 1.35배로 과장해 보여 준다.
        val exaggerated = 1.35f
        val base = coverPx.width * 0.22f
        val mainIcon = base * (exaggerated + (1f - exaggerated) * corrected)

        val cover = deviceFrame(lc, coverTop, coverPx)
        val main = deviceFrame(lc, mainTop, mainPx)
        icons(cover.topLeft, cover.size, base, glyph)
        icons(main.topLeft, main.size, mainIcon, glyph)
    }
}

/** 아이콘 격자 + 글자 줄. [s]는 아이콘 한 변(px). */
private fun DrawScope.icons(topLeft: Offset, area: Size, s: Float, color: Color) {
    val pad = s * 0.45f
    var y = topLeft.y + pad
    repeat(2) {
        var x = topLeft.x + pad
        while (x + s <= topLeft.x + area.width - pad * 0.5f) {
            drawRoundRect(color, Offset(x, y), Size(s, s), CornerRadius(s * 0.28f))
            x += s + pad
        }
        y += s + pad
    }
    // 글자 줄(같은 글꼴 크기)
    val lineH = s * 0.32f
    repeat(3) { i ->
        val w = (area.width - pad * 2) * (if (i == 2) 0.6f else 1f)
        drawRoundRect(color.copy(alpha = 0.55f), Offset(topLeft.x + pad, y), Size(w, lineH), CornerRadius(lineH / 2))
        y += lineH * 2f
    }
}
