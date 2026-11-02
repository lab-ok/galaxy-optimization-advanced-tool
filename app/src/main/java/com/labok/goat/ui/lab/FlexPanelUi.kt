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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var canSpeed by mutableStateOf(false)

    /** 속도를 바꿨는데 앱이 받지 않았을 때 보여 줄 말(비면 없음). */
    var speedNote by mutableStateOf("")
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

    /** 반사 대신 포스터 원본을 보여 줄 때(포스터 모드·보호 화면 포스터 연출). */
    var posterShown by mutableStateOf(false)

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

    /** 근거리 고해상도 반사 띠(패널 위 [com.labok.goat.data.lab.ReflectionField.NEAR_FRAC]만큼, 기본 필드 위에 얹는다). */
    var near by mutableStateOf<ImageBitmap?>(null)

    /** 접는 선 바로 위 화면 한 줄(실제 색, 검은 여백이면 검은색). 패널 위 경계를 이 색으로 부드럽게 잇는다. */
    var hinge by mutableStateOf<ImageBitmap?>(null)
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

/** 잉크 가장자리 광택 색: 그 자리 반사를 밝힌 색(반사가 없으면 흰색). */
private val LocalTileGlint = androidx.compose.runtime.compositionLocalOf { Color.White }

/** 장면 전체 색 → 카드 틴트(어두운 유리 밝기 0.26, 채도는 0.07까지). 색이 거의 없으면 null. */
private fun sceneTintOf(c: Color): Color? {
    val o = AmbientColor.toOklab(android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
    val ch = hypot(o[1], o[2])
    if (ch < 0.025f) return null
    val k = minOf(1f, 0.07f / ch)
    return Color(AmbientColor.fromOklab(0.26f, o[1] * k, o[2] * k))
}

/** 반사색을 광택용으로 밝힌다(무채색이면 흰색). */
private fun glintOf(refl: Color?): Color {
    if (refl == null) return Color.White
    val o = AmbientColor.toOklab(android.graphics.Color.rgb((refl.red * 255).toInt(), (refl.green * 255).toInt(), (refl.blue * 255).toInt()))
    // 11-02 실사용 검수: 0.93·채도 70%는 밝은 잉크(#EBEBEB)와 거의 같아 광택이 구별되지 않았다. 조금 낮은 밝기와 채도를 살려 색이 보이게
    return Color(AmbientColor.fromOklab(0.84f, o[1] * 1.1f, o[2] * 1.1f))
}

// ---- 디자인 토큰(11-02 GPT 역설계 권장값 + 소장님 글래스 고도화 지시) ----
// GOAT 리퀴드 다크 팔레트(LiquidTheme의 DarkLiquid)를 그대로 쓴다: 바탕 #0F0F0F, 면 #171717, 선 #424242, 잉크(UiInk), 둥글기 LiquidShape.card.
// 11-02 소장님: 기존 리퀴드 UI와 다른 디자인처럼 보이면 실패 — 1.42.5의 남색 토큰을 GOAT 토큰으로 바꿨다
private object Glass {
    val Floor = Color(0xFF0F0F0F)
    val Tint = Color(0xFF171717)
    val TintPrimary = Color(0xFF222222)
    val Ink = Color(0xFFEBEBEB)

    // 11-02: 장면 색 틴트가 들어간 밝은 카드(주황·파랑) 위에서 #8D8D8D 보조 글자가 묻혔다 — 더 밝은 회색으로
    val Muted = Color(0xFFC4C4C4)
    val Line = Color(0xFF424242)
    val Accent = Color(0xFFEBEBEB)
    val Brand = Color(0xFFE50914)

    // 11-02 최종 통합 지시: 카드 모서리를 더 둥글고 유기적으로(GOAT 카드 22dp → 패널 타일 28dp). 작은 타일은 높이의 절반에서 멈춘다
    val radius = 28.dp
    val gap = 8.dp
}

/**
 * 앰비언트 프리셋(은은·균형·선명·몰입). 11-02 최종 통합 지시: 색 존재감을 30~50% 키우되 값을 한 숫자에 몰지 않고 나눴다.
 * - [k] 바닥 반사·빛의 전체 세기(균형 0.9 → 1.2, +33%)
 * - [reflection] 카드가 반사를 통과시키는 정도(예전 0.6 고정)
 * - [absorption] 유리 바탕의 불투명도: 낮을수록 반사가 더 비친다(예전 0.62 고정)
 * - [ink] 잉크가 반사색에 물드는 정도(예전 0.22 고정)
 * 채도는 반사 필드를 만들 때(AmbientColor.grade) 1.15배·상한 0.22로 이미 올렸다.
 */
private class AmbTone(val k: Float, val reflection: Float, val absorption: Float, val ink: Float)

private val TONES = listOf(
    AmbTone(0.6f, 0.55f, 0.62f, 0.16f),
    AmbTone(1.2f, 0.8f, 0.52f, 0.24f),
    AmbTone(1.45f, 0.92f, 0.46f, 0.28f),
    AmbTone(1.7f, 1.0f, 0.40f, 0.32f)
)

private fun tone(i: Int) = TONES[i.coerceIn(0, TONES.size - 1)]

private fun ambK(i: Int) = tone(i).k

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
    onLock: () -> Unit,
    onEntered: () -> Unit = {},
    onExited: () -> Unit = {}
) {
    var bar by remember { mutableIntStateOf(0) }
    // 앰비언트 색을 프레임마다 목표 쪽으로(엔진의 시간 안정화). 다 따라가면 갱신하지 않는다
    // 실시간 반응(11-02): 보통 변화 0.6초·장면 전환 0.18초로 따라간다(예전 1.2초·0.3초). 새 목표가 오면 그 자리에서 바로 다시 겨냥한다
    val sm = remember { Array(4) { AmbientColor.Smoother(0xFF3A4A6B.toInt(), slowTau = 0.6f, fastTau = 0.18f) } }
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
    // 등장·퇴장(11-02): 등장은 아래에서 짧게 올라오며(12%) 0.97배에서 자리 잡고 투명→불투명(감속 스프링, 약 0.3초),
    // 퇴장은 지금 모습 그대로 아래로 내려가며 투명해진다(0.24초). 끝나면 알려 GoatFlexPanel이 그때 창을 뗀다.
    // 반사·카드·잉크는 모두 이 한 층 안에 있어 같이 움직인다. 움직임을 끄면 바로 바뀌고 바로 알린다
    val k = remember { androidx.compose.animation.core.Animatable(0f) }
    var shownOnce by remember { mutableStateOf(false) }
    LaunchedEffect(state.visible, state.motion) {
        if (state.visible) {
            if (state.motion) k.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 420f)) else k.snapTo(1f)
            shownOnce = true
            onEntered()
        } else if (shownOnce) {
            if (state.motion) k.animateTo(0f, tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing)) else k.snapTo(0f)
            onExited()
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val v = k.value
            alpha = v
            translationY = (1f - v) * size.height * 0.12f
            val sc = 0.97f + 0.03f * v
            scaleX = sc
            scaleY = sc
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }.background(Glass.Floor)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.labok.goat.ui.liquid.LocalReduceMotion provides !state.motion,
            // 조절바는 GOAT 본체의 리퀴드 조절바(LiquidSlider)를 그대로 쓴다 — 패널은 늘 어두운 리퀴드 팔레트
            com.labok.goat.ui.liquid.LocalLiquid provides com.labok.goat.ui.liquid.DarkLiquid,
            LocalTileInk provides state.ink
        ) {
            AmbientBackdrop(state, shown)
            HingeBleed(state)
            PanelBody(state, shown, seekSec, show, bar, { bar = it }, onClose, onPlayPause, onSeek, onSeekTo, onSpeed, onSpeedSet, onBrightness, onVolume, onFullscreen, onLock)
        }
    }
}

