package com.labok.goat.ui.nav

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.labok.goat.AppInfo
import com.labok.goat.ui.about.LOGO_CORNER
import com.labok.goat.ui.about.idleRipple
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.SoftBlob
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.nudge
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.liquid.rememberSoftBlob
import kotlin.math.hypot
import kotlin.math.tanh

/**
 * 사이드바 머리: 유리 방울(머리판) 위에 앱 아이콘 방울과 염소 얼굴, 앱 이름이 놓인다.
 * 셋 다 연체 물리([SoftBlob])라서
 * - 사이드바가 열리면 머리판은 아이콘 자리에서 오른쪽으로 번지고, 아이콘은 동그란 방울이 네모로 부풀며,
 *   염소는 아이콘 안쪽에서 관성으로 흔들린다.
 * - 누르면 닿은 자리가 움푹 들어갔다 출렁이고, 끌면 손가락 쪽으로 늘어나며(고무줄처럼 갈수록 덜 늘어남)
 *   염소는 관성으로 늦게 따라오며 고개를 갸웃한다. 놓으면 표면장력으로 제자리에 모인다.
 * 아이콘 색과 모양은 앱 아이콘(ic_app_logo)과 같다.
 */
@Composable
internal fun DrawerHeader(version: String, visible: Boolean, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val d = LocalDensity.current
    val headH = with(d) { 64.dp.toPx() }
    val headR = with(d) { 22.dp.toPx() }
    val logoS = with(d) { 44.dp.toPx() }
    val logoR = logoS * LOGO_CORNER
    val logoX = with(d) { (10 + 22).dp.toPx() }
    val maxPull = with(d) { 18.dp.toPx() }
    val unit = logoS / 72f // 아이콘 벡터 72칸 → px

    var box by remember { mutableStateOf(IntSize.Zero) }
    val head = rememberSoftBlob()
    val logo = rememberSoftBlob()
    val drop = rememberSoftBlob()
    // 염소 표정: 누르면 찡그리고, 많이 누르면 울고, 더 누르면 화낸다
    val mood = rememberGoatMood()
    val goat = goatPainter(mood.mood, mood.variant)
    val text = remember { Animatable(0f) }
    val paths = remember { Path() to Path() }

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
        drop.moveTo(logoX, cy(), 4f * unit, 4f * unit, animate)
        if (animate) text.animateTo(1f, tween(260, delayMillis = 120)) else text.snapTo(1f)
    }
    // 홈·앱 정보 로고와 같이, 사이드바가 열려 있는 동안 가끔 표면장력으로 일렁인다
    LaunchedEffect(visible, box, reduce) {
        if (!visible || reduce || box == IntSize.Zero) return@LaunchedEffect
        idleRipple(logo, drop, { logoX }, { cy() }, logoS)
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
                    haptics.press()
                    val p = down.position

                    // 누른 자리를 안쪽으로 밀어 움푹 들어가게 한다
                    fun inward(cx: Float, cy: Float, speed: Float): Offset {
                        val dx = cx - p.x
                        val dy = cy - p.y
                        val l = hypot(dx, dy).coerceAtLeast(1f)
                        return Offset(dx / l * speed, dy / l * speed)
                    }
                    inward(headX(), cy(), 1800f).let { head.poke(p.x, p.y, it.x, it.y, headH * 0.6f) }
                    if (hypot(p.x - logoX, p.y - cy()) < logoS * 0.7f) {
                        if (mood.press()) haptics.snap()
                        val k = mood.impulse
                        inward(logoX, cy(), 1400f * k).let { logo.poke(p.x, p.y, it.x, it.y, logoS * 0.5f) }
                        drop.poke(logoX, cy(), (p.x - logoX) * -14f * k, (p.y - cy()) * -14f * k, logoS)
                    }
                    var total = Offset.Zero
                    drag(down.id) { change ->
                        total += change.positionChange()
                        change.consume()
                        // 고무줄: 멀리 끌수록 덜 늘어난다
                        val rx = maxPull * tanh(total.x / (maxPull * 3))
                        val ry = maxPull * tanh(total.y / (maxPull * 3))
                        head.nudge(headX() + rx * 0.5f, cy() + ry * 0.5f)
                        logo.nudge(logoX + rx, cy() + ry)
                        drop.nudge(logoX + rx, cy() + ry)
                    }
                    head.nudge(headX(), cy())
                    logo.nudge(logoX, cy())
                    drop.nudge(logoX, cy())
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            head.frame
            logo.frame
            drop.frame
            if (!head.blob.ready || !logo.blob.ready || !drop.blob.ready) return@Canvas
            val (hp, lp) = paths
            // 머리판(유리)
            head.blob.path(hp)
            drawPath(hp, c.surface)
            drawPath(hp, c.line, style = Stroke(1.dp.toPx()))
            // 아이콘 바탕
            logo.blob.path(lp)
            drawPath(lp, c.logo)
            // 염소 얼굴: 안쪽 방울(drop)의 중심을 따라가므로, 아이콘을 끌면 염소가 관성으로 늦게 따라오며 고개를 갸웃한다
            drawGoat(goat, lp, logo.blob.cx, logo.blob.cy, drop.blob.cx, drop.blob.cy, logoS)
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
                }
        ) {
            Text(AppInfo.NAME, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
            Text("버전 $version", color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}
