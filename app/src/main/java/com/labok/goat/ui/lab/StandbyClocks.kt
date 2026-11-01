package com.labok.goat.ui.lab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.R
import java.time.LocalDateTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 텐트 모드 스탠바이 시계 11종(11-02 소장님). 모두 검은 바탕에서 시작하고(소장님 지정), 시각이 바뀔 때·쉬는 동안의 움직임을 넣었다.
 * 모든 시계에 날짜와 초가 기본으로 나온다(11-02 소장님).
 * 숫자는 공개 글꼴(OFL: Inter·Outfit·Doto·Bebas Neue·Fraunces·Manrope)을 숫자·대문자만 잘라 넣었고, 한글은 기기 글꼴이다.
 * 크기는 바깥 화면 가로(높이 1248 기준)에 비례한다. 시안 생성기: ~/claude_workspace/develop/goat/branding/standby/clocks.py
 */
internal object StandbyClocks {
    val NAMES = listOf("리퀴드", "디지털", "플로트", "솔라", "아날로그", "닷 매트릭스", "플립", "나이트", "위젯", "매거진", "미니멀 링")
    val COUNT = NAMES.size
}

private val InterHeavy = FontFamily(Font(R.font.standby_inter_heavy))
private val InterThin = FontFamily(Font(R.font.standby_inter_thin))
private val InterBold = FontFamily(Font(R.font.standby_inter_bold))
private val OutfitBlack = FontFamily(Font(R.font.standby_outfit_black))
private val Doto = FontFamily(Font(R.font.standby_doto))
private val Bebas = FontFamily(Font(R.font.standby_bebas))
private val Fraunces = FontFamily(Font(R.font.standby_fraunces))
private val ManropeThin = FontFamily(Font(R.font.standby_manrope_thin))

/** 강조 감속 곡선(머티리얼 3 emphasized decelerate): 빠르게 나와 길게 미끄러지며 멈춘다. */
private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Ink = Color(0xFFF2F2F0)
private val Mute = Color(0xFF7C7C80)

private fun hh(t: LocalDateTime) = "${t.hour}"
private fun mm(t: LocalDateTime) = "%02d".format(t.minute)
private fun ss(t: LocalDateTime) = "%02d".format(t.second)
private fun dayKo(t: LocalDateTime) = t.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.KOREAN)
private fun dateKo(t: LocalDateTime) = "${t.monthValue}월 ${t.dayOfMonth}일"

/** 화면 높이에 비례한 글자 크기(시안은 높이 1248 기준). */
private fun Dp.ratio(px: Float): TextUnit = (value * px / 1248f).sp

/**
 * 시각 글자(11-02 명품화, AndroidNumericText·NumberFlow 방식 참고): 바뀐 글자만 바뀐다. 나가는 글자는 글자 높이의 40%만큼
 * 위로 떠오르며 작아지고·흐려지고·사라지고, 들어오는 글자는 아래에서 같은 길을 거꾸로 밟아 또렷해진다. 움직임은 스프링(감쇠 0.86)이라
 * 끝에서 아주 살짝 넘쳤다 자리 잡고, 오른쪽 글자부터 45ms씩 늦게 출발해 물결처럼 이어진다. 글자 폭이 달라지면 폭도 스프링으로 바뀐다.
 */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(i) { MorphChar(ch, style, color, (text.length - 1 - i) * 45L) }
        }
    }
}

@Composable
private fun MorphChar(ch: Char, style: TextStyle, color: Color, delayMs: Long) {
    var cur by remember { mutableStateOf(ch) }
    var old by remember { mutableStateOf<Char?>(null) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(ch) {
        if (ch == cur) return@LaunchedEffect
        old = cur
        cur = ch
        p.snapTo(0f)
        delay(delayMs)
        p.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 150f))
        old = null
    }
    Box(Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 260f)), contentAlignment = Alignment.Center) {
        old?.let { o -> Text(o.toString(), color = color, style = style, modifier = Modifier.graphicsLayer { morph(p.value, out = true) }) }
        Text(cur.toString(), color = color, style = style, modifier = Modifier.graphicsLayer { morph(p.value, out = false) })
    }
}

