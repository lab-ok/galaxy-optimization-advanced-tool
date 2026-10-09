package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 액체 버튼. 원본 liquid_button.js + engine.js burster를 그대로 옮겼다.
 * - 누르는 동안: 몸통이 옆으로 1.06배, 위아래로 1.16배 부푼다(jelly 스프링).
 * - 실행: 50ms 안에 몸통이 방울 18개로 '팍' 터져 바깥으로 튀고, 0.3초 머문 뒤 1.05초에 걸쳐
 *   버튼 중심선으로 기어 와 서로 붙으며 다시 몸통이 된다. 터진 자리에는 그림자와 물 자국이 남았다 마른다.
 *   다 뭉치면 한 번 출렁인다(0.92×1.12 → 1).
 * - 사용할 수 없는 버튼: 잉크 대신 평평한 회색, 눌러도 부풀지 않고 좌우로 짧게 흔들려 거절을 알린다.
 * [primary]가 false면 테두리 버튼(같은 눌림·흔들림, 터짐 없음). [danger]는 빨간색.
 */
@Composable
fun LiquidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    burst: Boolean = primary,
    /** 되돌리기 어려운 동작(권한 초기화 등)은 빨간색으로 표시한다. */
    danger: Boolean = false,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val ink = if (danger) c.danger else c.ink
    val fill = if (enabled) ink else c.line
    val fg = when {
        !enabled -> c.muted.copy(alpha = 0.55f)
        primary -> c.onInk
        else -> ink
    }

    // 몸통 크기 배율(jelly)과 거절 흔들림
    val bw = remember { Animatable(1f) }
    val bh = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(pressed, enabled) {
        val (tx, ty) = if (pressed && enabled) 1.06f to 1.16f else 1f to 1f
        launch { bw.animateTo(tx, Springs.pop(reduce)) }
        launch { bh.animateTo(ty, Springs.pop(reduce)) }
    }

    // 터짐 진행(초). -1이면 쉬는 중.
    var t by remember { mutableFloatStateOf(-1f) }
    var drops by remember { mutableStateOf(emptyList<Drop>()) }

    Box(
        modifier
            .height(LiquidShape.control + 4.dp)
            .defaultMinSize(minWidth = 96.dp)
            .graphicsLayer { translationX = shake.value }
            .semantics { if (!enabled) disabled() }
            .clickable(interaction, null, role = Role.Button) {
                if (!enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!reduce) scope.launch {
                        shake.snapTo(0f)
                        shake.animateTo(0f, keyframes {
                            durationMillis = 320
                            -14f at 50; 12f at 110; -8f at 170; 5f at 230; -2f at 280
                        })
                    }
                    return@clickable
                }
                onClick()
                if (burst && primary && !reduce && t < 0f) scope.launch {
                    drops = List(N) { i -> Drop.random(i) }
                    val start = withFrameNanos { it } - 14_000_000L      // 다음 프레임에 이미 절반 넘게 튀어 있도록
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    while (true) {
                        val now = withFrameNanos { it }
                        val s = (now - start) / 1e9f
                        if (s >= T_END) break
                        t = s
                    }
                    t = -1f
                    // 다 뭉치면 한 번 출렁
                    bw.snapTo(0.92f); bh.snapTo(1.12f)
                    launch { bw.animateTo(1f, Springs.pop()) }
                    launch { bh.animateTo(1f, Springs.pop()) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val running = t >= 0f
        val gather = if (running) gatherOf(t, drops) else 1f
        // 흔적: 터진 자리의 그림자와 물 자국 고리. 방울이 모일수록 마른다.
        if (running) Canvas(Modifier.matchParentSize()) {
            val a = 1f - gather
            val r = CornerRadius(size.height / 2)
            drawRoundRect(ink.copy(alpha = 0.16f * a), cornerRadius = r)
            drawRoundRect(ink.copy(alpha = 0.18f * a), cornerRadius = r, style = Stroke(1.6.dp.toPx()))
        }
        if (primary) {
            // 몸통과 방울: goo 층. 방울이 버튼 밖으로 튀므로 그리는 영역을 넉넉히 넓힌다.
            Canvas(Modifier.matchParentSize().overflow(OVERFLOW).goo(fill)) {
                val p = OVERFLOW.toPx()
                val bwPx = size.width - p * 2
                val bhPx = size.height - p * 2
                val cx = size.width / 2
                val cy = size.height / 2
                if (running) {
                    drawBody(fill, cx, cy, bwPx * gather.pow(1.25f), bhPx * min(1f, gather * 1.25f))
                    drops.forEach { d -> d.draw(this, fill, t, cx, cy, bwPx, bhPx) }
                } else {
                    drawBody(fill, cx, cy, bwPx * bw.value, bhPx * bh.value)
                }
            }
        } else {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width * bw.value
                val h = size.height * bh.value
                // 사용할 수 없으면 테두리 안을 평평한 회색으로 채워 눌리지 않는 버튼임을 분명히 한다.
                if (!enabled) drawRoundRect(
                    c.muted.copy(alpha = 0.10f),
                    Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(h / 2),
                )
                drawRoundRect(
                    if (enabled) (if (danger) c.danger else c.line) else c.line.copy(alpha = 0.6f),
                    Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(h / 2),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        Text(
            text,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .graphicsLayer { alpha = if (running) ((gather - 0.8f) / 0.2f).coerceIn(0f, 1f) else 1f },
        )
    }
}

private const val N = 18
private const val T_POP = 0.05f
private const val T_HANG = 0.3f
private const val T_GATHER = 1.05f
private const val T_SPREAD = 0.3f
private const val T_END = T_HANG + T_SPREAD + T_GATHER
private val OVERFLOW = 96.dp

private fun out4(k: Float) = 1f - (1f - k).pow(4)
private fun inOut(k: Float) = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).pow(3) / 2f

/** 모인 정도(0~1): 방울별 모임 진행의 평균. 머무는 동안은 0. */
private fun gatherOf(t: Float, drops: List<Drop>): Float {
    if (t < T_HANG || drops.isEmpty()) return 0f
    return drops.sumOf { inOut(((t - T_HANG - it.delay) / T_GATHER).coerceIn(0f, 1f)).toDouble() }.toFloat() / drops.size
}

private fun DrawScope.drawBody(color: androidx.compose.ui.graphics.Color, cx: Float, cy: Float, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    drawRoundRect(color, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(h / 2))
}

/** 그리는 영역을 사방으로 [pad]만큼 넓힌다(배치 크기는 그대로). */
private fun Modifier.overflow(pad: Dp) = layout { measurable, constraints ->
    val p = pad.roundToPx()
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(w + p * 2, h + p * 2))
    layout(w, h) { placeable.place(-p, -p) }
}

/** 방울 하나. 값은 원본 burster의 난수 범위를 dp로 옮긴 것(원본 단위 px = dp). */
private class Drop(
    val ex: Float, val ey: Float,
    val r: Float, val startK: Float, val dist: Float,
    val vx: Float, val vy: Float,
    val f: Float, val gyK: Float, val delay: Float,
) {
    fun draw(scope: DrawScope, color: androidx.compose.ui.graphics.Color, t: Float, cx: Float, cy: Float, w: Float, h: Float) = with(scope) {
        val dp = 1.dp.toPx()
        val sx = cx + ex * w * 0.3f * startK
        val sy = cy + ey * h * 0.3f * startK
        val endX = cx + ex * (w / 2 * 0.85f + dist * dp)
        val endY = cy + ey * (h / 2 * 0.85f + dist * dp * 0.75f)
        var rr = r * dp
        val x: Float
        val y: Float
        if (t < T_POP) {
            val k = out4(min(1f, t / T_POP))
            x = sx + (endX - sx) * k; y = sy + (endY - sy) * k
            rr *= 0.6f + 0.4f * k
        } else {
            // 튄 자리에서 아주 조금 미끄러지며 멈춘다(위에서 본 장면이라 중력은 없다)
            val hold = min(t, T_HANG) - T_POP
            val slide = (1f - exp(-6f * hold)) / 6f
            val hx = endX + vx * dp * slide
            val hy = endY + vy * dp * slide
            if (t < T_HANG) {
                x = hx; y = hy
            } else {
                val k = ((t - T_HANG - delay) / T_GATHER).coerceIn(0f, 1f)
                val e = inOut(k)
                val gx = cx - w / 2 + h / 2 + (w - h) * f
                val gy = cy + gyK * h * 0.25f
                x = hx + (gx - hx) * e; y = hy + (gy - hy) * e
                rr *= 1f - max(0f, (k - 0.75f) / 0.25f) * 0.85f      // 다 오면 몸통에 녹아든다
            }
        }
        // 그리는 영역 가장자리에서 잘리지 않게 안쪽에 둔다
        val px = x.coerceIn(rr + 1, size.width - rr - 1)
        val py = y.coerceIn(rr + 1, size.height - rr - 1)
        if (rr > 0f) drawCircle(color, rr, Offset(px, py))
    }

    companion object {
        fun random(i: Int): Drop {
            val a = (i.toFloat() / N) * 2f * PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.55f
            val ex = cos(a); val ey = sin(a)
            return Drop(
                ex = ex, ey = ey,
                r = 4f + Random.nextFloat() * 7.5f,
                startK = Random.nextFloat(),
                dist = 22f + Random.nextFloat() * 58f,
                vx = ex * (30f + Random.nextFloat() * 50f),
                vy = ey * (20f + Random.nextFloat() * 36f),
                f = (i % 7) / 6f,
                gyK = Random.nextFloat() - 0.5f,
                delay = Random.nextFloat() * T_SPREAD,
            )
        }
    }
}
