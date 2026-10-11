package com.labok.goat.ui.rotate

import com.labok.goat.ui.components.Gap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.nudge
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.text.Copy
import kotlin.math.hypot
import kotlinx.coroutines.delay

/** 자동 맞추기에 쓰는 시간(초). */
internal const val CALIBRATE_SECONDS = 4

/** 흔들림 점의 수. 이만큼 모이면 충분하다. */
private const val SLOTS = 6

/**
 * 자동 맞추기: [CALIBRATE_SECONDS]초 동안 흔들림을 모으고, 끝나면 그 세기에 맞는 단계를 [onDone]에 넘긴다(못 맞추면 null).
 * 가운데 물방울은 폰 안의 물처럼 흔드는 방향과 반대로 출렁이고, 흔들림이 하나 잡힐 때마다 그 세기만큼 통 튀며
 * 아래 점이 하나씩 튀어 오르며 찬다. 둘레의 호는 남은 시간이다. 맞추는 동안 서비스는 흔들어도 화면을 돌리지 않는다.
 */
@Composable
internal fun ShakeCalibration(onDone: (Int?) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val done by rememberUpdatedState(onDone)
    val blob = rememberSoftBlob()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var count by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(CALIBRATE_SECONDS) }
    val time = remember { Animatable(1f) }
    val path = remember { Path() }
    val radius = with(density) { 34.dp.toPx() }
    val reach = with(density) { 46.dp.toPx() }

    fun center() = Offset(box.width / 2f, box.height / 2f)

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        blob.blob.gain = 0.55f     // 목표를 느슨하게 따라가 물처럼 출렁인다
        blob.blob.damping = 0.8f
        blob.moveTo(center().x, center().y, radius * 2, radius * 2, animate = false)
    }
    LaunchedEffect(Unit) {
        // 남은 시간 호: 처음부터 끝까지 고르게 줄어든다
        launchTimer(time)
    }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
    }

    val detector = remember { ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                detector.feed(e.values[0], e.values[1], e.values[2], SystemClock.elapsedRealtime())
                if (box == IntSize.Zero || !blob.blob.ready) return
                val o = center()
                if (!reduce) {
                    // 폰 안의 물: 폰이 움직이는 반대쪽으로 쏠린다(화면 x는 기기 x, 화면 y는 기기 y의 반대)
                    val k = reach / 18f
                    val mx = (-detector.linearX * k).coerceIn(-reach, reach)
                    val my = (detector.linearY * k).coerceIn(-reach, reach)
                    blob.nudge(o.x + mx, o.y + my)
                }
                if (detector.peaks.size > count) {
                    val p = detector.peaks.last()
                    count = detector.peaks.size
                    if (!reduce) {
                        // 흔들림 세기만큼 바깥으로 통 튄다
                        val dir = Offset(-detector.linearX, detector.linearY).let { v ->
                            val l = hypot(v.x, v.y).coerceAtLeast(0.01f); Offset(v.x / l, v.y / l)
                        }
                        val speed = (p.force * 90f).coerceAtMost(3200f)
                        blob.poke(o.x - dir.x * radius, o.y - dir.y * radius, dir.x * speed, dir.y * speed, radius * 1.2f)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    // 시간이 다 되면 모은 흔들림으로 단계를 정해 넘긴다
    LaunchedEffect(left) {
        if (left == 0) done(ShakeRotatePolicy.calibrate(detector.peaks.map { it.force }))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Copy.SHAKE_CALIBRATE_NOW, color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().height(170.dp).onSizeChanged { box = it }) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                blob.frame
                val o = Offset(size.width / 2, size.height / 2)
                // 남은 시간 호
                val ring = radius + 26.dp.toPx()
                drawCircle(c.line, ring, o, style = Stroke(3.dp.toPx()))
                drawArc(
                    c.ink, -90f, 360f * time.value, false,
                    Offset(o.x - ring, o.y - ring), Size(ring * 2, ring * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
                if (blob.blob.ready) {
                    blob.blob.path(path)
                    drawPath(path, c.ink)
                }
            }
            Text(
                "$left", color = c.onInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    blob.frame
                    if (blob.blob.ready) { translationX = blob.blob.cx - box.width / 2f; translationY = blob.blob.cy - box.height / 2f }
                },
            )
        }
        // 잡힌 흔들림 점: 찰 때마다 통 튀어 오른다
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(SLOTS) { i -> Dot(filled = i < count) }
        }
        Spacer(Modifier.height(Gap.Tight))
        Text(if (count == 0) "흔들림을 기다리는 중" else "흔들림 ${count}번", color = c.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** 남은 시간 호를 1에서 0으로 고르게 줄인다. */
private suspend fun launchTimer(time: Animatable<Float, *>) {
    time.animateTo(0f, tween(CALIBRATE_SECONDS * 1000, easing = LinearEasing))
}

/** 흔들림 점 하나: 차면 통통 튀며 커졌다가 자리 잡는다. */
@Composable
private fun Dot(filled: Boolean) {
    val c = LocalLiquid.current
    val k by animateFloatAsState(
        if (filled) 1f else 0f,
        spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow),
        label = "dot",
    )
    Canvas(Modifier.size(16.dp)) {
        drawCircle(c.line, size.minDimension / 2 * 0.62f)
        if (k > 0.01f) drawCircle(c.ink, size.minDimension / 2 * (0.62f + 0.38f * k) * k.coerceAtMost(1.2f))
    }
}
