package com.labok.goat.ui.rotate

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.liquid.LiquidColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * 흔들어 화면 회전 그림. 모두 같은 순서로 움직인다:
 * 잠긴 채 가만히 → 좌우로 흔들림 → 화면이 돌아감 → 새 방향으로 다시 잠김.
 */

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 흔들면 돌아간다: 세로로 잠긴 폰을 흔들면 [toLandscape]면 가로로, 아니면 가로에서 세로로 돌아가 다시 잠긴다.
 * 오른쪽 위 알약에 지금 상태(잠김·흔들기·회전)가 나온다.
 */
@Composable
internal fun ShakeTurnArt(toLandscape: Boolean = true) {
    val t = artLoop(4200)
    ArtCanvas(170.dp) { c, tm ->
        val shake = seg(t, 0.22f, 0.46f)
        val turn = ease(seg(t, 0.5f, 0.68f))
        val from = if (toLandscape) 0f else 90f
        val to = if (toLandscape) 90f else 0f
        val angle = from + (to - from) * turn
        val wiggle = if (shake in 0.001f..0.999f) sin(shake * PI.toFloat() * 8) * 10.dp.toPx() * (1 - shake * 0.5f) else 0f
        val center = Offset(size.width / 2 + wiggle, size.height / 2 - 2.dp.toPx())
        // 흔드는 동안 양옆 물결 표시
        if (shake in 0.001f..0.999f) shakeMarks(c, center, 1f - shake * 0.3f)
        phone(c, center, angle, landscapeContent = turn > 0.5f == toLandscape, locked = turn == 0f || turn == 1f)
        val status = when {
            t < 0.22f -> "잠김"
            t < 0.5f -> "흔들기"
            t < 0.68f -> "회전"
            else -> "다시 잠김"
        }
        artPill(tm, c, status, Offset(size.width - 44.dp.toPx(), 18.dp.toPx()), filled = t >= 0.22f && t < 0.68f)
        artLabel(tm, if (toLandscape) "세로 → 가로" else "가로 → 세로", Offset(44.dp.toPx(), 18.dp.toPx()), c.muted)
    }
}

/**
 * 누워서 볼 때: 사람이 옆으로 누워 폰을 들면 자동 회전은 가로로 돌아가 버리지만, 잠가 두면 세로 그대로다.
 * 왼쪽은 자동 회전(가로로 돌아가 버림), 오른쪽은 GOAT(세로 유지).
 */
@Composable
internal fun LyingDownArt() {
    val t = artLoop(3600)
    ArtCanvas(180.dp) { c, tm ->
        val tilt = ease(seg(t, 0.15f, 0.4f))
        val left = Offset(size.width * 0.27f, size.height * 0.5f)
        val right = Offset(size.width * 0.73f, size.height * 0.5f)
        // 둘 다 폰을 옆으로 눕혀 든다(사람이 옆으로 누운 자세)
        val body = 90f * tilt
        // 자동 회전: 늦게 화면 내용이 가로로 돌아간다(폰 기준으로 내용이 -90도)
        val autoTurn = ease(seg(t, 0.5f, 0.65f))
        phone(c, left, body, landscapeContent = false, locked = false, contentAngle = -90f * autoTurn)
        phone(c, right, body, landscapeContent = false, locked = true)
        artLabel(tm, "자동 회전", Offset(left.x, size.height - 12.dp.toPx()), c.muted)
        artLabel(tm, "GOAT 잠금", Offset(right.x, size.height - 12.dp.toPx()), c.ink, bold = true)
        if (autoTurn > 0.9f) artPill(tm, c, "돌아가 버림", Offset(left.x, 14.dp.toPx()), filled = false)
        if (tilt > 0.9f) artPill(tm, c, "그대로", Offset(right.x, 14.dp.toPx()), filled = true)
    }
}

