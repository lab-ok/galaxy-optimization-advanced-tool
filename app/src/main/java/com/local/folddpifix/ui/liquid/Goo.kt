package com.local.folddpifix.ui.liquid

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 메타볼(goo) 효과. 원본 SVG 필터(goo-u: 블러 6 → 알파 행 22a−9)를 Android 12+ RenderEffect로 옮겼다.
 * 이 층 안에 그린 방울들은 가까워지면 표면장력처럼 하나로 합쳐진다. 층에는 방울만 그리고 글자는 위층에 둔다.
 */
@Composable
fun Modifier.goo(color: Color, blur: Dp = 6.dp): Modifier {
    val px = with(LocalDensity.current) { blur.toPx() }
    val argb = color.toArgb()
    val r = (argb shr 16 and 0xFF).toFloat()
    val g = (argb shr 8 and 0xFF).toFloat()
    val b = (argb and 0xFF).toFloat()
    return this.graphicsLayer {
        val matrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, r,
                0f, 0f, 0f, 0f, g,
                0f, 0f, 0f, 0f, b,
                0f, 0f, 0f, 22f, -9f * 255f,
            )
        )
        val blurFx = RenderEffect.createBlurEffect(px, px, Shader.TileMode.DECAL)
        val threshold = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
        renderEffect = RenderEffect.createChainEffect(threshold, blurFx).asComposeRenderEffect()
        clip = false
    }
}
