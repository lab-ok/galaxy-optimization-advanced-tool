package com.labok.goat.ui.liquid

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 리퀴드모피즘 색 토큰. 원본: trafix_its_edge static/component/core/tokens.css.
 * 방울(포인트)은 배경과 반대인 잉크색이다: 라이트 = 검정 방울, 다크 = 흰 방울. 브랜드 포인트도 검정(다크에서는 흰색)이다.
 */
@Immutable
data class LiquidColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val tint: Color,
    val brand: Color,
    val danger: Color,
    val dark: Boolean,
    /** 염소 로고 상자 색(홈·사이드바·앱 정보). 라이트는 잉크와 같고, 다크는 흰 잉크 대신 바탕(#0F0F0F)보다 한 단계 밝은 어두운 상자를 둔다. */
    val logo: Color = ink
) {
    /** 방울 위 글자색(배경색으로 반전). */
    val onInk: Color get() = bg
}

val LightLiquid = LiquidColors(
    bg = Color(0xFFEDEDED), surface = Color(0xFFF7F7F7), ink = Color(0xFF3D3833), muted = Color(0xFF686868),
    line = Color(0xFFD3D3D3), tint = Color(0x6BFFFFFF), brand = Color(0xFF000000), danger = Color(0xFFB3261E), dark = false
)
val DarkLiquid = LiquidColors(
    bg = Color(0xFF0F0F0F), surface = Color(0xFF171717), ink = Color(0xFFEBEBEB), muted = Color(0xFF8D8D8D),
    // 선·꺼진 스위치 바탕: 카드(#171717) 위에서 보이도록 #2A2A2A보다 밝게(다크 모드 전수 점검에서 꺼진 스위치가 흰 점만 보였다)
    line = Color(0xFF424242), tint = Color(0x12FFFFFF), brand = Color(0xFFFFFFFF), danger = Color(0xFFF2B8B5), dark = true,
    logo = Color(0xFF2A2A2A)
)

/** 모양 토큰(원본: 컨트롤 알약 999px, 상자 12px — 모바일 터치 크기에 맞춰 키움). */
object LiquidShape {
    val card = 22.dp
    val control = 44.dp
}

val LocalLiquid = staticCompositionLocalOf { LightLiquid }

/** 시스템 '애니메이션 제거'가 켜져 있으면 true. 원본 엔진도 prefers-reduced-motion을 따른다. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun LiquidTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val plain = if (dark) DarkLiquid else LightLiquid
    // 잉크 색을 갤럭시 테마로 고르면 팔레트에서 고른 색(기본은 강조색 1의 가장 진한, 다크는 가장 밝은 단계)을 쓴다
    val themeInk = colorResource(UiInk.tone.colorRes(dark))
    // 염소 아이콘 상자도 고른 색을 따른다. 흰 염소가 보이도록 다크 모드에서도 라이트 쪽(진한) 단계를 쓴다
    val themeLogo = colorResource(UiInk.tone.colorRes(dark = false))
    val c = if (UiInk.mode == UiInk.Mode.THEME) plain.copy(ink = themeInk, logo = themeLogo) else plain
    val context = LocalContext.current
    val reduce = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Material 구성요소도 같은 단색 체계를 쓰도록 맞춘다.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.ink, onPrimary = c.bg, primaryContainer = c.surface, onPrimaryContainer = c.ink,
            secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.line, onSecondaryContainer = c.ink,
            tertiary = c.brand, onTertiary = c.bg, tertiaryContainer = c.surface, onTertiaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.surface, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
            surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
            surfaceContainerLowest = c.bg, outline = c.line, outlineVariant = c.line,
            error = c.danger, onError = c.bg, errorContainer = c.surface, onErrorContainer = c.danger
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.bg, primaryContainer = c.surface, onPrimaryContainer = c.ink,
            secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.line, onSecondaryContainer = c.ink,
            tertiary = c.brand, onTertiary = c.bg, tertiaryContainer = c.surface, onTertiaryContainer = c.ink,
            background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.surface, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
            surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
            surfaceContainerLowest = c.bg, outline = c.line, outlineVariant = c.line,
            error = c.danger, onError = c.bg, errorContainer = c.surface, onErrorContainer = c.danger
        )
    }
    // 앱 전체 글자를 조금 작게: 시스템 글자 크기 배율에 TEXT_SCALE을 곱한다(sp로 쓴 모든 글자·줄 높이에 같이 적용, dp 크기는 그대로)
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalLiquid provides c,
        LocalReduceMotion provides reduce,
        LocalDensity provides Density(base.density, base.fontScale * TEXT_SCALE)
    ) {
        MaterialTheme(colorScheme = scheme, typography = LiquidType, content = content)
    }
}

/**
 * 기기 글꼴: 삼성 '글꼴 스타일'(기본 삼성 글꼴·내려받은 글꼴)은 시스템 기본 글꼴 객체(Typeface.DEFAULT)를 바꿔 끼우는
 * 방식이라, Compose 기본 글꼴(이름으로 sans-serif를 찾음)로는 반영되지 않는 기기가 있다. 그 객체를 직접 써서 기기 글꼴을 따른다.
 * 굵기는 안드로이드가 같은 글꼴에서 맞는 굵기를 고른다.
 */
