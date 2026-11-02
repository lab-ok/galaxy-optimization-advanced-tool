package com.labok.goat.ui.lab

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/** GOAT 플렉스 패널 화면 상태(세션에서 읽어 넣는다). */
class FlexPanelState {
    var app by mutableStateOf("")
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var playing by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var canSeek by mutableStateOf(false)
    var hasSession by mutableStateOf(false)
    var volume by mutableFloatStateOf(0.5f)
    var brightness by mutableFloatStateOf(0.5f)

    /** 앰비언트 빛의 바탕: 미디어 세션의 포스터(있으면)와 앱 대표 색. */
    var art by mutableStateOf<ImageBitmap?>(null)
    var tint by mutableStateOf(Color(0xFF3A4A6B))

    /** 영상(위 절반) 터치 잠금. */
    var locked by mutableStateOf(false)

    /** 보이는 중인지(false가 되면 아래로 내려가며 사라진다). */
    var visible by mutableStateOf(false)

    /** 이동 버튼을 연달아 누른 합(초, 잠깐 보였다 사라진다). */
    var seekBadge by mutableStateOf(0)
}

// 디자인 토큰(11-02 GPT 역설계 분석의 권장값을 글래스로): 저채도 남색 바탕, 카드·주요 카드, 강조색은 진행 막대에만
private val PanelBg = Color(0xFF202736)
private val Card = Color(0xB335435F)
private val CardPrimary = Color(0xCC455C84)
private val CardPressed = Color(0xE65A7099)
private val CardEdge = Color(0x33FFFFFF)
private val Ink = Color(0xFFF7F9FF)
private val Muted = Color(0xFFB6C2D8)
private val Accent = Color(0xFFFF5268)
private val Brand = Color(0xFFE50914)

/**
 * 넷플릭스 테이블탑 조작 화면을 따른 GOAT 플렉스 패널(아래 절반).
 * 1줄 닫기·앱/제목·잠금, 2줄 뒤로·재생(크게)·앞으로, 3줄 진행 막대(현재/전체 시간, 끄는 동안 미리 보기·놓으면 이동),
 * 4줄 속도·밝기·볼륨·전체 화면. 밝기·볼륨 타일을 누르면 3줄이 그 조절 막대로 바뀐다.
 * - 바탕은 앰비언트 빛: 영상은 화면 찍기가 막혀 있어 미디어 세션의 포스터를 흐려 깔고(없으면 앱 대표 색) 숨 쉬듯 일렁인다. 타일은 유리판.
 * - 위쪽(접는 선 쪽)은 손이 닿기 불편해 여백을 넉넉히 둔다. 자주 쓰는 재생·이동이 가장 크다.
 * - 누르면 짧은 진동과 눌림, 길게 누르면 기능 이름이 잠깐 뜬다. 나타날 때 아래에서 올라오고 닫을 때 내려간다.
 */
@Composable
fun FlexPanelUi(
    state: FlexPanelState,
    seekSec: Int,
    show: (String) -> Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onSpeed: () -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit = {}
) {
    var bar by remember { mutableStateOf(0) }
    val k by animateFloatAsState(if (state.visible) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 380f), label = "enter")
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = k
            translationY = (1f - k) * size.height * 0.35f
        }.background(PanelBg)
    ) {
        AmbientBackdrop(state)
        PanelBody(state, seekSec, show, bar, { bar = it }, onClose, onPlayPause, onSeek, onSeekTo, onSpeed, onBrightness, onVolume, onFullscreen, onLock)
    }
}

