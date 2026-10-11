package com.labok.goat.ui.rotate

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.goo
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.nudge
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.text.Copy
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay

/** 자동 맞추기에 쓰는 시간(초). */
internal const val CALIBRATE_SECONDS = 4

/** 흔들림 점의 수. 이만큼 모이면 충분하다. */
private const val SLOTS = 6

/**
 * 흔들어서 자동으로 맞추기(팝업 시트). 열리면 바로 [CALIBRATE_SECONDS]초 동안 흔들림을 모으고,
 * 끝나면 그 세기에 맞는 민감도를 저장해 결과를 보여 준다. 못 맞추면 다시 하게 한다.
 */
@Composable
internal fun ShakeCalibrationSheet(onClose: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 제목은 다른 시트처럼 왼쪽, 본문(물방울·결과)은 가운데
    SheetColumn(Copy.SHAKE_CALIBRATE) {
        AnimatedContent(Triple(round, result, failed), label = "calibrate") { (r, level, fail) ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    level != null -> {
                        GlassCard {
                            Text("민감도 $level", fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "방금 흔든 세기로 맞췄습니다. 다음부터는 이 정도로, 또는 조금 약하게 흔들어도 돌아갑니다.",
                                color = c.muted, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(Gap.Card))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LiquidButton("다시 하기", modifier = Modifier.weight(1f), primary = false, onClick = { result = null; failed = false; round++ })
                            LiquidButton("확인", modifier = Modifier.weight(1f), onClick = onClose)
                        }
                    }
                    fail -> {
                        GlassCard {
                            Text("다시 해 볼까요?", fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(Copy.SHAKE_CALIBRATE_FAIL, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(Gap.Card))
                        LiquidButton("다시 하기", modifier = Modifier.fillMaxWidth(), onClick = { failed = false; round++ })
                    }
                    else -> ShakeCalibration(key = r, onDone = { lv ->
                        if (lv == null) failed = true
                        else { ShakeRotate.setLevel(context, lv, "자동 맞추기"); result = lv }
                    })
                }
            }
        }
    }
}

/** 갈라져 나간 작은 물방울 하나. 본체 쪽으로 끌려 돌아와 합쳐진다. */
private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, var age: Float = 0f)

/**
 * 흔들림 모으기. 가운데 큰 물방울은 폰 안의 물처럼 흔드는 반대쪽으로 크게 출렁이고, 휘두름이 잡힐 때마다 그 세기만큼 통 튄다.
 * 세게 휘두르면 작은 방울이 떨어져 나갔다가 본체로 끌려와 다시 합쳐진다(goo 층이라 떨어지고 붙는 순간 목처럼 이어진다).
 * 둘레의 호는 남은 시간, 아래 점은 잡힌 휘두름 수다. 맞추는 동안 흔들어도 화면은 돌지 않는다.
 */
