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
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.deviceFrame
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.liquid.LiquidColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * 흔들어서 화면 회전 그림. 모두 같은 순서로 움직인다:
 * 잠긴 채 폰을 돌려 듦(화면은 그대로) → 흔듦(잠금이 잠깐 풀림) → 자동 회전이 화면을 든 방향으로 돌림 → 다시 잠김.
 */

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 흔들면 든 방향으로 돌아간다. [toLandscape]면 세로 폰을 가로로 돌려 들고, 아니면 가로 폰을 세로로 세워 든다.
 * 들기만 해서는 화면이 그대로이고, 흔들어야 그 순간 자동 회전이 화면을 돌린 뒤 다시 잠근다.
 * 오른쪽 위 알약에 지금 단계가 나온다.
 */
@Composable
internal fun ShakeTurnArt(toLandscape: Boolean = true) {
    val t = artLoop(5200)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(170.dp) { c, tm ->
        val hold = ease(seg(t, 0.14f, 0.32f))
        val shake = seg(t, 0.38f, 0.58f)
        val turn = ease(seg(t, 0.6f, 0.74f))
        // 몸체: 세로 0도 ↔ 가로 90도. 화면 내용은 몸체 기준 각도(가로 내용은 -90도).
        val body = if (toLandscape) 90f * hold else 90f * (1 - hold)
        val content = if (toLandscape) -90f * turn else -90f * (1 - turn)
        val wiggle = if (shake in 0.001f..0.999f) sin(shake * PI.toFloat() * 8) * 9.dp.toPx() * (1 - shake * 0.5f) else 0f
        val center = Offset(size.width / 2 + wiggle, size.height / 2 - 2.dp.toPx())
        if (shake in 0.001f..0.999f) shakeMarks(c, center, 1f - shake * 0.3f)
        val open = t >= 0.38f && t < 0.78f
        phone(c, aspect, center, body, content, locked = true, open = open)
        val status = when {
            t < 0.14f -> "잠김"
            t < 0.36f -> "기기 회전"
            t < 0.6f -> "흔들기"
            t < 0.78f -> "자동 회전"
            else -> "다시 잠김"
        }
        artPill(tm, c, status, Offset(size.width - 52.dp.toPx(), 18.dp.toPx()), filled = t >= 0.38f && t < 0.78f)
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
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(180.dp) { c, tm ->
        val tilt = ease(seg(t, 0.15f, 0.4f))
        val left = Offset(size.width * 0.27f, size.height * 0.5f)
        val right = Offset(size.width * 0.73f, size.height * 0.5f)
        // 둘 다 폰을 옆으로 눕혀 든다(사람이 옆으로 누운 자세)
        val body = 90f * tilt
        // 자동 회전: 늦게 화면 내용이 가로로 돌아간다(폰 기준으로 내용이 -90도)
        val autoTurn = ease(seg(t, 0.5f, 0.65f))
        phone(c, aspect, left, body, -90f * autoTurn, locked = false)
        phone(c, aspect, right, body, 0f, locked = true)
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
 * 폰 하나: 몸체를 [angle]도 돌려 그리고, 화면 내용은 몸체 기준으로 [turn]도 더 돌린다(0=세로 내용, -90=가로 내용).
 * [locked]면 아래에 자물쇠, [open]이면 자물쇠 고리가 풀린 모양.
 */
private fun DrawScope.phone(
    c: LiquidColors,
    aspect: Float,
    center: Offset,
    angle: Float,
    turn: Float,
    locked: Boolean,
    open: Boolean = false,
) {
    // 기기 모양은 고급 화면 배율 설정 그림과 같은 틀([deviceFrame])과 외부 화면 비율([aspect])을 쓴다
    val h = 92.dp.toPx(); val w = h * aspect
    rotate(angle, center) {
        val screen = deviceFrame(c, Offset(center.x - w / 2, center.y - h / 2), Size(w, h))
        rotate(turn, center) {
            // 가로 내용은 몸체의 긴 쪽을 따라 넓게, 세로 내용은 아이콘 격자
            if (turn < -45f) content(c, center, screen.height * 0.86f, screen.width * 0.8f, wide = true)
            else content(c, center, screen.width * 0.8f, screen.height * 0.86f, wide = false)
        }
    }
    // 자물쇠는 돌리지 않고 늘 폰 바로 아래에 똑바로 둔다
    if (locked) {
        val r = Math.toRadians(angle.toDouble())
        val halfH = (abs(cos(r)) * h / 2 + abs(sin(r)) * w / 2).toFloat()
        lock(c.ink, Offset(center.x, center.y + halfH + 13.dp.toPx()), open)
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

/** 작은 자물쇠. [open]이면 고리가 위로 들려 한쪽이 풀린다. */
private fun DrawScope.lock(color: Color, center: Offset, open: Boolean = false) {
    val bw = 12.dp.toPx(); val bh = 9.dp.toPx()
    drawRoundRect(color, Offset(center.x - bw / 2, center.y - bh / 2 + 2.dp.toPx()), Size(bw, bh), CornerRadius(2.dp.toPx()))
    val lift = if (open) 3.dp.toPx() else 0f
    val shift = if (open) 3.dp.toPx() else 0f
    drawArc(
        color, 180f, 180f, false,
        Offset(center.x - 4.dp.toPx() + shift, center.y - 7.dp.toPx() - lift), Size(8.dp.toPx(), 9.dp.toPx()),
        style = Stroke(2.dp.toPx()),
    )
}
