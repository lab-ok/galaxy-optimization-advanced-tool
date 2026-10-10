package com.local.folddpifix.ui.appsize

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.folddpifix.domain.AppDensityPolicy
import com.local.folddpifix.ui.art.LocalDeviceShape
import com.local.folddpifix.ui.art.ArtCanvas
import com.local.folddpifix.ui.art.artLabel
import com.local.folddpifix.ui.art.artLoop
import com.local.folddpifix.ui.art.artPill
import com.local.folddpifix.ui.art.deviceFrame
import com.local.folddpifix.ui.liquid.LiquidColors
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.sin

/*
 * '앱별 화면 배율 설정' 사용 방법 그림. 모두 앱 공통 기기 틀(deviceFrame) 위에 그리고, 하나의 진행값 t(0→1 반복)로 움직인다.
 * 동작 줄이기가 켜져 있으면 t = 1(끝 장면)로 멈춘다. 차이가 눈에 보이도록 실제보다 과장해서 그린다.
 */

/** 앱 화면 내용(제목 막대·사진·글자 줄)을 배율 [k]로 그린다. 배율이 클수록 내용이 크고 적게 들어간다. */
private fun DrawScope.appContent(area: Rect, k: Float, ink: Color) {
    val pad = area.width * 0.08f
    val unit = area.width * 0.11f * k
    var y = area.top + pad
    drawRoundRect(ink.copy(alpha = 0.75f), Offset(area.left + pad, y), Size(area.width * 0.55f * k.coerceAtMost(1.4f), unit * 0.55f), CornerRadius(unit * 0.25f))
    y += unit * 0.55f + pad * 0.8f
    val photoH = unit * 2.2f
    if (y + photoH < area.bottom - pad) {
        drawRoundRect(ink.copy(alpha = 0.18f), Offset(area.left + pad, y), Size(area.width - pad * 2, photoH), CornerRadius(unit * 0.3f))
        y += photoH + pad * 0.8f
    }
    var i = 0
    while (y + unit * 0.4f < area.bottom - pad) {
        val w = (area.width - pad * 2) * (if (i % 3 == 2) 0.6f else 1f)
        drawRoundRect(ink.copy(alpha = 0.45f), Offset(area.left + pad, y), Size(w, unit * 0.38f), CornerRadius(unit * 0.19f))
        y += unit * 0.38f + unit * 0.42f
        i++
    }
}

/** 기기 그림 하나(외부 화면 비율). 화면 영역을 돌려준다. */
private fun DrawScope.phone(c: LiquidColors, center: Offset, height: Float, aspect: Float, emphasis: Float = 1f): Rect {
    val w = height * aspect
    return deviceFrame(c, Offset(center.x - w / 2, center.y - height / 2), Size(w, height), emphasis = emphasis)
}

/** 1. 앱마다 따로: 왼쪽 앱은 배율이 커졌다 작아지고, 오른쪽 앱은 기본 그대로. */
@Composable
internal fun AppScaleArt() {
    val t = artLoop(2600, reverse = true)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(160.dp) { c, tm ->
        val h = size.height * 0.78f
        val left = phone(c, Offset(size.width * 0.3f, h / 2 + 4.dp.toPx()), h, aspect)
        val right = phone(c, Offset(size.width * 0.7f, h / 2 + 4.dp.toPx()), h, aspect)
        val k = 0.8f + 0.55f * t
        appContent(left, k, c.ink)
        appContent(right, 1f, c.ink)
        val dpi = AppDensityPolicy.STEPS[((t * 5.99f).toInt()).coerceIn(0, 5)]
        artPill(tm, c, "앱 A · $dpi", Offset(size.width * 0.3f, size.height - 8.dp.toPx()), true)
        artPill(tm, c, "앱 B · 기본", Offset(size.width * 0.7f, size.height - 8.dp.toPx()), false)
    }
}

