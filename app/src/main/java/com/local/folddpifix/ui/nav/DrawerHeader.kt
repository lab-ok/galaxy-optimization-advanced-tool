package com.local.folddpifix.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.local.folddpifix.AppInfo
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import com.local.folddpifix.ui.liquid.SoftBlob
import com.local.folddpifix.ui.liquid.moveTo
import com.local.folddpifix.ui.liquid.nudge
import com.local.folddpifix.ui.liquid.poke
import com.local.folddpifix.ui.liquid.rememberSoftBlob
import com.local.folddpifix.ui.liquid.shapeTo
import kotlin.math.hypot
import kotlin.math.tanh

/**
 * 사이드바 머리: 유리 방울(머리판) 위에 앱 아이콘 방울과 그 안의 땅콩 모양 물방울, 앱 이름이 놓인다.
 * 셋 다 연체 물리([SoftBlob])라서
 * - 사이드바가 열리면 머리판은 아이콘 자리에서 오른쪽으로 번지고, 아이콘은 동그란 방울이 네모로 부풀며,
 *   가운데 물방울은 작은 점이 땅콩 모양으로 맺힌다.
 * - 누르면 닿은 자리가 움푹 들어갔다 출렁이고, 끌면 손가락 쪽으로 늘어나며(고무줄처럼 갈수록 덜 늘어남)
 *   안쪽 물방울은 관성으로 늦게 따라와 출렁인다. 놓으면 표면장력으로 제자리에 모인다.
 * 아이콘 색과 모양은 앱 아이콘(ic_app_logo)과 같다.
 */
