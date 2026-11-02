package com.labok.goat.ui.lab

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.AmbientColor
import com.labok.goat.ui.liquid.liquidPress
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay

/** GOAT 플렉스 패널 화면 상태(세션·앰비언트 엔진이 채운다). */
class FlexPanelState {
    var app by mutableStateOf("")
    var appIcon by mutableStateOf<ImageBitmap?>(null)
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
    var art by mutableStateOf<ImageBitmap?>(null)
    var tint by mutableStateOf(Color(0xFF3A4A6B))
    var locked by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var seekBadge by mutableIntStateOf(0)

    /** 앰비언트 목표색(엔진이 넣는다): 좌·중·우·전체. 화면은 이 쪽으로 천천히 따라간다. */
    var ambLeft by mutableIntStateOf(0xFF3A4A6B.toInt())
    var ambCenter by mutableIntStateOf(0xFF3A4A6B.toInt())
    var ambRight by mutableIntStateOf(0xFF3A4A6B.toInt())
    var ambAll by mutableIntStateOf(0xFF3A4A6B.toInt())

    /** 앰비언트 색이 어디서 왔는지(화면 분석·포스터·앱 색·끔, 보호 콘텐츠면 그 사실). */
    var ambSource by mutableStateOf("")

    // 설정(시작할 때 넣는다)
    var quality by mutableIntStateOf(2)
    var intensity by mutableIntStateOf(1)
    var ambientOn by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var motion by mutableStateOf(true)
    var highContrast by mutableStateOf(false)

    /**
     * 공간 반사 필드([com.labok.goat.data.lab.ReflectionField]): 위 화면이 바닥에 비친 상(위아래 뒤집힘, 아래로 갈수록 흐려짐).
     * 바닥·카드·잉크가 이 한 장을 같은 좌표계(패널 전체)로 나눠 쓴다. 없으면 좌·중·우 색으로 대신한다.
     */
    var field by mutableStateOf<ImageBitmap?>(null)
    var fieldPrev by mutableStateOf<ImageBitmap?>(null)
    var fieldPx: IntArray? = null
    var fieldW = 0
    var fieldH = 0

    /** GOAT 잉크(앱 설정 UiInk의 다크 모드 색). 반사가 이 위에 얇게 비친다. */
    var ink by mutableStateOf(Color(0xFFEBEBEB))
}

/** 반사 필드에서 패널 자리(0~1)의 색. 필드가 없으면 null. */
private fun FlexPanelState.sampleAt(fx: Float, fy: Float): Color? {
    val px = fieldPx ?: return null
    if (fieldW <= 0 || fieldH <= 0) return null
    return Color(com.labok.goat.data.lab.ReflectionField.sample(px, fieldW, fieldH, fx, fy))
}

/**
 * 잉크 반사: 기본 잉크 위에 그 자리의 반사색이 얇게 비친다(표면보다 훨씬 절제). [k]는 아이콘·강조 0.22, 글자 0.07.
 * 반사색은 밝기를 끌어올려 잉크가 어두워지지 않게(가독성) 하고, 무채색·어두운 반사는 거의 물들이지 않는다.
 */
private fun inkWith(base: Color, refl: Color?, k: Float): Color {
    if (refl == null || k <= 0f) return base
    val o = AmbientColor.toOklab(android.graphics.Color.rgb((refl.red * 255).toInt(), (refl.green * 255).toInt(), (refl.blue * 255).toInt()))
    val ch = hypot(o[1], o[2])
    if (ch < 0.02f) return base
    val bright = Color(AmbientColor.fromOklab(0.86f, o[1] * 0.9f, o[2] * 0.9f))
    return lerp(base, bright, (k * (ch / 0.1f).coerceAtMost(1f)).coerceIn(0f, 0.35f))
}

/** 카드 안 내용(아이콘·막대)이 받을 잉크색. */
private val LocalTileInk = androidx.compose.runtime.compositionLocalOf { Color(0xFFEBEBEB) }