/** 흔들기 표시: 폰 양옆의 짧은 호 두 쌍. */
private fun DrawScope.shakeMarks(c: LiquidColors, center: Offset, alpha: Float) {
    val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    val col = c.muted.copy(alpha = alpha)
    for (side in listOf(-1f, 1f)) {
        for (k in 1..2) {
            val x = center.x + side * (38.dp.toPx() + k * 9.dp.toPx())
            drawLine(col, Offset(x, center.y - (10 + k * 5).dp.toPx()), Offset(x, center.y + (10 + k * 5).dp.toPx()), s.width, StrokeCap.Round)
        }
    }
}

/**
 * 폰 하나: 몸체를 [angle]도 돌려 그린다. 화면 안 내용은 [landscapeContent]면 가로용(긴 줄 두 개), 아니면 세로용(아이콘 격자).
 * [contentAngle]만큼 내용만 따로 돌린다(자동 회전이 화면을 돌린 모습). [locked]면 아래에 자물쇠.
 */
private fun DrawScope.phone(
    c: LiquidColors,
    center: Offset,
    angle: Float,
    landscapeContent: Boolean,
    locked: Boolean,
    contentAngle: Float = 0f,
) {
    val w = 50.dp.toPx(); val h = 92.dp.toPx()
    val stroke = 2.4.dp.toPx()
    rotate(angle, center) {
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        drawRoundRect(c.surface, tl, Size(w, h), CornerRadius(9.dp.toPx()))
        drawRoundRect(c.ink, tl, Size(w, h), CornerRadius(9.dp.toPx()), style = Stroke(stroke))
        rotate(contentAngle + if (landscapeContent) -90f else 0f, center) {
            // 내용은 '보는 사람 기준 위쪽'에 맞춰 그린다: 세로면 아이콘 격자, 가로면 넓은 줄
            if (landscapeContent || contentAngle < -45f) content(c, center, h * 0.8f, w * 0.75f, wide = true)
            else content(c, center, w * 0.75f, h * 0.8f, wide = false)
        }
    }
    // 자물쇠는 돌리지 않고 늘 폰 바로 아래에 똑바로 둔다
    if (locked) {
        val r = Math.toRadians(angle.toDouble())
        val halfH = (abs(cos(r)) * h / 2 + abs(sin(r)) * w / 2).toFloat()
        lock(c.ink, Offset(center.x, center.y + halfH + 13.dp.toPx()))
    }
}

/** 화면 내용: [wide]면 제목 줄과 긴 막대 둘(동영상·가로 화면), 아니면 아이콘 격자. */
private fun DrawScope.content(c: LiquidColors, center: Offset, w: Float, h: Float, wide: Boolean) {
    val left = center.x - w / 2; val top = center.y - h / 2
    if (wide) {
        drawRoundRect(c.ink, Offset(left + w * 0.06f, top + h * 0.12f), Size(w * 0.88f, h * 0.42f), CornerRadius(3.dp.toPx()))
        drawRoundRect(c.muted, Offset(left + w * 0.06f, top + h * 0.66f), Size(w * 0.6f, h * 0.1f), CornerRadius(2.dp.toPx()))
    } else {
        val cell = w / 3
        for (r in 0..2) for (col in 0..2) {
            drawRoundRect(
                c.muted, Offset(left + col * cell + cell * 0.2f, top + h * 0.06f + r * cell),
                Size(cell * 0.6f, cell * 0.6f), CornerRadius(2.dp.toPx()),
            )
        }
        drawRoundRect(c.line, Offset(left + w * 0.1f, top + h * 0.72f), Size(w * 0.8f, h * 0.06f), CornerRadius(2.dp.toPx()))
    }
}

/** 작은 자물쇠. */
private fun DrawScope.lock(color: Color, center: Offset) {
    val bw = 12.dp.toPx(); val bh = 9.dp.toPx()
    drawRoundRect(color, Offset(center.x - bw / 2, center.y - bh / 2 + 2.dp.toPx()), Size(bw, bh), CornerRadius(2.dp.toPx()))
    drawArc(
        color, 180f, 180f, false,
        Offset(center.x - 4.dp.toPx(), center.y - 7.dp.toPx()), Size(8.dp.toPx(), 9.dp.toPx()),
        style = Stroke(2.dp.toPx()),
    )
}