/**
 * 접는 선 경계 흐림(11-02 소장님 "패널 맨 위 경계 부드럽게 · 맨 위가 검은 여백이면 자연스럽게 검은색으로, 딱 자르지 말고").
 * 접는 선 바로 위 **실제 화면 한 줄**(반사용으로 다듬기 전, 레터박스를 빼기 전)을 패널 위 32dp에 펴고, 위는 불투명·아래로 갈수록
 * 부드러운 곡선으로 투명하게 깐다. 위 화면이 검은 여백이면 검은색이, 영상이면 그 영상 끝 색이 그대로 이어져 경계선이 없다.
 * 패널 자체를 투명하게 하면 밑에 있는 앱 화면이 비쳐 지저분해서 이 방식을 골랐다.
 */
@Composable
private fun HingeBleed(state: FlexPanelState) {
    val img = state.hinge ?: return
    Canvas(Modifier.fillMaxWidth().height(32.dp).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
        drawImage(
            img,
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
        )
        // 위 1 → 아래 0을 부드러운 곡선(대략 (1-t)^2)으로: 처음엔 거의 화면 그대로, 아래쪽은 아주 천천히 사라진다
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black,
                0.25f to Color.Black.copy(alpha = 0.56f),
                0.5f to Color.Black.copy(alpha = 0.25f),
                0.75f to Color.Black.copy(alpha = 0.06f),
                1f to Color.Transparent
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }
}

