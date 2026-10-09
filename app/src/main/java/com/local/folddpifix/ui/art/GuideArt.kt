package com.local.folddpifix.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.folddpifix.ui.liquid.LiquidColors
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 권한 설정 안내 단계 장면 종류. */
enum class GuideScene { DEV_OPTIONS, INSTALL, USB_CONNECT, PAIR, WIRELESS_CONNECT, COMMAND, DONE }

/**
 * 권한 설정 단계마다 보여 주는 짧은 반복 장면. 모두 공통 기기 틀(DeviceFrame) 위에 그리고,
 * 한 번 돌 때(약 4초) 여러 구간으로 나눠 '무엇을 하면 어떻게 되는지'를 순서대로 보여 준 뒤 잠깐 멈춘다.
 * - DEV_OPTIONS: 손가락이 '빌드번호'를 7번 톡톡, 남은 횟수가 줄고 → 알림 → USB 디버깅 스위치가 켜진다
 * - INSTALL: 브라우저에서 내려받기 → 진행 막대 → platform-tools 폴더와 체크
 * - USB_CONNECT: 케이블이 꽂히고 폰에 'USB 디버깅 허용' 창 → 손가락이 허용 → 체크
 * - PAIR: 폰에 6자리 코드가 하나씩 → PC에서 adb pair 입력 → 두 기기가 이어짐
 * - WIRELESS_CONNECT: 와이파이 신호가 오가고 PC 터미널에 connected가 찍힘
 * - COMMAND: 명령이 타이핑되고 Enter → 열쇠가 PC에서 폰으로 날아가 자물쇠가 열림
 * - DONE: 체크 방울이 출렁이고 주변 방울이 모여든다
 */
@Composable
fun GuideSceneArt(scene: GuideScene, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val t0 by rememberInfiniteTransition(label = "scene").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    // 애니메이션 제거 설정이면 '결과' 장면에서 멈춘다.
    val t = if (reduce) 0.92f else t0
    val measurer = rememberTextMeasurer()
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = c.ink, fontSize = 9.sp)
    val ui = MaterialTheme.typography.labelSmall.copy(color = c.ink)
    Canvas(modifier.fillMaxWidth().height(124.dp)) {
        val k = SceneKit(c, measurer, mono, ui)
        when (scene) {
            GuideScene.DEV_OPTIONS -> k.devOptions(this, t)
            GuideScene.INSTALL -> k.install(this, t)
            GuideScene.USB_CONNECT -> k.usbConnect(this, t)
            GuideScene.PAIR -> k.pair(this, t)
            GuideScene.WIRELESS_CONNECT -> k.wirelessConnect(this, t)
            GuideScene.COMMAND -> k.command(this, t)
            GuideScene.DONE -> k.done(this, t)
        }
    }
}

