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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.core.R
import com.labok.goat.data.lab.AmbSource
import com.labok.goat.data.lab.AmbientColor
import com.labok.goat.ui.text.Copy
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
    var ambLeft by mutableIntStateOf(NEUTRAL)
    var ambCenter by mutableIntStateOf(NEUTRAL)
    var ambRight by mutableIntStateOf(NEUTRAL)
    var ambAll by mutableIntStateOf(NEUTRAL)

    /** 앰비언트 색이 어디서 왔는지(화면 분석·포스터·앱 색·끔, 보호 콘텐츠면 그 사실). */
    var ambSource by mutableStateOf("")
    var ambKind by mutableStateOf<AmbSource?>(null)

    /** 지금 앰비언트 방식(0 없음·1 앱 색상·2 포스터·3 실시간)과, 머리글 칩을 눌러 연 고르기 탭이 열려 있는지. */
    var ambMode by mutableIntStateOf(3)
    var ambPicker by mutableStateOf(false)

    /** 실제 반사 갱신 수(초당, 5초 평균). 실시간일 때 머리글 칩에 표시해 실제 속도를 바로 확인한다. */
    var ambFps by mutableFloatStateOf(0f)

    /** 버튼 유리 블러(dp, 실험실 설정). */
    var frostDp by mutableFloatStateOf(60f)

    /** 버튼 유리 블러를 미리 흐린 반사(기본 해상도). 있으면 셰이더가 이것만 한 번 읽는다. */
    var frost by mutableStateOf<ImageBitmap?>(null)

    /** 첫 앰비언트 결과(화면 분석·포스터·앱 색)가 들어왔는지. 그 전에는 대체 그라데이션·빛을 그리지 않고 중립 유리로 둔다. */
    var ambReady by mutableStateOf(false)

    /** 반사 대신 포스터 원본을 보여 줄 때(포스터 모드·보호 화면 포스터 연출). */
    var posterShown by mutableStateOf(false)

    /** 무엇을 보일지: 1 앱 아이콘(앱 색상), 2 포스터(포스터·썸네일). */
    var posterKind by mutableIntStateOf(0)

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

/** 두 색이 눈에 띄게 다른지(어느 채널이든 2/255 넘게). */
private fun visiblyDiffers(a: Color, b: Color): Boolean = kotlin.math.abs(a.red - b.red) > 2f / 255f || kotlin.math.abs(a.green - b.green) > 2f / 255f || kotlin.math.abs(a.blue - b.blue) > 2f / 255f

/** 앰비언트가 준비되기 전의 중립 색(GOAT 다크 유리 면 #171717). */
const val NEUTRAL = 0xFF171717.toInt()

/** 카드 뒤 반사의 밝기(0~1): 아이콘 halo·글자 그림자 세기에 쓴다. */
private val LocalTileBg = androidx.compose.runtime.compositionLocalOf { 0.1f }