/** 포스터를 아주 작게(40칸) 줄인 것: 쌍선형으로 크게 펴면 부드럽게 흐린 바탕이 된다(한 번만 만든다). */
@Composable
private fun rememberPosterBlur(art: ImageBitmap?): ImageBitmap? = remember(art) {
    art?.let {
        val b = it.asAndroidBitmap()
        val w = 40
        val h = (w * b.height / b.width.coerceAtLeast(1)).coerceIn(8, 80)
        android.graphics.Bitmap.createScaledBitmap(b.copy(android.graphics.Bitmap.Config.ARGB_8888, false), w, h, true).asImageBitmap()
    }
}

/**
 * 포스터 표시(11-02 소장님 "포스터는 상하반전 말고 제대로 · 화질 최고 · 경계 부드럽게"). [area]는 패널 전체 크기, [origin]은 그 패널의 왼쪽 위
 * (카드 안에서 그릴 때는 카드 위치만큼 옮긴다). 바닥은 같은 포스터를 크게 흐려 채우고, 그 위에 포스터 원본을 **뒤집지 않고 전체가 보이게**
 * 가운데 놓는다(원본 해상도, 고품질 필터). 포스터 네 가장자리는 투명하게 녹여 흐린 바탕과 경계 없이 이어진다.
 */
private fun DrawScope.drawPoster(art: ImageBitmap, blur: ImageBitmap?, origin: Offset, area: Size, alpha: Float) {
    if (alpha <= 0f) return
    // 1) 흐린 바탕(꽉 채움)
    blur?.let { b ->
        val sc = maxOf(area.width / b.width, area.height / b.height)
        val bw = b.width * sc
        val bh = b.height * sc
        drawImage(
            b,
            dstOffset = androidx.compose.ui.unit.IntOffset((origin.x + (area.width - bw) / 2).toInt(), (origin.y + (area.height - bh) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(bw.toInt(), bh.toInt()),
            alpha = alpha * 0.8f,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
        )
    }
    // 2) 포스터 원본(전체가 보이게, 패널 높이의 96%까지)
    val sc = minOf(area.width / art.width, area.height * 0.96f / art.height)
    val pw = art.width * sc
    val ph = art.height * sc
    val left = origin.x + (area.width - pw) / 2
    val top = origin.y + (area.height - ph) / 2
    val rect = androidx.compose.ui.geometry.Rect(left, top, left + pw, top + ph)
    drawIntoCanvas { cv -> cv.saveLayer(rect, androidx.compose.ui.graphics.Paint()) }
    drawImage(
        art,
        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(pw.toInt(), ph.toInt()),
        alpha = alpha,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High
    )
    // 3) 가장자리 녹이기: 가로·세로로 각각 양 끝 14%를 투명하게(곱해져 모서리는 더 부드럽다)
    val fx = 0.14f
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent, startX = left, endX = left + pw),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
    )
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent, startY = top, endY = top + ph),
        topLeft = rect.topLeft,
        size = rect.size,
        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
    )
    drawIntoCanvas { cv -> cv.restore() }
}

/**
 * 앰비언트 바닥: 포스터를 크게 흐려 깔고(있으면), 좌·중·우 색의 넓은 가로 그라데이션, 위쪽(영상 쪽)에서 흘러내리는 빛,
 * 천천히 떠다니는 빛 두 덩이, 아래로 갈수록 어둡게. 강도 단계로 세기를 바꾼다.
 */
