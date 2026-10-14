package com.labok.goat.ui.lab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.liquid.goo
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 힌지 각도 게이지이자 범위 조절: 0°(닫힘)~180°(펼침) 반원 위에 플렉스 범위([min]~[max])를 잉크 띠로 칠하고, 띠 양 끝에 값이 적힌
 * 물방울 손잡이를 둔다. [editable]이면 손잡이를 반원을 따라 끌어 범위를 바꾼다([onRange], 손을 떼면 [onRangeFinished]).
 * 기본 조절바처럼 손잡이를 붙든 채 가만히 있으면 세밀 조절이 펼쳐져 반원 전체가 앞뒤 [GAUGE_FINE]°가 된다.
 * 지금 각도는 작은 물방울 바늘이 스프링으로 따라가며, goo로 띠·손잡이와 엉겨 붙는다. 끄는 동안에는 자동 플렉스를 잠시 멈춘다.
 */
@Composable
internal fun FlexAngleGauge(angle: Float?, min: Int, max: Int, active: Boolean, editable: Boolean = true, onRange: (Int, Int) -> Unit = { _, _ -> }, onRangeFinished: () -> Unit = {}) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val measurer = rememberTextMeasurer()
    val lo by rememberUpdatedState(min)
    val hi by rememberUpdatedState(max)
    val change by rememberUpdatedState(onRange)
    val finish by rememberUpdatedState(onRangeFinished)
    // 센서 값은 쉬지 않고 들어오므로 스프링을 매번 새로 걸지 않고(걸면 시작하기도 전에 취소돼 바늘이 멈춰 있었다),
    // 프레임마다 목표 쪽으로 물처럼 따라간다(감쇠 있는 용수철: 살짝 지나쳤다 돌아온다)
    val target by rememberUpdatedState(angle)
    var pos by remember { mutableStateOf(angle ?: 180f) }
    LaunchedEffect(reduce) {
        var vel = 0f
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val t = target ?: continue
            if (reduce) {
                pos = t
                continue
            }
            val k = 120f * UiMotion.speed * UiMotion.speed
            val damp = 2f * sqrt(k) * UiMotion.ratio(0.55f)
            vel += (k * (t - pos) - damp * vel) * dt
            pos += vel * dt
        }
    }
    // 잡은 손잡이: 0 없음, 1 범위 끝(min), 2 범위 시작(max). 잡으면 손잡이가 부풀어 오른다
    var held by remember { mutableStateOf(0) }
    // 세밀 조절: 펼쳐진 동안의 가운데 값(반원 꼭대기)
    var fineBase by remember { mutableStateOf<Int?>(null) }
    val fineK by animateFloatAsState(if (fineBase != null) 1f else 0f, Springs.water(reduce), label = "gaugeFine")
    val grow = listOf(
        animateFloatAsState(if (held == 1) 1f else 0f, Springs.water(reduce), label = "gaugeLo"),
        animateFloatAsState(if (held == 2) 1f else 0f, Springs.water(reduce), label = "gaugeHi")
    )
    val glow by animateFloatAsState(if (active) 1f else 0f, Springs.pop(reduce), label = "flexGlow")
    val handleStyle = TextStyle(fontFamily = GalaxyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    fun geo(w: Float, h: Float, density: Float): Pair<Offset, Float> {
        // 잡으면 값 방울이 반원 바깥으로 떠오르므로(GAUGE_LIFT) 위·양옆에 그만큼 자리를 둔다
        val r = minOf(w / 2 - 58 * density, h - 72 * density)
        return Offset(w / 2, h - 12 * density) to r
    }

    // 각도 → 화면 위치: 180°(펼침)는 오른쪽 끝, 0°(닫힘)는 왼쪽 끝
    fun at(center: Offset, r: Float, deg: Float) = center + Offset(-cos(Math.toRadians(deg.toDouble())).toFloat() * r, -sin(Math.toRadians(deg.toDouble())).toFloat() * r)

    // 화면 위치 → 각도(반원 아래쪽은 가까운 끝으로)
    fun degAt(center: Offset, p: Offset): Float {
        val dx = p.x - center.x
        val dy = center.y - p.y
        if (dy < 0) return if (dx < 0) 0f else 180f
        return Math.toDegrees(atan2(dy.toDouble(), -dx.toDouble())).toFloat().coerceIn(0f, 180f)
    }

    // 세밀 조절 중 값 [v]가 놓이는 반원 위 각도: 가운데 값이 꼭대기(90°), 앞뒤 GAUGE_FINE°가 양 끝
    fun fineDeg(base: Int, v: Int) = 90f + (v - base) * 90f / GAUGE_FINE

    // 손잡이가 그려지는 반원 위 각도: 세밀 조절 중인 손잡이는 확대 눈금 위 자리로 물 흐르듯 옮겨 간다
    fun shownDeg(i: Int, v: Int): Float {
        val base = fineBase ?: return v.toFloat()
        return if (held == i + 1) v + (fineDeg(base, v) - v) * fineK else v.toFloat()
    }

    // 물방울 손잡이: 손가락(값)을 용수철로 늦게 따라가고, 빨리 움직일수록 반원을 따라 길게 늘어났다가 멈추면 출렁이며 돌아온다
    val springs = remember { listOf(Animatable(min.toFloat()), Animatable(max.toFloat())) }
    listOf(min, max).forEachIndexed { i, v ->
        val goal = shownDeg(i, v)
        LaunchedEffect(goal, reduce) {
            if (reduce) springs[i].snapTo(goal) else springs[i].animateTo(goal, spring(UiMotion.ratio(0.5f), UiMotion.stiffness(700f, 0.5f)))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .pointerInput(editable) {
                if (!editable) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val (center, r) = geo(size.width.toFloat(), size.height.toFloat(), density)
                    val grab = 30.dp.toPx()
                    val dl = (down.position - at(center, r, lo.toFloat())).getDistance()
                    val dh = (down.position - at(center, r, hi.toFloat())).getDistance()
                    // 손잡이 근처를 눌렀을 때만 잡는다(그 밖은 화면 스크롤로 넘긴다)
                    val which = when {
                        dl > grab && dh > grab -> return@awaitEachGesture
                        dl <= dh -> 1
                        else -> 2
                    }
                    down.consume()
                    held = which
                    haptics.press()
                    AutoFlex.setPaused("drag", true)
                    val slop = 5.dp.toPx()
                    var still = down.position
                    var stillSince = down.uptimeMillis
                    var anchor = 0f
                    fun openFine(p: Offset) {
                        if (fineBase != null) return
                        fineBase = if (which == 1) lo else hi
                        anchor = degAt(center, p)
                        haptics.press()
                    }
                    fun set(v: Int) {
                        val (nl, nh) = if (which == 1) {
                            v.coerceIn(AutoFlex.ANGLE_LOW, hi - 10) to hi
                        } else {
                            lo to v.coerceIn(lo + 10, AutoFlex.ANGLE_HIGH)
                        }
                        if (nl != lo || nh != hi) {
                            change(nl, nh)
                            haptics.tick()
                        }
                    }
                    try {
                        while (true) {
                            // 가만히 붙들고 있으면 이벤트가 오지 않으므로 시간으로도 확인한다
                            val ev = withTimeoutOrNull(GAUGE_FINE_HOLD_MS) { awaitPointerEvent() }
                            if (ev == null) {
                                openFine(still)
                                continue
                            }
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            if ((ch.position - still).getDistance() > slop) {
                                still = ch.position
                                stillSince = ch.uptimeMillis
                            } else if (ch.uptimeMillis - stillSince >= GAUGE_FINE_HOLD_MS) {
                                openFine(ch.position)
                            }
                            val base = fineBase
                            if (base == null) {
                                set(degAt(center, ch.position).roundToInt())
                            } else {
                                // 세밀 조절: 반원 전체가 앞뒤 GAUGE_FINE°라 같은 손 움직임에 훨씬 조금씩 바뀐다
                                val d = (degAt(center, ch.position) - anchor) * (2f * GAUGE_FINE) / 180f
                                set((base + d.roundToInt()).coerceIn(base - GAUGE_FINE, base + GAUGE_FINE))
                            }
                        }
                    } finally {
                        held = 0
                        fineBase = null
                        AutoFlex.setPaused("drag", false)
                        finish()
                    }
                }
            }
    ) {
        // 바탕 반원(선)
        Canvas(Modifier.matchParentSize()) {
            val (center, r) = geo(size.width, size.height, density)
            drawArc(c.line, 180f, 180f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        // 범위 띠·손잡이·바늘(goo로 이어 붙는다). 세밀 조절 중에는 띠와 다른 손잡이가 잦아든다
        Canvas(Modifier.matchParentSize().goo(c.ink, 5.dp)) {
            val (center, r) = geo(size.width, size.height, density)
            val band = (8 + 4 * glow).dp.toPx() * (1 - fineK)
            if (band > 0.5f) {
                drawArc(c.ink, 180f + springs[0].value, springs[1].value - springs[0].value, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(band, cap = StrokeCap.Round))
            }
            listOf(min, max).forEachIndexed { i, v ->
                val shrink = if (fineBase != null && held != i + 1) 1 - fineK else 1f
                if (shrink < 0.05f) return@forEachIndexed
                val d = springs[i].value
                val p = at(center, r, d)
                val g = grow[i].value
                val tw = measurer.measure("$v°", handleStyle).size.width
                // 늘어남: 각속도(°/초)에 비례해 반원 방향으로 길어지고 두께는 조금 얇아진다
                val stretch = (abs(springs[i].velocity) / 400f).coerceAtMost(0.6f)
                // 기본 조절바와 같다: 잡으면 손잡이가 납작하고 넓어지고(30→24dp), 값 방울이 반원 바깥으로 떠올라 큰 글자로 값을 보여 준다
                val w = (tw + (22 + 8 * g).dp.toPx()) * shrink * (1 + stretch)
                val h = (28 - 4 * g).dp.toPx() * shrink * (1 - stretch * 0.3f)
                // 손잡이는 반원을 따라 눕는다(꼭대기 90°에서 수평, 양 끝으로 갈수록 세워진다)
                rotate(d - 90f, p) {
                    drawRoundRect(c.ink, Offset(p.x - w / 2, p.y - h / 2), Size(w, h), CornerRadius(h / 2))
                }
                if (g > 0.01f) {
                    val bs = (10 + 36 * g).dp.toPx()
                    val lift = measurer.measure("$v°", handleStyle.copy(fontSize = 17.sp)).size.width
                    val bw = maxOf(bs, (lift + 20.dp.toPx()) * (bs / 46.dp.toPx()))
                    val q = at(center, r + g * GAUGE_LIFT.dp.toPx(), d)
                    rotate(d - 90f, q) {
                        drawRoundRect(c.ink, Offset(q.x - bw / 2, q.y - bs / 2), Size(bw, bs), CornerRadius(bs / 2))
                    }
                }
            }
            // 세밀 조절 패널: 기본 조절바처럼 손잡이 자리에서 반원을 따라 양옆으로 퍼져 반원 전체를 덮는 잉크 띠(goo라 손잡이와 이어진다)
            if (fineK > 0.01f) {
                val fromDeg = listOf(min, max).getOrNull(held - 1)?.toFloat() ?: 90f
                val mid = fromDeg + (90f - fromDeg) * fineK
                val sweep = 12f + (180f - 12f) * fineK
                drawArc(c.ink, 180f + mid - sweep / 2, sweep, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
            }
            if (angle != null && fineBase == null) drawCircle(c.ink, (8 + 3 * glow).dp.toPx(), at(center, r, pos))
        }
        // 세밀 조절 눈금(패널 위, 반전색): 앞뒤 GAUGE_FINE칸, 5°마다 길게. 그 위에 손잡이 값 글자(반전)
        Canvas(Modifier.matchParentSize()) {
            val (center, r) = geo(size.width, size.height, density)
            val base = fineBase
            if (base != null && fineK > 0.01f) {
                for (i in -GAUGE_FINE..GAUGE_FINE) {
                    val v = base + i
                    val long = v % 5 == 0
                    val len = (if (long) 10 else 5).dp.toPx() * fineK
                    val d = fineDeg(base, v)
                    drawLine(c.onInk.copy(alpha = 0.75f * fineK), at(center, r - len / 2, d), at(center, r + len / 2, d), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }
            listOf(min, max).forEachIndexed { i, v ->
                if (fineBase != null && held != i + 1 && fineK > 0.5f) return@forEachIndexed
                val d = springs[i].value
                // 글자는 떠오른 값 방울을 따라 올라가며 커진다
                val g = grow[i].value
                val p = at(center, r + g * GAUGE_LIFT.dp.toPx(), d)
                val layout = measurer.measure("$v°", handleStyle.copy(color = c.onInk, fontSize = (12 + 5 * g).sp))
                rotate(d - 90f, p) {
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                }
            }
        }
        // 가운데 큰 각도 숫자와 지금 상태
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(angle?.let { "${pos.roundToInt()}°" } ?: "—", color = c.ink, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
            Text(if (active) "플렉스 모드" else "보통", color = if (active) c.ink else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** 잡았을 때 값 방울이 반원 바깥으로 떠오르는 거리(dp, 기본 조절바와 같다). */
private const val GAUGE_LIFT = 26

/** 게이지 세밀 조절이 보여 주는 앞뒤 칸(°). */
private const val GAUGE_FINE = 10

/** 이 시간(ms) 동안 거의 움직이지 않고 붙들고 있으면 게이지 세밀 조절을 연다(기본 조절바와 같다). */
private const val GAUGE_FINE_HOLD_MS = 750L