/** 바뀌는 글자의 위치·크기·흐림·투명도. [q]는 0(시작)→1(끝), 스프링이라 1을 살짝 넘을 수 있다. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.morph(q: Float, out: Boolean) {
    val travel = size.height * 0.4f
    val k = if (out) q.coerceIn(0f, 1f) else (1f - q).coerceAtLeast(0f)
    translationY = if (out) -travel * q else travel * (1f - q)
    val sc = 1f - 0.16f * k
    scaleX = sc
    scaleY = sc
    alpha = if (out) (1f - k * 1.5f).coerceIn(0f, 1f) else (1f - k * 1.2f).coerceIn(0f, 1f)
    val blur = size.height * 0.07f * k
    renderEffect = if (blur > 0.5f) android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
}

/** 숨 쉬는 쌍점: 2초에 한 번 은은하게 흐려졌다 돌아온다. */
@Composable
private fun BreathingColon(style: TextStyle, color: Color) {
    val a by rememberInfiniteTransition(label = "colon").animateFloat(1f, 0.35f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Text(":", color = color.copy(alpha = color.alpha * a), style = style)
}

/**
 * 시:분을 바뀌는 숫자와 숨 쉬는 쌍점으로. 초는 기본으로 분 뒤에 작게(글자 크기의 [sec]배, 숫자 바닥선에 맞춰) 붙는다.
 */
@Composable
private fun TimeRow(now: LocalDateTime, style: TextStyle, color: Color, modifier: Modifier = Modifier, pad2: Boolean = false, sec: Float = 0.26f, secColor: Color = color.copy(alpha = color.alpha * 0.5f)) {
    Row(modifier) {
        RollingText(if (pad2) "%02d".format(now.hour) else hh(now), style, color, Modifier.alignBy(FirstBaseline))
        Box(Modifier.alignBy(FirstBaseline)) { BreathingColon(style, color) }
        RollingText(mm(now), style, color, Modifier.alignBy(FirstBaseline))
        if (sec > 0f) {
            val gap = with(LocalDensity.current) { (style.fontSize * 0.08f).toDp() }
            Spacer(Modifier.width(gap))
            RollingText(ss(now), style.copy(fontSize = style.fontSize * sec, letterSpacing = 0.sp), secColor, Modifier.alignBy(FirstBaseline))
        }
    }
}

/** [index]번 시계를 [now] 시각으로 그린다. [battery]는 0~100(모르면 null), [charging]은 충전 중 여부. */
@Composable
internal fun StandbyClock(index: Int, now: LocalDateTime, battery: Int?, charging: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val h = maxHeight
        when (index) {
            0 -> LiquidClock(now, h)
            1 -> DigitalClock(now, h, battery, charging)
            2 -> FloatClock(now, h)
            3 -> SolarClock(now, h)
            4 -> AnalogClock(now, h)
            5 -> DotClock(now, h)
            6 -> FlipClock(now, h)
            7 -> NightClock(now, h)
            8 -> WidgetClock(now, h, battery, charging)
            9 -> SerifClock(now, h)
            else -> RingClock(now, h, battery, charging)
        }
    }
}

/** 날짜 줄(흐린 작은 글): 왼쪽 위. */
@Composable
private fun DateLine(now: LocalDateTime, h: Dp, modifier: Modifier = Modifier, color: Color = Mute) {
    Text("${dayKo(now)}  ·  ${dateKo(now)}", color = color, fontSize = h.ratio(44f), fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = modifier)
}

/** 오른쪽 위 작은 초(시:분이 따로 그려지는 시계용). */
@Composable
private fun CornerSeconds(now: LocalDateTime, h: Dp, modifier: Modifier = Modifier, color: Color = Mute) {
    RollingText(ss(now), TextStyle(fontFamily = InterBold, fontSize = h.ratio(52f)), color, modifier)
}

/**
 * 리퀴드(GOAT 고유, 11-02 소장님: 액체자석 시계처럼). 그라데이션 없는 검은 바탕에 흰 액체 하나만 숫자로 모인다.
 * - 점도(11-02 더 높게): 스프링을 약하게(8), 감쇠를 임계보다 크게(6.4) 해 튕기지 않고 꿀처럼 천천히 흘러 자리 잡는다.
 *   흐림·자르기를 세게 해 방울끼리 끈적하게 붙고, 떨림도 줄였다.
 * - 균형: 방울과 목표 점을 모두 왼쪽→오른쪽, 위→아래 순서로 짝지어 엇갈리지 않고 가까운 자리로 흐른다. 웅덩이도 좌우 가운데.
 */
