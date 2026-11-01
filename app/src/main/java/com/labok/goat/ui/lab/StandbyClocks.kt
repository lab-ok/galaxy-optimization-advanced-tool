package com.labok.goat.ui.lab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
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

/**
 * 텐트 모드 스탠바이 시계 10종(11-02 소장님: 시안 10개를 모두 고를 수 있게, 옆으로 넘겨 바꾸기). 시안 생성기와 같은 구성이다:
 * ~/claude_workspace/develop/goat/branding/standby/clocks.py. 숫자는 공개 글꼴(OFL: Inter·Outfit·Doto·Bebas Neue·Fraunces·Manrope)을
 * 숫자·대문자만 잘라 넣었고, 한글은 기기 글꼴을 쓴다. 바깥 화면 가로(약 2:1.25)에 맞춰 크기를 화면 높이에 비례해 정한다.
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

private fun hm(t: LocalDateTime) = "${t.hour}:${"%02d".format(t.minute)}"

/**
 * 시각 글자: 바뀐 글자만 아래에서 위로 밀려 올라오며 바뀐다(11-02 소장님: 시각이 바뀔 때 애니메이션).
 * 글자마다 따로 움직여 6:29 → 6:30이면 '2'와 '9'만 넘어간다.
 */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(420))) togetherWith
                            (slideOutVertically(tween(520, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(300)))
                    },
                    label = "digit"
                ) { c -> Text(c.toString(), color = color, style = style) }
            }
        }
    }
}

private fun dayKo(t: LocalDateTime) = t.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.KOREAN)

private fun dateKo(t: LocalDateTime) = "${t.monthValue}월 ${t.dayOfMonth}일"

/** 화면 높이 [h]에 비례한 글자 크기(시안은 높이 1248 기준). */
private fun Dp.ratio(px: Float): TextUnit = (value * px / 1248f).sp

/** [index]번 시계를 [now] 시각으로 그린다. [battery]는 0~100(모르면 null), [charging]은 충전 중 여부. */
@Composable
internal fun StandbyClock(index: Int, now: LocalDateTime, battery: Int?, charging: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        when (index) {
            0 -> LiquidClock(now, h)
            1 -> DigitalClock(now, h)
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

/**
 * 리퀴드(GOAT 고유, 11-02 소장님: 액체자석 시계처럼). Zelf Koelman의 Ferrolic처럼 밝은 유리판 안의 검은 자성 액체 방울들이
 * 숫자 모양으로 모여 시각을 만들고, 분이 바뀌면 방울들이 흘러 새 숫자로 다시 모인다. 쓰지 않는 방울은 바닥에 고인다.
 * 방울은 점들을 흐리게 그린 뒤 알파를 날카롭게 잘라(goo) 서로 엉겨 붙는 액체처럼 보이게 한다(안드로이드 12+ RenderEffect).
 */
@Composable
private fun LiquidClock(now: LocalDateTime, h: Dp) {
    val context = LocalContext.current
    val text = hm(now)
    val drops = remember { FerroDrops(FERRO_COUNT) }
    // 분이 바뀌면 새 숫자 모양의 목표 점을 만든다
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
        val blur = android.graphics.RenderEffect.createBlurEffect(22f, 22f, android.graphics.Shader.TileMode.DECAL)
        val m = android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 22f, -9f * 255f))
        android.graphics.RenderEffect.createChainEffect(android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)), blur).asComposeRenderEffect()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9E8E4), Color(0xFFCFCFCB))))
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
            frame.let { }
            val sx = size.width
            val sy = size.height
            val r = sy * 0.019f
            for (i in 0 until drops.n) {
                drawCircle(Color(0xFF111112), r, Offset(drops.x[i] * sx, drops.y[i] * sy))
            }
        }
        // 유리판 반사: 위쪽에 옅은 빛 띠
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)))))
        Text("${dayKo(now)} · ${dateKo(now)}", color = Color(0x99000000), fontSize = h.ratio(46f), modifier = Modifier.align(Alignment.TopStart).padding(start = h * 0.09f, top = h * 0.07f))
    }
}

private const val FERRO_COUNT = 640