/** 앰비언트 바탕: 포스터를 아주 흐리게 늘여 깔고, 대표 색 빛 두 덩이가 천천히 떠다니며, 아래로 갈수록 어두워진다. */
@Composable
private fun AmbientBackdrop(state: FlexPanelState) {
    val t = rememberInfiniteTransition(label = "amb")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "d")
    state.art?.let { img ->
        Image(
            img,
            null,
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.7f
                renderEffect = BlurEffect(90f, 90f, TileMode.Clamp)
                scaleX = 1.25f
                scaleY = 1.25f
            },
            contentScale = ContentScale.Crop
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = state.tint
        val a = Offset(size.width * (0.25f + 0.2f * drift), size.height * 0.1f)
        val b = Offset(size.width * (0.8f - 0.2f * drift), size.height * 0.55f)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.6f), Color.Transparent), a, size.width * 0.45f), size.width * 0.45f, a)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), b, size.width * 0.4f), size.width * 0.4f, b)
        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x80000000))))
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PanelBody(
    state: FlexPanelState,
    seekSec: Int,
    show: (String) -> Boolean,
    bar: Int,
    setBar: (Int) -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onSpeed: () -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit
) {
    val gap = 8.dp
    // 위쪽은 접는 선이라 여백을 크게(힌지 보호 구역)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(0.85f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PanelTile(Modifier.weight(0.7f), onClose, label = "패널 닫기") { Icon { close(it) } }
            // 제목 타일: 앱 첫 글자 로고 · 굵은 제목(2줄까지) · 흐린 회차 줄 · 오른쪽 꺾쇠
            PanelTile(Modifier.weight(4.2f), null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.app.take(1).uppercase(), color = Brand, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.title.ifEmpty { state.app }, color = Ink, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = state.subtitle.ifEmpty { if (state.hasSession) state.app else "재생 정보 없음 · 화면 누르기로 조작" }
                        Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(16) { chevron(it) }
                }
            }
            PanelTile(Modifier.weight(0.7f), onLock, selected = state.locked, label = if (state.locked) "영상 잠금 풀기" else "영상 터치 잠금") { Icon(22) { lock(it, state.locked) } }
        }
        Row(Modifier.fillMaxWidth().weight(1.6f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PanelTile(Modifier.weight(1f), { onSeek(-1) }, primary = true, label = "${seekSec}초 뒤로") {
                Icon { seekIcon(it, backward = true, sec = seekSec) }
                SeekBadge(state, back = true)
            }
            PanelTile(Modifier.weight(1.15f), onPlayPause, primary = true, label = if (state.playing) "일시정지" else "재생") {
                AnimatedContent(state.playing, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "pp") { playing ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(38) { if (playing) pause(it) else play(it) } }
                }
            }
            PanelTile(Modifier.weight(1f), { onSeek(1) }, primary = true, label = "${seekSec}초 앞으로") {
                Icon { seekIcon(it, backward = false, sec = seekSec) }
                SeekBadge(state, back = false)
            }
        }
        if (show("progress") || bar != 0) {
            PanelTile(Modifier.fillMaxWidth().weight(0.75f), null) {
                when (bar) {
                    1 -> Slider(state.brightness, Ink, { "밝기 ${(it * 100).toInt()}%" }, live = true) { onBrightness(it) }
                    2 -> Slider(state.volume, Ink, { "볼륨 ${(it * 100).toInt()}%" }, live = true) { onVolume(it) }
                    else -> {
                        val f = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
                        if (state.canSeek) {
                            Slider(f, Accent, { clock((state.duration * it).toLong()) + " / " + clock(state.duration) }, live = false) { onSeekTo(it) }
                        } else {
                            // 제어 불가: 막대를 흐리게 두고 이유를 적는다
                            Slider(f, Muted, { if (state.hasSession) "이 앱은 위치 이동을 지원하지 않음" else "재생 정보를 받지 못함" }, live = false, enabled = false) {}
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.85f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (show("speed")) {
                PanelTile(Modifier.weight(1f), onSpeed, label = "재생 속도 ${state.speed}x") {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(22) { gauge(it) }
                        if (state.speed != 1f) Text(" ${state.speed}x", color = Ink, fontSize = 13.sp)
                    }
                }
            }
            if (show("brightness")) PanelTile(Modifier.weight(1f), { setBar(if (bar == 1) 0 else 1) }, selected = bar == 1, label = "밝기") { Icon(22) { sun(it) } }
            if (show("volume")) PanelTile(Modifier.weight(1f), { setBar(if (bar == 2) 0 else 2) }, selected = bar == 2, label = "볼륨") { Icon(22) { speaker(it) } }
            if (show("fullscreen")) PanelTile(Modifier.weight(1f), onFullscreen, label = "전체 화면") { Icon(22) { expand(it) } }
        }
    }
}

/** 연달아 누른 이동 합(예: +30초)을 이동 타일 위에 잠깐 띄운다. */
@Composable
private fun BoxScope.SeekBadge(state: FlexPanelState, back: Boolean) {
    val v = state.seekBadge
    if (v == 0 || (v < 0) != back) return
    Text(
        (if (v > 0) "+" else "−") + "${kotlin.math.abs(v)}초",
        color = Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
    )
}

/**
 * 둥근 유리 타일: 누르면 살짝 작아지고 밝아지며 짧게 진동한다. 길게 누르면 [label]이 잠깐 뜬다. [primary]는 자주 쓰는 큰 버튼 색.
 * [onClick]이 null이면 눌리지 않는다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelTile(modifier: Modifier, onClick: (() -> Unit)?, selected: Boolean = false, primary: Boolean = false, label: String? = null, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(1200)
            hint = false
        }
    }
    val k by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "tile")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.fillMaxHeight()
            .graphicsLayer {
                scaleX = k
                scaleY = k
            }
            .clip(shape)
            .background(
                if (pressed || selected) {
                    CardPressed
                } else if (primary) {
                    CardPrimary
                } else {
                    Card
                }
            )
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))))
            .border(1.dp, CardEdge, shape)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(src, null, onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (label != null) hint = true
                    }) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (hint && label != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(label, color = Ink, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 조절 막대: 가로로 끌거나 눌러 값을 바꾼다. [live]면 끄는 동안 바로 바꾸고(밝기·볼륨), 아니면 끄는 동안 [label]로 미리 보여 주고
 * 손을 떼면 한 번만 바꾼다(재생 위치). 오른쪽에 값 글자.
 */
@Composable
private fun Slider(value: Float, color: Color, label: (Float) -> String, live: Boolean, enabled: Boolean = true, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.weight(1f).fillMaxHeight()
                .pointerInput(enabled, live) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled, live) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            drag?.let { if (!live) onChange(it) }
                            drag = null
                        },
                        onDragCancel = { drag = null }
                    ) { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        if (live) onChange(f)
                    }
                }
        ) {
            val y = size.height / 2
            val h = (if (drag != null) 6 else 4).dp.toPx()
            drawRoundRect(Color(0x40FFFFFF), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
            drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * shown, h), CornerRadius(h))
            drawCircle(color, (if (drag != null) 9 else 7).dp.toPx(), Offset(size.width * shown, y))
        }
        Spacer(Modifier.width(14.dp))
        Text(label(shown), color = if (enabled) Ink else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Icon(sizeDp: Int = 30, draw: DrawScope.(Color) -> Unit) {
    Canvas(Modifier.size(sizeDp.dp)) { draw(Ink) }
}

private fun DrawScope.stroke() = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round)