/** 2. Shizuku 다리: FoldFit → Shizuku → 시스템. 점이 화살표를 따라 흘러간다. */
@Composable
internal fun ShizukuBridgeArt() {
    val t = artLoop(2200)
    ArtCanvas(120.dp) { c, tm ->
        val y = size.height * 0.42f
        val xs = listOf(size.width * 0.17f, size.width * 0.5f, size.width * 0.83f)
        val box = size.height * 0.42f
        val names = listOf("FoldFit", "Shizuku", "시스템")
        val subs = listOf("크기 바꾸기 요청", "셸 권한 빌려줌", "앱 크기 저장")
        // 화살표
        for (i in 0..1) {
            val a = xs[i] + box / 2 + 6.dp.toPx(); val b = xs[i + 1] - box / 2 - 6.dp.toPx()
            drawLine(c.line, Offset(a, y), Offset(b, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c.line, Offset(b, y), Offset(b - 8.dp.toPx(), y - 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c.line, Offset(b, y), Offset(b - 8.dp.toPx(), y + 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        }
        // 흘러가는 점(앞 절반은 첫 화살표, 뒤 절반은 둘째 화살표)
        val seg = if (t < 0.5f) 0 else 1
        val k = (if (t < 0.5f) t else t - 0.5f) * 2f
        val a = xs[seg] + box / 2; val b = xs[seg + 1] - box / 2
        drawCircle(c.ink, 6.dp.toPx(), Offset(a + (b - a) * k, y))
        xs.forEachIndexed { i, x ->
            val active = (i == 0 && t < 0.15f) || (i == 1 && t in 0.45f..0.6f) || (i == 2 && t > 0.92f)
            drawRoundRect(if (active) c.ink else c.surface, Offset(x - box / 2, y - box / 2), Size(box, box), CornerRadius(box * 0.28f))
            drawRoundRect(c.ink, Offset(x - box / 2, y - box / 2), Size(box, box), CornerRadius(box * 0.28f), style = Stroke(2.dp.toPx()))
            artLabel(tm, names[i].take(1), Offset(x, y), if (active) c.onInk else c.ink, 18, bold = true)
            artLabel(tm, names[i], Offset(x, y + box / 2 + 12.dp.toPx()), c.ink, 11, bold = true)
            artLabel(tm, subs[i], Offset(x, y + box / 2 + 26.dp.toPx()), c.muted, 9)
        }
    }
}

/** 3. 바꿀 때만 필요: Shizuku 스위치가 꺼져도 앱 배율 값은 그대로 남는다. */
@Composable
internal fun PersistArt() {
    val t = artLoop(3000)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(140.dp) { c, tm ->
        // 왼쪽: Shizuku 스위치(앞 40%는 켜짐, 이후 꺼짐)
        val on = t < 0.4f
        val sw = Size(54.dp.toPx(), 30.dp.toPx())
        val sc = Offset(size.width * 0.28f, size.height * 0.4f)
        drawRoundRect(if (on) c.ink else c.line, Offset(sc.x - sw.width / 2, sc.y - sw.height / 2), sw, CornerRadius(sw.height / 2))
        val knobX = if (on) sc.x + sw.width / 2 - sw.height / 2 else sc.x - sw.width / 2 + sw.height / 2
        drawCircle(if (on) c.onInk else c.ink, sw.height * 0.36f, Offset(knobX, sc.y))
        artLabel(tm, if (on) "Shizuku 켜짐" else "Shizuku 꺼짐", Offset(sc.x, sc.y + 30.dp.toPx()), c.ink, 11, bold = true)
        artLabel(tm, if (on) "→ 값을 바꿀 수 있음" else "→ 바꾸기만 쉼", Offset(sc.x, sc.y + 46.dp.toPx()), c.muted, 9)
        // 오른쪽: 앱 배율 값은 계속 유지(체크 표시)
        val h = size.height * 0.8f
        val screen = phone(c, Offset(size.width * 0.72f, size.height / 2), h, aspect)
        appContent(screen, 1.25f, c.ink)
        artPill(tm, c, "480 유지", Offset(screen.center.x, screen.bottom - 12.dp.toPx()), true)
    }
}

/** 4. 여섯 단계: 물방울이 여섯 칸 사이를 옮겨 다니고, 칸 사이 값(예: 400)은 튕겨 나간다. */
@Composable
internal fun StepsArt() {
    val t = artLoop(4200)
    ArtCanvas(110.dp) { c, tm ->
        val steps = AppDensityPolicy.STEPS
        val left = size.width * 0.08f; val right = size.width * 0.92f
        val y = size.height * 0.45f
        val xOf = { i: Float -> left + (right - left) * i / (steps.size - 1) }
        drawLine(c.line, Offset(left, y), Offset(right, y), 6.dp.toPx(), StrokeCap.Round)
        steps.forEachIndexed { i, v ->
            drawCircle(c.line, 5.dp.toPx(), Offset(xOf(i.toFloat()), y))
            artLabel(tm, "$v", Offset(xOf(i.toFloat()), y + 20.dp.toPx()), c.muted, 10)
        }
        // 0~0.75: 칸에서 칸으로 이동(멈췄다 가기), 0.75~1: '400'을 넣었다가 튕김
        if (t < 0.75f) {
            val p = t / 0.75f * (steps.size - 1)
            val i = p.toInt(); val f = p - i
            val ease = if (f < 0.55f) 0f else ((f - 0.55f) / 0.45f).let { it * it * (3 - 2 * it) }
            val x = xOf(i + ease)
            val stretch = 1f + 0.6f * sin(ease * Math.PI.toFloat())
            val r = 9.dp.toPx()
            drawOval(c.ink, Offset(x - r * stretch, y - r / stretch.coerceAtLeast(1f)), Size(2 * r * stretch, 2 * r / stretch.coerceAtLeast(1f)))
        } else {
            val k = (t - 0.75f) / 0.25f
            val x400 = xOf(1f + (400f - 360f) / (420f - 360f))
            val bounce = abs(sin(k * Math.PI.toFloat() * 2)) * (1f - k)
            drawCircle(c.danger, 9.dp.toPx(), Offset(x400, y - 26.dp.toPx() * bounce - 6.dp.toPx()))
            artLabel(tm, "400 ✕", Offset(x400, y - 26.dp.toPx() * bounce - 26.dp.toPx()), c.danger, 10, bold = true)
            drawCircle(c.ink, 9.dp.toPx(), Offset(xOf(5f), y))
        }
    }
}

/** 5. 접고 펼 때: 같은 숫자(420)라도 외부·내부 화면에서 실제 크기가 다르게 보인다. */
@Composable
internal fun FoldDiffArt() {
    val t = artLoop(2600, reverse = true)
    val shape = LocalDeviceShape.current
    ArtCanvas(160.dp) { c, tm ->
        val h = size.height * 0.72f
        val coverW = h * shape.cover; val innerW = h * shape.inner
        val gap = size.width * 0.06f
        val start = (size.width - coverW - gap - innerW) / 2
        val cover = deviceFrame(c, Offset(start, 6.dp.toPx()), Size(coverW, h), emphasis = 0.5f + 0.5f * (1 - t))
        val inner = deviceFrame(c, Offset(start + coverW + gap, 6.dp.toPx()), Size(innerW, h), emphasis = 0.5f + 0.5f * t)
        // 같은 DPI라도 픽셀 밀도가 다른 화면에서는 실제 크기가 다르다(차이를 과장)
        appContent(cover, 1.15f, c.ink)
        // 내부 화면은 넓어서 두 단으로 채운다(같은 숫자인데 내용이 더 작게 보임)
        appContent(Rect(inner.left, inner.top, inner.center.x, inner.bottom), 0.85f, c.ink)
        appContent(Rect(inner.center.x, inner.top, inner.right, inner.bottom), 0.85f, c.ink)
        artPill(tm, c, "외부 420", Offset(cover.center.x, size.height - 12.dp.toPx()), t < 0.5f)
        artPill(tm, c, "내부 420", Offset(inner.center.x, size.height - 12.dp.toPx()), t >= 0.5f)
    }
}

/** 6. 알림창에서: 손가락이 [크게]를 누르면 알림 글자와 앱 화면이 함께 커진다. */
@Composable
internal fun NotifyArt() {
    val t = artLoop(3200)
    ArtCanvas(150.dp) { c, tm ->
        val pressed = t > 0.45f
        val cardW = size.width * 0.86f; val cardH = 78.dp.toPx()
        val tl = Offset((size.width - cardW) / 2, 8.dp.toPx())
        drawRoundRect(c.surface, tl, Size(cardW, cardH), CornerRadius(18.dp.toPx()))
        drawRoundRect(c.line, tl, Size(cardW, cardH), CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        val textLeft = tl.x + 16.dp.toPx()
        val tTitle = tm.measure("FoldFit · 앱 화면 크기", TextStyle(color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        drawText(tTitle, topLeft = Offset(textLeft, tl.y + 10.dp.toPx()))
        val tBody = tm.measure(if (pressed) "Discord · 480 dpi" else "Discord · 기본(450)", TextStyle(color = c.muted, fontSize = 10.sp))
        drawText(tBody, topLeft = Offset(textLeft, tl.y + 26.dp.toPx()))
        val labels = listOf("작게", "기본", "크게")
        val bw = (cardW - 32.dp.toPx()) / 3
        labels.forEachIndexed { i, s ->
            val cx = textLeft + bw * i + bw / 2
            val by = tl.y + cardH - 18.dp.toPx()
            if (i == 2 && t in 0.38f..0.6f) drawRoundRect(c.ink.copy(alpha = 0.12f), Offset(cx - bw / 2 + 4.dp.toPx(), by - 12.dp.toPx()), Size(bw - 8.dp.toPx(), 24.dp.toPx()), CornerRadius(12.dp.toPx()))
            artLabel(tm, s, Offset(cx, by), c.ink, 11, bold = true)
        }
        // 손가락(원): 아래에서 [크게]로 올라와 누르고 내려간다
        val target = Offset(textLeft + bw * 2 + bw / 2, tl.y + cardH - 18.dp.toPx())
        val rest = Offset(target.x + 30.dp.toPx(), size.height - 10.dp.toPx())
        val k = when {
            t < 0.35f -> t / 0.35f
            t < 0.6f -> 1f
            else -> 1f - (t - 0.6f) / 0.4f
        }.let { it * it * (3 - 2 * it) }
        val finger = Offset(rest.x + (target.x - rest.x) * k, rest.y + (target.y - rest.y) * k)
        val r = (if (t in 0.4f..0.5f) 9.dp else 11.dp).toPx()
        drawCircle(c.ink.copy(alpha = 0.25f), r + 4.dp.toPx(), finger)
        drawCircle(c.ink.copy(alpha = 0.55f), r, finger)
        // 아래: 앱 글자가 누른 뒤 커진다
        artLabel(tm, "앱 글자 가나다 Aa", Offset(size.width * 0.4f, tl.y + cardH + 26.dp.toPx()), c.ink, if (pressed) 17 else 12, bold = true)
    }
}

/** 7. 되돌리기: 정해 둔 값들이 차례로 '기본'으로 바뀐다. */
@Composable
internal fun ResetArt() {
    val t = artLoop(3000)
    ArtCanvas(120.dp) { c, tm ->
        val rows = listOf("카카오톡" to "420", "Chrome" to "360", "X" to "480")
        val rowH = size.height / 3.4f
        rows.forEachIndexed { i, (name, v) ->
            val y = 6.dp.toPx() + i * (rowH + 4.dp.toPx())
            val tl = Offset(size.width * 0.1f, y)
            val w = size.width * 0.8f
            drawRoundRect(c.surface, tl, Size(w, rowH), CornerRadius(rowH / 2.6f))
            drawRoundRect(c.line, tl, Size(w, rowH), CornerRadius(rowH / 2.6f), style = Stroke(1.dp.toPx()))
            drawRoundRect(c.ink.copy(alpha = 0.25f), Offset(tl.x + 10.dp.toPx(), y + rowH * 0.2f), Size(rowH * 0.6f, rowH * 0.6f), CornerRadius(rowH * 0.18f))
            val nl = tm.measure(name, TextStyle(color = c.ink, fontSize = 11.sp))
            drawText(nl, topLeft = Offset(tl.x + 16.dp.toPx() + rowH * 0.6f, y + (rowH - nl.size.height) / 2))
            val reset = t > 0.25f + i * 0.18f
            artPill(tm, c, if (reset) "기본" else v, Offset(tl.x + w - 30.dp.toPx(), y + rowH / 2), !reset)
        }
    }
}