/** 자성 액체 방울들: 화면 비율 좌표(0~1). 목표 점으로 스프링처럼 끌려가고 조금씩 떨린다. 목표가 없는 방울은 바닥에 고인다. */
private class FerroDrops(val n: Int) {
    val x = FloatArray(n) { 0.1f + 0.8f * it / n }
    val y = FloatArray(n) { 0.97f }
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val tx = FloatArray(n) { 0.1f + 0.8f * it / n }
    private val ty = FloatArray(n) { 0.97f }
    private val seed = FloatArray(n) { (it * 2654435761L % 1000) / 1000f }

    fun retarget(pts: List<Pair<Float, Float>>) {
        for (i in 0 until n) {
            val p = pts.getOrNull(i)
            if (p != null) {
                tx[i] = p.first
                ty[i] = p.second
            } else {
                tx[i] = 0.06f + 0.88f * seed[i]
                ty[i] = 0.965f + 0.02f * seed[(i * 7) % n]
            }
        }
    }

    fun step(dt: Float, t: Float) {
        val k = 38f
        val damp = 7.5f
        for (i in 0 until n) {
            // 살아 있는 액체처럼 목표 둘레에서 아주 작게 떤다
            val wob = 0.0035f
            val gx = tx[i] + wob * kotlin.math.sin(t * 2.1f + seed[i] * 40f)
            val gy = ty[i] + wob * kotlin.math.cos(t * 1.7f + seed[i] * 31f)
            vx[i] += ((gx - x[i]) * k - vx[i] * damp) * dt
            vy[i] += ((gy - y[i]) * k - vy[i] * damp) * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }
}

/** [text] 모양의 목표 점 [n]개(비율 좌표). 숫자를 작은 그림에 그려 채워진 칸을 고르게 골라 쓴다. */
private fun ferroTargets(context: android.content.Context, text: String, n: Int): List<Pair<Float, Float>> {
    val bw = 240
    val bh = 150
    val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ALPHA_8)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.standby_inter_heavy)
        textSize = bh * 0.7f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    // 네 자리(15:21)도 화면 안에 들어가게 폭에 맞춰 줄인다
    val tw = paint.measureText(text)
    if (tw > bw * 0.86f) paint.textSize *= bw * 0.86f / tw
    val fm = paint.fontMetrics
    c.drawText(text, bw / 2f, bh / 2f - (fm.ascent + fm.descent) / 2f, paint)
    val filled = ArrayList<Pair<Float, Float>>()
    for (yy in 0 until bh step 2) {
        for (xx in 0 until bw step 2) {
            if ((bmp.getPixel(xx, yy) ushr 24) > 128) filled += (xx / bw.toFloat()) to (0.06f + 0.84f * yy / bh.toFloat())
        }
    }
    bmp.recycle()
    if (filled.isEmpty()) return emptyList()
    // 채워진 칸이 방울 수보다 많으면 고르게 건너뛰어 고른다(숫자 모양은 방울의 약 85%로, 나머지는 바닥에 고인다)
    val use = (n * 0.85f).toInt()
    val stride = filled.size / use.toFloat()
    return if (stride <= 1f) filled else List(use) { filled[(it * stride).toInt()] }
}

@Composable
private fun DigitalClock(now: LocalDateTime, h: Dp) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text("${dayKo(now).take(1)} · ${dateKo(now)}", color = Color(0xFF8C8C8C), fontSize = h.ratio(54f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = h * 0.09f, top = h * 0.07f))
        RollingText(hm(now), TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(640f), letterSpacing = (-0.02).sp), Color(0xFFF5F5F5), Modifier.align(Alignment.Center))
    }
}

