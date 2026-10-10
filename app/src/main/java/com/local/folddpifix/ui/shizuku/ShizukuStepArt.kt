package com.local.folddpifix.ui.shizuku

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.art.ArtCanvas
import com.local.folddpifix.ui.art.artLabel
import com.local.folddpifix.ui.art.artLoop
import kotlin.math.min

/*
 * Shizuku 연결 단계 그림(지금 할 단계에만 보인다). 처음 해 보는 사람도 어느 화면에서 무엇을 누르는지 알 수 있게
 * 화면 요소를 단순하게 흉내 내고, 누를 곳에 손가락(동그라미)이 다가가 누른다.
 */

/** 손가락: [from]에서 [to]로 다가가 누르고(작아짐) 다시 물러난다. [t]는 0~1. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.finger(c: com.local.folddpifix.ui.liquid.LiquidColors, from: Offset, to: Offset, t: Float) {
    val k = when {
        t < 0.4f -> t / 0.4f
        t < 0.65f -> 1f
        else -> 1f - (t - 0.65f) / 0.35f
    }.let { it * it * (3 - 2 * it) }
    val p = Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k)
    val r = (if (t in 0.45f..0.55f) 9.dp else 11.dp).toPx()
    drawCircle(c.ink.copy(alpha = 0.22f), r + 5.dp.toPx(), p)
    drawCircle(c.ink.copy(alpha = 0.55f), r, p)
}

/** 1. 설치: 앱 아이콘으로 내려받기 화살표가 내려와 꽂히고 체크가 생긴다. */
@Composable
internal fun StoreStepArt() {
    val t = artLoop(2400)
    ArtCanvas(96.dp) { c, tm ->
        val s = size.height * 0.5f
        val tl = Offset(size.width / 2 - s / 2, size.height * 0.2f)
        drawRoundRect(c.ink, tl, Size(s, s), CornerRadius(s * 0.28f))
        artLabel(tm, "S", Offset(tl.x + s / 2, tl.y + s / 2), c.onInk, 20, bold = true)
        val k = min(1f, t / 0.6f)
        val ax = tl.x + s + 26.dp.toPx(); val ay = tl.y - 6.dp.toPx() + (s * 0.6f) * k
        drawLine(c.ink, Offset(ax, ay - 18.dp.toPx()), Offset(ax, ay), 3.dp.toPx(), StrokeCap.Round)
        drawLine(c.ink, Offset(ax, ay), Offset(ax - 6.dp.toPx(), ay - 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        drawLine(c.ink, Offset(ax, ay), Offset(ax + 6.dp.toPx(), ay - 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        if (t > 0.65f) artLabel(tm, "✓ 설치됨", Offset(size.width / 2, size.height * 0.88f), c.ink, 11, bold = true)
        else artLabel(tm, "Shizuku · 무료", Offset(size.width / 2, size.height * 0.88f), c.muted, 11)
    }
}

/** 2. 무선 디버깅: 설정 줄의 스위치가 켜지고 Wi-Fi 물결이 퍼진다. */
@Composable
internal fun WirelessStepArt() {
    val t = artLoop(2400)
    ArtCanvas(96.dp) { c, tm ->
        val on = t > 0.4f
        val rowTl = Offset(size.width * 0.08f, size.height * 0.28f); val rowW = size.width * 0.84f; val rowH = size.height * 0.44f
        drawRoundRect(c.surface, rowTl, Size(rowW, rowH), CornerRadius(rowH / 2))
        drawRoundRect(c.line, rowTl, Size(rowW, rowH), CornerRadius(rowH / 2), style = Stroke(1.dp.toPx()))
        artLabel(tm, "무선 디버깅", Offset(rowTl.x + rowW * 0.3f, rowTl.y + rowH / 2), c.ink, 12, bold = true)
        val sw = Size(40.dp.toPx(), 22.dp.toPx()); val sc = Offset(rowTl.x + rowW - 34.dp.toPx(), rowTl.y + rowH / 2)
        drawRoundRect(if (on) c.ink else c.line, Offset(sc.x - sw.width / 2, sc.y - sw.height / 2), sw, CornerRadius(sw.height / 2))
        drawCircle(if (on) c.onInk else c.ink, sw.height * 0.36f, Offset(if (on) sc.x + sw.width / 2 - sw.height / 2 else sc.x - sw.width / 2 + sw.height / 2, sc.y))
        if (on) {
            val wc = Offset(rowTl.x + rowW * 0.62f, rowTl.y + rowH * 0.78f)
            val pulse = ((t - 0.4f) / 0.6f)
            for (i in 1..3) {
                val r = 6.dp.toPx() * i * (0.7f + 0.3f * pulse)
                drawArc(c.ink.copy(alpha = 0.6f - 0.15f * i), -135f, 90f, false, Offset(wc.x - r, wc.y - r), Size(2 * r, 2 * r), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        finger(c, Offset(sc.x + 30.dp.toPx(), size.height), sc, t)
    }
}

/** 3. 페어링: 왼쪽 설정 화면의 6자리 숫자가 오른쪽 Shizuku 알림 칸에 한 자리씩 들어간다. */
@Composable
internal fun PairingStepArt() {
    val t = artLoop(3600)
    ArtCanvas(120.dp) { c, tm ->
        val code = "482931"
        val w = size.width * 0.42f; val h = size.height * 0.78f
        val left = Offset(size.width * 0.05f, size.height * 0.08f)
        val right = Offset(size.width * 0.53f, size.height * 0.08f)
        // 왼쪽: 설정 → 무선 디버깅 → 페어링 코드
        drawRoundRect(c.surface, left, Size(w, h), CornerRadius(14.dp.toPx()))
        drawRoundRect(c.line, left, Size(w, h), CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
        artLabel(tm, "페어링 코드", Offset(left.x + w / 2, left.y + h * 0.25f), c.muted, 10)
        artLabel(tm, "482 931", Offset(left.x + w / 2, left.y + h * 0.52f), c.ink, 16, bold = true)
        artLabel(tm, "설정 화면", Offset(left.x + w / 2, left.y + h * 0.82f), c.muted, 9)
        // 오른쪽: Shizuku 알림의 입력 칸
        drawRoundRect(c.surface, right, Size(w, h), CornerRadius(14.dp.toPx()))
        drawRoundRect(c.ink, right, Size(w, h), CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        artLabel(tm, "Shizuku 알림", Offset(right.x + w / 2, right.y + h * 0.18f), c.ink, 10, bold = true)
        val typed = (t / 0.7f * code.length).toInt().coerceIn(0, code.length)
        val fieldTl = Offset(right.x + w * 0.1f, right.y + h * 0.34f)
        drawRoundRect(c.line.copy(alpha = 0.5f), fieldTl, Size(w * 0.8f, h * 0.26f), CornerRadius(8.dp.toPx()))
        artLabel(tm, code.take(typed).padEnd(6, '·'), Offset(right.x + w / 2, fieldTl.y + h * 0.13f), c.ink, 13, bold = true)
        val done = t > 0.75f
        val btnTl = Offset(right.x + w * 0.25f, right.y + h * 0.68f)
        drawRoundRect(if (done) c.ink else c.line, btnTl, Size(w * 0.5f, h * 0.2f), CornerRadius(h * 0.1f))
        artLabel(tm, "보내기", Offset(right.x + w / 2, btnTl.y + h * 0.1f), if (done) c.onInk else c.muted, 10, bold = true)
        // 숫자가 왼쪽에서 오른쪽으로 옮겨 가는 점
        if (!done) {
            val f = (t / 0.7f * code.length) % 1f
            drawCircle(c.ink.copy(alpha = 0.6f), 4.dp.toPx(), Offset(left.x + w + (right.x - left.x - w) * f, size.height * 0.5f))
        }
    }
}

/** 4. 허용: Shizuku 창에서 손가락이 '항상 허용'을 누른다. */
@Composable
internal fun AllowStepArt() {
    val t = artLoop(2600)
    ArtCanvas(110.dp) { c, tm ->
        val w = size.width * 0.8f; val h = size.height * 0.84f
        val tl = Offset((size.width - w) / 2, size.height * 0.06f)
        drawRoundRect(c.surface, tl, Size(w, h), CornerRadius(18.dp.toPx()))
        drawRoundRect(c.line, tl, Size(w, h), CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx()))
        artLabel(tm, "FoldFit이 Shizuku를 쓰도록 허용할까요?", Offset(size.width / 2, tl.y + h * 0.22f), c.ink, 11, bold = true)
        val pressed = t in 0.45f..0.75f
        val b1 = Offset(tl.x + w * 0.1f, tl.y + h * 0.42f)
        drawRoundRect(if (pressed) c.ink else c.line.copy(alpha = 0.6f), b1, Size(w * 0.8f, h * 0.22f), CornerRadius(h * 0.11f))
        artLabel(tm, "항상 허용", Offset(size.width / 2, b1.y + h * 0.11f), if (pressed) c.onInk else c.ink, 11, bold = true)
        artLabel(tm, "거부", Offset(size.width / 2, tl.y + h * 0.82f), c.muted, 10)
        finger(c, Offset(size.width * 0.8f, size.height), Offset(size.width / 2 + w * 0.2f, b1.y + h * 0.11f), t)
    }
}