@Composable
private fun LiquidClock(now: LocalDateTime, h: Dp) {
    val context = LocalContext.current
    val text = "${hh(now)}:${mm(now)}"
    val drops = remember { FerroDrops(FERRO_COUNT) }
    LaunchedEffect(text) { drops.retarget(ferroTargets(context, text, FERRO_COUNT)) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = t
                drops.step(dt, t / 1e9f)
                frame = t
            }
        }
    }
    val goo = remember {
        val blur = android.graphics.RenderEffect.createBlurEffect(34f, 34f, android.graphics.Shader.TileMode.DECAL)
        val m = android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 30f, -12f * 255f))
        android.graphics.RenderEffect.createChainEffect(android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)), blur).asComposeRenderEffect()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
            if (frame < 0) return@Canvas
            val r = size.height * 0.02f
            for (i in 0 until drops.n) drawCircle(Ink, r, Offset(drops.x[i] * size.width, drops.y[i] * size.height))
        }
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
        CornerSeconds(now, h, Modifier.align(Alignment.TopEnd).padding(end = h * 0.09f, top = h * 0.07f))
    }
}

private const val FERRO_COUNT = 640

/** 자성 액체 방울들: 화면 비율 좌표(0~1). 목표 점으로 무겁게 끌려가고 아주 조금 떤다. 목표가 없는 방울은 바닥 가운데에 고인다. */
private class FerroDrops(val n: Int) {
    val x = FloatArray(n) { 0.2f + 0.6f * it / n }
    val y = FloatArray(n) { 0.95f }
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val tx = FloatArray(n) { 0.2f + 0.6f * it / n }
    private val ty = FloatArray(n) { 0.95f }
    private val seed = FloatArray(n) { (it * 2654435761L % 1000) / 1000f }

    /** 방울과 목표를 모두 왼→오, 위→아래 순으로 짝지어 엇갈리지 않게 한다. 남는 방울은 바닥 웅덩이로. */
    fun retarget(pts: List<Pair<Float, Float>>) {
        val order = (0 until n).sortedWith(compareBy({ x[it] }, { y[it] }))
        val sorted = pts.sortedWith(compareBy({ it.first }, { it.second }))
        val use = sorted.size.coerceAtMost(n)
        // 숫자에 쓸 방울은 가로로 고르게 뽑는다(웅덩이도 좌우 균형)
        val chosen = BooleanArray(n)
        for (k in 0 until use) chosen[order[(k.toLong() * n / use.coerceAtLeast(1)).toInt().coerceAtMost(n - 1)]] = true
        var t = 0
        var pool = 0
        val poolCount = n - chosen.count { it }
        for (i in order) {
            if (chosen[i] && t < use) {
                tx[i] = sorted[t].first
                ty[i] = sorted[t].second
                t++
            } else {
                val f = (pool + 0.5f) / poolCount.coerceAtLeast(1)
                tx[i] = 0.18f + 0.64f * f
                ty[i] = 0.955f + 0.012f * sin(f * 9f)
                pool++
            }
        }
    }

    fun step(dt: Float, t: Float) {
        val k = 8f
        val damp = 6.4f
        for (i in 0 until n) {
            val wob = 0.0016f
            val gx = tx[i] + wob * sin(t * 1.3f + seed[i] * 40f)
            val gy = ty[i] + wob * cos(t * 1.1f + seed[i] * 31f)
            vx[i] += ((gx - x[i]) * k - vx[i] * damp) * dt
            vy[i] += ((gy - y[i]) * k - vy[i] * damp) * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }
}

/** [text] 모양의 목표 점(비율 좌표). 숫자를 작은 그림에 그려 채워진 칸을 고르게 골라 쓴다. 숫자는 화면 가운데·폭 86% 안. */
private fun ferroTargets(context: android.content.Context, text: String, n: Int): List<Pair<Float, Float>> {
    val bw = 240
    val bh = 150
    val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ALPHA_8)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.standby_inter_heavy)
        textSize = bh * 0.66f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val tw = paint.measureText(text)
    if (tw > bw * 0.86f) paint.textSize *= bw * 0.86f / tw
    val fm = paint.fontMetrics
    c.drawText(text, bw / 2f, bh * 0.47f - (fm.ascent + fm.descent) / 2f, paint)
    val filled = ArrayList<Pair<Float, Float>>()
    for (yy in 0 until bh) {
        for (xx in 0 until bw) {
            if ((bmp.getPixel(xx, yy) ushr 24) > 128) filled += (xx / bw.toFloat()) to (yy / bh.toFloat())
        }
    }
    bmp.recycle()
    if (filled.isEmpty()) return emptyList()
    val use = (n * 0.86f).toInt()
    val stride = filled.size / use.toFloat()
    return if (stride <= 1f) filled else List(use) { filled[(it * stride).toInt()] }
}