/** 반사색 밝기 → 그림자 세기(0~1). 반사가 어두우면 0에 가깝고, 밝을수록(OKLab L 0.35→0.8) 1로. */
private fun backLuma(c: Color, primary: Boolean): Float {
    val o = AmbientColor.toOklab(android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
    return (((o[0] - 0.35f) / 0.45f).coerceIn(0f, 1f) * (if (primary) 1f else 0.9f))
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
    // 11-03 재질 계층: 버튼은 바닥보다 더 불투명한 '조작 표면'으로(흡수 +0.22). 바닥은 반대로 더 비친다(AmbientBackdrop)
    AmbTone(0.6f, 0.55f, 0.82f, 0.16f),
    AmbTone(1.2f, 0.8f, 0.74f, 0.24f),
    AmbTone(1.45f, 0.92f, 0.68f, 0.28f),
    AmbTone(1.7f, 1.0f, 0.62f, 0.32f)
)

private fun tone(i: Int) = TONES[i.coerceIn(0, TONES.size - 1)]

private fun ambK(i: Int) = tone(i).k

/**
 * 주변광 색의 비침 세기 보정: 무채색(회색·흰 화면)은 적게, 밝은 색도 조금 덜 비친다. 밝은 설정 화면 위에서 패널 전체가 회색으로
 * 바래 어두운 유리 느낌이 사라졌다(11-02 에뮬레이터 비교). 색이 있는 장면만 또렷하게 번진다.
 */
private fun ambWeight(c: Color): Float {
    val o = AmbientColor.toOklab(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
    val chroma = (hypot(o[1], o[2]) / 0.08f).coerceIn(0f, 1f)
    // 밝은 화면은 덜 비친다. 11-06 소장님 "패널 번쩍번쩍": 예전에는 밝기 0.6을 넘는 순간 1 → 0.6으로 계단처럼 바뀌어,
    // 밝기가 0.6 근처인 야외 장면에서 대표색이 조금만 흔들려도 패널 전체 반사가 40%씩 켜졌다 꺼졌다 했다. 0.5~0.7 사이를 부드럽게 잇는다
    val t = ((o[0] - 0.5f) / 0.2f).coerceIn(0f, 1f)
    val light = 1f - 0.4f * t * t * (3f - 2f * t)
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
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onLock: () -> Unit,
    onEntered: () -> Unit = {},
    onExited: () -> Unit = {}
) {
    // 앰비언트 색을 프레임마다 목표 쪽으로(엔진의 시간 안정화). 다 따라가면 갱신하지 않는다
    // 실시간 반응(11-02): 보통 변화 0.6초·장면 전환 0.18초로 따라간다(예전 1.2초·0.3초). 새 목표가 오면 그 자리에서 바로 다시 겨냥한다
    // 11-02 첫 등장 깜박임: 예전에는 슬레이트 블루(#3A4A6B)에서 시작해 앱 틴트(YouTube 빨강)와 섞이며 분홍·자주로 보였다가
    // 실제 반사색으로 바뀌었다. 이제 중립 다크 유리(#171717)에서 시작하고, 첫 분석 결과가 오면 그쪽으로 이어 간다
    val sm = remember { Array(4) { AmbientColor.Smoother(NEUTRAL, slowTau = 0.35f, fastTau = 0.15f) } }
    var shown by remember { mutableStateOf(List(4) { Color(NEUTRAL) }) }
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
                // 눈에 띄는 변화(채널 2/255 이상)일 때만 바꾼다: 아주 작은 변화마다 패널 전체가 다시 구성되던 것을 줄인다(11-02 최적화)
                if (next.indices.any { visiblyDiffers(next[it], shown[it]) }) {
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
            PanelBody(state, shown, seekSec, show, onClose, onPlayPause, onSeek, onSeekTo, onBrightness, onVolume, onLock)
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

/**
 * 포스터 블러(11-02 소장님 "고화질 포스터에 블러를 전체적으로 먹여서"): 포스터를 가로 240칸으로 줄이고 상자 흐림 3번(가우시안 근사,
 * 반지름 9칸)을 한 번만 걸어 둔다. 크게 펴도 계단 없이 부드럽다. 포스터가 바뀔 때만 다시 만든다.
 */
@Composable
private fun rememberPosterBlur(art: ImageBitmap?, icon: Boolean): ImageBitmap? = remember(art, icon) {
    art?.let {
        val b = it.asAndroidBitmap()
        val w = 240
        val h = (w * b.height / b.width.coerceAtLeast(1)).coerceIn(16, 480)
        val small = android.graphics.Bitmap.createScaledBitmap(b.copy(android.graphics.Bitmap.Config.ARGB_8888, false), w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val out = if (icon) {
            // 앱 아이콘(11-02 소장님 "앱 색은 앱 아이콘 색으로 영롱한 블러"): 투명한 바탕은 어둡게 채우고, 훨씬 크게(반지름 22칸) 흐려
            // 아이콘 모양이 아니라 색 덩어리가 번지게 한 뒤, 채도 1.5배·밝기를 조금 올려 빛나는 느낌을 준다
            for (i in px.indices) {
                val a = (px[i] ushr 24) / 255f
                val r = ((px[i] shr 16 and 0xFF) * a + 15 * (1 - a)).toInt()
                val g = ((px[i] shr 8 and 0xFF) * a + 15 * (1 - a)).toInt()
                val bb = ((px[i] and 0xFF) * a + 15 * (1 - a)).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bb
            }
            val blurred = com.labok.goat.data.lab.ReflectionField.blur(px, w, h, 22f)
            IntArray(blurred.size) { i ->
                val o = AmbientColor.toOklab(blurred[i])
                // 11-02 소장님 "앱 아이콘 색으로 패널색 잡는 건 좀 더 어둡게": 밝기는 낮추고(최대 0.42) 색은 살린다
                AmbientColor.fromOklab((o[0] * 0.62f).coerceAtMost(0.42f), o[1] * 1.35f, o[2] * 1.35f)
            }
        } else {
            // 1.42.16 실화면에서 반지름 3칸은 포스터 형태가 거의 그대로 보였다 — 전체적으로 흐리게 9칸(소장님 "블러 전체적으로")
            com.labok.goat.data.lab.ReflectionField.blur(px, w, h, 9f)
        }
        autoDim(out)
        android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * 밝기 자동 낮춤(11-02 소장님 "포스터나 앱 색이 너무 밝으면 자동으로 좀 낮춰줘"). 흐린 그림의 평균 밝기(OKLab L)가 [MAX_MEAN]을 넘으면
 * 평균이 그 값이 되게 모든 칸의 밝기를 같은 비율로 낮추고, 아주 밝은 칸은 [MAX_PEAK]에서 부드럽게 눌러 패널이 하얗게 뜨지 않게 한다.
 * 색(채도)은 그대로라 포스터 분위기는 남는다. 어두운 그림은 건드리지 않는다.
 */
private fun autoDim(px: IntArray) {
    val labs = Array(px.size) { AmbientColor.toOklab(px[it]) }
    val mean = labs.sumOf { it[0].toDouble() }.toFloat() / labs.size.coerceAtLeast(1)
    val k = if (mean > MAX_MEAN) MAX_MEAN / mean else 1f
    for (i in px.indices) {
        val o = labs[i]
        var l = o[0] * k
        if (l > MAX_PEAK) l = MAX_PEAK + (l - MAX_PEAK) * 0.3f
        if (k < 1f || o[0] > MAX_PEAK) px[i] = AmbientColor.fromOklab(l, o[1], o[2])
    }
}

private const val MAX_MEAN = 0.42f
private const val MAX_PEAK = 0.62f

/** 포스터 자리에 보일 그림: 포스터를 쓸 때는 포스터, 앱 색 대체일 때는 앱 아이콘. 그 밖에는 null(반사를 그린다). */
private fun posterSource(state: FlexPanelState): ImageBitmap? = when {
    !state.posterShown -> null
    // 앱 색상: 포스터가 있어도 앱 아이콘만
    state.posterKind == 1 -> state.appIcon
    // 포스터: 포스터(없을 때만 앱 아이콘으로 대신 — 그때 출처 표시는 "앱 색"이 된다)
    else -> state.art ?: state.appIcon
}

/** 지금 보일 그림이 앱 아이콘인지(아이콘은 크게 흐리고 어둡게 처리한다). */
private fun iconShown(state: FlexPanelState) = state.posterKind == 1 || state.art == null

/**
 * 포스터 표시: 흐린 포스터를 패널 전체에 꽉 채운다(뒤집지 않음). [area]는 패널 전체 크기, [origin]은 그 패널의 왼쪽 위
 * (카드 안에서 그릴 때는 카드 위치만큼 옮겨 바닥과 같은 좌표계). 패널 위쪽 16%는 투명에서 서서히 나타나게 해서
 * 포스터가 접는 선에서 잘린 것처럼 보이지 않게 한다(그 위는 접는 선 경계 번짐이 위 화면 색으로 잇는다).
 */
private fun DrawScope.drawPoster(blur: ImageBitmap?, origin: Offset, area: Size, alpha: Float) {
    val b = blur ?: return
    if (alpha <= 0f) return
    val sc = maxOf(area.width / b.width, area.height / b.height)
    val bw = b.width * sc
    val bh = b.height * sc
    val bounds = androidx.compose.ui.geometry.Rect(origin, area)
    drawIntoCanvas { cv -> cv.saveLayer(bounds, androidx.compose.ui.graphics.Paint()) }
    drawImage(
        b,
        dstOffset = androidx.compose.ui.unit.IntOffset((origin.x + (area.width - bw) / 2).toInt(), (origin.y + (area.height - bh) / 2).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(bw.toInt(), bh.toInt()),
        alpha = alpha,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
    )
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = origin.y, endY = origin.y + area.height * 0.16f),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
    )
    drawIntoCanvas { cv -> cv.restore() }
}

/**
 * 리퀴드 글래스 셰이더가 실제로 도는지 진단 로그에 한 번 남긴다(11-02: 실기 화면이 에뮬레이터와 달라 보였는데, 진단 파일에는 GOAT 태그만
 * 들어가 셰이더 실패 여부를 확인할 수 없었다).
 */
@Volatile private var glassLogged = false

private fun glassLog(context: android.content.Context, ok: Boolean) {
    if (glassLogged) return
    glassLogged = true
    // 앱 로그(진단 파일의 「앱 로그」)에 남긴다 — logcat은 진단 파일에 최근 300줄만 들어가 1.42.23에서 빠졌다
    com.labok.goat.data.log.LogRepository.from(context).add("GOAT 플렉스 패널: 리퀴드 글래스 셰이더 ${if (ok) "동작" else "만들지 못해 예전 방식으로 그림"} (Android ${android.os.Build.VERSION.SDK_INT})")
}

/** 반사 색 보정(채도 1.22·대비 1.18, 가운데 0.48. 11-03: 채도 1.3이 초록 같은 한 색을 넓게 덮어 구조가 묻혀, 채도를 조금 낮추고 대비로 구조를 살렸다) — 예전 CPU의 AmbientColor.grade를 GPU 색 행렬로 근사. 바닥·근거리 띠가 같이 쓴다. */
fun reflectionGrade(): androidx.compose.ui.graphics.ColorFilter {
    val sat = 1.22f
    val lr = 0.2126f * (1 - sat)
    val lg = 0.7152f * (1 - sat)
    val lb = 0.0722f * (1 - sat)
    val c = 1.18f
    val t = 0.48f * (1 - c) * 255f
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                c * (lr + sat), c * lg, c * lb, 0f, t,
                c * lr, c * (lg + sat), c * lb, 0f, t,
                c * lr, c * lg, c * (lb + sat), 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/**
 * 근거리 고해상도 띠(바닥): GPU에서 블러(약 2dp)와 색 보정(채도 1.3·대비 1.12, 기본 필드와 같은 보정)을 걸어 그린다.
 * CPU는 뒤집기·세기만 해서 실기 프레임마다 분석이 밀리지 않는다(11-02).
 */
@Composable
private fun NearLayer(state: FlexPanelState, alpha: Float) {
    val n = state.near ?: return
    val cf = remember { reflectionGrade() }
    // 블러는 반사를 만들 때 줄마다 거리 곡선대로 이미 들어가 있다(GPU 일정 블러는 곡선과 어긋나 뺐다, 11-02)
    Canvas(
        Modifier.fillMaxWidth().fillMaxHeight(com.labok.goat.data.lab.ReflectionField.NEAR_FRAC)
    ) {
        drawImage(n, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), alpha = alpha, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium, colorFilter = cf)
    }
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
    val poster = posterSource(state)
    val posterBlur = rememberPosterBlur(poster, iconShown(state))
    val gradeCf = remember { reflectionGrade() }
    Canvas(Modifier.fillMaxSize()) {
        if (kk > 0f && poster != null) {
            drawPoster(posterBlur, Offset.Zero, size, (0.92f * kk).coerceAtMost(1f))
        } else if (kk > 0f && field != null) {
            // 공간 반사: 위 화면이 바닥에 비친 상을 패널 전체에 깐다(쌍선형 확대). 회색·밝은 화면은 덜 비친다
            // 11-03 재질 계층: 바닥은 더 비치게(0.92 → 1.0배, 아래 어둡힘 45% → 30%)
            val w = (1.0f * kk * ambWeight(c[3]).coerceAtLeast(0.45f)).coerceAtMost(1f)
            val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            drawImage(field, dstSize = dst, alpha = w, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low, colorFilter = gradeCf)
            // 근거리 띠: 같은 좌표계로 패널 위쪽에 고해상도 반사를 얹는다(띠 아래쪽은 투명해져 기본 필드로 이어진다)
            // 근거리 띠는 아래 NearLayer가 GPU 블러·색 보정으로 따로 그린다
        } else if (kk > 0f && state.ambReady) {
            drawRect(Brush.horizontalGradient(listOf(c[0].copy(alpha = 0.38f * kk * ambWeight(c[0])), c[1].copy(alpha = 0.30f * kk * ambWeight(c[1])), c[2].copy(alpha = 0.38f * kk * ambWeight(c[2])))))
            // 영상(위)에서 흘러내리는 빛
            drawRect(Brush.verticalGradient(listOf(c[3].copy(alpha = (0.5f * kk * ambWeight(c[3])).coerceAtMost(0.75f)), Color.Transparent), 0f, size.height * 0.55f))
        }
        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x4D000000))))
    }
    if (kk > 0f && field != null && poster == null) NearLayer(state, (1.0f * kk * ambWeight(c[3]).coerceAtLeast(0.45f)).coerceAtMost(1f))
    // 떠다니는 빛 두 덩이: 한 번 그려 둔 층을 옮기기만 한다(움직이는 동안 다시 그리지 않아 가볍다). 품질 '높음'·애니메이션 켬일 때만 움직인다
    if (kk > 0f && field == null && state.ambReady) GlowLayer(c, kk, state.motion && state.quality >= 3)
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
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
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
            GlassTile(state, amb, Modifier.weight(0.7f), onClose, label = stringResource(R.string.lab_panel_close), shape = SuperellipseShape.Capsule) { Icon { close(it) } }
            // 컨텍스트 카드: 앱 아이콘·앱 이름 · 제목(2줄까지) · 재생 상태 · 앰비언트 출처 · 꺾쇠
            GlassTile(state, amb, Modifier.weight(4.2f), null, shape = SuperellipseShape.Capsule) {
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
                                listOf(state.subtitle.ifEmpty { state.app }, if (state.playing) stringResource(R.string.lab_panel_playing) else stringResource(R.string.lab_panel_paused)).joinToString(" · "),
                                color = Glass.Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(state.app, color = state.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(if (state.hasSession) stringResource(R.string.lab_panel_no_title) else stringResource(R.string.lab_panel_no_session), color = Glass.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.ambSource.isNotEmpty()) {
                        // 누르면 앰비언트 고르기 탭이 열린다(11-02 소장님)
                        Text(
                            if (state.ambMode == 3 && state.ambFps > 0f && state.ambKind == AmbSource.SCREEN && com.labok.goat.data.lab.LabUnlock.unlocked) stringResource(R.string.lab_panel_ambient_live_fps, state.ambFps.toInt()) else state.ambSource,
                            // 상태 표시는 정보 계층 맨 아래(11-03): 보조 회색
                            color = if (state.ambPicker) Glass.Ink else Glass.Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (state.ambPicker) Color(0x44FFFFFF) else Color(0x22FFFFFF))
                                .clickable { state.ambPicker = !state.ambPicker }.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(16) { chevron(it) }
                }
            }
            GlassTile(state, amb, Modifier.weight(0.7f), onLock, shape = SuperellipseShape.Capsule, selected = state.locked, label = if (state.locked) stringResource(R.string.lab_panel_unlock_video) else stringResource(R.string.lab_panel_lock_video)) { Icon(22) { lock(it, state.locked) } }
        }
        Row(Modifier.fillMaxWidth().weight(1.65f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GlassTile(state, amb, Modifier.weight(1f), { onSeek(-1) }, label = stringResource(R.string.lab_panel_seek_back, seekSec)) {
                Icon(38) { seekIcon(it, backward = true, sec = seekSec) }
                SeekBadge(state, back = true)
            }
            GlassTile(state, amb, Modifier.weight(1.2f), onPlayPause, primary = true, label = if (state.playing) stringResource(R.string.lab_panel_pause) else stringResource(R.string.lab_panel_play)) {
                AnimatedContent(state.playing, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "pp") { playing ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(48) { if (playing) pause(it) else play(it) } }
                }
            }
            GlassTile(state, amb, Modifier.weight(1f), { onSeek(1) }, label = stringResource(R.string.lab_panel_seek_forward, seekSec)) {
                Icon(38) { seekIcon(it, backward = false, sec = seekSec) }
                SeekBadge(state, back = false)
            }
        }
        if (show("progress") || state.ambPicker) {
            GlassTile(state, amb, Modifier.fillMaxWidth().weight(0.9f), null, clipContent = false, shape = SuperellipseShape.Capsule) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                if (state.ambPicker) {
                    AmbientTabs(state.ambMode) { m -> com.labok.goat.data.lab.GoatFlexPanel.pickAmbient(ctx, m) }
                } else if (state.canSeek) {
                    val f = (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxSize().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            PanelSlider(f, 1000, { clock(state.duration * it / 1000) }, stringResource(R.string.lab_panel_position), live = false) { onSeekTo(it) }
                        }
                        // 11-03 정보 계층: 지금 위치(값)는 진하고 크게, 전체 길이(보조)는 옅게
                        Text(clock(state.position), color = Glass.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(" / " + clock(state.duration), color = Glass.Muted, fontSize = 12.sp)
                    }
                } else {
                    // 진행 정보가 없으면 가짜 진행률을 그리지 않고 이유만 적는다
                    // 손잡이·0%를 그리지 않는다(실제 0%로 오해). 짧게 이유만
                    GlassSlider(0f, Glass.Muted, { if (state.hasSession) Copy.text(R.string.lab_panel_seek_unsupported) else Copy.text(R.string.lab_panel_no_position) }, live = false, enabled = false) {}
                }
            }
        }
        // 아래 줄: 밝기·음량을 눌러서 여는 대신 그 자리에서 바로 끄는 조절바로 둔다(11-02 소장님). 재생 속도(YouTube 등이 세션으로 받지 않음)와
        // 전체 화면(실기에서 동작하지 않음)은 뺐다
        if (show("brightness") || show("volume")) {
            Row(Modifier.fillMaxWidth().weight(0.9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (show("brightness")) {
                    GlassTile(state, amb, Modifier.weight(1f), null, label = stringResource(R.string.lab_panel_brightness), clipContent = false, shape = SuperellipseShape.Capsule) {
                        InlineSlider(state.brightness, stringResource(R.string.lab_panel_brightness), { sun(it) }) { onBrightness(it) }
                    }
                }
                if (show("volume")) {
                    GlassTile(state, amb, Modifier.weight(1f), null, label = stringResource(R.string.lab_panel_volume), clipContent = false, shape = SuperellipseShape.Capsule) {
                        InlineSlider(state.volume, stringResource(R.string.lab_panel_volume), { speaker(it) }) { onVolume(it) }
                    }
                }
            }
        }
    }
}

/**
 * 앰비언트 고르기 리퀴드 탭(11-02 소장님 "실시간 앰비언트·포스터(썸네일)·앱 색상·없음 4개"). 선택 표시 알약이 스프링으로 미끄러지며,
 * 움직이는 동안 진행 방향으로 살짝 늘어났다가(젤리) 자리에서 돌아온다.
 */
@Composable
private fun AmbientTabs(mode: Int, onPick: (Int) -> Unit) {
    val items = listOf(3 to stringResource(R.string.lab_panel_ambient_live), 2 to stringResource(R.string.lab_panel_ambient_poster), 1 to stringResource(R.string.lab_panel_ambient_app_color), 0 to stringResource(R.string.lab_panel_ambient_none))
    val sel = items.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    val pos by animateFloatAsState(sel.toFloat(), spring(dampingRatio = 0.72f, stiffness = 520f), label = "tab")
    val ink = LocalTileInk.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp)) {
        val segW = maxWidth / items.size
        val stretch = 1f + (kotlin.math.abs(pos - sel) * 0.35f).coerceAtMost(0.35f)
        Box(
            Modifier.offset(x = segW * pos).width(segW).fillMaxHeight().graphicsLayer { scaleX = stretch }
                .clip(SuperellipseShape.Capsule).background(ink.copy(alpha = 0.92f))
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, (m, label) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(SuperellipseShape.Capsule).clickable { onPick(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (i == sel) Color(0xFF111111) else ink, fontSize = 13.sp, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
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
        stringResource(if (v > 0) R.string.lab_panel_seek_badge_forward else R.string.lab_panel_seek_badge_back, kotlin.math.abs(v)),
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
/**
 * 버튼 곡면 광학 모델(11-03 소장님 "Aspect-Ratio-Aware Liquid Optics"). 버튼을 짧은 축으로 휜 유리 쿠션으로 보고, 크기(짧은 변 dp)와
 * 종횡비(긴 변/짧은 변)로 빛 분포를 연속으로 정한다. 상수 묶음을 버튼 종류마다 두지 않고 한 식으로 보간한다.
 * - [k] 크기 0(작은 캡슐 ≈ 44dp)~1(큰 쿠션 ≈ 120dp): 클수록 솟은 범위·광택 자국·굴절 띠가 넓어진다(밝기를 통째로 올리지 않는다)
 * - [e] 길쭉함 0(정사각)~1(4:1 이상): 길수록 곡률은 짧은 축에만 — 솟음은 얕고 넓게, 광택 자국은 긴 가장자리를 따라 가늘고 길게
 * 가장자리 선 광택은 쓰지 않는다(11-02 회색 선·11-03 볼록 지적). 모두 면의 밝기 분포다.
 */
private class TileOptics(w: Float, h: Float, density: Float, primary: Boolean) {
    private val shortDp = minOf(w, h) / density
    val k = ((shortDp - 44f) / 76f).coerceIn(0f, 1f)
    val e = ((maxOf(w, h) / minOf(w, h).coerceAtLeast(1f) - 1f) / 3f).coerceIn(0f, 1f)

    // 얕게 만드는 것은 '작고 긴' 카드(헤더·진행·밝기)에만 — 큰 버튼은 길쭉해도 넓은 쿠션이다(1차 측정: 가로 화면의 큰 버튼이 1.44.10보다 어두워짐)
    private val thin = e * (1f - k)

    // 위 광택 띠: 세로(짧은 축) 곡률을 따라 높이 비율로. 길쭉한 카드는 얕게
    val sheenExtent = h * (0.30f + 0.25f * k) * (1f - 0.25f * thin)
    val sheenAlpha = 0.13f + 0.03f * k

    // 가운데 솟음: 축마다 반경을 따로 — 긴 축은 너비, 짧은 축은 높이 비례. 길쭉할수록 얕게(중심 발광 과다 방지)
    val liftRx = w * (0.50f + 0.20f * k)

    // 짧은 축 반경은 높이의 75~100%: 3차 측정에서 60%면 아래쪽이 1.44.10보다 7~10 어두워졌다(하단이 죽어 보임)
    val liftRy = h * (0.75f + 0.25f * k) * (1f - 0.2f * thin)
    val liftCy = h * (0.42f - 0.06f * k)

    // 1.44.10의 왼쪽 위 안쪽 빛(0.07)을 빼고 그만큼을 가운데 솟음으로 옮겼다 — 전체 밝기는 유지, 분포만 곡면으로
    val liftAlpha = (0.07f + 0.09f * k) * (1f - 0.35f * thin) + if (primary) 0.04f else 0f

    // 광택 자국: 높이는 짧은 축 비례, 너비는 길쭉할수록 늘어난다(긴 가장자리를 따라 맺히는 반사)
    val specRy = h * (0.16f + 0.10f * k)
    val specRx = (specRy * (1.6f + 2.4f * e)).coerceAtMost(w * 0.45f)
    val specCy = h * (0.12f + 0.04f * k)
    val specAlpha = 0.06f + 0.05f * k

    // 아래 가라앉음: 진하게가 아니라 넓게, 길쭉한 카드는 더 옅게(짙은 가장자리 그림자 방지)
    val sinkExtent = h * (0.18f + 0.20f * k)
    val sinkAlpha = 0.025f * (1f - 0.3f * thin)

    // 셰이더 가장자리 굴절 띠: 짧은 변의 28%(14~28dp) — 작은 캡슐은 좁고 선명, 큰 쿠션은 넓고 완만.
    // 2차 측정: 20%(80dp 버튼에서 16dp)는 1.44.10의 22dp보다 좁아 가장자리 Fresnel 빛이 줄고 아래가 어두워졌다
    val bevelDp = (shortDp * 0.28f).coerceIn(14f, 28f)
}

/** 부드러운 타원 빛: 원형 방사 그라데이션을 축마다 늘려 그린다([mid]는 반경 55% 지점의 상대 세기). */
private fun DrawScope.softEllipse(c: Offset, rx: Float, ry: Float, color: Color, alpha: Float, mid: Float) {
    if (alpha <= 0f || rx <= 0f || ry <= 0f) return
    val r = maxOf(rx, ry)
    withTransform({ scale(rx / r, ry / r, c) }) {
        drawCircle(Brush.radialGradient(0f to color.copy(alpha = alpha), 0.55f to color.copy(alpha = alpha * mid), 1f to Color.Transparent, center = c, radius = r), r, c)
    }
}

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
    /** 모양: 큰 미디어 버튼은 [SuperellipseShape.Media], 헤더·닫기·잠금·진행·아래 줄은 캡슐. */
    shape: SuperellipseShape = SuperellipseShape.Media,
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
    val tn = tone(state.intensity)
    val sceneTint = remember(amb[3], state.ambientOn) { if (state.ambientOn) sceneTintOf(amb[3]) else null }
    val posterBlurTile = rememberPosterBlur(posterSource(state), iconShown(state))
    // 리퀴드 글래스 셰이더(API 33+, 카드마다 하나). 만들 수 없으면(구형 기기·셰이더 컴파일 실패) 예전 겹친 레이어로 그린다
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val glass = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching { GlassShader() }.onFailure { android.util.Log.w("GOATGLASS", "셰이더 생성 실패: ${it.message}") }.getOrNull().also { glassLog(appCtx, it != null) }
        } else {
            null
        }
    }
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
                    // 버튼 크기 0(작은 캡슐 ≈ 44dp)~1(큰 버튼 ≈ 120dp): 곡면 광학을 크기에 맞춰 키운다(아래 4·몸통, 셰이더 안쪽 그늘)
                    val sizeK = ((size.minDimension / density - 44f) / 76f).coerceIn(0f, 1f)
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
                                tn.absorption + 0.16f
                            } else {
                                tn.absorption
                            }
                        )
                    )
                    // 2 공간 반사: 바닥과 같은 반사 필드를 패널 좌표 그대로(카드 위치만큼 옮겨) 그려 카드 경계에서 끊기지 않는다.
                    //   유리는 반사를 절반쯤 통과시키고, 잉크로 찬 버튼은 표면 광택처럼 옅게 받는다
                    val fieldImg = state.field
                    val posterImg = posterSource(state)
                    if (q >= 2 && kk > 0f && posterImg != null) {
                        // 포스터도 바닥과 같은 좌표계(패널 전체)로 그려 카드 경계에서 끊기지 않는다
                        val wk = (kk * tn.reflection).coerceAtMost(1f)
                        drawPoster(posterBlurTile, Offset(-pos.x, -pos.y), Size(rootSize.width.toFloat(), rootSize.height.toFloat()), wk)
                    }
                    val shaderOn = glass != null && q >= 2 && !hc && android.os.Build.VERSION.SDK_INT >= 33
                    if (shaderOn) {
                        // 반사(굴절·분산 포함)와 테두리 빛·Fresnel·안쪽 그늘을 셰이더 한 번으로. 포스터를 보일 때는 반사 대신 테두리 빛만
                        val useField = kk > 0f && fieldImg != null && posterImg == null
                        // 카드는 바닥보다 반사를 덜 통과시킨다(유리 두께감 — 1.42.15에서 카드가 반사로 꽉 차 유리로 안 보였다).
                        // 11-03 재질 계층: 0.72 → 0.45, 상한 0.85 → 0.6 — 바닥은 '공간', 버튼은 그 위에 뜬 '조작 표면'으로 읽히게
                        val wk = if (useField) (kk * ambWeight(amb[3]).coerceAtLeast(0.45f) * tn.reflection * 0.45f).coerceAtMost(0.6f) else 0f
                        glass!!.update(
                            if (useField) fieldImg else null,
                            if (useField) state.near else null,
                            pos,
                            rootSize.width.toFloat(),
                            rootSize.height.toFloat(),
                            com.labok.goat.data.lab.ReflectionField.NEAR_FRAC,
                            size.width,
                            size.height,
                            shape.radiusFor(size),
                            density,
                            wk,
                            primary,
                            press,
                            if (useField) state.frost else null,
                            sizeK,
                            TileOptics(size.width, size.height, density, primary).bevelDp
                        )
                        drawRect(ShaderBrush(glass.shader))
                    } else if (posterImg != null && q >= 2 && kk > 0f) {
                        // 포스터는 위에서 그렸다
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
                    // 크기별 곡면(11-03 소장님 "버튼 크기에 따라 liquid glass의 곡면 광학 표현이 함께 스케일링"): 예전에는 모든 버튼이
                    // 같은 비율·같은 세기의 빛을 써서, 큰 미디어 버튼은 넓은 면적에 비해 곡면 반응이 약해 평평하고 탁하게(눌려) 보였다.
                    // 짧은 변(dp)으로 0(작은 캡슐 ≈ 44dp)~1(큰 버튼 ≈ 120dp)을 정해 위 광택 폭·가운데 밝기·광택 자국·아래 가라앉음이 함께 커진다.
                    // 밝기를 통째로 올리지 않고 분포(폭·반경)를 키운다. 가로로 긴 카드(헤더·진행)는 짧은 변이 작아 작은 버튼 규칙을 따른다
                    // 4 곡면 광학(11-03 종횡비 인식): 위 광택 띠 · 가운데 솟음(타원) · 광택 자국(긴 축으로 늘어난 타원) · 아래 가라앉음을
                    //   [TileOptics] 한 모델이 크기·종횡비로 연속 계산한다. 광택 자국은 앰비언트가 더 밝은 쪽으로 조금 옮겨 실제 반사광과 방향을 맞춘다
                    if (q >= 2) {
                        val o = TileOptics(size.width, size.height, density, primary)
                        val top = size.height * (0.0f + 0.08f * press)
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = o.sheenAlpha * (1f - 0.5f * press)), Color.Transparent), top, top + o.sheenExtent))
                        softEllipse(Offset(size.width / 2, o.liftCy), o.liftRx, o.liftRy, Color.White, o.liftAlpha, 0.45f)
                        val side = if (state.ambientOn) (amb[2].luminance() - amb[0].luminance()).coerceIn(-1f, 1f) else 0f
                        softEllipse(Offset(size.width * (0.5f + 0.12f * side), o.specCy), o.specRx, o.specRy, Color.White, o.specAlpha * (1f - 0.6f * press), 0.35f)
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = o.sinkAlpha)), size.height - o.sinkExtent, size.height))
                        // 안쪽 가장자리 그늘(두께감): 둥근 테두리 안쪽으로 얇은 그늘(셰이더를 쓰면 셰이더가 그린다)
                        if (!shaderOn) drawPath(tileClip, Color.Black.copy(alpha = 0.22f), style = Stroke(5.dp.toPx()))
                    }
                    // 7 누름: 살짝 어두워진다 / 선택: 주변광으로 밝아진다
                    if (press > 0f) drawRect(Color.Black.copy(alpha = 0.16f * press))
                    if (selected) drawRect(Color.White.copy(alpha = 0.10f))
                    // 6 테두리 빛: 위는 밝고 아래는 어두운 얇은 선
                    val refl = state.sampleAt(xFrac, (pos.y / rootSize.height).coerceIn(0f, 1f))
                    val edge = if (selected) (refl ?: ambientAt(xFrac, amb[0], amb[1], amb[2])).copy(alpha = 0.9f) else lerp(Color.White, refl ?: Color.White, 0.35f)
                    // 모서리 반지름은 실제 그려진 모양과 같게(작은 타일은 높이의 절반) — 가장자리 빛·그늘이 둥근 모서리를 그대로 따라간다
                    val r = shape.radiusFor(size)
                    // 셰이더가 테두리 빛을 그리면 이 선은 선택 표시(·고대비)일 때만
                    if (!shaderOn || selected) {
                        drawPath(
                            tileClip,
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
                            style = Stroke((if (selected) 1.5.dp.toPx() else 1.dp.toPx()) * 2f)
                        )
                    }
                    // 모서리 광택: 왼쪽 위 둥근 모서리를 따라 맺히는 짧은 빛(그 자리 반사색으로 살짝 물든다). 누르면 약해진다
                    if (q >= 2 && !hc && !shaderOn) {
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
        val glint = if (state.ambientOn) glintOf(refl) else Color.White
        // 적응형 그림자(11-02 소장님 "Adaptive Text Shadow · Subtle Soft Halo"): 카드 가운데 뒤 반사 밝기(0~1)로 그림자·halo의
        // 불투명도와 번짐 반경을 정한다. 밝은 반사 위에서는 진하고 넓게, 어두운 반사 위에서는 거의 없게. 글자 자체는 그대로 선명하다
        val bgLuma = if (state.ambientOn) backLuma(state.sampleAt(xFrac, ((pos.y + rootSize.height * 0f + 1f) / rootSize.height).coerceIn(0f, 1f)) ?: amb[3], primary) else 0.1f
        // 전경 대비 보정(11-03): 밝은 반사 위에서는 잉크가 반사색에 덜 물들게(최대 60% 덜) — 밝은 배경에 밝은 색 잉크가 묻히지 않게
        val tileInk = inkWith(state.ink, refl, (if (primary) 0.12f else tn.ink) * on * (1f - 0.6f * bgLuma))
        val shadow = androidx.compose.ui.graphics.Shadow(
            // 11-02 소장님 "각 요소에 검은색 그림자 블러 더": 기본 세기와 번짐을 올렸다
            color = Color.Black.copy(alpha = 0.42f + 0.45f * bgLuma),
            offset = Offset(0f, with(androidx.compose.ui.platform.LocalDensity.current) { (0.8f + bgLuma).dp.toPx() }),
            blurRadius = with(androidx.compose.ui.platform.LocalDensity.current) { (6f + 8f * bgLuma).dp.toPx() }
        )
        androidx.compose.runtime.CompositionLocalProvider(
            LocalTileInk provides tileInk,
            LocalTileGlint provides glint,
            LocalTileBg provides bgLuma,
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(shadow = shadow)
        ) { content() }
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
    // 11-02 Ultimate UX: 밝고 진한 반사(빨강·초록) 위에서 잉크 아이콘이 묻혔다. 아이콘 뒤에 아주 부드러운 어두운 번짐을 깔아
    // 반사 색은 살리면서 아이콘 대비를 확보한다(글자 가독성과 같은 원리, 테두리 없는 비네트)
    val bg = LocalTileBg.current
    Box(contentAlignment = Alignment.Center) {
        // 반사 밝기에 따라: 어두운 반사 위 alpha 0.12·반경 1.7배, 밝은 반사 위 alpha 0.5·반경 2.4배
        // 11-02 소장님 "아이콘 그림자 반 이상 옅게": 0.26~0.62 → 0.1~0.24, 반경도 줄였다
        Canvas(Modifier.size((sizeDp * (1.8f + 0.5f * bg)).dp)) {
            val r = size.minDimension / 2f
            val a = 0.10f + 0.14f * bg
            drawCircle(Brush.radialGradient(0f to Color.Black.copy(alpha = a), 0.45f to Color.Black.copy(alpha = a * 0.6f), 1f to Color.Transparent, center = center, radius = r), r)
        }
        IconInk(sizeDp, ink, glint, draw)
    }
}

