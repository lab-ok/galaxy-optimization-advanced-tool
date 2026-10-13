package com.labok.goat.ui.about

import com.labok.goat.ui.liquid.SoftBlobState
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import com.labok.goat.ui.nav.rememberGoatMood
import com.labok.goat.ui.nav.goatPainter
import com.labok.goat.ui.liquid.rememberLiquidHaptics
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.labok.goat.R
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.nudge
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberSoftBlob
import kotlin.math.hypot
import kotlin.math.tanh

/**
 * 앱 정보 머리: 사이드바 머리([com.labok.goat.ui.nav.DrawerHeader])와 같은 리퀴드 로고.
 * 나타날 때 아이콘 방울이 원에서 둥근 네모로 맺히고 염소가 톡 떨어진다. 누르면 움푹 들어가고, 끌면 고무줄처럼 늘어났다가
 * 표면장력으로 돌아오며, 염소는 안쪽 물방울을 따라 늦게 움직이며 고개를 갸웃한다. 제목과 설명도 아이콘을 조금 덜 따라간다.
 */
@Composable
internal fun AboutHeader(title: String, subtitle: String) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val d = LocalDensity.current
    val logoS = with(d) { 84.dp.toPx() }
    val logoR = logoS * LOGO_CORNER
    val logoTop = with(d) { 8.dp.toPx() }
    val maxPull = with(d) { 22.dp.toPx() }
    val unit = logoS / 72f  // 아이콘 벡터 72칸 → px

    var box by remember { mutableStateOf(IntSize.Zero) }
    val logo = rememberSoftBlob()
    val drop = rememberSoftBlob()
    // 염소 표정: 누르면 찡그리고, 많이 누르면 울고, 더 누르면 화낸다
    val mood = rememberGoatMood()
    val goat = goatPainter(mood.mood)
    val text = remember { Animatable(0f) }
    val path = remember { Path() }

    fun cx() = box.width / 2f
    fun cy() = logoTop + logoS / 2f

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        if (!reduce) {
            logo.moveTo(cx(), cy(), logoS * 0.45f, logoS * 0.45f, animate = false)
            drop.moveTo(cx(), cy(), 4f * unit, 4f * unit, animate = false)
        }
        logo.moveTo(cx(), cy(), logoS, logoS, !reduce, corner = logoR)
        drop.moveTo(cx(), cy(), 4f * unit, 4f * unit, !reduce)
        if (reduce) text.snapTo(1f) else text.animateTo(1f, tween(300, delayMillis = 140))
    }
    LaunchedEffect(box, reduce) {
        if (reduce || box == IntSize.Zero) return@LaunchedEffect
        idleRipple(logo, drop, { cx() }, { cy() }, logoS)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { box = it }
            .pointerInput(reduce) {
                if (reduce) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = down.position
                    if (hypot(p.x - cx(), p.y - cy()) > logoS * 0.75f) return@awaitEachGesture
                    haptics.press()
                    if (mood.press()) haptics.reject() else if (mood.startedCrying) haptics.snap()
                    val k = mood.impulse
                    // 누른 자리를 안쪽으로 밀어 움푹 들어가게 하고, 염소 방울은 반대로 튕긴다
                    val dx = cx() - p.x; val dy = cy() - p.y; val l = hypot(dx, dy).coerceAtLeast(1f)
                    logo.poke(p.x, p.y, dx / l * 1600f * k, dy / l * 1600f * k, logoS * 0.5f)
                    drop.poke(cx(), cy(), (p.x - cx()) * -14f * k, (p.y - cy()) * -14f * k, logoS)
                    var total = Offset.Zero
                    drag(down.id) { change ->
                        total += change.positionChange()
                        change.consume()
                        // 고무줄: 멀리 끌수록 덜 늘어난다
                        val rx = maxPull * tanh(total.x / (maxPull * 3)); val ry = maxPull * tanh(total.y / (maxPull * 3))
                        logo.nudge(cx() + rx, cy() + ry)
                        drop.nudge(cx() + rx, cy() + ry)
                    }
                    logo.nudge(cx(), cy()); drop.nudge(cx(), cy())
                }
            },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(84.dp + 16.dp)) {
                logo.frame; drop.frame
                if (!logo.blob.ready || !drop.blob.ready) return@Canvas
                logo.blob.path(path)
                drawPath(path, Color(0xFF111111))
                val gs = logoS * 0.98f
                val tilt = ((drop.blob.cx - logo.blob.cx) / logoS * 40f).coerceIn(-14f, 14f)
                rotate(tilt, Offset(drop.blob.cx, drop.blob.cy)) {
                    translate(drop.blob.cx - gs / 2, drop.blob.cy - gs / 2) { with(goat) { draw(Size(gs, gs)) } }
                }
            }
            Column(
                Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        logo.frame
                        val k = text.value
                        alpha = k
                        // 제목은 아이콘 방울이 끌려간 만큼 조금 덜 따라간다
                        val dx = if (logo.blob.ready) logo.blob.cx - cx() else 0f
                        val dy = if (logo.blob.ready) logo.blob.cy - cy() else 0f
                        translationX = dx * 0.6f
                        translationY = dy * 0.6f + (1f - k) * 10.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
                Text(subtitle, color = c.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * 작은 리퀴드 로고(홈 머리용): [AboutHeader]와 같은 검은 물방울 상자와 염소를 [size] 크기로 그린다.
 * 나타날 때 원에서 둥근 네모로 맺히고, 누르면 움푹 들어가며 염소가 튕기고, 끌면 고무줄처럼 늘어났다 돌아온다.
 * 모서리는 앱 정보 로고보다 둥글게(크기의 40%) 해 물방울에 가깝게 보인다. [idle]이면 가만히 있어도 몇 초마다
 * 가장자리를 살짝 건드려 표면장력으로 출렁였다 돌아오는 모습을 보인다(동작 줄이기면 하지 않는다).
 */
@Composable
internal fun LiquidGoatLogo(size: Dp, modifier: Modifier = Modifier, idle: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val d = LocalDensity.current
    val logoS = with(d) { size.toPx() }
    val logoR = logoS * LOGO_CORNER
    val maxPull = logoS * 22f / 84f
    val unit = logoS / 72f
    var box by remember { mutableStateOf(IntSize.Zero) }
    val logo = rememberSoftBlob()
    val drop = rememberSoftBlob()
    val mood = rememberGoatMood()
    val goat = goatPainter(mood.mood)
    val path = remember { Path() }
    fun cx() = box.width / 2f
    fun cy() = box.height / 2f

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        if (!reduce) {
            logo.moveTo(cx(), cy(), logoS * 0.45f, logoS * 0.45f, animate = false)
            drop.moveTo(cx(), cy(), 4f * unit, 4f * unit, animate = false)
        }
        logo.moveTo(cx(), cy(), logoS, logoS, !reduce, corner = logoR)
        drop.moveTo(cx(), cy(), 4f * unit, 4f * unit, !reduce)
    }
    // 쉬는 동안의 출렁임: 3~6초마다 가장자리 한 곳을 안쪽으로 살짝 밀면, 표면장력이 펴고 압력이 되밀며 물결처럼 잦아든다
    LaunchedEffect(box, idle, reduce) {
        if (!idle || reduce || box == IntSize.Zero) return@LaunchedEffect
        idleRipple(logo, drop, { cx() }, { cy() }, logoS)
    }

    Canvas(
        modifier
            .size(size)
            .onSizeChanged { box = it }
            .pointerInput(reduce) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = down.position
                    haptics.press()
                    if (mood.press()) haptics.reject() else if (mood.startedCrying) haptics.snap()
                    if (reduce) return@awaitEachGesture
                    val k = mood.impulse
                    val dx = cx() - p.x; val dy = cy() - p.y; val l = hypot(dx, dy).coerceAtLeast(1f)
                    logo.poke(p.x, p.y, dx / l * 1600f * k, dy / l * 1600f * k, logoS * 0.5f)
                    drop.poke(cx(), cy(), (p.x - cx()) * -14f * k, (p.y - cy()) * -14f * k, logoS)
                    var total = Offset.Zero
                    drag(down.id) { change ->
                        total += change.positionChange()
                        change.consume()
                        val rx = maxPull * tanh(total.x / (maxPull * 3)); val ry = maxPull * tanh(total.y / (maxPull * 3))
                        logo.nudge(cx() + rx, cy() + ry)
                        drop.nudge(cx() + rx, cy() + ry)
                    }
                    logo.nudge(cx(), cy()); drop.nudge(cx(), cy())
                }
            },
    ) {
        logo.frame; drop.frame
        if (!logo.blob.ready || !drop.blob.ready) return@Canvas
        logo.blob.path(path)
        drawPath(path, Color(0xFF111111))
        val gs = logoS * 0.98f
        val tilt = ((drop.blob.cx - logo.blob.cx) / logoS * 40f).coerceIn(-14f, 14f)
        rotate(tilt, Offset(drop.blob.cx, drop.blob.cy)) {
            translate(drop.blob.cx - gs / 2, drop.blob.cy - gs / 2) { with(goat) { draw(Size(gs, gs)) } }
        }
    }
}

/** 리퀴드 로고 상자의 모서리 반지름(크기 대비). 홈·앱 정보·사이드바가 같은 모양을 쓴다. */
internal const val LOGO_CORNER = 0.4f

/**
 * 쉬는 동안의 출렁임: 3~6초마다 상자 가장자리 한 곳을 안쪽으로 살짝 밀면, 표면장력이 펴고 압력이 되밀며 물결처럼 잦아든다.
 * 염소 물방울도 반대쪽으로 조금 흔들린다. 부른 쪽의 LaunchedEffect가 끝나면(화면을 떠나면) 멈춘다.
 */
internal suspend fun idleRipple(logo: SoftBlobState, drop: SoftBlobState, cx: () -> Float, cy: () -> Float, logoS: Float) {
    val rnd = kotlin.random.Random(System.nanoTime())
    while (true) {
        delay(3000L + rnd.nextLong(3000L))
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val ex = cx() + cos(a) * logoS / 2; val ey = cy() + sin(a) * logoS / 2
        logo.poke(ex, ey, -cos(a) * 520f, -sin(a) * 520f, logoS * 0.45f)
        drop.poke(cx(), cy(), cos(a) * 60f, sin(a) * 60f, logoS)
    }
}