/** 디지털: 꽉 찬 굵은 숫자, 숨 쉬는 쌍점, 왼쪽 위 날짜, 오른쪽 아래 배터리. */
@Composable
private fun DigitalClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    Box(Modifier.fillMaxSize()) {
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
        TimeRow(now, TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(580f), letterSpacing = (-0.03).sp), Ink, Modifier.align(Alignment.Center))
        battery?.let {
            Text((if (charging) "충전 " else "") + "$it%", color = if (charging) Color(0xFF7FD48A) else Mute, fontSize = h.ratio(40f), modifier = Modifier.align(Alignment.BottomEnd).padding(end = h * 0.09f, bottom = h * 0.07f))
        }
    }
}

/** 플로트: 파스텔 그라데이션 숫자가 각자 다른 박자로 둥실 떠 있고, 아래에 같은 색 빛이 번진다. 바뀔 때는 통통 튀어 나온다. */
@Composable
private fun FloatClock(now: LocalDateTime, h: Dp) {
    val digits = hh(now) + mm(now)
    val grads = listOf(
        listOf(Color(0xFFFF9EB8), Color(0xFFFFCB86)),
        listOf(Color(0xFF9CCFFF), Color(0xFFAA94FF)),
        listOf(Color(0xFF9EFFD2), Color(0xFF66D4FF)),
        listOf(Color(0xFFFFDA7A), Color(0xFFFF8A8A))
    )
    val t by rememberInfiniteTransition(label = "float").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
    val glow = remember { android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() }
    Box(Modifier.fillMaxSize()) {
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
        CornerSeconds(now, h, Modifier.align(Alignment.TopEnd).padding(end = h * 0.09f, top = h * 0.07f), Color(0xFFB9A8FF))
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        digits.forEachIndexed { i, ch ->
            key(i) {
                val bob = sin(t + i * 1.3f) * h.value * 0.012f
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = 220f), 0.55f) + fadeIn(tween(260))) togetherWith (scaleOut(tween(240), 1.25f) + fadeOut(tween(240))) },
                    modifier = Modifier.graphicsLayer { translationY = bob * density },
                    label = "float"
                ) { c ->
                    val style = TextStyle(fontFamily = OutfitBlack, fontSize = h.ratio(560f), brush = Brush.verticalGradient(grads[i % grads.size]))
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            c.toString(),
                            style = style,
                            modifier = Modifier.graphicsLayer {
                                alpha = 0.35f
                                translationY = h.value * 0.04f * density
                                renderEffect = glow
                            }
                        )
                        Text(c.toString(), style = style)
                    }
                }
            }
            if (i == hh(now).length - 1) Spacer(Modifier.width(h * 0.06f))
        }
    }
}

/** 솔라: 시각에 따라 해가 떠오르고 지며(낮에는 높고 밝게, 아침·저녁은 낮고 붉게) 천천히 일렁인다. 가는 숫자. */
@Composable
private fun SolarClock(now: LocalDateTime, h: Dp) {
    val m = now.hour * 60 + now.minute
    val day = ((m - 360) / 720f).coerceIn(0f, 1f)
    val lift = sin(day * PI).toFloat()
    val pulse by rememberInfiniteTransition(label = "sun").animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.fillMaxSize().drawBehind {
            val c = Offset(size.width / 2, size.height * (1.28f - 0.5f * lift))
            val outer = Color(red = 1f, green = 0.35f + 0.25f * lift, blue = 0.1f + 0.2f * lift)
            drawCircle(Brush.radialGradient(listOf(outer.copy(alpha = 0.85f), outer.copy(alpha = 0f)), c, size.height * 0.95f * pulse), size.height * 0.95f * pulse, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE2A8), Color(0x00FFE2A8)), c, size.height * 0.42f), size.height * 0.42f, c)
            drawLine(Color(0x22FFFFFF), Offset(size.width * 0.1f, size.height * 0.86f), Offset(size.width * 0.9f, size.height * 0.86f), 1.5f)
        }
    ) {
        Column(Modifier.align(Alignment.Center).padding(bottom = h * 0.1f), horizontalAlignment = Alignment.CenterHorizontally) {
            TimeRow(now, TextStyle(fontFamily = InterThin, fontSize = h.ratio(400f)), Color(0xFFFFF1E4))
            Text("${dayKo(now)} · ${dateKo(now)}", color = Color(0xFFFFC7A0), fontSize = h.ratio(44f), letterSpacing = 1.sp)
        }
    }
}