// ---- 디자인 토큰(11-02 GPT 역설계 권장값 + 소장님 글래스 고도화 지시) ----
// GOAT 리퀴드 다크 팔레트(LiquidTheme의 DarkLiquid)를 그대로 쓴다: 바탕 #0F0F0F, 면 #171717, 선 #424242, 잉크(UiInk), 둥글기 LiquidShape.card.
// 11-02 소장님: 기존 리퀴드 UI와 다른 디자인처럼 보이면 실패 — 1.42.5의 남색 토큰을 GOAT 토큰으로 바꿨다
private object Glass {
    val Floor = Color(0xFF0F0F0F)
    val Tint = Color(0xFF171717)
    val TintPrimary = Color(0xFF222222)
    val Ink = Color(0xFFEBEBEB)
    val Muted = Color(0xFF8D8D8D)
    val Line = Color(0xFF424242)
    val Accent = Color(0xFFEBEBEB)
    val Brand = Color(0xFFE50914)
    val radius = com.labok.goat.ui.liquid.LiquidShape.card
    val gap = 8.dp
}

/** 강도 단계별 앰비언트 세기(은은·균형·몰입). */
private fun ambK(i: Int) = when (i) {
    0 -> 0.45f
    2 -> 1.35f
    else -> 0.9f
}

/** 주변광 색에서 고른 강조색: 색이 거의 없으면 기본 강조색. */
private fun adaptiveAccent(c: Int, on: Boolean): Color {
    if (!on) return Glass.Accent
    val o = AmbientColor.toOklab(c)
    val ch = hypot(o[1], o[2])
    if (ch < 0.035f) return Glass.Accent
    val k = 0.15f / ch
    return Color(AmbientColor.fromOklab(0.72f, o[1] * k, o[2] * k))
}

/**
 * 주변광 색의 비침 세기 보정: 무채색(회색·흰 화면)은 적게, 밝은 색도 조금 덜 비친다. 밝은 설정 화면 위에서 패널 전체가 회색으로
 * 바래 어두운 유리 느낌이 사라졌다(11-02 에뮬레이터 비교). 색이 있는 장면만 또렷하게 번진다.
 */
