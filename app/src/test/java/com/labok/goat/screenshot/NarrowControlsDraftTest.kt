package com.labok.goat.screenshot

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.labok.goat.ui.lab.NarrowControls
import com.labok.goat.ui.liquid.LiquidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 여백 컨트롤러 시안(실제 GOAT 리퀴드 부품으로 그린 것). 폴드8 커버 크기(1248x1972)에서 16:9로 좁힌 앱이 오른쪽에 붙고
 * 왼쪽 여백 139px(제스처 내비게이션)에 컨트롤러가 뜬 모습. 결과: build/outputs/roborazzi/narrow_*.png
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w555dp-h876dp-night-360dpi")
class NarrowControlsDraftTest {
    @get:Rule
    val compose = createComposeRule()

    private fun icon(color: Int, letter: String): ImageBitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        cv.drawRoundRect(0f, 0f, 96f, 96f, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        cv.drawText(letter, 48f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; textSize = 48f; textAlign = Paint.Align.CENTER; isFakeBoldText = true })
        return b.asImageBitmap()
    }

    private fun shot(name: String, compact: Boolean = false, recents: Boolean = true) {
        compose.mainClock.autoAdvance = false
        val icons = if (recents) {
            listOf(icon(0xFFFF0033.toInt(), "▶"), icon(0xFF3B5BDB.toInt(), "D"), icon(0xFFFEE500.toInt(), "K"), icon(0xFF03C75A.toInt(), "N"), icon(0xFF5865F2.toInt(), "C"))
        } else {
            emptyList()
        }
        compose.setContent {
            LiquidTheme {
                Row(Modifier.fillMaxSize().background(Color.Black)) {
                    // 여백 139px = 61.8dp(360dpi)
                    NarrowControls(appLeft = false, modifier = Modifier.width(61.8.dp), compact = compact, recents = icons)
                    Box(
                        Modifier.fillMaxHeight().width(492.9.dp).background(
                            Brush.linearGradient(listOf(Color(0xFFF2735A), Color(0xFF8C3FA6), Color(0xFF1A1F4D)))
                        )
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(10) {
            Thread.sleep(120)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(200)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun controller() = shot("narrow_v2")

    @Test fun keyboard() = shot("narrow_v2_keyboard", compact = true)
}