@Composable
internal fun DrawerHeader(version: String, visible: Boolean, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val d = LocalDensity.current
    val headH = with(d) { 64.dp.toPx() }
    val headR = with(d) { 22.dp.toPx() }
    val logoS = with(d) { 44.dp.toPx() }
    val logoR = with(d) { 13.dp.toPx() }
    val logoX = with(d) { (10 + 22).dp.toPx() }
    val maxPull = with(d) { 18.dp.toPx() }
    val unit = logoS / 72f  // 아이콘 벡터 72칸 → px

    var box by remember { mutableStateOf(IntSize.Zero) }
    val head = rememberSoftBlob()
    val logo = rememberSoftBlob()
    val drop = rememberSoftBlob()
    val peanut = remember(unit) { peanutOutline(SoftBlob.N, unit) }
    val text = remember { Animatable(0f) }
    val paths = remember { Triple(Path(), Path(), Path()) }

    fun cy() = box.height / 2f
    fun headX() = box.width / 2f

    // 등장: 머리판은 아이콘 크기에서 오른쪽으로 번지고, 아이콘은 원 → 둥근 네모, 물방울은 점 → 땅콩
    LaunchedEffect(visible, box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val animate = visible && !reduce
        if (animate) {
            head.moveTo(logoX, cy(), logoS, logoS, animate = false)
            logo.moveTo(logoX, cy(), logoS * 0.5f, logoS * 0.5f, animate = false)
            drop.moveTo(logoX, cy(), 4f * unit, 4f * unit, animate = false)
            text.snapTo(0f)
        }
        head.moveTo(headX(), cy(), box.width.toFloat(), headH, animate, corner = headR)
        logo.moveTo(logoX, cy(), logoS, logoS, animate, corner = logoR)
        drop.shapeTo(logoX, cy(), peanut.first, peanut.second, animate)
        if (animate) text.animateTo(1f, tween(260, delayMillis = 120)) else text.snapTo(1f)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .onSizeChanged { box = it }
            .pointerInput(reduce) {
                if (reduce) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val p = down.position
                    // 누른 자리를 안쪽으로 밀어 움푹 들어가게 한다
                    fun inward(cx: Float, cy: Float, speed: Float): Offset {
                        val dx = cx - p.x; val dy = cy - p.y; val l = hypot(dx, dy).coerceAtLeast(1f)
                        return Offset(dx / l * speed, dy / l * speed)
                    }
                    inward(headX(), cy(), 1800f).let { head.poke(p.x, p.y, it.x, it.y, headH * 0.6f) }
                    if (hypot(p.x - logoX, p.y - cy()) < logoS * 0.7f) {
                        inward(logoX, cy(), 1400f).let { logo.poke(p.x, p.y, it.x, it.y, logoS * 0.5f) }
                        drop.poke(logoX, cy(), (p.x - logoX) * -14f, (p.y - cy()) * -14f, logoS)
                    }
                    var total = Offset.Zero
                    drag(down.id) { change ->
                        total += change.positionChange()
                        change.consume()
                        // 고무줄: 멀리 끌수록 덜 늘어난다
                        val rx = maxPull * tanh(total.x / (maxPull * 3)); val ry = maxPull * tanh(total.y / (maxPull * 3))
                        head.nudge(headX() + rx * 0.5f, cy() + ry * 0.5f)
                        logo.nudge(logoX + rx, cy() + ry)
                        drop.nudge(logoX + rx, cy() + ry)
                    }
                    head.nudge(headX(), cy()); logo.nudge(logoX, cy()); drop.nudge(logoX, cy())
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            head.frame; logo.frame; drop.frame
            if (!head.blob.ready || !logo.blob.ready || !drop.blob.ready) return@Canvas
            val (hp, lp, dp) = paths
            // 머리판(유리)
            head.blob.path(hp)
            drawPath(hp, c.surface)
            drawPath(hp, c.line, style = Stroke(1.dp.toPx()))
            // 아이콘 바탕
            logo.blob.path(lp)
            drawPath(lp, Color(0xFF111111))
            // 화면 테두리·안쪽·접는 선은 아이콘 방울 중심을 따라 움직인다(벡터 좌표 54,54가 중심)
            val ox = logo.blob.cx; val oy = logo.blob.cy
            fun vx(x: Float) = ox + (x - 54f) * unit
            fun vy(y: Float) = oy + (y - 54f) * unit
            drawRoundRect(Color.White, Offset(vx(28.5f), vy(34.75f)), Size(51f * unit, 38.5f * unit), CornerRadius(2.4f * unit))
            val inner = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(vx(30.9f), vy(37.15f), vx(77.1f), vy(70.85f), CornerRadius(1.2f * unit)))
            }
            drawPath(inner, Color(0xFF1C1C1C))
            drawLine(Color.White.copy(alpha = 0.45f), Offset(vx(54f), vy(37.15f)), Offset(vx(54f), vy(70.85f)), 1.2f * unit)
            // 가운데 물방울(땅콩): 화면 안쪽을 벗어나지 않게 자른다
            clipPath(inner) { drawPath(drop.blob.path(dp), Color.White) }
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 66.dp)
                .graphicsLayer {
                    logo.frame
                    val k = text.value
                    alpha = k
                    // 글자는 아이콘 방울이 끌려간 만큼 조금 덜 따라간다(번지는 동안에는 아이콘이 제자리라 겹치지 않는다)
                    val dx = if (logo.blob.ready) logo.blob.cx - logoX else 0f
                    val dy = if (logo.blob.ready) logo.blob.cy - box.height / 2f else 0f
                    translationX = dx * 0.8f + (1f - k) * -10.dp.toPx()
                    translationY = dy * 0.8f
                },
        ) {
            Text(AppInfo.NAME, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
            Text("버전 $version", color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 앱 아이콘 가운데 땅콩 모양(ic_app_logo의 마지막 path)을 점 [n]개로 고르게 나눈다.
 * 중심(벡터 54,54) 기준 px 좌표이고, 윗변 가운데(목)에서 시계 방향으로 시작한다.
 */
private fun peanutOutline(n: Int, unit: Float): Pair<FloatArray, FloatArray> {
    val path = PathParser().parsePathString(
        "M44.8,49C49,49 50.7,51.9 54,51.9C57.3,51.9 59,49 63.2,49A5,5 0 1 1 63.2,59C59,59 57.3,56.1 54,56.1C50.7,56.1 49,59 44.8,59A5,5 0 1 1 44.8,49Z",
    ).toPath()
    val m = PathMeasure().apply { setPath(path, false) }
    val len = m.length
    val xs = FloatArray(n); val ys = FloatArray(n)
    // 시작점을 윗변 가운데(54, 51.9)로 맞춘다: 그 점까지의 길이를 찾는다
    var start = 0f; var best = Float.MAX_VALUE
    for (k in 0 until 400) {
        val p = m.getPosition(len * k / 400f)
        val dd = hypot(p.x - 54f, p.y - 51.9f)
        if (dd < best) { best = dd; start = len * k / 400f }
    }
    for (i in 0 until n) {
        val p = m.getPosition((start + len * i / n) % len)
        xs[i] = (p.x - 54f) * unit; ys[i] = (p.y - 54f) * unit
    }
    return xs to ys
}
