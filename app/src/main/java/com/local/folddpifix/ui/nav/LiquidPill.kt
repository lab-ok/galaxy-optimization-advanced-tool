package com.local.folddpifix.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 넓적한 물방울(사이드바 선택 표시). 폭을 [SEGMENTS]개의 세로 조각으로 나눠 조각마다 스프링을 따로 둔다.
 * 가운데 조각이 먼저 움직이고 가장자리는 조금 늦게 따라와(스프링이 약해서) 옮겨 가는 동안 물처럼 휘고,
 * 도착하면 가장자리가 살짝 넘쳤다 돌아오며 출렁인다. 조각들의 위치로 매끈한 외곽선 하나를 그려 들쭉날쭉하지 않다.
 * 표면장력: 꼬리는 따로 떨어진 방울을 만들지 않는다. 몸통 뒤에 짧고 굵은 목 하나가 늘어나되 몸통에서
 * 행 높이의 [TAIL_REACH]배 이상은 멀어지지 못하게 묶여, 끌려가다가 탄성 있게 몸통으로 되돌아와 합쳐진다.
 */
class LiquidPillState internal constructor() {
    internal val segments = List(SEGMENTS) { Animatable(-1f) }
    internal val tail = Animatable(-1f)

    /** 몸통 가운데 조각의 위치(글자 반전 계산용). 아직 자리를 못 잡았으면 -1. */
    val center: Float get() = segments[SEGMENTS / 2].value

    internal suspend fun moveTo(y: Float, reduce: Boolean) {
        if (center < 0f || reduce) {
            segments.forEach { it.snapTo(y) }; tail.snapTo(y); return
        }
        coroutineScope {
            val mid = (SEGMENTS - 1) / 2f
            segments.forEachIndexed { i, a ->
                // 가운데 1 → 가장자리 0
                val k = 1f - abs(i - mid) / mid
                launch { a.animateTo(y, spring(dampingRatio = 0.7f + 0.1f * k, stiffness = 260f + 300f * k)) }
            }
            launch { tail.animateTo(y, spring(dampingRatio = 0.8f, stiffness = 200f)) }
        }
    }

    companion object {
        const val SEGMENTS = 7
        /** 꼬리가 몸통에서 벗어날 수 있는 최대 거리(행 높이 배수). 작을수록 표면장력이 세 보인다. */
        const val TAIL_REACH = 0.75f
    }
}

/** [target](선택 항목의 위쪽 y)이 바뀌면 물방울을 그리로 옮긴다. */
@Composable
fun rememberLiquidPill(target: Float?, reduce: Boolean): LiquidPillState {
    val state = remember { LiquidPillState() }
    LaunchedEffect(target) { target?.let { state.moveTo(it, reduce) } }
    return state
}

/** 물방울 그리기(goo 층 안에서 부른다). [width]·[rowH]는 항목 하나의 크기. */
fun DrawScope.drawLiquidPill(state: LiquidPillState, color: Color, width: Float, rowH: Float) {
    val seg = state.segments.map { it.value }
    if (seg.any { it < 0f } || rowH <= 0f) return
    val r = rowH / 2
    val n = seg.size
    val xs = List(n) { i -> r + (width - 2 * r) * i / (n - 1) }

    // 위 외곽선(왼→오), 오른쪽 반원, 아래 외곽선(오→왼), 왼쪽 반원. 조각 사이는 중점을 잇는 2차 곡선으로 매끈하게.
    val path = Path().apply {
        moveTo(xs[0], seg[0])
        for (i in 1 until n) {
            val mx = (xs[i - 1] + xs[i]) / 2; val my = (seg[i - 1] + seg[i]) / 2
            quadraticBezierTo(xs[i - 1], seg[i - 1], mx, my)
        }
        lineTo(xs[n - 1], seg[n - 1])
        arcTo(Rect(Offset(xs[n - 1], seg[n - 1] + r), r), -90f, 180f, false)
        for (i in n - 2 downTo 0) {
            val mx = (xs[i + 1] + xs[i]) / 2; val my = (seg[i + 1] + seg[i]) / 2 + rowH
            quadraticBezierTo(xs[i + 1], seg[i + 1] + rowH, mx, my)
        }
        lineTo(xs[0], seg[0] + rowH)
        arcTo(Rect(Offset(xs[0], seg[0] + r), r), 90f, 180f, false)
        close()
    }
    drawPath(path, color)

    // 꼬리(목): 몸통 가운데에서 이동 반대쪽으로 짧게 늘어난 굵은 방울. 몸통에 묶여 일정 거리 이상 떨어지지 않는다.
    val body = seg[n / 2]
    val pull = (state.tail.value - body).coerceIn(-rowH * LiquidPillState.TAIL_REACH, rowH * LiquidPillState.TAIL_REACH)
    if (abs(pull) > 1f) {
        val k = abs(pull) / (rowH * LiquidPillState.TAIL_REACH)
        val rx = width * (0.16f - 0.05f * k)
        val ry = r * (0.95f - 0.15f * k)
        val cy = body + r + pull
        drawOval(color, Offset(width / 2 - rx, cy - ry), androidx.compose.ui.geometry.Size(rx * 2, ry * 2))
    }
}
