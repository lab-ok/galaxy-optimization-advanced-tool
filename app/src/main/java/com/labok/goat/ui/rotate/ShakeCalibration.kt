package com.labok.goat.ui.rotate

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
                    result != null -> "민감도 $result 로 맞췄습니다"
                    failed -> "흔들림이 잘 잡히지 않았습니다"
                    else -> "폰을 움직여 보세요. 물방울이 따라 출렁입니다."
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
                                "다음부터는 방금 정도로, 또는 조금 약하게 흔들어도 돌아갑니다.",
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
                                if (fail) Copy.SHAKE_CALIBRATE_FAIL else "[시작]을 누르고 ${CALIBRATE_SECONDS}초 동안 화면을 돌릴 때처럼 휙휙 흔드세요.",
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

/** 갈라져 나간 작은 물방울 하나. 본체 쪽으로 끌려 돌아와 합쳐진다. */
private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, var age: Float = 0f)

/**
 * 폰 움직임을 따라 노는 물방울. 늘 센서를 듣고 큰 물방울이 폰 안의 물처럼 반대쪽으로 출렁이며, 휘두를 때마다 그 세기만큼 통 튄다.
 * 세게 휘두르면 작은 방울이 떨어져 나갔다가 본체로 끌려와 다시 합쳐진다(goo 층이라 떨어지고 붙는 순간 목처럼 이어진다).
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
    val blob = rememberSoftBlob()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var count by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(CALIBRATE_SECONDS) }
    val time = remember { Animatable(1f) }
    val path = remember { Path() }
    val drops = remember { mutableListOf<Drop>() }
    var tick by remember { mutableLongStateOf(0L) }
    val radius = with(density) { 60.dp.toPx() }
    val reach = with(density) { 72.dp.toPx() }
    // 화면용(늘 듣는다)과 기록용(시작한 회차만)
    val visual = remember { ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN) }
    var record by remember { mutableStateOf(ShakeRotatePolicy.Detector(ShakeRotatePolicy.CALIBRATE_MIN)) }
    var seen by remember { mutableIntStateOf(0) }

    fun center() = Offset(box.width / 2f, box.height / 2f)

    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        blob.blob.gain = 0.35f     // 목표를 느슨하게 따라가 물처럼 크게 출렁인다
        blob.blob.damping = 0.6f
        blob.moveTo(center().x, center().y, radius * 2, radius * 2, animate = false)
    }
    // 시작한 회차: 기록을 새로 하고, 남은 시간 호를 줄이며, 끝나면 단계를 정한다
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
                d.vx += (cx - d.x) * 26f * dt; d.vy += (cy - d.y) * 26f * dt
                d.vx *= 1f - 2.8f * dt; d.vy *= 1f - 2.8f * dt
                d.x += d.vx * dt; d.y += d.vy * dt
                d.age > 0.3f && hypot(d.x - cx, d.y - cy) < radius * 0.6f
            }
            tick++
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
                    visual.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    if (rec) record.feedGyro(e.values[0], e.values[1], e.values[2], ms)
                    return
                }
                visual.feed(e.values[0], e.values[1], e.values[2], ms)
                if (rec) { record.feed(e.values[0], e.values[1], e.values[2], ms); count = record.peaks.size }
                if (box == IntSize.Zero || !blob.blob.ready || reduce) return
                val o = center()
                // 폰 안의 물: 폰이 움직이는 반대쪽으로 쏠린다(화면 x는 기기 x, 화면 y는 기기 y의 반대). 작은 움직임도 크게 보이게.
                val k = reach / 3f
                val mx = (-visual.linearX * k).coerceIn(-reach, reach)
                val my = (visual.linearY * k).coerceIn(-reach, reach)
                blob.nudge(o.x + mx, o.y + my)
                if (visual.peaks.size != seen) {
                    seen = visual.peaks.size
                    val p = visual.peaks.last()
                    val l = hypot(p.x, p.y).coerceAtLeast(0.01f)
                    val dir = Offset(-p.x / l, p.y / l)
                    // 휘두름 세기만큼 바깥으로 통 튄다(약한 휘두름도 보이게 최소 속도를 둔다)
                    val speed = (900f + p.force * 220f).coerceAtMost(6000f)
                    blob.poke(o.x - dir.x * radius, o.y - dir.y * radius, dir.x * speed, dir.y * speed, radius * 1.3f)
                    // 세게 휘두르면 방울이 갈라져 나간다(세기에 따라 1~4개)
                    val n = if (p.force > SPLIT_FORCE) 1 + ((p.force - SPLIT_FORCE) / 4f).toInt().coerceIn(0, 3) else 0
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
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose {
            sm.unregisterListener(listener)
            ShakeRotate.calibrating = false
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(280.dp).onSizeChanged { box = it }) {
            // 남은 시간 호(물방울보다 뒤, goo 없음). 기록 중에만 줄어든다.
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val o = Offset(size.width / 2, size.height / 2)
                val ring = radius + 44.dp.toPx()
                drawCircle(c.line, ring, o, style = Stroke(3.dp.toPx()))
                if (recording) drawArc(
                    c.ink, -90f, 360f * time.value, false,
                    Offset(o.x - ring, o.y - ring), Size(ring * 2, ring * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // 물방울과 갈라진 방울(goo 층: 떨어지고 붙는 순간 목처럼 이어진다)
            Canvas(Modifier.fillMaxWidth().height(280.dp).goo(c.ink, 10.dp)) {
                blob.frame; tick
                if (blob.blob.ready) drawPath(blob.blob.path(path), c.ink)
                drops.forEach { drawCircle(c.ink, it.r, Offset(it.x, it.y)) }
            }
            if (recording) Text(
                "$left", color = c.onInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    blob.frame
                    if (blob.blob.ready) { translationX = blob.blob.cx - box.width / 2f; translationY = blob.blob.cy - box.height / 2f }
                },
            )
        }
        // 잡힌 휘두름 점: 찰 때마다 통 튀어 오른다(기록 중에만 센다)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(SLOTS) { i -> Dot(filled = recording && i < count) }
        }
    }
}

/** 이 세기(m/s²)를 넘는 휘두름이면 작은 방울이 갈라져 나간다. */
private const val SPLIT_FORCE = 6f

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
