package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.art.deviceFrame
import com.labok.goat.ui.liquid.LiquidColors

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 다크 모드 강제 그림: 다크 모드를 지원하지 않는 같은 앱(흰 바탕·검은 글줄·사진 칸)이 두 폰에 열려 있다.
 * 시스템이 다크 모드로 바뀌면(0.3~0.55) 왼쪽(강제 안 함)은 그대로 하얗고, 오른쪽(강제)은 위에서부터 어두운 바탕·밝은 글줄로 바뀐다.
 * 사진 칸은 색을 바꾸지 않는다(강제 다크는 사진을 반전하지 않는다). [on]이면 오른쪽을 진하게 표시한다.
 */
@Composable
internal fun DarkArt(on: Boolean) {
    val t = artLoop(3200)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(176.dp) { c, tm ->
        val h = 116.dp.toPx()
        val w = h * aspect
        val top = 10.dp.toPx()
        val k = ease(seg(t, 0.3f, 0.55f)) * (1 - ease(seg(t, 0.9f, 1f)))
        listOf(0.3f to false, 0.7f to true).forEachIndexed { i, (fx, forced) ->
            val screen = deviceFrame(c, Offset(size.width * fx - w / 2, top), Size(w, h), emphasis = if (i == 1 && on || i == 0 && !on) 1f else 0.6f)
            app(c, screen, if (forced) k else 0f)
            artPill(tm, c, if (forced) "어둡게 바뀜" else "하얀 그대로", Offset(screen.center.x, top + h + 16.dp.toPx()), filled = forced == on)
            artLabel(tm, if (forced) "강제 다크" else "강제 안 함", Offset(screen.center.x, top + h + 36.dp.toPx()), if (forced == on) c.ink else c.muted, bold = forced == on)
        }
    }
}

/** 그림 속 앱의 밝은 바탕·어두운 바탕. GOAT 테마(밝음·어두움)와 상관없이 같은 뜻으로 보이도록 고정 색을 쓴다. */
private val LIGHT = Color(0xFFF7F7F7)
private val DARK = Color(0xFF1C1C1E)

/** 앱 화면 하나. [dark] 0→1이면 위에서부터 바탕이 어두워지고 글줄이 밝아진다(사진 칸은 그대로). */
private fun DrawScope.app(c: LiquidColors, screen: Rect, dark: Float) {
    val wipe = screen.top + screen.height * dark
    val light = LIGHT
    val ink = DARK
    clipRect(screen.left, screen.top, screen.right, screen.bottom) {
        drawRect(light, screen.topLeft, screen.size)
        if (dark > 0f) drawRect(lerp(ink, light, 0.08f), screen.topLeft, Size(screen.width, wipe - screen.top))
        val pad = screen.width * 0.12f
        fun line(y: Float, frac: Float, hFrac: Float = 0.035f) {
            val col = if (y < wipe) light.copy(alpha = 0.85f) else ink.copy(alpha = 0.7f)
            drawRoundRect(col, Offset(screen.left + pad, y), Size((screen.width - 2 * pad) * frac, screen.height * hFrac), CornerRadius(screen.height * 0.02f))
        }
        line(screen.top + screen.height * 0.08f, 0.55f, 0.05f)
        // 사진 칸(색을 바꾸지 않는다)
        drawRoundRect(Color(0xFF9A9A9E), Offset(screen.left + pad, screen.top + screen.height * 0.2f), Size(screen.width - 2 * pad, screen.height * 0.26f), CornerRadius(screen.height * 0.03f))
        listOf(0.52f to 1f, 0.6f to 0.9f, 0.68f to 1f, 0.76f to 0.6f).forEach { (y, f) -> line(screen.top + screen.height * y, f) }
    }
}