@OptIn(ExperimentalTextApi::class)
val GalaxyFont: FontFamily = FontFamily(
    (1..9).flatMap { w -> listOf(false, true).map { italic -> DeviceDefaultFont(FontWeight(w * 100), italic) } }
)

/** 기기 기본 글꼴(Typeface.DEFAULT)의 [weight]·기울임 한 벌. 굵기마다 안드로이드가 같은 글꼴의 그 굵기를 고른다. */
@OptIn(ExperimentalTextApi::class)
private class DeviceDefaultFont(
    override val weight: FontWeight,
    italic: Boolean
) : AndroidFont(FontLoadingStrategy.Blocking, Loader) {
    override val style = if (italic) FontStyle.Italic else FontStyle.Normal

    private object Loader : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface = Typeface.create(Typeface.DEFAULT, font.weight.weight, font.style == FontStyle.Italic)

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
    }
}

/**
 * 글자 간격 규칙(앱 전체):
 * 1. 설명 안의 줄 간격은 제목과 설명 사이 간격보다 좁다. 제목(본문 큰 글자)은 줄 높이에 위아래 약 3sp 여유를,
 *    설명(작은 본문·라벨)은 약 1.5sp만 둬서, 제목과 설명 사이(약 4.5sp)보다 설명 줄 사이(약 3sp)가 늘 좁다.
 * 2. 같은 제목 아래 이어지는 설명 문단 사이에는 따로 간격(Spacer)을 넣지 않는다(줄 간격으로만 나눈다).
 * 글자 크기는 Material 3 기본과 같고 줄 높이만 줄였다.
 */
private val LiquidType = Typography().let { t0 ->
    // 모든 글자 모양에 기기 글꼴(GalaxyFont)을 쓴다
    val t = t0.copy(
        displayLarge = t0.displayLarge.copy(fontFamily = GalaxyFont), displayMedium = t0.displayMedium.copy(fontFamily = GalaxyFont),
        displaySmall = t0.displaySmall.copy(fontFamily = GalaxyFont), headlineLarge = t0.headlineLarge.copy(fontFamily = GalaxyFont),
        headlineMedium = t0.headlineMedium.copy(fontFamily = GalaxyFont), headlineSmall = t0.headlineSmall.copy(fontFamily = GalaxyFont),
        titleLarge = t0.titleLarge.copy(fontFamily = GalaxyFont), titleMedium = t0.titleMedium.copy(fontFamily = GalaxyFont),
        titleSmall = t0.titleSmall.copy(fontFamily = GalaxyFont), bodyLarge = t0.bodyLarge.copy(fontFamily = GalaxyFont),
        bodyMedium = t0.bodyMedium.copy(fontFamily = GalaxyFont), bodySmall = t0.bodySmall.copy(fontFamily = GalaxyFont),
        labelLarge = t0.labelLarge.copy(fontFamily = GalaxyFont), labelMedium = t0.labelMedium.copy(fontFamily = GalaxyFont),
        labelSmall = t0.labelSmall.copy(fontFamily = GalaxyFont)
    )
    val t2 = t.copy(
        bodyLarge = t.bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 18.sp),
        bodySmall = t.bodySmall.copy(lineHeight = 15.sp),
        labelMedium = t.labelMedium.copy(lineHeight = 15.sp),
        labelSmall = t.labelSmall.copy(lineHeight = 14.sp)
    )

    // 한국어 줄바꿈: 글자 중간이 아니라 어절(띄어쓰기) 단위로 넘기고, 줄 길이를 고르게 맞춰
    // 좁은 화면에서 마지막 줄에 '다.' 한 글자만 남는 일을 막는다
    // 구절 줄바꿈은 글자의 언어가 한국어일 때만 동작하므로, 기기 언어가 영어여도 한국어로 다루게 언어를 정해 둔다
    fun TextStyle.kr() = copy(lineBreak = KoreanLineBreak, localeList = LocaleList("ko-KR"))
    t2.copy(
        displayLarge = t2.displayLarge.kr(), displayMedium = t2.displayMedium.kr(), displaySmall = t2.displaySmall.kr(),
        headlineLarge = t2.headlineLarge.kr(), headlineMedium = t2.headlineMedium.kr(), headlineSmall = t2.headlineSmall.kr(),
        titleLarge = t2.titleLarge.kr(), titleMedium = t2.titleMedium.kr(), titleSmall = t2.titleSmall.kr(),
        bodyLarge = t2.bodyLarge.kr(), bodyMedium = t2.bodyMedium.kr(), bodySmall = t2.bodySmall.kr(),
        labelLarge = t2.labelLarge.kr(), labelMedium = t2.labelMedium.kr(), labelSmall = t2.labelSmall.kr()
    )
}

/** 어절 단위·고른 줄 길이 줄바꿈(Android 13+ 구절 줄바꿈). */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase
)

/** 앱 글자 크기 배율(시스템 글자 크기 설정에 곱한다). */
private const val TEXT_SCALE = 0.92f
