package com.labok.goat.ui.rotate

import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.liquid.FluidSim
import androidx.compose.ui.graphics.Color
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.data.log.LogRepository
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.labok.goat.ui.liquid.LiquidLook
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidTune
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
    var tune by remember { mutableStateOf(LiquidTune.read(context)) }
    var tuning by remember { mutableStateOf(false) }
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
            LiquidPlayground(round = round, recording = running, look = LiquidTune.look(tune), onDone = { lv ->
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
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.FLUID_TUNE, modifier = Modifier.fillMaxWidth(), primary = false, onClick = { tuning = !tuning })
            AnimatedVisibility(tuning, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                FluidTuner(tune, onChange = { tune = it })
            }
        }
    }
}

/**
 * 유리잔 속 점도 높은 물 한 방울([FluidSim], 모양·물성은 [LiquidLook.default]). 중력이 1/20뿐인 곳에 있는 듯 움직인다:
 * - 가속도 원값을 저역 통과해 중력을 따로 떼어 내고, 중력은 [LiquidLook.gravity]배(1/20)만 건다. 기울이면 느릿하게 그쪽으로 가라앉는다.
 * - 나머지(기기를 움직인 관성)는 그대로 건다. 흔들면 반대쪽으로 쏠렸다 출렁이고, 세게 치면 벽에 늘어 붙었다가 표면장력으로 다시 둥글게 맺힌다.
 * - 이 화면의 주된 목적은 흔들림·충격이 잡히는 것을 눈으로 보여 주는 것이다. 지금 설정(민감도·고급 설정)대로 판정하는 검출기를
 *   늘 돌려, 휘두름이나 탭·충격이 하나 잡힐 때마다 그 반대쪽(빈 곳)으로 물방울이 튀고, 흔들기로 판정되면 사방으로 톡 터진다.
 * [recording]이 켜지면 [round]마다 새로 [CALIBRATE_SECONDS]초 동안 휘두름을 모아 [onDone]에 단계를 넘긴다(못 맞추면 null).
 * 둘레의 호는 남은 시간, 아래 점은 잡힌 휘두름 수다. 이 시트가 열려 있는 동안 흔들어도 화면은 돌지 않는다.
 */
