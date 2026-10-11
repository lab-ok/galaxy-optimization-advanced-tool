package com.labok.goat.ui.rotate

import com.labok.goat.ui.liquid.FluidSim
import androidx.compose.runtime.mutableFloatStateOf
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.data.log.LogRepository
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
import kotlinx.coroutines.launch

/** 자동 맞추기에 쓰는 시간(초). */
internal const val CALIBRATE_SECONDS = 4

/** 흔들림 점의 수. 이만큼 모이면 충분하다. */
private const val SLOTS = 6

/**
 * 흔들어서 자동으로 맞추기(팝업 시트). 열면 물방울이 폰 움직임을 따라 출렁이며 놀 수 있고, [시작]을 누르면
 * [CALIBRATE_SECONDS]초 동안 휘두름을 모은다. 끝나면 그 세기에 맞는 민감도를 저장해 결과를 보여 준다.
 */
@Composable
internal fun ShakeCalibrationSheet(onClose: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 제목은 다른 시트처럼 왼쪽, 본문(물방울·결과)은 가운데
    SheetColumn(Copy.SHAKE_CALIBRATE) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    running -> Copy.SHAKE_CALIBRATE_NOW
                    result != null -> "민감도를 ${result}(으)로 맞췄습니다"
                    failed -> "흔들림을 인식하지 못했습니다"
                    else -> "기기를 움직이면 물방울이 함께 움직입니다."
                },
                color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
            LiquidPlayground(round = round, recording = running, onDone = { lv ->
                running = false
                if (lv == null) failed = true
                else { ShakeRotate.setLevel(context, lv, "자동 맞추기"); result = lv }
            })
            Spacer(Modifier.height(Gap.Item))
            AnimatedContent(Triple(running, result, failed), label = "calibrate") { (run, level, fail) ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        run -> LiquidButton("흔드는 중…", modifier = Modifier.fillMaxWidth(), enabled = false, onClick = {})
                        level != null -> {
                            Text(
                                "이후에는 방금과 같거나 조금 약하게 흔들어도 화면이 회전합니다.",
                                color = c.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(Gap.Item))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LiquidButton("다시 하기", modifier = Modifier.weight(1f), primary = false, onClick = { result = null; round++; running = true })
                                LiquidButton("확인", modifier = Modifier.weight(1f), onClick = onClose)
                            }
                        }
                        else -> {
                            Text(
                                if (fail) Copy.SHAKE_CALIBRATE_FAIL else "[시작]을 누른 뒤 ${CALIBRATE_SECONDS}초 동안 평소처럼 기기를 흔들어 주세요.",
                                color = c.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(Gap.Item))
                            LiquidButton(if (fail) "다시 하기" else "시작", modifier = Modifier.fillMaxWidth(), onClick = { failed = false; round++; running = true })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 유리잔 속 액체(입자 [PARTICLES]개, [FluidSim]). 센서를 그대로 액체에 건다:
 * - 가속도 원값 = 중력 + 기기를 움직인 관성. 기울이면 액체가 아래로 흘러내리고, 흔들면 반대쪽으로 쏠렸다 출렁이며, 세게 흔들면 튀어 오른다.
 * - 자이로 z(화면을 돌리는 회전) = 잔 속 소용돌이.
 * - 기기를 바닥에 눕혀 기울기가 없으면 가운데로 살짝 모여 방울이 된다.
 * [recording]이 켜지면 [round]마다 새로 [CALIBRATE_SECONDS]초 동안 휘두름을 모아 [onDone]에 단계를 넘긴다(못 맞추면 null).
 * 둘레의 호는 남은 시간, 아래 점은 잡힌 휘두름 수다. 이 시트가 열려 있는 동안 흔들어도 화면은 돌지 않는다.
 */
@Composable
private fun LiquidPlayground(round: Int, recording: Boolean, onDone: (Int?) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val done by rememberUpdatedState(onDone)
    val rec by rememberUpdatedState(recording)
    var box by remember { mutableStateOf(IntSize.Zero) }
    var count by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(CALIBRATE_SECONDS) }
    val time = remember { Animatable(1f) }
    var frame by remember { mutableLongStateOf(0L) }
    val haptics = rememberLiquidHaptics()
    val h = with(density) { 22.dp.toPx() }
    val glass = with(density) { 112.dp.toPx() }
    val dp = density.density
    val sim = remember { FluidSim(PARTICLES, h) }
    // 센서 최신값(화면 좌표로 바꾼 힘, 자이로 z)
    val force = remember { FloatArray(2) }
    var spin by remember { mutableFloatStateOf(0f) }
    var record by remember { mutableStateOf(ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN)) }

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        if (!sim.ready) sim.reset(box.width / 2f, box.height / 2f, glass) else sim.container(box.width / 2f, box.height / 2f, glass)
    }
    // 시작한 회차: 기록을 새로 하고, 남은 시간 호를 줄이며, 끝나면 세기·빠르기·손목 회전을 맞춘다
    LaunchedEffect(round, recording) {
        if (!recording) { time.snapTo(1f); return@LaunchedEffect }
        record = ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN)
        count = 0; left = CALIBRATE_SECONDS; time.snapTo(1f)
        kotlinx.coroutines.coroutineScope {
            launch { time.animateTo(0f, tween(CALIBRATE_SECONDS * 1000, easing = LinearEasing)) }
            while (left > 0) { delay(1000); left-- }
        }
        val level = ShakeRotatePolicy.calibrate(record.peaks.map { it.force })
        LogRepository.from(context).add(
            "흔들어서 화면 회전 자동 맞추기: 휘두름 ${record.peaks.size}번, 세기 ${record.peaks.map { "%.1f".format(it.force) }}" +
                if (level == null) " → 실패(흔들림 부족)" else " → 민감도 $level",
        )
        // 세기를 맞췄으면 그 사람의 흔들기 리듬(빠르기)과 손목 회전 기준도 함께 맞춘다
        if (level != null) {
            val pace = ShakeRotatePolicy.rhythmOf(record.peaks)?.let { ShakeRotatePolicy.paceOfGap(ShakeRotatePolicy.maxGapFor(it)) }
            ShakeRotate.setAdvanced(context, pace = pace, turn = ShakeRotatePolicy.calibrateTurn(record.peaks), how = "자동 맞추기")
        }
        done(level)
    }
    // 시뮬레이션: 실제 시간만큼(1 = 1/60초) 두 번에 나눠 적분한다
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val f = if (last == 0L) 1f else ((now - last) / 16_666_667f).coerceIn(0.25f, 3f)
            last = now
            if (!sim.ready) continue
            val sub = 2
            repeat(sub) {
                val ax = force[0]; val ay = force[1]
                // 기울기가 거의 없으면(바닥에 눕힘) 가운데로 모으는 힘을 조금 준다
                val tilt = kotlin.math.hypot(ax, ay) / (G_TO_PX * 9.8f * dp)
                val pull = 0.0009f * (1f - tilt).coerceIn(0f, 1f)
                sim.step(f / sub, ax, ay, spin, pull)
            }
            frame++
        }
    }

    // 이 시트가 열려 있는 동안은 흔들어도 화면을 돌리지 않는다
    DisposableEffect(Unit) {
        ShakeRotate.calibrating = true
        val sm = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val ms = e.timestamp / 1_000_000
                if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // 화면을 시계 방향으로 돌리면(z가 음수) 잔 속 액체는 반대로 처진다: 회전 속도에 비례한 소용돌이
                    spin = (-e.values[2] * SWIRL).coerceIn(-0.02f, 0.02f)
                    if (rec) record.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    return
                }
                // 가속도 원값(중력 + 관성) → 화면 좌표 힘: 기기 x는 화면 오른쪽, 기기 y는 화면 위쪽(반대로)
                force[0] = -e.values[0] * G_TO_PX * dp
                force[1] = e.values[1] * G_TO_PX * dp
                if (rec) {
                    record.feed(e.values[0], e.values[1], e.values[2], ms)
                    if (record.peaks.size > count) haptics.tick()
                    count = record.peaks.size
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose {
            sm.unregisterListener(listener)
            ShakeRotate.calibrating = false
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(280.dp).onSizeChanged { box = it }) {
            // 유리잔 테두리와 남은 시간 호(액체보다 뒤, goo 없음)
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val o = Offset(size.width / 2, size.height / 2)
                val ring = glass + 6.dp.toPx()
                drawCircle(c.line, ring, o, style = Stroke(3.dp.toPx()))
                if (recording) drawArc(
                    c.ink, -90f, 360f * time.value, false,
                    Offset(o.x - ring, o.y - ring), Size(ring * 2, ring * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // 액체(goo 층: 입자들이 표면장력처럼 한 덩어리로 보이고, 튀어 나간 방울은 목처럼 이어졌다 떨어진다)
            Canvas(Modifier.fillMaxWidth().height(280.dp).goo(c.ink, 12.dp)) {
                frame
                if (!sim.ready) return@Canvas
                val r = h * 0.62f
                for (i in 0 until sim.n) drawCircle(c.ink, r, Offset(sim.x[i], sim.y[i]))
            }
            if (recording) Text(
                "$left", color = c.onInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    frame
                    if (sim.ready) {
                        val (mx, my) = sim.center()
                        translationX = mx - box.width / 2f; translationY = my - box.height / 2f
                    }
                },
            )
        }
        // 잡힌 휘두름 점: 찰 때마다 통 튀어 오른다(기록 중에만 센다)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(SLOTS) { i -> Dot(filled = recording && i < count) }
        }
    }
}

/** 액체 입자 수. */
private const val PARTICLES = 90
/** 가속도(m/s²) → 화면 힘(dp/프레임²) 배수. 9.8이면 약 0.34dp/프레임². */
private const val G_TO_PX = 0.035f
/** 자이로 z(rad/s) → 소용돌이 세기. */
private const val SWIRL = 0.004f

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