/** 아날로그: 정밀한 분 눈금, 12·3·6·9만 숫자, 매 프레임 부드럽게 미끄러지는 초침, 오른쪽에 요일·날짜·작은 디지털 시각. */
@Composable
private fun AnalogClock(now: LocalDateTime, h: Dp) {
    var ms by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { ms = System.currentTimeMillis() } }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxHeight().weight(1.15f).padding(h * 0.06f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val c = center
                drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1C1E), Color(0xFF0B0B0C)), c, r), r, c)
                drawCircle(Color(0xFF2A2A2D), r, c, style = Stroke(r * 0.006f))
                for (k in 0 until 60) {
                    val a = Math.toRadians(k * 6.0 - 90)
                    val major = k % 5 == 0
                    val r1 = r * if (major) 0.86f else 0.92f
                    drawLine(
                        if (major) Color(0xFFE8E8E8) else Color(0xFF55555A),
                        Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                        Offset(c.x + r * 0.96f * cos(a).toFloat(), c.y + r * 0.96f * sin(a).toFloat()),
                        if (major) r * 0.018f else r * 0.007f,
                        StrokeCap.Round
                    )
                }
                val sec = (ms / 1000.0 % 60).toFloat()
                val min = now.minute + sec / 60f
                val hour = now.hour % 12 + min / 60f
                fun hand(deg: Float, len: Float, w: Float, col: Color, tail: Float = 0f) = rotate(deg, c) {
                    drawLine(col, Offset(c.x, c.y + tail), Offset(c.x, c.y - len), w, StrokeCap.Round)
                }
                hand(hour * 30f, r * 0.52f, r * 0.045f, Color(0xFFF4F4F4))
                hand(min * 6f, r * 0.8f, r * 0.028f, Color(0xFFF4F4F4))
                hand(sec * 6f, r * 0.88f, r * 0.01f, Color(0xFFFF9F0A), r * 0.16f)
                drawCircle(Color(0xFFFF9F0A), r * 0.032f, c)
                drawCircle(Color(0xFF0B0B0C), r * 0.012f, c)
            }
        }
        Column(Modifier.weight(1f).padding(end = h * 0.08f)) {
            Text(dayKo(now), color = Ink, fontSize = h.ratio(96f), fontWeight = FontWeight.SemiBold)
            Text(dateKo(now), color = Mute, fontSize = h.ratio(64f))
            Spacer(Modifier.height(h * 0.04f))
            TimeRow(now, TextStyle(fontFamily = InterThin, fontSize = h.ratio(120f)), Color(0xFFBDBDC2), sec = 0.45f)
        }
    }
}

/** 닷 매트릭스: 점 글꼴 숫자, 1초마다 깜빡이는 빨간 점, 영문 날짜. */
@Composable
private fun DotClock(now: LocalDateTime, h: Dp) {
    val blink by rememberInfiniteTransition(label = "dot").animateFloat(1f, 0.15f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxSize()) {
        TimeRow(now, TextStyle(fontFamily = Doto, fontSize = h.ratio(500f)), Color(0xFFEDEDED), Modifier.align(Alignment.Center), pad2 = true, sec = 0.4f)
        Box(Modifier.align(Alignment.TopEnd).padding(top = h * 0.1f, end = h * 0.11f).size(h * 0.03f).graphicsLayer { alpha = blink }.clip(CircleShape).background(Color(0xFFFF2A2A)))
        val en = now.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase() + " %02d ".format(now.dayOfMonth) +
            now.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase()
        Text(en, color = Color(0xFF6A6A6A), style = TextStyle(fontFamily = Doto, fontSize = h.ratio(64f)), modifier = Modifier.align(Alignment.BottomStart).padding(start = h * 0.1f, bottom = h * 0.08f))
    }
}