@Composable
private fun LiquidPlayground(round: Int, recording: Boolean, look: LiquidLook, onDone: (Int?) -> Unit) {
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
    val h = with(density) { look.hDp.dp.toPx() }
    val glass = with(density) { look.glassDp.dp.toPx() }
    val dp = density.density
    // 물의 양(입자 수)이 바뀌면 새로 채우고, 나머지 물성은 조절바를 움직이는 동안 그대로 이어서 바꾼다
    val sim = remember(look.particles) { FluidSim(look.particles, h).apply { look.apply(this) } }
    LaunchedEffect(look) { look.apply(sim) }
    // 센서 콜백·프레임 루프는 한 번만 등록하므로 최신 액체와 물성을 이 상태로 읽는다
    val simNow by rememberUpdatedState(sim)
    val lookNow by rememberUpdatedState(look)
    // 센서 최신값(화면 좌표로 바꾼 힘)과 저역 통과로 떼어 낸 중력(기기 좌표)
    val force = remember { FloatArray(2) }
    val gravity = remember { FloatArray(2) }
    // 지금 설정 그대로 판정하는 검출기(시작 전에도 돈다): 잡히는 것을 물방울로 보여 준다
    val live = remember {
        ShakeRotatePolicy.Detector(
            threshold = ShakeRotatePolicy.thresholdOf(ShakeRotate.level(context)),
            gyroMin = ShakeRotatePolicy.gyroOfTurn(ShakeRotate.turn(context)),
            maxGap = ShakeRotatePolicy.gapOfPace(ShakeRotate.pace(context)),
            needed = ShakeRotate.swings(context),
            rejectTaps = ShakeRotate.rejectTaps(context),
        )
    }
    var record by remember { mutableStateOf(ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN)) }

    LaunchedEffect(box, sim) {
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
    // 시뮬레이션: 실제 시간만큼(1 = 1/60초) 적분한다
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val f = if (last == 0L) 1f else ((now - last) / 16_666_667f).coerceIn(0.25f, 3f)
            last = now
            val sim = simNow; val look = lookNow
            if (!sim.ready) continue
            // 한 걸음은 반 프레임 이하로 나눈다: 프레임이 밀려 한 번에 크게 적분하면 점성·표면장력이 강한 액체가 터진다.
            // 너무 밀리면(4걸음 넘게) 그만큼 느리게 움직이게 둔다.
            val sub = kotlin.math.ceil(f / MAX_STEP).toInt().coerceIn(1, 4)
            val dt = (f / sub).coerceAtMost(MAX_STEP)
            repeat(sub) { sim.step(dt, force[0], force[1], 0f, look.pull, look.cohesion) }
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
                    live.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    if (rec) record.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    return
                }
                // 가속도 원값 = 중력 + 관성. 중력은 천천히 따라가는 저역 통과로 떼어 내 약하게(달 중력)만 걸고, 관성은 그대로 건다.
                // 화면 좌표: 기기 x는 화면 오른쪽, 기기 y는 화면 위쪽(반대로)
                val ax = e.values[0]; val ay = e.values[1]
                gravity[0] += (ax - gravity[0]) * GRAVITY_LP; gravity[1] += (ay - gravity[1]) * GRAVITY_LP
                val sim = simNow; val look = lookNow
                val g = look.gToPx * dp
                val lx = ax - gravity[0]; val ly = ay - gravity[1]
                force[0] = -(lx + gravity[0] * look.gravity) * g
                force[1] = (ly + gravity[1] * look.gravity) * g
                // 잡힌 것을 보여 준다: 휘두름·탭·충격 하나 → 덩어리는 한쪽으로 쏠리고 반대쪽(빈 곳)으로 물방울이 튄다.
                // 흔들기 판정 → 사방으로 톡 터진다. 세기는 판정 기준의 몇 배였는지(최대 3배)에 비례한다.
                val before = live.peaks.lastOrNull()?.at
                val shook = live.feed(ax, ay, e.values[2], ms)
                val p = live.peaks.lastOrNull()
                if (p != null && p.at != before) {
                    val k = (p.force / live.threshold).coerceIn(1f, 3f)
                    sim.splash(p.x, -p.y, k * look.splash * dp)
                }
                if (shook) sim.burst(look.splash * dp)
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
            // 액체(goo 층: 입자가 매끈한 한 덩어리 표면이 되고, 튄 물방울은 목처럼 이어졌다 떨어진다)
            Canvas(Modifier.fillMaxWidth().height(280.dp).goo(c.ink, look.blurDp.dp)) {
                frame
                if (!sim.ready) return@Canvas
                val r = look.radiusDp.dp.toPx()
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

/** 시뮬레이션 한 걸음의 최대 길이(프레임). */
private const val MAX_STEP = 0.5f
/** 중력 저역 통과 계수(센서 값 하나마다, 50Hz면 약 0.4초에 따라간다). */
private const val GRAVITY_LP = 0.05f

/** 흔들림 점 하나: 차면 통통 튀며 커졌다가 자리 잡는다. */
@Composable
private fun Dot(filled: Boolean) {
    val c = LocalLiquid.current
    val k by animateFloatAsState(
        if (filled) 1f else 0f,
        spring(dampingRatio = UiMotion.ratio(0.32f), stiffness = UiMotion.stiffness(Spring.StiffnessMediumLow, 0.32f)),
        label = "dot",
    )
    Canvas(Modifier.size(18.dp)) {
        drawCircle(c.line, size.minDimension / 2 * 0.62f)
        if (k > 0.01f) drawCircle(c.ink, size.minDimension / 2 * (0.62f + 0.38f * k) * k.coerceAtMost(1.2f))
    }
}

/**
 * 물방울 조절바들([LiquidTune]). 움직이는 동안 위 물방울에 바로 반영하고, 손을 떼면 저장한다.
 * 물의 양은 바꾸면 물방울을 새로 채우므로 손을 뗐을 때만 반영한다.
 */
@Composable
private fun FluidTuner(tune: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Gap.Item))
        Text(Copy.FLUID_TUNE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        LiquidTune.all.forEach { knob ->
            Spacer(Modifier.height(Gap.Item))
            Text(knob.label, color = c.ink)
            var v by remember(tune[knob.key]) { mutableIntStateOf(tune[knob.key] ?: knob.default) }
            LiquidSlider(
                value = v, range = knob.range,
                onValueChange = { v = it; if (knob !== LiquidTune.WATER) onChange(tune + (knob.key to it)) },
                onValueChangeFinished = { LiquidTune.save(context, knob, v); onChange(tune + (knob.key to v)) },
                valueText = knob.text, description = knob.label,
            )
        }
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(Copy.FLUID_RESET, modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
            LiquidTune.reset(context); onChange(LiquidTune.defaults())
        })
    }
}
