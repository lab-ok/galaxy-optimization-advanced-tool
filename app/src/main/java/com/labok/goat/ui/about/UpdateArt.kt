package com.labok.goat.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.labok.goat.data.update.Updater
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.goo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 업데이트 리퀴드 그림: 한 방울이 상태마다 모양을 바꾸며 이어진다(상태가 바뀌면 스프링으로 서서히 넘어간다).
 * - 쉬는 중: 방울이 숨 쉬듯 살짝 부풀었다 줄어든다.
 * - 확인 중: 작은 방울 셋이 큰 방울 둘레를 돌며 붙었다 떨어진다(goo로 이어짐).
 * - 최신: 방울이 한 번 통 튀며 둥글게 자리 잡고 체크가 그려진다.
 * - 새 버전: 방울이 위아래로 떠다니고 꼭대기에서 작은 방울이 맺혀 올라간다. 가운데에 아래 화살표.
 * - 내려받는 중: 방울이 길게 늘어나 알약 모양 통이 되고, 받은 양([progress])만큼 물이 왼쪽부터 차오른다.
 *   물의 앞면은 받는 속도([rate], 0~1)가 빠를수록 크게 출렁이고, 앞면에서 튀어 오르는 물방울도 많아진다.
 * - 설치 중: 꽉 찬 통의 물이 가운데로 소용돌이치며 다시 한 방울로 모인다.
 * - 실패: 방울이 납작하게 주저앉고 아래로 물방울이 떨어진다. 가운데에 느낌표.
 * 동작 줄이기면 시간을 멈추고 상태별 모양만 보여 준다.
 */
/** 프레임을 계속 돌려야 하는지: 물 높이가 아직 목표에 닿지 않았거나 목표가 0이 아니면(통이 보이는 동안) 돈다. */
private fun isActive(target: Float, fill: Float) = abs(target - fill) > 0.002f || target > 0f

@Composable
internal fun LiquidUpdateArt(state: Updater.State, progress: Float, rate: Float, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    // 시간(초): 프레임마다 흐른다
    var time by remember { mutableFloatStateOf(0f) }
    // 통의 물 높이: 진행률이 아주 자주(내려받는 조각마다) 바뀌므로 스프링을 매번 새로 걸지 않고,
    // 프레임마다 목표 쪽으로 물처럼 따라가게 한다(스프링을 매번 걸면 첫 프레임 전에 취소돼 물이 차오르지 않았다)
    val fillTarget = when (state) {
        is Updater.State.Downloading -> progress.coerceIn(0f, 1f)
        is Updater.State.Installing -> 1f
        else -> 0f
    }
    val target by rememberUpdatedState(fillTarget)
    var fill by remember { mutableFloatStateOf(0f) }
    // 배터리: 쉬는 중·최신처럼 움직일 것이 없으면 프레임을 돌리지 않는다(앱 설정을 열어 두어도 그리지 않음).
    // 확인 중·새 버전·내려받기·설치·실패 동안만, 그리고 물 높이가 목표에 닿을 때까지만 돈다.
    val animating = state !is Updater.State.Idle && state !is Updater.State.UpToDate
    val stillAnimating by rememberUpdatedState(animating)
    val moving = !reduce && (animating || abs(fillTarget - fill) > 0.002f)
    LaunchedEffect(moving, reduce) {
        if (!moving) {
            snapshotFlow { target }.collect { fill = it }
            return@LaunchedEffect
        }
        val start = withFrameNanos { it } - (time * 1e9f).toLong()
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) / 1e9f
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            fill += (target - fill) * (1f - exp(-dt * 7f))
            // 쉬는 상태로 돌아와 물도 다 빠졌으면 멈춘다
            if (!stillAnimating && !isActive(target, fill)) break
        }
    }
    // 상태별 무게(0~1). 상태가 바뀌면 각 무게가 스프링으로 옮겨 가 모양이 섞이며 넘어간다
    val orbit = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val arrow = remember { Animatable(0f) }
    val tank = remember { Animatable(0f) }
    val swirl = remember { Animatable(0f) }
    val sad = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state::class) {
        fun target(a: Animatable<Float, *>, on: Boolean) = launch { a.animateTo(if (on) 1f else 0f, Springs.water(reduce)) }
        target(orbit, state is Updater.State.Checking)
        target(arrow, state is Updater.State.Available)
        target(tank, state is Updater.State.Downloading || state is Updater.State.Installing)
        target(swirl, state is Updater.State.Installing)
        target(sad, state is Updater.State.Failed)
        if (state is Updater.State.UpToDate) {
            // 최신: 한 번 통 튀고 체크를 그린다
            launch {
                pop.snapTo(0.7f)
                pop.animateTo(1f, Springs.pop(reduce))
            }
            launch {
                check.snapTo(0f)
                check.animateTo(1f, Springs.calm(reduce))
            }
        } else {
            target(check, false)
        }
    }

    Box(modifier.fillMaxWidth().height(84.dp)) {
        // 1층: 물(goo로 방울끼리 이어 붙는다)
        Canvas(Modifier.fillMaxWidth().height(84.dp).goo(c.ink, 7.dp)) {
            liquid(time, orbit.value, arrow.value, tank.value, swirl.value, sad.value, pop.value, fill, rate)
        }
        // 2층: 또렷한 선(통 테두리·체크·화살표·느낌표)
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            marks(c.ink, c.onInk, time, check.value, arrow.value, tank.value, swirl.value, sad.value, pop.value)
        }
    }
}

