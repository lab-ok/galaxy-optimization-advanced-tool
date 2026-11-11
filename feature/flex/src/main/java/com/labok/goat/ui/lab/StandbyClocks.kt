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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.core.R
import com.labok.goat.ui.text.Copy
import java.time.LocalDateTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 텐트 모드 스탠바이 시계 11종(11-02 소장님). 모두 검은 바탕에서 시작하고(소장님 지정), 시각이 바뀔 때·쉬는 동안의 움직임을 넣었다.
 * 모든 시계에 날짜와 초가 기본으로 나온다(11-02 소장님).
 * 숫자는 공개 글꼴(OFL: Inter·Outfit·Doto·Bebas Neue·Fraunces·Manrope)을 숫자·대문자만 잘라 넣었고, 한글은 기기 글꼴이다.
 * 크기는 바깥 화면 가로(높이 1248 기준)에 비례한다. 시안 생성기: ~/claude_workspace/develop/goat/branding/standby/clocks.py
 */
object StandbyClocks {
    private val NAME_IDS = listOf(R.string.lab_standby_clock_liquid, R.string.lab_standby_clock_digital, R.string.lab_standby_clock_float, R.string.lab_standby_clock_solar, R.string.lab_standby_clock_analog, R.string.lab_standby_clock_dot_matrix, R.string.lab_standby_clock_flip, R.string.lab_standby_clock_night, R.string.lab_standby_clock_widget, R.string.lab_standby_clock_magazine, R.string.lab_standby_clock_minimal_ring)
    val NAMES: List<String> get() = NAME_IDS.map { Copy.text(it) }
    val COUNT = NAME_IDS.size
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
private fun dateKo(t: LocalDateTime) = Copy.text(R.string.lab_date_month_day, t.monthValue, t.dayOfMonth)

/** 화면 높이에 비례한 글자 크기(시안은 높이 1248 기준). */
private fun Dp.ratio(px: Float): TextUnit = (value * px / 1248f).sp

/**
 * 시각 글자(11-02 명품화, AndroidNumericText·NumberFlow 방식 참고): 바뀐 글자만 바뀐다. 나가는 글자는 글자 높이의 40%만큼
 * 위로 떠오르며 작아지고·흐려지고·사라지고, 들어오는 글자는 아래에서 같은 길을 거꾸로 밟아 또렷해진다. 움직임은 스프링(감쇠 0.86)이라
 * 끝에서 아주 살짝 넘쳤다 자리 잡고, 오른쪽 글자부터 45ms씩 늦게 출발해 물결처럼 이어진다. 숫자는 고정폭 칸에 들어가 자리가 흔들리지 않는다.
 */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val cell = rememberDigitWidth(style)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(i) { MorphChar(ch, style, color, (text.length - 1 - i) * 45L, if (ch.isDigit()) cell else null) }
        }
    }
}

/**
 * 숫자 한 칸의 고정 폭: 0~9 가운데 가장 넓은 글자 폭(11-02 소장님: 고정폭으로, 넘어갈 때 다시 정렬되지 않게).
 * 잘라 넣은 글꼴에는 고정폭 숫자(tnum) 기능이 없어, 칸 폭을 직접 재서 모든 숫자를 같은 폭 칸 가운데에 둔다.
 */
@Composable
private fun rememberDigitWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val px = ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
        with(density) { px.toDp() }
    }
}