/**
 * 플립(스플릿 플랩): 바뀔 때 이전 숫자의 위 반쪽이 경첩을 따라 앞으로 접혀 내려오고(0→-90°), 이어서 새 숫자의 아래 반쪽이 펴진다(90→0°).
 * 실제 플립 시계처럼 위·아래 반쪽을 따로 자르고 원근을 주며, 접히는 동안 판이 빛을 등지는 만큼 어두워진다.
 * 오른쪽 아래에 작은 초 카드가 매초 넘어가고, 아래에 날짜가 있다.
 */
@Composable
private fun FlipClock(now: LocalDateTime, h: Dp) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(horizontal = h * 0.1f, vertical = h * 0.16f), horizontalArrangement = Arrangement.spacedBy(h * 0.05f)) {
            FlipCard("%02d".format(now.hour), h, Modifier.weight(1f))
            FlipCard(mm(now), h, Modifier.weight(1f))
            Box(Modifier.weight(0.4f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                FlipCard(ss(now), h, Modifier.fillMaxHeight(0.4f), scale = 0.4f)
            }
        }
        DateLine(now, h, Modifier.align(Alignment.BottomStart).padding(start = h * 0.1f, bottom = h * 0.05f))
    }
}

@Composable
private fun FlipCard(value: String, h: Dp, modifier: Modifier, scale: Float = 1f) {
    var shown by remember { mutableStateOf(value) }
    var prev by remember { mutableStateOf(value) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == shown) return@LaunchedEffect
        prev = shown
        shown = value
        p.snapTo(0f)
        p.animateTo(1f, tween(760, easing = Emphasized))
    }
    val style = TextStyle(fontFamily = Bebas, fontSize = h.ratio(720f * scale), textAlign = TextAlign.Center, color = Color(0xFFEDEDED))
    val gap = h * 0.006f * scale.coerceAtLeast(0.5f)
    val shade = 0.5f * sin(PI.toFloat() * p.value)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val full = maxHeight
        val half = (full - gap) / 2

        /** 카드 반쪽: 전체 숫자를 카드 높이로 그리고 위 또는 아래 반만 보이게 자른다. */
        @Composable
        fun Half(text: String, top: Boolean, mod: Modifier = Modifier, dark: Float = 0f) {
            Box(
                mod.fillMaxWidth().height(half).clip(RoundedCornerShape(h * 0.04f * scale)).background(if (top) Color(0xFF232325) else Color(0xFF1B1B1D)).clipToBounds()
                    .drawWithContent {
                        drawContent()
                        if (dark > 0f) drawRect(Color.Black.copy(alpha = dark))
                    }
            ) {
                Box(Modifier.fillMaxWidth().wrapContentHeight(if (top) Alignment.Top else Alignment.Bottom, unbounded = true).height(full), contentAlignment = Alignment.Center) {
                    Text(text, style = style)
                }
            }
        }
        Column {
            Box {
                Half(shown, true)
                if (p.value < 0.5f) {
                    Half(
                        prev,
                        true,
                        Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            rotationX = -180f * p.value
                            cameraDistance = 30f * density
                        },
                        shade
                    )
                }
            }
            Spacer(Modifier.height(gap))
            Box {
                Half(if (p.value < 1f) prev else shown, false)
                if (p.value >= 0.5f) {
                    Half(
                        shown,
                        false,
                        Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = 180f * (1f - p.value)
                            cameraDistance = 30f * density
                        },
                        shade
                    )
                }
            }
        }
    }
}

/** 나이트: 아주 어두운 빨강, 쌍점이 느리게 숨 쉰다. 밤에 눈부시지 않게. */
@Composable
private fun NightClock(now: LocalDateTime, h: Dp) {
    Box(Modifier.fillMaxSize()) {
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f), Color(0xFF5A0707))
        TimeRow(now, TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(560f), letterSpacing = (-0.03).sp), Color(0xFF8E0A0A), Modifier.align(Alignment.Center))
    }
}