@Composable
private fun ShakeCalibration(key: Int, onDone: (Int?) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val done by rememberUpdatedState(onDone)
    val blob = rememberSoftBlob()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var count by remember(key) { mutableIntStateOf(0) }
    var left by remember(key) { mutableIntStateOf(CALIBRATE_SECONDS) }
    val time = remember(key) { Animatable(1f) }
    val path = remember { Path() }
    val drops = remember { mutableListOf<Drop>() }
    var tick by remember { mutableLongStateOf(0L) }
    val radius = with(density) { 56.dp.toPx() }
    val reach = with(density) { 64.dp.toPx() }
    val detector = remember(key) { ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN) }

    fun center() = Offset(box.width / 2f, box.height / 2f)

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        blob.blob.gain = 0.45f     // 목표를 느슨하게 따라가 물처럼 크게 출렁인다
        blob.blob.damping = 0.7f
        blob.moveTo(center().x, center().y, radius * 2, radius * 2, animate = false)
    }
    LaunchedEffect(key) { time.animateTo(0f, tween(CALIBRATE_SECONDS * 1000, easing = LinearEasing)) }
    LaunchedEffect(key) {
        while (left > 0) { delay(1000); left-- }
        done(ShakeRotatePolicy.calibrate(detector.peaks.map { it.force }))
    }
    // 떨어져 나간 방울: 본체 쪽으로 용수철처럼 끌려와, 가까워지면 합쳐져 사라진다
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 1f / 60 else ((now - last) / 1e9f).coerceAtMost(1f / 20)
            last = now
            if (drops.isEmpty() || !blob.blob.ready) continue
            val cx = blob.blob.cx; val cy = blob.blob.cy
            drops.removeAll { d ->
                d.age += dt
                d.vx += (cx - d.x) * 30f * dt; d.vy += (cy - d.y) * 30f * dt
                d.vx *= 1f - 3.2f * dt; d.vy *= 1f - 3.2f * dt
                d.x += d.vx * dt; d.y += d.vy * dt
                d.age > 0.25f && hypot(d.x - cx, d.y - cy) < radius * 0.6f
            }
            tick++
        }
    }

    // 맞추는 동안은 흔들어도 화면을 돌리지 않는다
    DisposableEffect(key) {
        ShakeRotate.calibrating = true
        val sm = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                detector.feed(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000)
                if (box == IntSize.Zero || !blob.blob.ready || reduce) { count = detector.peaks.size; return }
                val o = center()
                // 폰 안의 물: 폰이 움직이는 반대쪽으로 쏠린다(화면 x는 기기 x, 화면 y는 기기 y의 반대)
                val k = reach / 6f
                val mx = (-detector.linearX * k).coerceIn(-reach, reach)
                val my = (detector.linearY * k).coerceIn(-reach, reach)
                blob.nudge(o.x + mx, o.y + my)
                if (detector.peaks.size > count) {
                    val p = detector.peaks.last()
                    count = detector.peaks.size
                    val l = hypot(p.x, p.y).coerceAtLeast(0.01f)
                    val dir = Offset(-p.x / l, p.y / l)
                    // 휘두름 세기만큼 바깥으로 통 튄다
                    val speed = (p.force * 160f).coerceAtMost(5200f)
                    blob.poke(o.x - dir.x * radius, o.y - dir.y * radius, dir.x * speed, dir.y * speed, radius * 1.3f)
                    // 세게 휘두르면 방울이 갈라져 나간다(세기에 따라 1~4개)
                    val n = ((p.force - SPLIT_FORCE) / 6f).toInt().coerceIn(0, 3) + if (p.force > SPLIT_FORCE) 1 else 0
                    repeat(n) { i ->
                        val a = kotlin.math.atan2(dir.y, dir.x) + (i - (n - 1) / 2f) * 0.45f
                        val sp = speed * (0.55f + 0.1f * i)
                        drops += Drop(
                            blob.blob.cx + cos(a) * radius * 0.8f, blob.blob.cy + sin(a) * radius * 0.8f,
                            cos(a) * sp, sin(a) * sp, radius * (0.32f - 0.05f * i),
                        )
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose {
            sm.unregisterListener(listener)
            ShakeRotate.calibrating = false
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Copy.SHAKE_CALIBRATE_NOW, color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().height(260.dp).onSizeChanged { box = it }) {
            // 남은 시간 호(물방울보다 뒤, goo 없음)
            Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                val o = Offset(size.width / 2, size.height / 2)
                val ring = radius + 40.dp.toPx()
                drawCircle(c.line, ring, o, style = Stroke(3.dp.toPx()))
                drawArc(
                    c.ink, -90f, 360f * time.value, false,
                    Offset(o.x - ring, o.y - ring), Size(ring * 2, ring * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // 물방울과 갈라진 방울(goo 층: 떨어지고 붙는 순간 목처럼 이어진다)
            Canvas(Modifier.fillMaxWidth().height(260.dp).goo(c.ink, 10.dp)) {
                blob.frame; tick
                if (blob.blob.ready) drawPath(blob.blob.path(path), c.ink)
                drops.forEach { drawCircle(c.ink, it.r, Offset(it.x, it.y)) }
            }
            Text(
                "$left", color = c.onInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    blob.frame
                    if (blob.blob.ready) { translationX = blob.blob.cx - box.width / 2f; translationY = blob.blob.cy - box.height / 2f }
                },
            )
        }
        // 잡힌 휘두름 점: 찰 때마다 통 튀어 오른다
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(SLOTS) { i -> Dot(filled = i < count) }
        }
        Spacer(Modifier.height(Gap.Tight))
        Text(if (count == 0) "흔들림을 기다리는 중" else "흔들림 ${count}번", color = c.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** 이 세기(m/s²)를 넘는 휘두름이면 작은 방울이 갈라져 나간다. */
private const val SPLIT_FORCE = 12f

/** 흔들림 점 하나: 차면 통통 튀며 커졌다가 자리 잡는다. */
@Composable
private fun Dot(filled: Boolean) {
    val c = LocalLiquid.current
    val k by animateFloatAsState(
        if (filled) 1f else 0f,
        spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow),
        label = "dot",
    )
    Canvas(Modifier.size(18.dp)) {
        drawCircle(c.line, size.minDimension / 2 * 0.62f)
        if (k > 0.01f) drawCircle(c.ink, size.minDimension / 2 * (0.62f + 0.38f * k) * k.coerceAtMost(1.2f))
    }
}