@Composable
private fun MorphChar(ch: Char, style: TextStyle, color: Color, delayMs: Long, cell: Dp? = null) {
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
    Box(if (cell != null) Modifier.width(cell) else Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 260f)), contentAlignment = Alignment.Center) {
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
fun StandbyClock(index: Int, now: LocalDateTime, battery: Int?, charging: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val h = maxHeight
        when (index) {
            0 -> LiquidClock(now, h, maxWidth / maxHeight)
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
 * 리퀴드(GOAT 고유, 11-02 소장님: 자석 액체 시계처럼). 순수 검은 바탕에 흰 자성 액체가 얇은 선으로 숫자를 만든다.
 * - 기하학 숫자: 실제 자석 액체 시계(전자석 격자)처럼 숫자를 7개 막대(세그먼트)로 만들고, 막대마다 정해진 자리에 방울을 붙든다.
 * - 바뀐 숫자만: 시각이 바뀌면 바뀐 칸에서 꺼질 막대의 방울만 떨어져 나와 새로 켤 막대로 흐른다(가까운 방울부터, 18ms 간격으로 줄지어).
 *   남거나 모자란 방울은 바닥 웅덩이와 주고받는다. 그대로인 막대·숫자·쌍점은 움직이지 않는다.
 * - 점도(11-02 크게): 스프링 3.5, 감쇠 4.6(임계 이상)이라 튕기지 않고 2~3초 동안 꿀처럼 느리게 흐른다.
 * - 두께: 방울을 작게(높이의 1.2%) 해 얇은 선으로 이어지게 하고, 흐림·자르기로 방울끼리 끈적하게 붙인다.
 */
@Composable
private fun LiquidClock(now: LocalDateTime, h: Dp, aspect: Float) {
    val text = "%02d".format(now.hour) + mm(now)
    val ferro = remember(aspect) { Ferro(aspect) }
    LaunchedEffect(text, ferro) { ferro.show(text, System.nanoTime() / 1e9f) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ferro) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = t
                ferro.step(dt, t / 1e9f)
                frame = t
            }
        }
    }
    val density = LocalDensity.current
    val goo = remember(h, density) {
        val r = with(density) { h.toPx() } * FERRO_R
        val blur = android.graphics.RenderEffect.createBlurEffect(r * 0.9f, r * 0.9f, android.graphics.Shader.TileMode.DECAL)
        val m = android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 22f, -9f * 255f))
        android.graphics.RenderEffect.createChainEffect(android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)), blur).asComposeRenderEffect()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
            if (frame < 0) return@Canvas
            val u = size.height
            val r = u * FERRO_R
            for (i in 0 until ferro.n) drawCircle(Ink, r, Offset(ferro.x[i] * u, ferro.y[i] * u))
        }
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
        CornerSeconds(now, h, Modifier.align(Alignment.TopEnd).padding(end = h * 0.09f, top = h * 0.07f))
    }
}

/** 방울 반지름(화면 높이 비율). */
private const val FERRO_R = 0.012f

/** 막대 하나에 붙드는 방울 수. */
private const val FERRO_PER_SEG = 12

/** 0~9를 켜는 막대(비트 0~6 = a 위, b 오른쪽 위, c 오른쪽 아래, d 아래, e 왼쪽 아래, f 왼쪽 위, g 가운데). */
private val SEGMENTS = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

/**
 * 자성 액체 방울들. 좌표는 화면 높이를 1로 둔 단위(가로는 0~[aspect]). 칸 4개(시·시·분·분)의 막대마다 방울 [FERRO_PER_SEG]개를 붙들고,
 * 쌍점 방울과 바닥 웅덩이가 따로 있다. 방울 수는 모든 칸이 8일 때도 모자라지 않게 잡았다.
 */
private class Ferro(private val aspect: Float) {
    private val cellW = 0.28f
    private val cellH = 0.5f
    private val top = 0.24f
    private val inset = 0.03f
    private val colonN = 10
    val n = 4 * 7 * FERRO_PER_SEG + colonN + 48
    val x = FloatArray(n)
    val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val tx = FloatArray(n)
    private val ty = FloatArray(n)

    // 줄지어 흐르게: 새 목표([nx], [ny])로 떠날 시각(초). 음수면 떠날 일 없음
    private val nx = FloatArray(n)
    private val ny = FloatArray(n)
    private val leave = FloatArray(n) { -1f }
    private val seed = FloatArray(n) { (it * 2654435761L % 1000) / 1000f }

    // 칸·막대별로 붙든 방울(없으면 null)과 지금 칸에 보이는 숫자
    private val owner = Array(4) { arrayOfNulls<IntArray>(7) }
    private val shown = IntArray(4) { -1 }
    private val pool = ArrayList<Int>()

    init {
        val cx = aspect / 2
        for (i in 0 until n) {
            x[i] = cx - 0.55f + 1.1f * i / n
            y[i] = POOL_Y
            tx[i] = x[i]
            ty[i] = y[i]
        }
        // 쌍점: 가운데 위·아래 작은 방울 덩어리(움직이지 않는다)
        for (k in 0 until colonN) {
            val upper = k < colonN / 2
            val a = (k % (colonN / 2)) * (2 * PI.toFloat() / (colonN / 2))
            tx[k] = cx + 0.012f * cos(a)
            ty[k] = top + cellH * (if (upper) 0.3f else 0.72f) + 0.012f * sin(a)
        }
        for (i in colonN until n) pool += i
    }

