package com.labok.goat.ui.rotate

import com.labok.goat.ui.liquid.FluidSim
import androidx.compose.ui.graphics.Color
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
import com.labok.goat.ui.liquid.gooShaded
import com.labok.goat.ui.liquid.LiquidLook
import androidx.compose.ui.graphics.lerp
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
 * 유리잔 속 점도 높은 물 한 방울([FluidSim], 모양·물성은 [LiquidLook.default]). 달처럼 중력이 약한 곳에 있는 듯 움직인다:
 * - 가속도 원값을 저역 통과해 중력을 따로 떼어 내고, 중력은 [LiquidLook.gravity]배(약 1/6)만 건다. 기울이면 느릿하게 그쪽으로 가라앉는다.
 * - 나머지(기기를 움직인 관성)는 그대로 건다. 흔들면 반대쪽으로 쏠렸다 출렁이고, 세게 치면 벽에 늘어 붙었다가 표면장력으로 다시 둥글게 맺힌다.
 * - 왼쪽 위에서 빛을 받는 것처럼 가장자리를 밝게 그려 광택을 낸다.
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
    val look = LiquidLook.default
    val h = with(density) { look.hDp.dp.toPx() }
    val glass = with(density) { look.glassDp.dp.toPx() }
    val dp = density.density
    val sim = remember { FluidSim(look.particles, h).apply { look.apply(this) } }
    // 센서 최신값(화면 좌표로 바꾼 힘)과 저역 통과로 떼어 낸 중력(기기 좌표)
    val force = remember { FloatArray(2) }
    val gravity = remember { FloatArray(2) }
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
            repeat(sub) { sim.step(f / sub, force[0], force[1], 0f, look.pull, look.cohesion) }
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
                    if (rec) record.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    return
                }
                // 가속도 원값 = 중력 + 관성. 중력은 천천히 따라가는 저역 통과로 떼어 내 약하게(달 중력)만 걸고, 관성은 그대로 건다.
                // 화면 좌표: 기기 x는 화면 오른쪽, 기기 y는 화면 위쪽(반대로)
                val ax = e.values[0]; val ay = e.values[1]
                gravity[0] += (ax - gravity[0]) * GRAVITY_LP; gravity[1] += (ay - gravity[1]) * GRAVITY_LP
                val g = look.gToPx * dp
                force[0] = -((ax - gravity[0]) + gravity[0] * look.gravity) * g
                force[1] = ((ay - gravity[1]) + gravity[1] * look.gravity) * g
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
            // 액체(색을 지우지 않는 goo 층: 입자가 매끈한 한 덩어리 표면이 되고, 입자마다 다른 밝기가 섞여 광택이 된다)
            Canvas(Modifier.fillMaxWidth().height(280.dp).gooShaded(look.blurDp.dp)) {
                frame
                if (!sim.ready) return@Canvas
                val r = look.radiusDp.dp.toPx()
                val (mx, my) = sim.center()
                val spread = look.spread(sim, mx, my)
                for (i in 0 until sim.n) drawCircle(lerp(c.ink, Color.White, look.shine(sim, i, mx, my, spread)), r, Offset(sim.x[i], sim.y[i]))
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

/** 중력 저역 통과 계수(센서 값 하나마다, 50Hz면 약 0.4초에 따라간다). */
private const val GRAVITY_LP = 0.05f

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
