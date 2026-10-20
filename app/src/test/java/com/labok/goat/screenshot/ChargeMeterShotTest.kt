package com.labok.goat.screenshot

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.labok.goat.data.lab.ChargeMeter
import com.labok.goat.ui.lab.ChargeMeterCard
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.liquid.LocalLiquid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 실험실 '충전 실측 용량' 카드 캡처: 측정 중(물 막대)과 측정 결과(중앙값·기둥·목록). 에뮬레이터에는 실제 배터리 전류가 없어 저장 값을 미리 넣는다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = COVER)
class ChargeMeterShotTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun seed(live: ChargeMeter.Live?) {
        val day = 86_400_000L
        val base = 1_792_300_000_000L
        val results = listOf(
            ChargeMeter.Result(base, 18, 71, 2_170, 4_094, 33f),
            ChargeMeter.Result(base + day, 25, 88, 2_540, 4_032, 34f),
            ChargeMeter.Result(base + 2 * day, 31, 74, 1_760, 4_093, 31f),
            ChargeMeter.Result(base + 3 * day, 22, 90, 2_790, 4_103, 35f)
        )
        app.getSharedPreferences("charge_meter", Context.MODE_PRIVATE).edit()
            .putBoolean("on", true)
            .putInt("design", 4_400)
            .putString("results", results.joinToString(";") { it.serialize() })
            .apply { if (live != null) putString("live", live.serialize()) else remove("live") }
            .commit()
    }

    private fun shot(name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LiquidTheme {
                Box(Modifier.fillMaxSize().background(LocalLiquid.current.bg).padding(16.dp)) {
                    GlassCard { ChargeMeterCard() }
                }
            }
        }
        repeat(8) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(250)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun measuring() {
        seed(ChargeMeter.Live(0, 24, 37, 27, 37, 452, 2_140))
        shot("cm_01_measuring")
    }

    @Test fun results() {
        seed(null)
        shot("cm_02_results")
    }

    // 야간 한정자는 화면 밀도보다 앞에 와야 한다
    @Config(qualifiers = "w555dp-h876dp-night-360dpi")
    @Test
    fun results_dark() {
        seed(ChargeMeter.Live(0, 40, 65, 43, 65, 905, 1_870))
        shot("cm_03_dark")
    }
}