    /** 칸 [slot]의 왼쪽 끝(가운데 쌍점 양옆으로 두 칸씩). */
    private fun cellLeft(slot: Int): Float {
        val cx = aspect / 2
        val pair = 0.06f
        val colon = 0.09f
        return when (slot) {
            0 -> cx - colon - pair - 2 * cellW
            1 -> cx - colon - cellW
            2 -> cx + colon
            else -> cx + colon + cellW + pair
        }
    }

    /** 칸 [slot] 막대 [seg]의 [k]번째 방울 자리. */
    private fun point(slot: Int, seg: Int, k: Int): Pair<Float, Float> {
        val l = cellLeft(slot)
        val w = cellW
        val hh = cellH
        val f = k / (FERRO_PER_SEG - 1f)
        val (x0, y0, x1, y1) = when (seg) {
            0 -> floatArrayOf(inset, 0f, w - inset, 0f)
            1 -> floatArrayOf(w, inset, w, hh / 2 - inset)
            2 -> floatArrayOf(w, hh / 2 + inset, w, hh - inset)
            3 -> floatArrayOf(inset, hh, w - inset, hh)
            4 -> floatArrayOf(0f, hh / 2 + inset, 0f, hh - inset)
            5 -> floatArrayOf(0f, inset, 0f, hh / 2 - inset)
            else -> floatArrayOf(inset, hh / 2, w - inset, hh / 2)
        }
        return (l + x0 + (x1 - x0) * f) to (top + y0 + (y1 - y0) * f)
    }

    /** [digits](4자리)를 보인다. 바뀐 칸만 막대를 주고받는다. [now]는 프레임과 같은 시계(초). */
    fun show(digits: String, now: Float) {
        // 방울을 내놓는 칸부터 처리해 웅덩이가 모자라지 않게
        val order = (0 until 4).sortedBy { s ->
            val d = digits[s].digitToInt()
            Integer.bitCount(SEGMENTS[d]) - (if (shown[s] < 0) 0 else Integer.bitCount(SEGMENTS[shown[s]]))
        }
        for (s in order) {
            val d = digits[s].digitToInt()
            if (d == shown[s]) continue
            val oldMask = if (shown[s] < 0) 0 else SEGMENTS[shown[s]]
            val newMask = SEGMENTS[d]
            shown[s] = d
            val supply = ArrayList<Int>()
            for (seg in 0 until 7) {
                if (oldMask and (1 shl seg) != 0 && newMask and (1 shl seg) == 0) {
                    owner[s][seg]?.let { supply.addAll(it.toList()) }
                    owner[s][seg] = null
                }
            }
            val need = ArrayList<Pair<Int, Int>>()
            for (seg in 0 until 7) if (newMask and (1 shl seg) != 0 && oldMask and (1 shl seg) == 0) for (k in 0 until FERRO_PER_SEG) need += seg to k
            // 모자라면 웅덩이에서 이 칸에 가까운 방울을 데려온다
            if (need.size > supply.size) {
                val mid = cellLeft(s) + cellW / 2
                pool.sortBy { abs(x[it] - mid) }
                val take = pool.take(need.size - supply.size)
                pool.removeAll(take.toSet())
                supply.addAll(take)
            }
            // 가까운 짝부터: 목표마다 남은 방울 가운데 가장 가까운 것을 붙인다. 가까운 것부터 먼저 떠난다
            val pairs = ArrayList<Triple<Int, Int, Float>>()
            val left = supply.toMutableList()
            val segDrops = HashMap<Int, IntArray>()
            for ((seg, k) in need) {
                val (px, py) = point(s, seg, k)
                val best = left.minBy { (x[it] - px) * (x[it] - px) + (y[it] - py) * (y[it] - py) }
                left.remove(best)
                segDrops.getOrPut(seg) { IntArray(FERRO_PER_SEG) }[k] = best
                pairs += Triple(best, seg * FERRO_PER_SEG + k, (x[best] - px) * (x[best] - px) + (y[best] - py) * (y[best] - py))
            }
            segDrops.forEach { (seg, arr) -> owner[s][seg] = arr }
            pairs.sortedBy { it.third }.forEachIndexed { order2, (drop, slotK, _) ->
                val (px, py) = point(s, slotK / FERRO_PER_SEG, slotK % FERRO_PER_SEG)
                send(drop, px, py, now + order2 * 0.018f)
            }
            // 남는 방울은 웅덩이로
            pool.addAll(left)
        }
        // 웅덩이: 지금 들어 있는 방울을 왼→오 순서로 바닥에 고르게 편다(새로 들어온 것만 크게 움직인다)
        val cx = aspect / 2
        pool.sortBy { x[it] }
        pool.forEachIndexed { k, i ->
            val px = cx - 0.5f + 1f * (k + 0.5f) / pool.size
            val py = POOL_Y
            if (leave[i] < 0f && abs(tx[i] - px) + abs(ty[i] - py) > 0.0005f) send(i, px, py, now + 0.4f)
        }
    }