@Composable
private fun IconInk(sizeDp: Int, ink: Color, glint: Color, draw: DrawScope.(Color) -> Unit) {
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
    // 11-02 Ultimate UX 재설계: 앞으로는 시계 방향(⟳), 뒤로는 그것을 좌우로 뒤집은 반시계 방향(⟲) — 완전 대칭.
    // 굵은 둥근 선 끝의 300° 호, 호 끝에 이어 붙은 채운 화살촉, 가운데 큰 숫자(5·10·15·30). 새로고침 기호처럼 보이지 않게 숫자를 키웠다
    val s = size.minDimension
    val center = Offset(s / 2, s / 2)
    // 11-03: 버튼 크기에 맞게 존재감을 키웠다(아이콘 34 → 38dp, 선 0.085 → 0.095, 숫자 0.36 → 0.42)
    val r = s * 0.40f
    val sw = s * 0.095f
    val startDeg = -62f
    val sweep = 292f
    withTransform({ if (backward) scale(-1f, 1f, center) }) {
        drawArc(c, startDeg, sweep, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
        // 화살촉: 호 끝(시계 방향 끝)에서 접선 방향으로
        val endRad = Math.toRadians((startDeg + sweep).toDouble())
        val end = Offset(center.x + r * cos(endRad).toFloat(), center.y + r * sin(endRad).toFloat())
        val tan = Offset(-sin(endRad).toFloat(), cos(endRad).toFloat())
        val nrm = Offset(cos(endRad).toFloat(), sin(endRad).toFloat())
        // 방향이 한눈에 읽히게 화살촉을 키웠다(11-03: 0.17·0.13 → 0.2·0.15)
        val hl = s * 0.20f
        val hw = s * 0.15f
        val head = Path().apply {
            moveTo(end.x + tan.x * hl * 0.75f, end.y + tan.y * hl * 0.75f)
            lineTo(end.x - tan.x * hl * 0.25f + nrm.x * hw, end.y - tan.y * hl * 0.25f + nrm.y * hw)
            lineTo(end.x - tan.x * hl * 0.25f - nrm.x * hw, end.y - tan.y * hl * 0.25f - nrm.y * hw)
            close()
        }
        drawPath(head, c)
        drawPath(head, c, style = Stroke(s * 0.03f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
    val p = android.graphics.Paint().apply {
        color = c.toArgb()
        textSize = s * 0.42f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 800, false)
    }
    val fm = p.fontMetrics
    drawContext.canvas.nativeCanvas.drawText("$sec", center.x, center.y - (fm.ascent + fm.descent) / 2f, p)
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
