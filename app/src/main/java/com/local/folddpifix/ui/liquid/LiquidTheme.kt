package com.local.folddpifix.ui.liquid

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
) {
    /** 방울 위 글자색(배경색으로 반전). */
    val onInk: Color get() = bg
}

val LightLiquid = LiquidColors(
    bg = Color(0xFFEDEDED), surface = Color(0xFFF7F7F7), ink = Color(0xFF151515), muted = Color(0xFF686868),
    line = Color(0xFFD3D3D3), tint = Color(0x6BFFFFFF), brand = Color(0xFF000000), danger = Color(0xFFB3261E), dark = false,
)
val DarkLiquid = LiquidColors(
    bg = Color(0xFF0F0F0F), surface = Color(0xFF171717), ink = Color(0xFFEBEBEB), muted = Color(0xFF8D8D8D),
    line = Color(0xFF2A2A2A), tint = Color(0x12FFFFFF), brand = Color(0xFFFFFFFF), danger = Color(0xFFF2B8B5), dark = true,
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
    val c = if (dark) DarkLiquid else LightLiquid
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
            error = c.danger, onError = c.bg, errorContainer = c.surface, onErrorContainer = c.danger,
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
            error = c.danger, onError = c.bg, errorContainer = c.surface, onErrorContainer = c.danger,
        )
    }
    CompositionLocalProvider(LocalLiquid provides c, LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