    private fun send(i: Int, px: Float, py: Float, at: Float) {
        nx[i] = px
        ny[i] = py
        leave[i] = at
    }

    fun step(dt: Float, t: Float) {
        val k = 3.5f
        val damp = 4.6f
        for (i in 0 until n) {
            if (leave[i] in 0f..t) {
                tx[i] = nx[i]
                ty[i] = ny[i]
                leave[i] = -1f
            }
            val wob = 0.0009f
            val gx = tx[i] + wob * sin(t * 1.1f + seed[i] * 40f)
            val gy = ty[i] + wob * cos(t * 0.9f + seed[i] * 31f)
            vx[i] += ((gx - x[i]) * k - vx[i] * damp) * dt
            vy[i] += ((gy - y[i]) * k - vy[i] * damp) * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    private companion object {
        const val POOL_Y = 0.93f
    }
}

/** 디지털: 꽉 찬 굵은 숫자, 숨 쉬는 쌍점, 왼쪽 위 날짜, 오른쪽 아래 배터리. */
@Composable
private fun DigitalClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    Box(Modifier.fillMaxSize()) {
        DateLine(now, h, Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
        TimeRow(now, TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(500f), letterSpacing = (-0.03).sp), Ink, Modifier.align(Alignment.Center))
        battery?.let { BatteryPill(it, charging, h, Modifier.align(Alignment.BottomEnd).padding(end = h * 0.09f, bottom = h * 0.07f)) }
    }
}

/** 배터리 알약: 작은 배터리 모양(채움 = 잔량, 20% 아래 빨강, 충전 중 초록)과 숫자. */
@Composable
private fun BatteryPill(level: Int, charging: Boolean, h: Dp, modifier: Modifier = Modifier) {
    val col = when {
        charging -> Color(0xFF7FD48A)
        level <= 20 -> Color(0xFFFF6B5E)
        else -> Color(0xFFBDBDC2)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$level%", color = col, style = TextStyle(fontFamily = InterBold, fontSize = h.ratio(38f)))
        Spacer(Modifier.width(h * 0.015f))
        Canvas(Modifier.width(h * 0.07f).height(h * 0.032f)) {
            val cap = size.width * 0.08f
            val body = Size(size.width - cap, size.height)
            val cr = androidx.compose.ui.geometry.CornerRadius(size.height * 0.28f)
            drawRoundRect(col.copy(alpha = 0.55f), Offset.Zero, body, cr, style = Stroke(size.height * 0.09f))
            val pad = size.height * 0.16f
            drawRoundRect(col, Offset(pad, pad), Size((body.width - 2 * pad) * level / 100f, body.height - 2 * pad), androidx.compose.ui.geometry.CornerRadius(size.height * 0.14f))
            drawRoundRect(col.copy(alpha = 0.55f), Offset(body.width + cap * 0.15f, size.height * 0.3f), Size(cap * 0.85f, size.height * 0.4f), androidx.compose.ui.geometry.CornerRadius(cap))
        }
    }
}

/** 플로트: 파스텔 그라데이션 숫자가 각자 다른 박자로 둥실 떠 있고, 아래에 같은 색 빛이 번진다. 바뀔 때는 통통 튀어 나온다. */
@Composable
private fun FloatClock(now: LocalDateTime, h: Dp) {
    val digits = "%02d".format(now.hour) + mm(now)
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
    val floatCell = rememberDigitWidth(TextStyle(fontFamily = OutfitBlack, fontSize = h.ratio(560f)))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        digits.forEachIndexed { i, ch ->
            key(i) {
                val bob = sin(t + i * 1.3f) * h.value * 0.012f
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = 220f), 0.55f) + fadeIn(tween(260))) togetherWith (scaleOut(tween(240), 1.25f) + fadeOut(tween(240))) },
                    modifier = Modifier.width(floatCell).graphicsLayer { translationY = bob * density },
                    contentAlignment = Alignment.Center,
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
            if (i == 1) Spacer(Modifier.width(h * 0.06f))
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
    val dateMeasurer = rememberTextMeasurer()
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
                // 시 자리 야광 점(12시는 두 개) — 실제 손목시계의 인덱스처럼 눈금 안쪽에
                for (k in 0 until 12) {
                    if (k == 3) continue
                    val a = Math.toRadians(k * 30.0 - 90)
                    val rr = r * 0.78f
                    val p = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
                    if (k == 0) {
                        drawCircle(Color(0xFFDCE8D8), r * 0.022f, p + Offset(-r * 0.03f, 0f))
                        drawCircle(Color(0xFFDCE8D8), r * 0.022f, p + Offset(r * 0.03f, 0f))
                    } else {
                        drawCircle(Color(0xFFDCE8D8), r * 0.022f, p)
                    }
                }
                // 3시 날짜 창
                val dw = r * 0.2f
                val dh = r * 0.13f
                val dc = Offset(c.x + r * 0.68f, c.y)
                drawRoundRect(Color(0xFF050506), Offset(dc.x - dw / 2, dc.y - dh / 2), Size(dw, dh), androidx.compose.ui.geometry.CornerRadius(r * 0.015f))
                drawRoundRect(Color(0xFF3A3A3E), Offset(dc.x - dw / 2, dc.y - dh / 2), Size(dw, dh), androidx.compose.ui.geometry.CornerRadius(r * 0.015f), style = Stroke(r * 0.006f))
                val dayLayout = dateMeasurer.measure("${now.dayOfMonth}", TextStyle(fontFamily = InterBold, fontSize = (dh * 0.62f / density / fontScale).sp, color = Color(0xFFEDEDED)))
                drawText(dayLayout, topLeft = Offset(dc.x - dayLayout.size.width / 2f, dc.y - dayLayout.size.height / 2f))
                val sec = (ms / 1000.0 % 60).toFloat()
                val min = now.minute + sec / 60f
                val hour = now.hour % 12 + min / 60f

                // 바늘: 아래 그림자(빛이 위에서 비치는 느낌) → 금속 바늘 → 가운데 야광 띠
                fun hand(deg: Float, len: Float, w: Float, col: Color, tail: Float = 0f, lume: Boolean = false) {
                    rotate(deg, c) {
                        drawLine(Color(0x66000000), Offset(c.x + w * 0.4f, c.y + tail + w * 0.6f), Offset(c.x + w * 0.4f, c.y - len + w * 0.6f), w * 1.1f, StrokeCap.Round)
                        drawLine(col, Offset(c.x, c.y + tail), Offset(c.x, c.y - len), w, StrokeCap.Round)
                        if (lume) drawLine(Color(0xFFDCE8D8), Offset(c.x, c.y - len * 0.3f), Offset(c.x, c.y - len * 0.94f), w * 0.42f, StrokeCap.Round)
                    }
                }
                hand(hour * 30f, r * 0.52f, r * 0.05f, Color(0xFFBFC0C4), lume = true)
                hand(min * 6f, r * 0.8f, r * 0.034f, Color(0xFFBFC0C4), lume = true)
                rotate(sec * 6f, c) {
                    drawLine(Color(0xFFFF9F0A), Offset(c.x, c.y + r * 0.18f), Offset(c.x, c.y - r * 0.88f), r * 0.009f, StrokeCap.Round)
                    drawCircle(Color(0xFFFF9F0A), r * 0.028f, Offset(c.x, c.y + r * 0.16f))
                }
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
                mod.fillMaxWidth().height(half).clip(RoundedCornerShape(h * 0.04f * scale))
                    .background(Brush.verticalGradient(if (top) listOf(Color(0xFF2A2A2D), Color(0xFF202022)) else listOf(Color(0xFF1C1C1E), Color(0xFF161618))))
                    .clipToBounds()
                    .drawWithContent {
                        drawContent()
                        // 접는 선 쪽 그늘과 양옆 경첩 핀(실제 플립 시계처럼)
                        val edge = if (top) size.height else 0f
                        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x30000000)), if (top) size.height * 0.8f else size.height * 0.2f, edge), Offset(0f, if (top) size.height * 0.8f else 0f), Size(size.width, size.height * 0.2f))
                        val pinW = size.width * 0.025f
                        val pinH = size.height * 0.12f
                        val pinY = if (top) size.height - pinH else 0f
                        drawRect(Color(0xFF0A0A0B), Offset(0f, pinY), Size(pinW, pinH))
                        drawRect(Color(0xFF0A0A0B), Offset(size.width - pinW, pinY), Size(pinW, pinH))
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
        TimeRow(now, TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(500f), letterSpacing = (-0.03).sp), Color(0xFF8E0A0A), Modifier.align(Alignment.Center))
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
            TimeRow(now, TextStyle(fontFamily = InterBold, fontSize = h.ratio(290f), letterSpacing = (-0.02).sp), Ink)
            Text("${dayKo(now)}  ${dateKo(now)}", color = Mute, fontSize = h.ratio(54f))
        }
        Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(pad * 0.6f)) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.06f)).background(Brush.linearGradient(listOf(Color(0xFF243E70), Color(0xFF15264A)))).padding(h * 0.045f)) {
                Text(stringResource(R.string.lab_standby_month_day_of_week, now.monthValue, dayKo(now)), color = Color(0xFFC9D8FF), fontSize = h.ratio(48f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${now.dayOfMonth}", color = Color.White, style = TextStyle(fontFamily = InterThin, fontSize = h.ratio(190f)))
            }
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.06f)).background(Brush.linearGradient(listOf(Color(0xFF1C4A2B), Color(0xFF10291A)))).padding(h * 0.045f)) {
                Text(if (charging) stringResource(R.string.lab_battery_status_charging) else stringResource(R.string.lab_battery_label), color = Color(0xFFB4F0BE), fontSize = h.ratio(48f), fontWeight = FontWeight.SemiBold)
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
            val r = size.minDimension / 2 - w * 8
            drawCircle(Color(0xFF1E1E20), r, style = Stroke(w))
            // 바깥 분 눈금: 지난 초까지는 밝게, 5초마다 길게
            val done = ((ms % 60000) / 1000).toInt()
            for (k in 0 until 60) {
                val a = Math.toRadians(k * 6.0 - 90)
                val long = k % 5 == 0
                val r1 = r + w * 3f
                val r2 = r1 + w * (if (long) 4f else 2f)
                drawLine(
                    if (k <= done) Color(0xFF8C8C92) else Color(0xFF2A2A2D),
                    Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                    Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                    w * (if (long) 0.5f else 0.3f),
                    StrokeCap.Round
                )
            }
            val sweep = 360f * ((ms % 60000) / 60000f)
            drawArc(Color(0xFFF2F2F2), -90f, sweep, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
            val a = Math.toRadians((sweep - 90).toDouble())
            val tip = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)), tip, w * 6), w * 6, tip)
            drawCircle(Color.White, w * 1.1f, tip)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimeRow(now, TextStyle(fontFamily = ManropeThin, fontSize = h.ratio(230f)), Ink)
            Text("${dayKo(now)} · ${dateKo(now)}", color = Mute, fontSize = h.ratio(44f))
            Text(if (charging) stringResource(R.string.lab_standby_charging_level, battery?.let { "$it%" } ?: "") else battery?.let { "$it%" } ?: "", color = if (charging) Color(0xFF82DC8C) else Mute, fontSize = h.ratio(40f))
        }
    }
}

/** 시계 아래 점 표시(지금 몇 번째 스타일인지). 넘길 때 잠깐 보인다. */
@Composable
fun StandbyDots(index: Int, alpha: Float) {
    Row(Modifier.graphicsLayer { this.alpha = alpha }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(StandbyClocks.COUNT) { i ->
            Box(Modifier.size(if (i == index) 9.dp else 6.dp).clip(CircleShape).background(if (i == index) Color.White else Color(0x66FFFFFF)))
        }
    }
}