/** 위젯: 시계 카드 + 달력 카드 + 배터리 카드(충전 중이면 막대 위로 빛이 흐름). 카드는 은은한 그라데이션. */
@Composable
private fun WidgetClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    val pad = h * 0.05f
    val shimmer by rememberInfiniteTransition(label = "shim").animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "s")
    Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(pad * 0.6f)) {
        Column(
            Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(h * 0.07f)).background(Brush.verticalGradient(listOf(Color(0xFF1E1E21), Color(0xFF131315)))),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimeRow(now, TextStyle(fontFamily = InterBold, fontSize = h.ratio(380f), letterSpacing = (-0.02).sp), Ink)
            Text("${dayKo(now)}  ${dateKo(now)}", color = Mute, fontSize = h.ratio(54f))
        }
        Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(pad * 0.6f)) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.06f)).background(Brush.linearGradient(listOf(Color(0xFF243E70), Color(0xFF15264A)))).padding(h * 0.045f)) {
                Text("${now.monthValue}월 · ${dayKo(now)}", color = Color(0xFFC9D8FF), fontSize = h.ratio(48f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${now.dayOfMonth}", color = Color.White, style = TextStyle(fontFamily = InterThin, fontSize = h.ratio(190f)))
            }
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.06f)).background(Brush.linearGradient(listOf(Color(0xFF1C4A2B), Color(0xFF10291A)))).padding(h * 0.045f)) {
                Text(if (charging) "충전 중" else "배터리", color = Color(0xFFB4F0BE), fontSize = h.ratio(48f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(battery?.let { "$it%" } ?: "-", color = Color.White, style = TextStyle(fontFamily = InterThin, fontSize = h.ratio(170f)))
                Box(Modifier.fillMaxWidth().height(h * 0.022f).clip(RoundedCornerShape(50)).background(Color(0xFF285A37))) {
                    Box(
                        Modifier.fillMaxWidth((battery ?: 0) / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(0xFF5AE678)).drawBehind {
                            if (charging) drawRect(Brush.horizontalGradient(listOf(Color(0x00FFFFFF), Color(0x88FFFFFF), Color(0x00FFFFFF)), size.width * (shimmer - 0.2f), size.width * (shimmer + 0.2f)))
                        }
                    )
                }
            }
        }
    }
}

/** 매거진: 검은 바탕의 상아색 세리프 숫자, 금빛 가는 선, 자간 넓은 영문 날짜. */
@Composable
private fun SerifClock(now: LocalDateTime, h: Dp) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        TimeRow(now, TextStyle(fontFamily = Fraunces, fontSize = h.ratio(560f)), Color(0xFFF1E9DA))
        Box(Modifier.width(h * 1.1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color(0x00C9A45C), Color(0xFFC9A45C), Color(0x00C9A45C)))))
        Spacer(Modifier.height(h * 0.04f))
        val en = now.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase() + "  ·  " +
            now.month.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase() + " ${now.dayOfMonth}"
        Text(en, color = Color(0xFF9A8F7C), style = TextStyle(fontFamily = InterBold, fontSize = h.ratio(40f), letterSpacing = 4.sp))
    }
}

/** 미니멀 링: 초 진행 링이 매 프레임 끊김 없이 돌고, 끝에 빛나는 점이 달린다. 가는 숫자. */
@Composable
private fun RingClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    var ms by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { ms = System.currentTimeMillis() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(h * 0.84f)) {
            val w = size.minDimension * 0.012f
            val r = size.minDimension / 2 - w * 2
            drawCircle(Color(0xFF1E1E20), r, style = Stroke(w))
            val sweep = 360f * ((ms % 60000) / 60000f)
            drawArc(Color(0xFFF2F2F2), -90f, sweep, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
            val a = Math.toRadians((sweep - 90).toDouble())
            val tip = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)), tip, w * 6), w * 6, tip)
            drawCircle(Color.White, w * 1.1f, tip)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimeRow(now, TextStyle(fontFamily = ManropeThin, fontSize = h.ratio(270f)), Ink)
            Text("${dayKo(now)} · ${dateKo(now)}", color = Mute, fontSize = h.ratio(44f))
            Text((if (charging) "충전 " else "") + (battery?.let { "$it%" } ?: ""), color = if (charging) Color(0xFF82DC8C) else Mute, fontSize = h.ratio(40f))
        }
    }
}

/** 시계 아래 점 표시(지금 몇 번째 스타일인지). 넘길 때 잠깐 보인다. */
@Composable
internal fun StandbyDots(index: Int, alpha: Float) {
    Row(Modifier.graphicsLayer { this.alpha = alpha }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(StandbyClocks.COUNT) { i ->
            Box(Modifier.size(if (i == index) 9.dp else 6.dp).clip(CircleShape).background(if (i == index) Color.White else Color(0x66FFFFFF)))
        }
    }
}