private fun DrawScope.u(v: Float) = v * 1.dp.toPx()

/** 방울·통 안의 물·튀는 물방울. */
private fun DrawScope.liquid(t: Float, orbit: Float, arrow: Float, tank: Float, swirl: Float, sad: Float, pop: Float, fill: Float, rate: Float) {
    val cx = size.width / 2
    val cy = size.height / 2
    val ink = androidx.compose.ui.graphics.Color.Black
    val breathe = 1f + 0.04f * sin(t * 2.2f)
    // 기본 방울 반지름: 실패면 납작하게, 통이 되면 사라진다(통의 물이 대신한다)
    val r = u(22f) * breathe * pop * (1f - 0.15f * sad) * (1f - tank)
    val bob = u(4f) * sin(t * 2.4f) * arrow
    if (r > 0.5f) {
        if (sad > 0.01f) {
            // 주저앉은 방울: 아래로 퍼진 타원
            val w = r * (1f + 0.35f * sad)
            val h = r * (1f - 0.3f * sad)
            drawOval(ink, Offset(cx - w, cy - h + u(6f) * sad), Size(w * 2, h * 2))
            // 아래로 떨어지는 물방울(1.4초마다 하나)
            val k = (t / 1.4f) % 1f
            val dy = cy + h + u(4f) + k * k * u(26f)
            drawCircle(ink, u(5f) * (1f - 0.6f * k) * sad, Offset(cx, dy))
        } else {
            drawCircle(ink, r, Offset(cx, cy + bob))
        }
    }
    // 확인 중: 작은 방울 셋이 돌며 큰 방울에 붙었다 떨어진다
    if (orbit > 0.01f) {
        for (i in 0 until 3) {
            val a = t * 2.6f + i * (2f * PI.toFloat() / 3f)
            val dist = u(30f) + u(6f) * sin(t * 3.1f + i)
            drawCircle(ink, u(7f) * orbit, Offset(cx + cos(a) * dist * orbit, cy + sin(a) * dist * orbit))
        }
    }
    // 새 버전: 꼭대기에서 작은 방울이 맺혀 올라간다(1.8초 주기)
    if (arrow > 0.01f) {
        val k = (t / 1.8f) % 1f
        drawCircle(ink, u(6f) * arrow * (1f - k * 0.5f), Offset(cx, cy + bob - u(22f) - k * u(16f)))
    }
    // 통 안의 물: 왼쪽부터 fill만큼, 앞면은 속도만큼 출렁인다
    if (tank > 0.01f) {
        val left = cx - u(130f) * tank
        val width = u(260f) * tank
        val top = cy - u(14f)
        val h = u(28f)
        val front = left + width * fill
        val amp = u(3f) + u(7f) * rate.coerceIn(0f, 1f) * (1f - swirl)
        val speed = 4f + 8f * rate
        val path = Path().apply {
            moveTo(left, top)
            var y = top
            lineTo(front, top)
            while (y <= top + h) {
                val x = front + amp * sin(y / u(6f) + t * speed)
                lineTo(x.coerceAtLeast(left), y)
                y += u(2f)
            }
            lineTo(left, top + h)
            close()
        }
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + width, top + h, CornerRadius(h / 2))) }) {
            drawPath(path, ink)
        }
        // 앞면에서 튀어 오르는 물방울: 받는 속도가 빠를수록 많이·높이
        val drops = (1 + (rate * 5).toInt()) * if (fill in 0.02f..0.98f) 1 else 0
        for (i in 0 until drops) {
            val k = ((t * (0.9f + 0.6f * rate) + i * 0.37f) % 1f)
            val arc = 4f * k * (1f - k)
            drawCircle(
                ink,
                u(3.2f) * (1f - k * 0.5f),
                Offset(front + u(6f) + k * u(14f), top + h / 2 - arc * u(10f + 14f * rate) + (i % 2) * u(4f))
            )
        }
        // 설치 중: 물이 가운데로 소용돌이치며 모인다
        if (swirl > 0.01f) {
            for (i in 0 until 6) {
                val a = t * 4f + i * (PI.toFloat() / 3f)
                val d = u(26f) * (1f - swirl * 0.6f) * (0.7f + 0.3f * abs(sin(t * 2f + i)))
                drawCircle(ink, u(5f) * swirl, Offset(cx + cos(a) * d, cy + sin(a) * d * 0.5f))
            }
            drawCircle(ink, u(16f) * swirl * (1f + 0.08f * sin(t * 5f)), Offset(cx, cy))
        }
    }
}