@Composable
private fun FloatClock(now: LocalDateTime, h: Dp) {
    val digits = hm(now).replace(":", "")
    val grads = listOf(
        listOf(Color(0xFFFF8CAA), Color(0xFFFFC878)),
        listOf(Color(0xFF8CC8FF), Color(0xFFA08CFF)),
        listOf(Color(0xFF96FFC8), Color(0xFF5AD2FF)),
        listOf(Color(0xFFFFD36E), Color(0xFFFF7A7A))
    )
    Row(Modifier.fillMaxSize().background(Color(0xFF08080A)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        digits.forEachIndexed { i, ch ->
            key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { (scaleIn(spring(dampingRatio = 0.45f, stiffness = 260f), 0.6f) + fadeIn()) togetherWith (scaleOut(tween(220), 1.2f) + fadeOut(tween(220))) },
                    label = "float"
                ) { c ->
                    Text(
                        c.toString(),
                        style = TextStyle(fontFamily = OutfitBlack, fontSize = h.ratio(if (digits.length > 3) 600f else 700f), brush = Brush.verticalGradient(grads[i % grads.size])),
                        modifier = Modifier.padding(horizontal = h * 0.01f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SolarClock(now: LocalDateTime, h: Dp) {
    // 시각에 따라 해의 높이와 색이 바뀐다: 아침·저녁은 낮고 붉게, 낮에는 높고 밝게
    val m = now.hour * 60 + now.minute
    val day = ((m - 360) / 720f).coerceIn(0f, 1f)
    val lift = kotlin.math.sin(day * Math.PI).toFloat()
    Box(
        Modifier.fillMaxSize().background(Color(0xFF06040A)).drawWithContent {
            val c = Offset(size.width / 2, size.height * (1.25f - 0.45f * lift))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7828), Color(0x00FF7828)), c, size.height * 0.9f), size.height * 0.9f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC878), Color(0x00FFC878)), c, size.height * 0.45f), size.height * 0.45f, c)
            drawContent()
        }
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(hm(now), TextStyle(fontFamily = InterThin, fontSize = h.ratio(420f)), Color(0xFFFFF0E1))
            Text("${dayKo(now)} · ${dateKo(now)}", color = Color(0xFFFFBE96), fontSize = h.ratio(48f))
        }
    }
}

@Composable
private fun AnalogClock(now: LocalDateTime, h: Dp) {
    Row(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.fillMaxHeight().weight(1.15f).padding(h * 0.05f)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Color(0xFF181818), r, c)
            for (k in 0 until 60) {
                val a = Math.toRadians(k * 6.0 - 90)
                val major = k % 5 == 0
                val r1 = r - if (major) r * 0.13f else r * 0.06f
                drawLine(
                    if (major) Color(0xFFE6E6E6) else Color(0xFF787878),
                    Offset(c.x + r1 * kotlin.math.cos(a).toFloat(), c.y + r1 * kotlin.math.sin(a).toFloat()),
                    Offset(c.x + (r - r * 0.03f) * kotlin.math.cos(a).toFloat(), c.y + (r - r * 0.03f) * kotlin.math.sin(a).toFloat()),
                    if (major) r * 0.022f else r * 0.009f
                )
            }
            fun hand(deg: Float, len: Float, w: Float, col: Color, tail: Float = 0f) = rotate(deg, c) {
                drawLine(col, Offset(c.x, c.y + tail), Offset(c.x, c.y - len), w, StrokeCap.Round)
            }
            val sec = now.second
            hand((now.hour % 12 + now.minute / 60f) * 30f, r * 0.55f, r * 0.047f, Color(0xFFF0F0F0))
            hand((now.minute + sec / 60f) * 6f, r * 0.83f, r * 0.03f, Color(0xFFF0F0F0))
            hand(sec * 6f, r * 0.89f, r * 0.011f, Color(0xFFFFC800), r * 0.17f)
            drawCircle(Color(0xFFFFC800), r * 0.04f, c)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayKo(now), color = Color(0xFFF0F0F0), fontSize = h.ratio(110f), fontWeight = FontWeight.Bold)
            Text(dateKo(now), color = Color(0xFF969696), fontSize = h.ratio(80f))
        }
    }
}

@Composable
private fun DotClock(now: LocalDateTime, h: Dp) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RollingText("%02d:%02d".format(now.hour, now.minute), TextStyle(fontFamily = Doto, fontSize = h.ratio(520f)), Color(0xFFEBEBEB), Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.TopEnd).padding(top = h * 0.1f, end = h * 0.11f).size(h * 0.032f).clip(CircleShape).background(Color(0xFFE61E1E)))
        val en = now.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase() + " %02d ".format(now.dayOfMonth) +
            now.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase()
        Text(en, color = Color(0xFF6E6E6E), style = TextStyle(fontFamily = Doto, fontSize = h.ratio(70f)), modifier = Modifier.align(Alignment.BottomStart).padding(start = h * 0.1f, bottom = h * 0.08f))
    }
}