@Composable
private fun AmbientBackdrop(state: FlexPanelState, c: List<Color>) {
    val kk = if (state.ambientOn) ambK(state.intensity) else 0f
    // 시간 전환은 엔진의 FieldBlender가 한다(지금 보이는 상태에서 새 목표로 이어 감). 여기서는 받은 그림을 그대로 그린다
    val field = state.field
    val poster = state.art?.takeIf { state.posterShown }
    val posterBlur = rememberPosterBlur(poster)
    Canvas(Modifier.fillMaxSize()) {
        if (kk > 0f && poster != null) {
            drawPoster(poster, posterBlur, Offset.Zero, size, (0.92f * kk).coerceAtMost(1f))
        } else if (kk > 0f && field != null) {
            // 공간 반사: 위 화면이 바닥에 비친 상을 패널 전체에 깐다(쌍선형 확대). 회색·밝은 화면은 덜 비친다
            val w = (0.92f * kk * ambWeight(c[3]).coerceAtLeast(0.45f)).coerceAtMost(1f)
            val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            drawImage(field, dstSize = dst, alpha = w, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
            // 근거리 띠: 같은 좌표계로 패널 위쪽에 고해상도 반사를 얹는다(띠 아래쪽은 투명해져 기본 필드로 이어진다)
            state.near?.let { n ->
                drawImage(n, dstSize = androidx.compose.ui.unit.IntSize(dst.width, (dst.height * com.labok.goat.data.lab.ReflectionField.NEAR_FRAC).toInt()), alpha = w, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            }
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
    // 창이 시스템 막대를 무시하고 화면 맨 아래까지 덮으므로(접는 선 정렬), 3버튼 내비게이션이 있으면 그 높이만큼 내용을 올린다
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        Row(Modifier.fillMaxWidth().weight(0.9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GlassTile(state, amb, Modifier.weight(0.7f), onClose, label = "패널 닫기") { Icon { close(it) } }
            // 컨텍스트 카드: 앱 아이콘·앱 이름 · 제목(2줄까지) · 재생 상태 · 앰비언트 출처 · 꺾쇠
            GlassTile(state, amb, Modifier.weight(4.2f), null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = state.appIcon
                    if (icon != null) {
                        Image(icon, null, Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)))
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
        if (show("progress")) {
            GlassTile(state, amb, Modifier.fillMaxWidth().weight(0.9f), null, clipContent = false) {
                if (state.canSeek) {
                    val f = (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxSize().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            PanelSlider(f, 1000, { clock(state.duration * it / 1000) }, "재생 위치", live = false) { onSeekTo(it) }
                        }
                        Text(clock(state.position) + " / " + clock(state.duration), color = Glass.Muted, fontSize = 12.sp)
                    }
                } else {
                    // 진행 정보가 없으면 가짜 진행률을 그리지 않고 이유만 적는다
                    GlassSlider(0f, Glass.Muted, { if (state.hasSession) "이 앱은 위치 이동을 지원하지 않습니다" else "재생 위치 정보를 받지 못했습니다" }, live = false, enabled = false) {}
                }
            }
        }
        // 아래 줄: 밝기·음량을 눌러서 여는 대신 그 자리에서 바로 끄는 조절바로 둔다(11-02 소장님). 재생 속도(YouTube 등이 세션으로 받지 않음)와
        // 전체 화면(실기에서 동작하지 않음)은 뺐다
        if (show("brightness") || show("volume")) {
            Row(Modifier.fillMaxWidth().weight(0.9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (show("brightness")) {
                    GlassTile(state, amb, Modifier.weight(1f), null, label = "밝기", clipContent = false) {
                        InlineSlider(state.brightness, "밝기", { sun(it) }) { onBrightness(it) }
                    }
                }
                if (show("volume")) {
                    GlassTile(state, amb, Modifier.weight(1f), null, label = "음량", clipContent = false) {
                        InlineSlider(state.volume, "음량", { speaker(it) }) { onVolume(it) }
                    }
                }
            }
        }
    }
}

/** 아래 줄 조절바: 작은 아이콘 + 리퀴드 조절바(끄는 동안 바로 반영, 손잡이에 % 표시). */
@Composable
private fun InlineSlider(value: Float, desc: String, icon: DrawScope.(Color) -> Unit, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(20) { icon(it) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            PanelSlider(value, 100, { "$it%" }, desc, live = true, onChange = onChange)
        }
    }
}

/**
 * 패널 조절바: GOAT 본체의 리퀴드 조절바([com.labok.goat.ui.liquid.LiquidSlider])를 그대로 쓴다(11-02 소장님 "조절바 리퀴드로").
 * 끄는 동안 손잡이가 납작해지고 값 방울이 떠오른다. [steps] 칸으로 나눠 0~1 값을 주고받는다. [live]면 끄는 동안 바로 반영한다.
 */
@Composable
private fun PanelSlider(value: Float, steps: Int, text: (Int) -> String, desc: String, live: Boolean, onChange: (Float) -> Unit) {
    var v by remember { mutableIntStateOf((value * steps).toInt()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) v = (value * steps).toInt() }
    // 타일 가운데에 둔다(11-02 소장님 "조절바 너무 아래로 내려갔어"). 값 방울은 타일이 내용을 자르지 않아(clipContent = false) 위로 나와도 된다
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        com.labok.goat.ui.liquid.LiquidSlider(
            value = v,
            range = 0..steps,
            onValueChange = {
                dragging = true
                v = it
                if (live) onChange(it / steps.toFloat())
            },
            onValueChangeFinished = {
                dragging = false
                onChange(v / steps.toFloat())
            },
            valueText = text,
            description = desc
        )
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
    /** false면 내용(조절바의 값 방울 등)이 타일 모서리 밖으로 나와도 잘리지 않는다. 재질은 그래도 둥근 모양 안에만 그린다. */
    clipContent: Boolean = true,
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
    val tn = tone(state.intensity)
    val sceneTint = remember(amb[3], state.ambientOn) { if (state.ambientOn) sceneTintOf(amb[3]) else null }
    val posterBlurTile = rememberPosterBlur(state.art?.takeIf { state.posterShown })
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
            // GOAT 리퀴드 눌림(liquidPress와 같은 젤리 모양·pop 스프링). 진동과 움직임을 따로 끈다:
            // 진동 끔 → 진동만 없고 젤리는 그대로, 움직임 끔 → 스프링 없이 눌린 모양으로 바로 바뀜(눌림은 여전히 보임)
            .then(if (onClick != null && enabled) Modifier.panelPress(src, state.haptics, state.motion) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(if (clipContent) Modifier.clip(shape) else Modifier)
            .drawBehind {
                // 재질은 늘 둥근 모양 안에만(내용을 자르지 않는 타일도 바탕·반사·테두리는 모서리를 따른다)
                val tileClip = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
                clipPath(tileClip) {
                    // 재생(주 버튼)도 같은 액상 유리. 잉크로 칠하지 않고 밀도(조금 더 밝은 바탕)·가운데 빛·더 또렷한 가장자리로 구분한다(11-02 소장님)
                    // 카드 표면 틴트: 장면 전체 색을 어두운 유리 색으로 낮춰 섞는다(파란 장면이면 청색, 따뜻한 장면이면 주황 기운이
                    // 카드 자체에서 느껴지게, 11-02 실사용 검수). 무채색 장면은 거의 섞이지 않는다
                    val plain = if (primary) Glass.TintPrimary else Glass.Tint
                    val base = if (sceneTint != null) lerp(plain, sceneTint, 0.38f * (kk / 1.2f).coerceAtMost(1.3f)) else plain
                    if (q == 0) {
                        drawRect(if (selected || pressed) lerp(base, Color.White, 0.18f) else base)
                        return@clipPath
                    }
                    // 1 유리 색조(낮은 불투명도, 고대비면 거의 불투명)
                    drawRect(
                        base.copy(
                            alpha = if (hc) {
                                0.97f
                            } else if (primary) {
                                tn.absorption + 0.12f
                            } else {
                                tn.absorption
                            }
                        )
                    )
                    // 2 공간 반사: 바닥과 같은 반사 필드를 패널 좌표 그대로(카드 위치만큼 옮겨) 그려 카드 경계에서 끊기지 않는다.
                    //   유리는 반사를 절반쯤 통과시키고, 잉크로 찬 버튼은 표면 광택처럼 옅게 받는다
                    val fieldImg = state.field
                    val posterImg = state.art?.takeIf { state.posterShown }
                    if (q >= 2 && kk > 0f && posterImg != null) {
                        // 포스터도 바닥과 같은 좌표계(패널 전체)로 그려 카드 경계에서 끊기지 않는다
                        val wk = (kk * tn.reflection).coerceAtMost(1f)
                        drawPoster(posterImg, posterBlurTile, Offset(-pos.x, -pos.y), Size(rootSize.width.toFloat(), rootSize.height.toFloat()), wk)
                    } else if (q >= 2 && kk > 0f && fieldImg != null) {
                        val wk = (kk * ambWeight(amb[3]).coerceAtLeast(0.45f) * tn.reflection).coerceAtMost(1f)
                        val off = androidx.compose.ui.unit.IntOffset(-pos.x.toInt(), -pos.y.toInt())
                        // 반사가 유리를 통과한다: 보통 합성으로 색 분포를 보이고, 밝은 부분은 '빛 더하기(스크린)'로 한 번 더 얹어 어두운 카드에서도 죽지 않게
                        drawImage(fieldImg, dstOffset = off, dstSize = rootSize, alpha = wk, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
                        state.near?.let { n ->
                            drawImage(n, dstOffset = off, dstSize = androidx.compose.ui.unit.IntSize(rootSize.width, (rootSize.height * com.labok.goat.data.lab.ReflectionField.NEAR_FRAC).toInt()), alpha = wk, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
                        }
                        drawImage(fieldImg, dstOffset = off, dstSize = rootSize, alpha = wk * 0.45f, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low, blendMode = androidx.compose.ui.graphics.BlendMode.Screen)
                        // 내부 굴절: 같은 반사를 살짝 아래·옆으로 밀어 옅게 한 번 더(두께 있는 액체 속을 지나 굽은 빛)
                        if (q >= 3 || primary) {
                            val shift = androidx.compose.ui.unit.IntOffset(-pos.x.toInt() + (rootSize.width * 0.012f).toInt(), -pos.y.toInt() + (rootSize.height * 0.03f).toInt())
                            drawImage(fieldImg, dstOffset = shift, dstSize = rootSize, alpha = wk * 0.25f, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
                        }
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
                    // 가운데 액체 몸통: 가운데가 은은히 밝고 가장자리로 갈수록 살짝 가라앉는다(주 버튼은 조금 더 밝게)
                    if (q >= 2) {
                        val ctr = Offset(size.width / 2, size.height * 0.42f)
                        val rr = size.maxDimension * 0.7f
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (primary) 0.10f else 0.05f), Color.Transparent), ctr, rr), rr, ctr)
                        // 안쪽 가장자리 그늘(두께감): 둥근 테두리 안쪽으로 얇은 그늘
                        val r0 = minOf(Glass.radius.toPx(), size.minDimension / 2f)
                        drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(r0 - 2.dp.toPx()), style = Stroke(3.dp.toPx()))
                    }
                    // 7 누름: 살짝 어두워진다 / 선택: 주변광으로 밝아진다
                    if (press > 0f) drawRect(Color.Black.copy(alpha = 0.16f * press))
                    if (selected) drawRect(Color.White.copy(alpha = 0.10f))
                    // 6 테두리 빛: 위는 밝고 아래는 어두운 얇은 선
                    val refl = state.sampleAt(xFrac, (pos.y / rootSize.height).coerceIn(0f, 1f))
                    val edge = if (selected) (refl ?: ambientAt(xFrac, amb[0], amb[1], amb[2])).copy(alpha = 0.9f) else lerp(Color.White, refl ?: Color.White, 0.35f)
                    // 모서리 반지름은 실제 그려진 모양과 같게(작은 타일은 높이의 절반) — 가장자리 빛·그늘이 둥근 모서리를 그대로 따라간다
                    val r = minOf(Glass.radius.toPx(), size.minDimension / 2f)
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(
                                edge.copy(
                                    alpha = if (hc) {
                                        0.9f
                                    } else if (selected) {
                                        0.85f
                                    } else if (primary) {
                                        0.42f
                                    } else {
                                        0.28f
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
                    // 모서리 광택: 왼쪽 위 둥근 모서리를 따라 맺히는 짧은 빛(그 자리 반사색으로 살짝 물든다). 누르면 약해진다
                    if (q >= 2 && !hc) {
                        val inset = 1.5.dp.toPx()
                        drawArc(
                            lerp(Color.White, refl ?: Color.White, 0.3f).copy(alpha = (if (primary) 0.30f else 0.22f) * (1f - 0.5f * press)),
                            185f,
                            80f,
                            false,
                            Offset(inset, inset),
                            Size(2f * r - 2f * inset, 2f * r - 2f * inset),
                            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
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
        // 잉크: 주 버튼(재생 상태 아이콘)은 가독성이 먼저라 반사를 더 적게(12%), 그 밖은 22%. 가장자리 광택은 그 자리 반사색
        val refl = state.sampleAt(xFrac, ((pos.y + 1f) / rootSize.height).coerceIn(0f, 1f))
        val on = if (state.ambientOn) 1f else 0f
        val tileInk = inkWith(state.ink, refl, (if (primary) 0.12f else tn.ink) * on)
        val glint = if (state.ambientOn) glintOf(refl) else Color.White
        androidx.compose.runtime.CompositionLocalProvider(LocalTileInk provides tileInk, LocalTileGlint provides glint) { content() }
        if (hint && label != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp)) {
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
    val glint = LocalTileGlint.current
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
                // 손잡이 위쪽 가장자리 광택(그 자리 반사색)
                drawArc(glint.copy(alpha = 0.7f), 200f, 140f, false, Offset(hx - hr + 1.5f, y - hr + 1.5f), Size(hr * 2 - 3f, hr * 2 - 3f), style = Stroke(1.5.dp.toPx()))
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
    val glint = LocalTileGlint.current
    Canvas(Modifier.size(sizeDp.dp).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
        draw(ink)
        // 잉크 위쪽 가장자리에 그 자리 반사색 광택이 얇게 맺힌다(잉크 모양 안에만, SrcAtop). 아래쪽은 기본 잉크 그대로라 대비가 유지된다
        drawRect(Brush.verticalGradient(listOf(glint.copy(alpha = 0.55f), glint.copy(alpha = 0.12f), Color.Transparent), 0f, size.height * 0.55f), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
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
    drawRoundRect(c, Offset(s * 0.24f, s * 0.18f), Size(s * 0.18f, s * 0.64f), CornerRadius(s * 0.08f))
    drawRoundRect(c, Offset(s * 0.58f, s * 0.18f), Size(s * 0.18f, s * 0.64f), CornerRadius(s * 0.08f))
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

/**
 * 패널 눌림: GOAT [com.labok.goat.ui.liquid.liquidPress]와 같은 젤리(옆으로 1.04·위아래 0.94, pop 스프링)인데 진동과 움직임을 따로 다룬다.
 * [haptics]가 꺼지면 진동만 없고, [motion]이 꺼지면 스프링 없이 눌린 모양으로 바로 바뀐다.
 */
@Composable
private fun Modifier.panelPress(src: MutableInteractionSource, haptics: Boolean, motion: Boolean): Modifier {
    val pressed by src.collectIsPressedAsState()
    val h = com.labok.goat.ui.liquid.rememberLiquidHaptics()
    val ax = remember { androidx.compose.animation.core.Animatable(1f) }
    val ay = remember { androidx.compose.animation.core.Animatable(1f) }
    var was by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (haptics) {
            if (pressed) {
                h.press()
            } else if (was) {
                h.release()
            }
        }
        was = pressed
        val tx = if (pressed) 1.04f else 1f
        val ty = if (pressed) 0.94f else 1f
        if (!motion) {
            ax.snapTo(tx)
            ay.snapTo(ty)
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { ax.animateTo(tx, com.labok.goat.ui.liquid.Springs.pop(false)) }
                launch { ay.animateTo(ty, com.labok.goat.ui.liquid.Springs.pop(false)) }
            }
        }
    }
    return this.graphicsLayer {
        scaleX = ax.value
        scaleY = ay.value
    }
}