/** 또렷한 선: 통 테두리, 최신 체크, 새 버전 화살표, 실패 느낌표. */
private fun DrawScope.marks(ink: androidx.compose.ui.graphics.Color, onInk: androidx.compose.ui.graphics.Color, t: Float, check: Float, arrow: Float, tank: Float, swirl: Float, sad: Float, pop: Float) {
    val cx = size.width / 2
    val cy = size.height / 2
    val s = Stroke(u(3f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (tank > 0.01f) {
        val left = cx - u(130f) * tank
        val width = u(260f) * tank
        drawRoundRect(ink, Offset(left - u(3f), cy - u(17f)), Size(width + u(6f), u(34f)), CornerRadius(u(17f)), style = Stroke(u(1.6f)), alpha = tank * (1f - swirl))
    }
    if (check > 0.01f) {
        // 체크를 그리는 정도만큼 선을 늘인다
        val p1 = Offset(cx - u(9f), cy)
        val p2 = Offset(cx - u(2.5f), cy + u(7f))
        val p3 = Offset(cx + u(10f), cy - u(7f))
        val k = check.coerceIn(0f, 1f) * pop
        val first = (k / 0.4f).coerceIn(0f, 1f)
        drawLine(onInk, p1, p1 + (p2 - p1) * first, s.width, StrokeCap.Round)
        if (k > 0.4f) drawLine(onInk, p2, p2 + (p3 - p2) * ((k - 0.4f) / 0.6f).coerceIn(0f, 1f), s.width, StrokeCap.Round)
    }
    if (arrow > 0.01f) {
        val bob = u(4f) * sin(t * 2.4f) * arrow
        val a = arrow
        drawLine(onInk, Offset(cx, cy + bob - u(9f) * a), Offset(cx, cy + bob + u(8f) * a), s.width, StrokeCap.Round)
        drawLine(onInk, Offset(cx - u(7f) * a, cy + bob + u(1f) * a), Offset(cx, cy + bob + u(8f) * a), s.width, StrokeCap.Round)
        drawLine(onInk, Offset(cx + u(7f) * a, cy + bob + u(1f) * a), Offset(cx, cy + bob + u(8f) * a), s.width, StrokeCap.Round)
    }
    if (sad > 0.01f) {
        val y0 = cy + u(6f) * sad
        drawLine(onInk, Offset(cx, y0 - u(9f) * sad), Offset(cx, y0 + u(2f) * sad), s.width, StrokeCap.Round)
        drawCircle(onInk, u(2f) * sad, Offset(cx, y0 + u(8f) * sad))
    }
}