@Composable
private fun FlipClock(now: LocalDateTime, h: Dp) {
    Row(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(horizontal = h * 0.1f, vertical = h * 0.14f), horizontalArrangement = Arrangement.spacedBy(h * 0.05f)) {
        listOf("%02d".format(now.hour), "%02d".format(now.minute)).forEach { t ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(h * 0.037f))) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF262628)))
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF1E1E20)))
                }
                AnimatedContent(
                    targetState = t,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 200)) + scaleIn(tween(420), initialScale = 1f, transformOrigin = TransformOrigin(0.5f, 0.5f))) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1f))
                    },
                    modifier = Modifier.align(Alignment.Center),
                    label = "flip"
                ) { v ->
                    val flip = remember { Animatable(-90f) }
                    LaunchedEffect(v) {
                        flip.snapTo(-90f)
                        flip.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                    }
                    Text(
                        v,
                        color = Color(0xFFEEEEEE),
                        style = TextStyle(fontFamily = Bebas, fontSize = h.ratio(760f), textAlign = TextAlign.Center),
                        modifier = Modifier.graphicsLayer {
                            rotationX = flip.value
                            cameraDistance = 24f * density
                        }
                    )
                }
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(h * 0.005f).background(Color(0xFF050505)))
            }
        }
    }
}

@Composable
private fun NightClock(now: LocalDateTime, h: Dp) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RollingText(hm(now), TextStyle(fontFamily = InterHeavy, fontSize = h.ratio(640f)), Color(0xFF960A0A), Modifier.align(Alignment.Center))
    }
}

@Composable
private fun WidgetClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    val pad = h * 0.048f
    Row(Modifier.fillMaxSize().background(Color.Black).padding(pad), horizontalArrangement = Arrangement.spacedBy(pad * 0.7f)) {
        Column(Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(h * 0.072f)).background(Color(0xFF1A1A1C)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(hm(now), TextStyle(fontFamily = InterBold, fontSize = h.ratio(400f)), Color(0xFFF5F5F5))
            Text("${dayKo(now)} ${dateKo(now)}", color = Color(0xFF969696), fontSize = h.ratio(60f))
        }
        Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(pad * 0.7f)) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.064f)).background(Color(0xFF1E3C6E)).padding(h * 0.04f)) {
                Text("${now.monthValue}월", color = Color(0xFFD2E1FF), fontSize = h.ratio(54f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${now.dayOfMonth}", color = Color.White, style = TextStyle(fontFamily = InterThin, fontSize = h.ratio(200f)))
            }
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(h * 0.064f)).background(Color(0xFF183C24)).padding(h * 0.04f)) {
                Text(if (charging) "충전 중" else "배터리", color = Color(0xFFB4F0BE), fontSize = h.ratio(54f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(battery?.let { "$it%" } ?: "-", color = Color.White, style = TextStyle(fontFamily = InterThin, fontSize = h.ratio(200f)))
                Box(Modifier.fillMaxWidth().height(h * 0.022f).clip(RoundedCornerShape(50)).background(Color(0xFF285A37))) {
                    Box(Modifier.fillMaxWidth((battery ?: 0) / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(0xFF5AE678)))
                }
            }
        }
    }
}

@Composable
private fun SerifClock(now: LocalDateTime, h: Dp) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF2EEE6)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        RollingText(hm(now), TextStyle(fontFamily = Fraunces, fontSize = h.ratio(600f)), Color(0xFF181614))
        Box(Modifier.width(h * 1.1f).height(1.dp).background(Color(0xFF181614)))
        Spacer(Modifier.height(h * 0.04f))
        val en = now.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase() + " · " +
            now.month.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase() + " ${now.dayOfMonth}"
        Text(en, color = Color(0xFF5A5550), style = TextStyle(fontFamily = InterBold, fontSize = h.ratio(46f), letterSpacing = 2.sp))
    }
}

@Composable
private fun RingClock(now: LocalDateTime, h: Dp, battery: Int?, charging: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(h * 0.84f)) {
            val w = size.minDimension * 0.013f
            drawCircle(Color(0xFF282828), size.minDimension / 2 - w, style = Stroke(w))
            drawArc(Color(0xFFF0F0F0), -90f, 360f * now.second / 60f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(hm(now), TextStyle(fontFamily = ManropeThin, fontSize = h.ratio(330f)), Color(0xFFF5F5F5))
            Text((if (charging) "충전 " else "") + (battery?.let { "$it%" } ?: ""), color = Color(0xFF82DC8C), fontSize = h.ratio(52f))
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