/** [a]~[b] 구간 안에서의 진행(0~1), 부드럽게. */
private fun phase(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

private class SceneKit(
    val c: LiquidColors,
    val measurer: TextMeasurer,
    val mono: TextStyle,
    val ui: TextStyle,
) {
    // ---- 공통 부품 ----

    /** 세로 폰. 화면 영역을 돌려준다. */
    fun DrawScope.phone(center: Offset, h: Float): Rect {
        val w = h * FoldGeometry.COVER_ASPECT
        return deviceFrame(c, Offset(center.x - w / 2, center.y - h / 2), Size(w, h))
    }

    /** 가운데로 모은 PC + 간격 + 폰. (모니터 화면, 폰 화면) */
    fun DrawScope.pcAndPhone(pcRatio: Float = 0.95f, gapRatio: Float = 0.62f): Pair<Rect, Rect> {
        val h = size.height
        val pcW = h * pcRatio
        val phH = h * 0.86f
        val phW = phH * FoldGeometry.COVER_ASPECT
        val gap = h * gapRatio
        val left = (size.width - (pcW + gap + phW)) / 2
        val pcScreen = monitorFrame(c, Offset(left + pcW / 2, h / 2), pcW)
        val ph = phone(Offset(left + pcW + gap + phW / 2, h / 2), phH)
        return pcScreen to ph
    }

    /** 손가락 끝(누르는 동그라미 + 퍼지는 고리). [press] 0~1. */
    fun DrawScope.finger(at: Offset, press: Float, visible: Float = 1f) {
        if (visible <= 0f) return
        val r = 7.dp.toPx()
        if (press > 0f) drawCircle(c.ink.copy(alpha = 0.30f * (1f - press) * visible), r + 12.dp.toPx() * press, at, style = Stroke(2.dp.toPx()))
        drawCircle(c.ink.copy(alpha = 0.18f * visible), r * 1.5f, at)
        drawCircle(c.ink.copy(alpha = 0.85f * visible), r * (1f - 0.18f * press), at)
    }

    fun DrawScope.text(s: String, at: Offset, style: TextStyle, center: Boolean = true, alpha: Float = 1f) {
        if (alpha <= 0f) return
        val l = measurer.measure(s, style.copy(color = style.color.copy(alpha = style.color.alpha * alpha)))
        drawText(l, topLeft = if (center) Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f) else Offset(at.x, at.y - l.size.height / 2f))
    }

    fun DrawScope.bar(x: Float, y: Float, w: Float, alpha: Float, hDp: Float = 4f) {
        if (w <= 0f || alpha <= 0f) return
        val hh = hDp.dp.toPx()
        drawRoundRect(c.ink.copy(alpha = alpha), Offset(x, y - hh / 2), Size(w, hh), CornerRadius(hh / 2))
    }

    fun DrawScope.check(center: Offset, r: Float, p: Float) {
        if (p <= 0f) return
        val rr = r * (0.6f + 0.4f * p)
        drawCircle(c.ink.copy(alpha = p), rr, center)
        val s = rr * 0.5f
        val col = c.onInk.copy(alpha = p)
        drawLine(col, Offset(center.x - s, center.y), Offset(center.x - s * 0.2f, center.y + s * 0.7f), rr * 0.22f, StrokeCap.Round)
        drawLine(col, Offset(center.x - s * 0.2f, center.y + s * 0.7f), Offset(center.x + s, center.y - s * 0.6f), rr * 0.22f, StrokeCap.Round)
    }

    /** 스위치(꺼짐 0 → 켜짐 1). [at]은 왼쪽 가운데. */
    fun DrawScope.toggle(at: Offset, w: Float, on: Float) {
        val h = w * 0.56f
        drawRoundRect(c.ink.copy(alpha = 0.18f + 0.72f * on), Offset(at.x, at.y - h / 2), Size(w, h), CornerRadius(h / 2))
        drawCircle(c.surface, h * 0.38f, Offset(at.x + h / 2 + (w - h) * on, at.y))
    }

    /**
     * 터미널 줄들(위쪽부터). [lines]는 (글자, 진행 0~1).
     * 가장 긴 줄이 화면 폭 안에 들어오도록 글자 크기를 함께 줄이고, 화면 밖으로는 그리지 않는다.
     */
    fun DrawScope.terminal(screen: Rect, lines: List<Pair<String, Float>>, cursorBlink: Boolean) {
        val pad = screen.width * 0.08f
        val maxW = screen.width - pad * 2
        val widest = lines.maxOfOrNull { (s, _) -> measurer.measure(s, mono).size.width } ?: 0
        val scale = if (widest > maxW) maxW / widest else 1f
        val style = mono.copy(fontSize = mono.fontSize * scale)
        var y = screen.top + screen.height * 0.22f
        val step = screen.height * 0.24f
        clipRect(screen.left, screen.top, screen.right, screen.bottom) {
            lines.forEach { (s, p) ->
                if (p > 0f) text(s.take((s.length * p).toInt().coerceAtLeast(1)), Offset(screen.left + pad, y), style, center = false)
                y += step
            }
            if (cursorBlink) drawRect(c.ink, Offset(screen.left + pad, y - 1.dp.toPx()), Size(6.dp.toPx(), 2.dp.toPx()))
        }
    }

    private fun blink(t: Float) = (t * 8).toInt() % 2 == 0

    // ---- 장면 ----

    fun devOptions(d: DrawScope, t: Float) = with(d) {
        val h = size.height
        val screen = phone(Offset(size.width / 2, h / 2), h * 0.96f)
        val pad = screen.width * 0.12f
        val rowH = screen.height * 0.17f
        fun rowY(i: Int) = screen.top + screen.height * 0.18f + rowH * i
        val taps = phase(t, 0.05f, 0.55f)
        val tapCount = (taps * 7).toInt().coerceAtMost(7)
        val debugOn = phase(t, 0.72f, 0.8f)
        // 설정 목록 4줄: 2번째 줄이 '빌드번호', 4번째 줄이 'USB 디버깅'
        for (i in 0 until 4) {
            drawCircle(c.ink.copy(alpha = 0.25f), 3.5.dp.toPx(), Offset(screen.left + pad, rowY(i)))
            bar(screen.left + pad * 2.1f, rowY(i), screen.width * (if (i == 3) 0.34f else 0.5f), if (i == 1) 0.8f else 0.28f)
        }
        toggle(Offset(screen.right - pad - 18.dp.toPx(), rowY(3)), 18.dp.toPx(), debugOn)
        // 빌드번호를 7번 톡톡
        val tapping = t in 0.05f..0.55f
        finger(
            Offset(screen.center.x + screen.width * 0.1f, rowY(1) + 2.dp.toPx()),
            if (tapping) (taps * 7) % 1f else 0f,
            visible = 1f - phase(t, 0.58f, 0.64f),
        )
        // 남은 횟수 방울
        val left = 7 - tapCount
        if (t < 0.58f && left > 0) {
            val b = Offset(screen.right + 16.dp.toPx(), screen.top + 10.dp.toPx())
            drawCircle(c.ink, 11.dp.toPx(), b)
            text("$left", b, ui.copy(color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 12.sp))
        }
        // '개발자 모드' 알림 알약
        val toast = phase(t, 0.56f, 0.62f) * (1f - phase(t, 0.9f, 0.97f))
        if (toast > 0f) {
            val w = screen.width * 0.8f
            val ph = 14.dp.toPx()
            val y = screen.bottom - ph - 6.dp.toPx() + (1f - toast) * 8.dp.toPx()
            drawRoundRect(c.ink.copy(alpha = toast), Offset(screen.center.x - w / 2, y), Size(w, ph), CornerRadius(ph / 2))
            drawRoundRect(c.onInk.copy(alpha = 0.8f * toast), Offset(screen.center.x - w * 0.3f, y + ph / 2 - 1.5.dp.toPx()), Size(w * 0.6f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        // USB 디버깅 스위치를 켜는 손가락
        finger(
            Offset(screen.right - pad - 9.dp.toPx(), rowY(3) + 2.dp.toPx()),
            if (t in 0.7f..0.8f) phase(t, 0.7f, 0.8f) else 0f,
            visible = phase(t, 0.64f, 0.7f) * (1f - phase(t, 0.86f, 0.92f)),
        )
    }

    fun install(d: DrawScope, t: Float) = with(d) {
        val h = size.height
        val screen = monitorFrame(c, Offset(size.width / 2, h / 2), h * 1.35f)
        // 브라우저 상단 막대(점 3개 + 주소창)
        val top = screen.top + 9.dp.toPx()
        repeat(3) { i -> drawCircle(c.ink.copy(alpha = 0.3f), 2.5.dp.toPx(), Offset(screen.left + 10.dp.toPx() + i * 8.dp.toPx(), top)) }
        drawRoundRect(c.ink.copy(alpha = 0.1f), Offset(screen.left + 38.dp.toPx(), top - 4.dp.toPx()), Size(screen.width * 0.55f, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawLine(c.ink.copy(alpha = 0.12f), Offset(screen.left, top + 9.dp.toPx()), Offset(screen.right, top + 9.dp.toPx()), 1.dp.toPx())
        val cx = screen.center.x
        val base = screen.top + screen.height * 0.78f
        // 1) 내려받기 화살표가 떨어진다
        val drop = phase(t, 0.05f, 0.33f)
        val gone = phase(t, 0.34f, 0.4f)
        if (gone < 1f) {
            val y = screen.top + screen.height * 0.36f + screen.height * 0.24f * drop
            val a = 1f - gone
            val s = 7.dp.toPx()
            drawLine(c.ink.copy(alpha = a), Offset(cx, y - 14.dp.toPx()), Offset(cx, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c.ink.copy(alpha = a), Offset(cx - s, y - s), Offset(cx, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(c.ink.copy(alpha = a), Offset(cx + s, y - s), Offset(cx, y), 3.dp.toPx(), StrokeCap.Round)
        }
        // 2) 진행 막대
        val prog = phase(t, 0.36f, 0.7f)
        val bw = screen.width * 0.6f
        drawRoundRect(c.ink.copy(alpha = 0.1f), Offset(cx - bw / 2, base - 3.dp.toPx()), Size(bw, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        if (prog > 0f) drawRoundRect(c.ink, Offset(cx - bw / 2, base - 3.dp.toPx()), Size(bw * prog, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        // 3) platform-tools 폴더 + 체크
        val folder = phase(t, 0.72f, 0.82f)
        if (folder > 0f) {
            val fw = 28.dp.toPx() * folder
            val fh = 19.dp.toPx() * folder
            val fy = screen.top + screen.height * 0.5f
            drawRoundRect(c.ink.copy(alpha = 0.85f), Offset(cx - fw / 2, fy - fh / 2 - 4.dp.toPx() * folder), Size(fw * 0.45f, 7.dp.toPx() * folder), CornerRadius(2.dp.toPx()))
            drawRoundRect(c.ink.copy(alpha = 0.85f), Offset(cx - fw / 2, fy - fh / 2), Size(fw, fh), CornerRadius(3.dp.toPx()))
            check(Offset(cx + fw / 2 + 2.dp.toPx(), fy + fh / 2), 8.dp.toPx(), phase(t, 0.82f, 0.88f))
        }
    }

    fun usbConnect(d: DrawScope, t: Float) = with(d) {
        val (pc, ph) = pcAndPhone()
        // 케이블: PC 오른쪽 아래 → 폰 아래 단자. 처음에 나타나며 꽂힌다.
        val plug = phase(t, 0.02f, 0.2f)
        val a = Offset(pc.right - pc.width * 0.12f, pc.bottom + size.height * 0.15f)
        val b = Offset(ph.center.x, ph.bottom + size.height * 0.05f)
        val ctrl = Offset((a.x + b.x) / 2, size.height * 1.02f)
        val path = Path().apply { moveTo(a.x, a.y); quadraticBezierTo(ctrl.x, ctrl.y, b.x, b.y) }
        drawPath(path, c.ink.copy(alpha = 0.5f * plug), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawRoundRect(c.ink.copy(alpha = plug), Offset(b.x - 5.dp.toPx(), b.y - 3.dp.toPx() + (1 - plug) * 6.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()), CornerRadius(2.dp.toPx()))
        // PC: adb devices → device
        terminal(pc, listOf("adb devices" to phase(t, 0.06f, 0.22f), "… device" to phase(t, 0.7f, 0.78f)), cursorBlink = blink(t))
        // 폰: 'USB 디버깅 허용?' 창 → [허용] 탭 → 체크
        val dialog = phase(t, 0.25f, 0.32f) * (1f - phase(t, 0.62f, 0.68f))
        if (dialog > 0f) {
            val w = ph.width * 0.86f
            val dh = ph.height * 0.46f
            val top = ph.center.y - dh / 2
            drawRoundRect(c.surface, Offset(ph.center.x - w / 2, top), Size(w, dh), CornerRadius(5.dp.toPx()))
            drawRoundRect(c.ink.copy(alpha = 0.7f * dialog), Offset(ph.center.x - w / 2, top), Size(w, dh), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
            bar(ph.center.x - w * 0.36f, top + dh * 0.28f, w * 0.72f, 0.6f * dialog, 3f)
            bar(ph.center.x - w * 0.36f, top + dh * 0.46f, w * 0.5f, 0.3f * dialog, 3f)
            val bw = w * 0.36f
            val by = top + dh * 0.76f
            drawRoundRect(c.ink.copy(alpha = 0.15f * dialog), Offset(ph.center.x - w * 0.42f, by - 5.dp.toPx()), Size(bw, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
            drawRoundRect(c.ink.copy(alpha = dialog), Offset(ph.center.x + w * 0.06f, by - 5.dp.toPx()), Size(bw, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
            val press = if (t in 0.48f..0.6f) phase(t, 0.48f, 0.6f) else 0f
            finger(Offset(ph.center.x + w * 0.06f + bw / 2, by + 2.dp.toPx()), press, visible = phase(t, 0.38f, 0.46f) * dialog)
        }
        check(ph.center, ph.width * 0.24f, phase(t, 0.68f, 0.76f))
        // 허용 뒤 케이블로 신호가 오간다
        if (t > 0.7f) {
            val u = ((t - 0.7f) / 0.3f * 2) % 1f
            val x = (1 - u) * (1 - u) * a.x + 2 * (1 - u) * u * ctrl.x + u * u * b.x
            val y = (1 - u) * (1 - u) * a.y + 2 * (1 - u) * u * ctrl.y + u * u * b.y
            drawCircle(c.ink, 4.dp.toPx(), Offset(x, y))
        }
    }

    fun pair(d: DrawScope, t: Float) = with(d) {
        val (pc, ph) = pcAndPhone()
        // 폰: 제목 줄과 6자리 코드가 하나씩
        bar(ph.center.x - ph.width * 0.3f, ph.top + ph.height * 0.2f, ph.width * 0.6f, 0.5f, 3f)
        val code = "482913"
        val boxW = ph.width * 0.13f
        val gap = ph.width * 0.022f
        val total = boxW * 6 + gap * 5
        val y = ph.center.y
        for (i in 0 until 6) {
            val p = phase(t, 0.05f + i * 0.05f, 0.1f + i * 0.05f)
            val x = ph.center.x - total / 2 + i * (boxW + gap)
            drawRoundRect(c.ink.copy(alpha = 0.1f + 0.1f * p), Offset(x, y - boxW * 0.7f), Size(boxW, boxW * 1.4f), CornerRadius(2.dp.toPx()))
            text(code[i].toString(), Offset(x + boxW / 2, y), mono.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), alpha = p)
        }
        // PC: adb pair → 코드 → paired
        terminal(
            pc,
            listOf("adb pair …" to phase(t, 0.36f, 0.5f), "482913" to phase(t, 0.52f, 0.62f), "paired" to phase(t, 0.68f, 0.74f)),
            cursorBlink = blink(t),
        )
        // 두 기기 사이: 점선 → 이어지면 실선과 고리
        val linked = phase(t, 0.7f, 0.8f)
        val x0 = pc.right + size.height * 0.07f
        val x1 = ph.left - size.height * 0.07f
        val ly = pc.center.y
        if (linked < 1f) {
            var x = x0
            while (x < x1) {
                drawCircle(c.ink.copy(alpha = 0.3f * (1f - linked)), 1.6.dp.toPx(), Offset(x, ly)); x += 7.dp.toPx()
            }
        }
        if (linked > 0f) {
            drawLine(c.ink.copy(alpha = linked), Offset(x0, ly), Offset(x0 + (x1 - x0) * linked, ly), 2.5.dp.toPx(), StrokeCap.Round)
            val mid = Offset((x0 + x1) / 2, ly)
            drawCircle(c.surface, 6.dp.toPx() * linked, mid)
            drawCircle(c.ink.copy(alpha = linked), 6.dp.toPx() * linked, mid, style = Stroke(2.dp.toPx()))
        }
    }

    fun wirelessConnect(d: DrawScope, t: Float) = with(d) {
        val (pc, ph) = pcAndPhone()
        // 폰 화면: 'IP 주소 및 포트' 줄이 강조된다
        val hl = phase(t, 0.05f, 0.18f)
        bar(ph.left + ph.width * 0.16f, ph.top + ph.height * 0.24f, ph.width * 0.5f, 0.25f, 3f)
        drawRoundRect(c.ink.copy(alpha = 0.1f * hl), Offset(ph.left + ph.width * 0.08f, ph.center.y - 9.dp.toPx()), Size(ph.width * 0.84f, 18.dp.toPx()), CornerRadius(4.dp.toPx()))
        bar(ph.left + ph.width * 0.16f, ph.center.y, ph.width * 0.6f, 0.4f + 0.4f * hl, 3f)
        // 두 기기 사이의 와이파이 부채꼴이 차례로 켜진다
        val cx = (pc.right + ph.left) / 2
        val cy = pc.center.y + 10.dp.toPx()
        for (k in 0..2) {
            val r = size.height * (0.09f + 0.09f * k)
            val on = ((t * 4).toInt() % 3) >= k
            drawArc(
                c.ink.copy(alpha = if (on) 0.85f else 0.15f), -135f, 90f, false,
                Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawCircle(c.ink, 3.dp.toPx(), Offset(cx, cy))
        terminal(pc, listOf("adb connect …" to phase(t, 0.25f, 0.45f), "connected" to phase(t, 0.62f, 0.7f)), cursorBlink = blink(t))
        check(Offset(ph.right - 4.dp.toPx(), ph.top + 4.dp.toPx()), 7.dp.toPx(), phase(t, 0.7f, 0.78f))
    }

    fun command(d: DrawScope, t: Float) = with(d) {
        val (pc, ph) = pcAndPhone(pcRatio = 1.05f, gapRatio = 0.58f)
        terminal(
            pc,
            listOf("adb shell" to phase(t, 0.04f, 0.18f), "pm grant …" to phase(t, 0.18f, 0.32f), "WRITE_SECURE…" to phase(t, 0.32f, 0.42f)),
            cursorBlink = t < 0.46f && blink(t),
        )
        // Enter 키: 모니터 받침대 오른쪽의 키캡. 누르면 눌려 내려가고 진해진다.
        val enter = if (t in 0.44f..0.52f) phase(t, 0.44f, 0.48f) * (1f - phase(t, 0.48f, 0.52f)) else 0f
        val kw = 30.dp.toPx()
        val kh = 14.dp.toPx()
        val kx = pc.right - kw
        val ky = pc.bottom + size.height * 0.13f + enter * 2.dp.toPx()
        drawRoundRect(c.ink.copy(alpha = 0.18f), Offset(kx, ky + 2.dp.toPx()), Size(kw, kh), CornerRadius(3.dp.toPx()))
        drawRoundRect(c.surface, Offset(kx, ky), Size(kw, kh), CornerRadius(3.dp.toPx()))
        drawRoundRect(c.ink.copy(alpha = 0.35f + 0.6f * enter), Offset(kx, ky), Size(kw, kh), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        text("Enter", Offset(kx + kw / 2, ky + kh / 2), mono.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold))
        // 열쇠가 포물선으로 날아간다
        val fly = phase(t, 0.52f, 0.76f)
        val start = Offset(pc.right - 6.dp.toPx(), pc.center.y)
        val end = ph.center
        if (fly in 0.001f..0.999f) {
            val x = start.x + (end.x - start.x) * fly
            val y = start.y + (end.y - start.y) * fly - sin(fly * PI).toFloat() * size.height * 0.3f
            keyGlyph(Offset(x, y))
        }
        // 폰: 자물쇠가 열리고 체크
        lock(ph.center, ph.width * 0.3f, phase(t, 0.76f, 0.84f))
        check(Offset(ph.right - 4.dp.toPx(), ph.top + 4.dp.toPx()), 7.dp.toPx(), phase(t, 0.84f, 0.9f))
    }

    fun done(d: DrawScope, t: Float) = with(d) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.height * 0.3f
        // 주변 방울이 모여든다
        for (i in 0 until 8) {
            val a = (i / 8f) * 2f * PI.toFloat() + t * 0.8f
            val gather = phase(t, 0.05f + i * 0.03f, 0.45f + i * 0.03f)
            val dist = r * (2.1f - 1.1f * gather)
            val dr = 5.dp.toPx() * (1f - gather * 0.9f)
            if (dr > 0.5f) drawCircle(c.ink.copy(alpha = 0.7f), dr, Offset(center.x + cos(a) * dist, center.y + sin(a) * dist * 0.75f))
        }
        // 다 모이면 한 번 출렁인다
        val wob = sin(t * 2 * PI * 2).toFloat() * (1f - phase(t, 0.5f, 0.9f)) * 0.06f
        drawOval(c.ink, Offset(center.x - r * (1 + wob), center.y - r * (1 - wob)), Size(2 * r * (1 + wob), 2 * r * (1 - wob)))
        check(center, r, 1f)
    }

    // ---- 작은 그림 ----

    fun DrawScope.keyGlyph(at: Offset) {
        val s = 6.dp.toPx()
        drawCircle(c.ink, s, Offset(at.x - s, at.y), style = Stroke(2.5.dp.toPx()))
        drawLine(c.ink, Offset(at.x, at.y), Offset(at.x + s * 2, at.y), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(c.ink, Offset(at.x + s * 1.4f, at.y), Offset(at.x + s * 1.4f, at.y + s * 0.8f), 2.5.dp.toPx(), StrokeCap.Round)
    }

    /** 자물쇠. [open] 0이면 잠김, 1이면 고리가 들려 옆으로 열림. */
    fun DrawScope.lock(center: Offset, w: Float, open: Float) {
        val bodyH = w * 0.8f
        val top = center.y - bodyH * 0.2f
        val sh = w * 0.32f
        val lift = open * w * 0.22f
        drawArc(
            c.ink, 180f, 180f, false, Offset(center.x - sh + open * w * 0.2f, top - sh - lift), Size(sh * 2, sh * 2),
            style = Stroke(w * 0.13f, cap = StrokeCap.Round),
        )
        if (open < 1f) {
            // 잠긴 동안에는 고리 양 다리가 몸통까지 내려온다
            drawLine(c.ink, Offset(center.x - sh + open * w * 0.2f, top - lift), Offset(center.x - sh + open * w * 0.2f, top), w * 0.13f)
        }
        drawLine(c.ink, Offset(center.x + sh + open * w * 0.2f, top - lift), Offset(center.x + sh + open * w * 0.2f, top - lift * 0.2f), w * 0.13f)
        drawRoundRect(c.ink, Offset(center.x - w / 2, top), Size(w, bodyH), CornerRadius(w * 0.15f))
        drawCircle(c.surface, w * 0.09f, Offset(center.x, top + bodyH * 0.45f))
    }
}