private fun DrawScope.close(c: Color) {
    val s = size.minDimension
    drawLine(c, Offset(s * 0.25f, s * 0.25f), Offset(s * 0.75f, s * 0.75f), s * 0.08f, StrokeCap.Round)
    drawLine(c, Offset(s * 0.75f, s * 0.25f), Offset(s * 0.25f, s * 0.75f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.play(c: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.3f, s * 0.18f)
            lineTo(s * 0.82f, s * 0.5f)
            lineTo(s * 0.3f, s * 0.82f)
            close()
        },
        c
    )
}

private fun DrawScope.pause(c: Color) {
    val s = size.minDimension
    drawRoundRect(c, Offset(s * 0.24f, s * 0.18f), Size(s * 0.18f, s * 0.64f), CornerRadius(s * 0.04f))
    drawRoundRect(c, Offset(s * 0.58f, s * 0.18f), Size(s * 0.18f, s * 0.64f), CornerRadius(s * 0.04f))
}

/** 10초 이동: 원을 따라 도는 화살표와 가운데 숫자. */
private fun DrawScope.seekIcon(c: Color, backward: Boolean, sec: Int) {
    // 뒤로(⟲)는 화살촉이 왼쪽 위, 앞으로(⟳)는 오른쪽 위(에뮬레이터에서 반대로 그려진 것을 바로잡음)
    val back = !backward
    val s = size.minDimension
    val r = s * 0.4f
    val center = Offset(s / 2, s / 2)
    drawArc(c, if (back) -60f else -120f, if (back) -280f else 280f, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = stroke())
    // 화살촉(원 위쪽)
    val a = Math.toRadians((if (back) -60.0 else -120.0))
    val tip = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    val dx = if (back) -1f else 1f
    drawLine(c, tip, tip + Offset(dx * s * 0.14f, -s * 0.02f), s * 0.08f, StrokeCap.Round)
    drawLine(c, tip, tip + Offset(dx * s * 0.02f, s * 0.14f), s * 0.08f, StrokeCap.Round)
    val p = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = s * 0.32f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText("$sec", center.x, center.y + s * 0.11f, p)
}