private fun ambWeight(c: Color): Float {
    val o = AmbientColor.toOklab(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
    val chroma = (hypot(o[1], o[2]) / 0.08f).coerceIn(0f, 1f)
    val light = if (o[0] > 0.6f) 0.6f else 1f
    return (0.3f + 0.7f * chroma) * light
}

/** 세 구역 색을 가로 위치 [x](0~1)로 섞은 주변광. */
private fun ambientAt(x: Float, l: Color, c: Color, r: Color) = if (x < 0.5f) lerp(l, c, (x * 2f).coerceIn(0f, 1f)) else lerp(c, r, ((x - 0.5f) * 2f).coerceIn(0f, 1f))

/**
 * 넷플릭스 테이블탑 조작 화면(아이폰 듀오 레퍼런스)을 따른 GOAT 플렉스 패널(아래 절반).
 * 1줄 닫기·앱/제목/재생 상태·잠금, 2줄 뒤로·재생(가장 큼)·앞으로, 3줄 오목한 진행 막대(밝기·볼륨·속도 줄로 바뀜), 4줄 속도·밝기·볼륨·전체 화면.
 * - 앰비언트: 엔진이 넣은 좌·중·우 색으로 바닥에 넓은 가로 그라데이션을 깔고, 위(영상 쪽)에서 빛이 흘러내린다. 카드는 자기 가로 위치의 색을 받는다.
 * - 글래스 카드([glass]): 바닥·유리 색조·주변광·재질 결·테두리 빛·위쪽 하이라이트·누름 반응을 한 번의 그리기로 겹친다(품질 단계로 층을 끈다).
 * - 상태: 기본·눌림·선택(활성)·비활성. 줄이기 동작·고대비 설정을 따른다.
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
    onSpeedSet: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit
) {
    var bar by remember { mutableIntStateOf(0) }
    // 앰비언트 색을 프레임마다 목표 쪽으로(엔진의 시간 안정화). 다 따라가면 갱신하지 않는다
    val sm = remember { Array(4) { AmbientColor.Smoother(0xFF3A4A6B.toInt()) } }
    var shown by remember { mutableStateOf(listOf(Color(0xFF3A4A6B), Color(0xFF3A4A6B), Color(0xFF3A4A6B), Color(0xFF3A4A6B))) }
    // 목표가 바뀔 때만 프레임을 돌리고, 다 따라가면(두 프레임 연속 그대로) 멈춘다. 늘 프레임을 돌리면 패널 전체가 매 프레임
    // 다시 그려져 에뮬레이터에서 CPU 300%가 나왔다(11-02 측정)
    LaunchedEffect(state.ambLeft, state.ambCenter, state.ambRight, state.ambAll, state.motion) {
        val targets = intArrayOf(state.ambLeft, state.ambCenter, state.ambRight, state.ambAll)
        if (!state.motion) {
            shown = List(4) { Color(targets[it]) }
            return@LaunchedEffect
        }
        var last = 0L
        var still = 0
        while (still < 2) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = t
                val next = List(4) { i -> Color(sm[i].step(targets[i], dt)) }
                if (next != shown) {
                    shown = next
                    still = 0
                } else {
                    still++
                }
            }
        }
    }
    val k by animateFloatAsState(if (state.visible) 1f else 0f, if (state.motion) spring(dampingRatio = 0.85f, stiffness = 380f) else snap(), label = "enter")
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = k
            translationY = (1f - k) * size.height * 0.3f
        }.background(Glass.Floor)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.labok.goat.ui.liquid.LocalReduceMotion provides !state.motion,
            LocalTileInk provides state.ink
        ) {
            AmbientBackdrop(state, shown)
            PanelBody(state, shown, seekSec, show, bar, { bar = it }, onClose, onPlayPause, onSeek, onSeekTo, onSpeed, onSpeedSet, onBrightness, onVolume, onFullscreen, onLock)
        }
    }
}

/**
 * 앰비언트 바닥: 포스터를 크게 흐려 깔고(있으면), 좌·중·우 색의 넓은 가로 그라데이션, 위쪽(영상 쪽)에서 흘러내리는 빛,
 * 천천히 떠다니는 빛 두 덩이, 아래로 갈수록 어둡게. 강도 단계로 세기를 바꾼다.
 */
@Composable
private fun AmbientBackdrop(state: FlexPanelState, c: List<Color>) {
    val kk = if (state.ambientOn) ambK(state.intensity) else 0f
    val field = state.field
    // 새 반사 필드로 0.5초 동안 서서히 넘어간다(필드가 바뀔 때만 프레임을 돈다)
    val fade = remember(field) { androidx.compose.animation.core.Animatable(if (state.motion && state.fieldPrev != null) 0f else 1f) }
    LaunchedEffect(field) { if (fade.value < 1f) fade.animateTo(1f, tween(500)) }
    Canvas(Modifier.fillMaxSize()) {
        if (kk > 0f && field != null) {
            // 공간 반사: 위 화면이 바닥에 비친 상을 패널 전체에 깐다(쌍선형 확대). 회색·밝은 화면은 덜 비친다
            val w = (0.92f * kk * ambWeight(c[3]).coerceAtLeast(0.45f)).coerceAtMost(1f)
            val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            state.fieldPrev?.takeIf { fade.value < 1f }?.let { drawImage(it, dstSize = dst, alpha = w * (1f - fade.value), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low) }
            drawImage(field, dstSize = dst, alpha = w * fade.value, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
        } else if (kk > 0f) {
            drawRect(Brush.horizontalGradient(listOf(c[0].copy(alpha = 0.38f * kk * ambWeight(c[0])), c[1].copy(alpha = 0.30f * kk * ambWeight(c[1])), c[2].copy(alpha = 0.38f * kk * ambWeight(c[2])))))
            // 영상(위)에서 흘러내리는 빛
            drawRect(Brush.verticalGradient(listOf(c[3].copy(alpha = (0.5f * kk * ambWeight(c[3])).coerceAtMost(0.75f)), Color.Transparent), 0f, size.height * 0.55f))
        }
        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x73000000))))
    }
    // 떠다니는 빛 두 덩이: 한 번 그려 둔 층을 옮기기만 한다(움직이는 동안 다시 그리지 않아 가볍다). 품질 '높음'·애니메이션 켬일 때만 움직인다
    if (kk > 0f && field == null) GlowLayer(c, kk, state.motion && state.quality >= 3)
}