private fun DrawScope.gauge(c: Color) {
    val s = size.minDimension
    drawArc(c, 150f, 240f, false, Offset(s * 0.12f, s * 0.12f), Size(s * 0.76f, s * 0.76f), style = stroke())
    drawLine(c, Offset(s / 2, s / 2), Offset(s * 0.68f, s * 0.32f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.sun(c: Color) {
    val s = size.minDimension
    val center = Offset(s / 2, s / 2)
    drawCircle(c, s * 0.18f, center, style = stroke())
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(c, center + d * (s * 0.32f), center + d * (s * 0.44f), s * 0.07f, StrokeCap.Round)
    }
}

private fun DrawScope.speaker(c: Color) {
    val s = size.minDimension
    drawPath(
        Path().apply {
            moveTo(s * 0.12f, s * 0.38f)
            lineTo(s * 0.3f, s * 0.38f)
            lineTo(s * 0.52f, s * 0.18f)
            lineTo(s * 0.52f, s * 0.82f)
            lineTo(s * 0.3f, s * 0.62f)
            lineTo(s * 0.12f, s * 0.62f)
            close()
        },
        c
    )
    drawArc(c, -45f, 90f, false, Offset(s * 0.42f, s * 0.3f), Size(s * 0.34f, s * 0.4f), style = stroke())
    drawArc(c, -45f, 90f, false, Offset(s * 0.4f, s * 0.16f), Size(s * 0.5f, s * 0.68f), style = stroke())
}

private fun DrawScope.chevron(c: Color) {
    val s = size.minDimension
    drawLine(c.copy(alpha = 0.7f), Offset(s * 0.35f, s * 0.2f), Offset(s * 0.7f, s * 0.5f), s * 0.12f, StrokeCap.Round)
    drawLine(c.copy(alpha = 0.7f), Offset(s * 0.7f, s * 0.5f), Offset(s * 0.35f, s * 0.8f), s * 0.12f, StrokeCap.Round)
}

/** 자물쇠: 잠기면 고리가 닫히고 몸통이 채워진다. */
private fun DrawScope.lock(c: Color, locked: Boolean) {
    val s = size.minDimension
    val body = Offset(s * 0.22f, s * 0.46f)
    if (locked) {
        drawRoundRect(c, body, Size(s * 0.56f, s * 0.42f), CornerRadius(s * 0.08f))
    } else {
        drawRoundRect(c, body, Size(s * 0.56f, s * 0.42f), CornerRadius(s * 0.08f), style = stroke())
    }
    val shift = if (locked) 0f else s * 0.14f
    drawArc(c, 180f, 180f, false, Offset(s * 0.32f + shift, s * 0.14f), Size(s * 0.36f, s * 0.4f), style = stroke())
    if (!locked) drawLine(c, Offset(s * 0.68f + shift, s * 0.34f), Offset(s * 0.68f + shift, s * 0.4f), s * 0.08f, StrokeCap.Round)
    if (locked) drawLine(c, Offset(s * 0.32f, s * 0.34f), Offset(s * 0.32f, s * 0.46f), s * 0.08f, StrokeCap.Round)
    if (locked) drawLine(c, Offset(s * 0.68f, s * 0.34f), Offset(s * 0.68f, s * 0.46f), s * 0.08f, StrokeCap.Round)
}

private fun DrawScope.expand(c: Color) {
    val s = size.minDimension
    val w = s * 0.08f
    drawLine(c, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.42f, s * 0.42f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.38f, s * 0.2f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.2f, s * 0.38f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.8f, s * 0.8f), Offset(s * 0.58f, s * 0.58f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.8f, s * 0.8f), Offset(s * 0.62f, s * 0.8f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.8f, s * 0.8f), Offset(s * 0.8f, s * 0.62f), w, StrokeCap.Round)
}