/** 빛 두 덩이 층. [moving]일 때만 무한 애니메이션을 만든다(멈춰 있으면 프레임을 요청하지 않는다). */
@Composable
private fun GlowLayer(c: List<Color>, kk: Float, moving: Boolean) {
    val drift = if (moving) {
        rememberInfiniteTransition(label = "amb").animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "d")
    } else {
        null
    }
    run {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val d = (drift?.value ?: 0.5f) - 0.5f
                translationX = d * size.width * 0.2f
            }
        ) {
            val a = Offset(size.width * 0.32f, size.height * 0.05f)
            val b = Offset(size.width * 0.72f, size.height * 0.5f)
            drawCircle(Brush.radialGradient(listOf(c[0].copy(alpha = 0.35f * kk * ambWeight(c[0])), Color.Transparent), a, size.width * 0.42f), size.width * 0.42f, a)
            drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = 0.25f * kk * ambWeight(c[2])), Color.Transparent), b, size.width * 0.38f), size.width * 0.38f, b)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PanelBody(
    state: FlexPanelState,
    amb: List<Color>,
    seekSec: Int,
    show: (String) -> Boolean,
    bar: Int,
    setBar: (Int) -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onSpeed: () -> Unit,
    onSpeedSet: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit
) {
    val gap = Glass.gap
    // 위쪽은 접는 선이라 여백을 크게(힌지 보호 구역)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(0.9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GlassTile(state, amb, Modifier.weight(0.7f), onClose, label = "패널 닫기") { Icon { close(it) } }
            // 컨텍스트 카드: 앱 아이콘·앱 이름 · 제목(2줄까지) · 재생 상태 · 앰비언트 출처 · 꺾쇠
            GlassTile(state, amb, Modifier.weight(4.2f), null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = state.appIcon
                    if (icon != null) {
                        Image(icon, null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                    } else {
                        Text(state.app.take(1).uppercase(), color = Glass.Brand, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (state.title.isNotEmpty()) {
                            Text(state.title, color = state.ink, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(state.subtitle.ifEmpty { state.app }, if (state.playing) "재생 중" else "일시정지됨").joinToString(" · "),
                                color = Glass.Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(state.app, color = state.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(if (state.hasSession) "제목 정보 없음 · 조작은 됩니다" else "재생 정보 없음 · 화면 누르기로 조작", color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.ambSource.isNotEmpty()) {
                        Text(state.ambSource, color = Glass.Muted, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(16) { chevron(it) }
                }
            }
            GlassTile(state, amb, Modifier.weight(0.7f), onLock, selected = state.locked, label = if (state.locked) "영상 잠금 풀기" else "영상 터치 잠금") { Icon(22) { lock(it, state.locked) } }
        }
        Row(Modifier.fillMaxWidth().weight(1.65f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GlassTile(state, amb, Modifier.weight(1f), { onSeek(-1) }, label = "${seekSec}초 뒤로") {
                Icon(34) { seekIcon(it, backward = true, sec = seekSec) }
                SeekBadge(state, back = true)
            }
            GlassTile(state, amb, Modifier.weight(1.2f), onPlayPause, primary = true, label = if (state.playing) "일시정지" else "재생") {
                AnimatedContent(state.playing, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "pp") { playing ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(40) { if (playing) pause(it) else play(it) } }
                }
            }
            GlassTile(state, amb, Modifier.weight(1f), { onSeek(1) }, label = "${seekSec}초 앞으로") {
                Icon(34) { seekIcon(it, backward = false, sec = seekSec) }
                SeekBadge(state, back = false)
            }
        }
        if (show("progress") || bar != 0) {
            GlassTile(state, amb, Modifier.fillMaxWidth().weight(0.75f), null) {
                when (bar) {
                    1 -> GlassSlider(state.brightness, LocalTileInk.current, { "밝기 ${(it * 100).toInt()}%" }, live = true) { onBrightness(it) }
                    2 -> GlassSlider(state.volume, LocalTileInk.current, { "볼륨 ${(it * 100).toInt()}%" }, live = true) { onVolume(it) }
                    3 -> SpeedRow(state.speed) {
                        onSpeedSet(it)
                        setBar(0)
                    }
                    else -> {
                        if (state.canSeek) {
                            val f = (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
                            GlassSlider(f, LocalTileInk.current, { clock((state.duration * it).toLong()) + " / " + clock(state.duration) }, live = false) { onSeekTo(it) }
                        } else {
                            // 진행 정보가 없으면 가짜 진행률을 그리지 않고 이유만 적는다
                            GlassSlider(0f, Glass.Muted, { if (state.hasSession) "이 앱은 위치 이동을 지원하지 않습니다" else "재생 위치 정보를 받지 못했습니다" }, live = false, enabled = false) {}
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.85f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (show("speed")) {
                GlassTile(state, amb, Modifier.weight(1f), onSpeed, onLong = { setBar(if (bar == 3) 0 else 3) }, selected = bar == 3, label = "속도 ${state.speed}x · 길게 눌러 고르기", enabled = state.hasSession) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(22) { gauge(it) }
                        if (state.speed != 1f) Text(" ${state.speed}x", color = Glass.Ink, fontSize = 13.sp)
                    }
                }
            }
            if (show("brightness")) GlassTile(state, amb, Modifier.weight(1f), { setBar(if (bar == 1) 0 else 1) }, selected = bar == 1, label = "밝기") { Icon(22) { sun(it) } }
            if (show("volume")) GlassTile(state, amb, Modifier.weight(1f), { setBar(if (bar == 2) 0 else 2) }, selected = bar == 2, label = "볼륨") { Icon(22) { speaker(it) } }
            if (show("fullscreen")) GlassTile(state, amb, Modifier.weight(1f), onFullscreen, label = "전체 화면") { Icon(22) { expand(it) } }
        }
    }
}

/** 속도 고르기 줄(속도 타일을 길게 누르면). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedRow(now: Float, onPick: (Float) -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
            val on = kotlin.math.abs(s - now) < 0.01f
            Text(
                "${s}x",
                color = if (on) Color.Black else Glass.Ink,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Glass.Ink else Color(0x22FFFFFF)).combinedClickable(onClick = { onPick(s) }).padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
        color = Glass.Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
    )
}

/** 품질 '높음'의 재질 결: 아주 옅은 잡음 무늬(한 번 만들어 둔다). */
private val grain: ImageBitmap by lazy {
    val n = 64
    val r = java.util.Random(7)
    val px = IntArray(n * n) {
        val v = 128 + (r.nextGaussian() * 40).toInt().coerceIn(-127, 127)
        (0x10 shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * 글래스 카드. 한 번의 그리기 안에서 층을 겹친다(층마다 따로 화면 밖 버퍼를 만들지 않는다).
 * 1 유리 색조 → 2 주변광(카드 가로 위치의 앰비언트 색) → 3 재질 결(높음) → 4 위쪽 하이라이트 → 5 안쪽 반사(높음)
 * → 6 테두리 빛(위가 밝고 아래가 어두운 얇은 선) → 7 누름 반응(어두워지고 하이라이트가 내려감) → 8 내용.
 * 품질: 0 끔(단색), 1 낮음(색조·테두리), 2 균형(+주변광·하이라이트), 3 높음(+결·안쪽 반사).
 * 상태: 눌림(작아짐·어두워짐·진동), 선택(주변광 테두리), 비활성(채도·대비 낮춤). 길게 누르면 [label]이 잠깐 뜬다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassTile(
    state: FlexPanelState,
    amb: List<Color>,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    selected: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    onLong: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    var hint by remember { mutableStateOf(false) }
    var xFrac by remember { mutableFloatStateOf(0.5f) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(1200)
            hint = false
        }
    }
    val press by animateFloatAsState(if (pressed) 1f else 0f, if (state.motion) spring(dampingRatio = 0.7f, stiffness = 900f) else snap(), label = "press")
    val shape = RoundedCornerShape(Glass.radius)
    val q = state.quality
    val kk = if (state.ambientOn) ambK(state.intensity) else 0f
    val hc = state.highContrast
    Box(
        modifier.fillMaxHeight()
            .onGloballyPositioned { co ->
                val root = co.findRootCoordinates().size
                rootSize = androidx.compose.ui.unit.IntSize(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1))
                pos = co.positionInRoot()
                xFrac = ((pos.x + co.size.width / 2f) / rootSize.width).coerceIn(0f, 1f)
            }
            // GOAT 리퀴드 눌림(젤리: 옆으로 퍼지고 위아래로 눌렸다 pop 스프링으로 돌아옴, 리퀴드 햅틱). 진동을 끄면 같은 모양을 진동 없이
            .then(if (state.haptics && onClick != null && enabled) Modifier.liquidPress(src, sx = 1.04f, sy = 0.94f) else Modifier)
            .graphicsLayer {
                val s = if (state.haptics) 1f else 1f - 0.045f * press
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(shape)
            .drawBehind {
                val base = if (primary) state.ink else Glass.Tint
                if (q == 0) {
                    drawRect(if (selected || pressed) lerp(base, Color.White, 0.18f) else base)
                    return@drawBehind
                }
                // 1 유리 색조(낮은 불투명도, 고대비면 거의 불투명)
                drawRect(
                    base.copy(
                        alpha = if (hc) {
                            0.97f
                        } else if (primary) {
                            0.94f
                        } else {
                            0.62f
                        }
                    )
                )
                // 2 공간 반사: 바닥과 같은 반사 필드를 패널 좌표 그대로(카드 위치만큼 옮겨) 그려 카드 경계에서 끊기지 않는다.
                //   유리는 반사를 절반쯤 통과시키고, 잉크로 찬 버튼은 표면 광택처럼 옅게 받는다
                val fieldImg = state.field
                if (q >= 2 && kk > 0f && fieldImg != null) {
                    val wk = (kk * ambWeight(amb[3]).coerceAtLeast(0.45f) * (if (primary) 0.22f else 0.55f)).coerceAtMost(0.8f)
                    drawImage(fieldImg, dstOffset = androidx.compose.ui.unit.IntOffset(-pos.x.toInt(), -pos.y.toInt()), dstSize = rootSize, alpha = wk, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
                } else if (q >= 2 && kk > 0f) {
                    val c = ambientAt(xFrac, amb[0], amb[1], amb[2])
                    val wk = kk * ambWeight(c)
                    drawRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.34f * wk), c.copy(alpha = 0.10f * wk))))
                }
                // 3 재질 결
                if (q >= 3) drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
                // 4 위쪽 하이라이트(누르면 아래로 내려가며 약해진다)
                if (q >= 2) {
                    val top = size.height * (0.0f + 0.08f * press)
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f * (1f - 0.5f * press)), Color.Transparent), top, top + size.height * 0.5f))
                }
                // 5 안쪽 반사(왼쪽 위의 은은한 빛)
                if (q >= 3) {
                    val o = Offset(size.width * 0.18f, size.height * 0.1f)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), o, size.minDimension * 0.9f), size.minDimension * 0.9f, o)
                }
                // 7 누름: 살짝 어두워진다 / 선택: 주변광으로 밝아진다
                if (press > 0f) drawRect(Color.Black.copy(alpha = 0.16f * press))
                if (selected) drawRect(Color.White.copy(alpha = 0.10f))
                // 6 테두리 빛: 위는 밝고 아래는 어두운 얇은 선
                val refl = state.sampleAt(xFrac, (pos.y / rootSize.height).coerceIn(0f, 1f))
                val edge = if (selected) (refl ?: ambientAt(xFrac, amb[0], amb[1], amb[2])).copy(alpha = 0.9f) else lerp(Color.White, refl ?: Color.White, 0.35f)
                val r = Glass.radius.toPx()
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            edge.copy(
                                alpha = if (hc) {
                                    0.9f
                                } else if (selected) {
                                    0.85f
                                } else {
                                    0.26f
                                }
                            ),
                            edge.copy(
                                alpha = if (hc) {
                                    0.6f
                                } else if (selected) {
                                    0.4f
                                } else {
                                    0.06f
                                }
                            )
                        )
                    ),
                    Offset(0.5f, 0.5f),
                    Size(size.width - 1f, size.height - 1f),
                    CornerRadius(r),
                    style = Stroke(if (selected) 1.5.dp.toPx() else 1.dp.toPx())
                )
            }
            .then(
                if (onClick != null && enabled) {
                    Modifier.combinedClickable(src, null, onLongClick = {
                        if (state.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (onLong != null) {
                            onLong()
                        } else if (label != null) {
                            hint = true
                        }
                    }) {
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val tileInk = if (primary) {
            Glass.Floor
        } else {
            inkWith(state.ink, state.sampleAt(xFrac, ((pos.y + 1f) / rootSize.height).coerceIn(0f, 1f)), 0.22f * (if (state.ambientOn) 1f else 0f))
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalTileInk provides tileInk) { content() }
        if (hint && label != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(label, color = Glass.Ink, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 오목한 유리 트랙 막대: 트랙 위쪽 안에 그늘, 아래쪽에 빛이 맺혀 파인 홈처럼 보인다. 활성 구간은 [color].
 * [live]면 끄는 동안 바로 바꾸고(밝기·볼륨), 아니면 끄는 동안 값 풍선으로 미리 보여 주고 놓으면 한 번만 바꾼다(재생 위치).
 * 손잡이는 끄는 동안 커진다. 터치는 카드 높이 전체(48dp 이상)에서 받는다.
 */
@Composable
private fun GlassSlider(value: Float, color: Color, label: (Float) -> String, live: Boolean, enabled: Boolean = true, onChange: (Float) -> Unit) {
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
            val h = (if (drag != null) 8 else 6).dp.toPx()
            val cr = CornerRadius(h / 2)
            // 오목한 홈: 바닥 · 위 안쪽 그늘 · 아래 가장자리 빛
            drawRoundRect(Color(0x59000000), Offset(0f, y - h / 2), Size(size.width, h), cr)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent), y - h / 2, y), Offset(0f, y - h / 2), Size(size.width, h), cr)
            drawLine(Color(0x2EFFFFFF), Offset(h / 2, y + h / 2 + 0.5f), Offset(size.width - h / 2, y + h / 2 + 0.5f), 1f)
            if (enabled) {
                drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * shown, h), cr)
                val hx = size.width * shown
                val hr = (if (drag != null) 11 else 8).dp.toPx()
                drawCircle(Color(0x40000000), hr + 2.dp.toPx(), Offset(hx, y + 1.dp.toPx()))
                drawCircle(color, hr, Offset(hx, y))
                drawCircle(Glass.Floor, hr * 0.32f, Offset(hx, y))
                // 끄는 동안 손잡이 위 값 풍선
                if (drag != null) {
                    val text = label(shown)
                    val p = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 12.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val tw = p.measureText(text) + 16.dp.toPx()
                    val bx = (hx - tw / 2).coerceIn(0f, size.width - tw)
                    val by = y - hr - 30.dp.toPx()
                    drawRoundRect(Color(0xE6101420), Offset(bx, by), Size(tw, 22.dp.toPx()), CornerRadius(11.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(text, bx + tw / 2, by + 15.dp.toPx(), p)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(label(shown), color = if (enabled) Glass.Ink else Glass.Muted, fontSize = 12.sp)
    }
}

/**
 * 아이콘(잉크): 카드가 준 잉크색(반사가 얇게 비친 색)으로 그리고, 그린 잉크 위에만 위쪽에서 들어오는 은은한 광택을 얹는다
 * (화면 밖 층에서 SrcAtop으로 합성해 잉크 모양 안에만 맺힌다).
 */
@Composable
private fun Icon(sizeDp: Int = 30, draw: DrawScope.(Color) -> Unit) {
    val ink = LocalTileInk.current
    Canvas(Modifier.size(sizeDp.dp).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
        draw(ink)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), 0f, size.height * 0.6f), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
    }
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
