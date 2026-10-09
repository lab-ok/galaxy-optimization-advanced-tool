package com.local.folddpifix.screenshot

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.local.folddpifix.data.display.DisplayDensity
import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.data.display.DpiState
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.ScreenInfo
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.domain.plan
import com.local.folddpifix.ui.about.AboutSheet
import com.local.folddpifix.ui.advanced.AdvancedSheet
import com.local.folddpifix.ui.help.GrantGuideSheet
import com.local.folddpifix.ui.help.HelpSheet
import com.local.folddpifix.ui.home.HomeScreen
import com.local.folddpifix.ui.home.UiState
import com.local.folddpifix.ui.liquid.LiquidTheme
import com.local.folddpifix.ui.liquid.LocalLiquid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 화면 캡처 검증. 결과는 app/build/outputs/roborazzi 폴더의 PNG 파일.
 * 실행: ./gradlew testDebugUnitTest -Pscreenshot=<x86 java 래퍼>  (ARM64 호스트는 qemu로 x86_64 JVM 사용)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = COVER)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun prepare(granted: Boolean, learned: Boolean) {
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        SettingsRepository.from(app).apply {
            targetDpi = 360
            adjust = -3
            autoRecover = true
            applyOnBoot = true
            watchFold = true
            reference = ScreenPolicy.Role.OUTER
            if (learned) {
                // Robolectric이 보고하는 현재 화면을 외부로 쓰고, 실기기(SM-F971N)처럼 켜졌을 때 보고 PPI는 같게,
                // 꺼졌을 때 내부 보고값은 404.2/428.2 비율로 둔다.
                val cur = DpiManager(app).currentScreen() ?: OUTER
                screens = listOf(cur, INNER.copy(ppi = cur.ppi))
                inactivePpi = mapOf(INNER.key to cur.ppi * 404.2f / 428.2f)
            }
        }
    }

    private fun settle(ms: Long = 1500) {
        compose.mainClock.autoAdvance = false
        repeat(8) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(ms / 8)
        }
    }

    private fun shotHome(name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LiquidTheme { HomeScreen() } }
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun shotContent(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LiquidTheme { Box(Modifier.fillMaxSize().background(LocalLiquid.current.bg)) { content() } }
        }
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home_setup_needed() { prepare(granted = false, learned = false); shotHome("01_home_setup_needed") }

    @Test fun home_ready() { prepare(granted = true, learned = true); shotHome("02_home_ready") }

    @Config(qualifiers = "$COVER-night")
    @Test fun home_ready_dark() { prepare(granted = true, learned = true); shotHome("03_home_ready_dark") }

    @Config(qualifiers = MAIN)
    @Test fun home_ready_inner() { prepare(granted = true, learned = true); shotHome("04_home_ready_inner") }

    private fun fakeState(): UiState {
        prepare(granted = true, learned = true)
        val s = SettingsRepository.from(app)
        val plan = s.plan()
        return UiState(
            loading = false, hasPermission = true, dpi = DpiState(420, 360, "IWindowManager"), target = 360, adjust = -3,
            plan = plan, activeScreen = OUTER, effective = plan?.targetFor(OUTER),
            displays = listOf(
                DisplayDensity(0, OUTER, 420, 360, true),
                DisplayDensity(1, INNER, 420, 337, true),
            ),
            inactivePpi = mapOf(INNER.key to 404.2f),
        )
    }

    @Test fun sheet_help() { val st = fakeState(); shotContent("05_sheet_help") { HelpSheet(st, onOpenGuide = {}) } }

    @Test fun sheet_guide() { shotContent("06_sheet_guide") { GrantGuideSheet(hasPermission = false, onCopied = {}) } }

    @Test fun sheet_advanced() { val st = fakeState(); shotContent("07_sheet_advanced") { AdvancedSheet(st, onClearLogs = {}) } }

    @Test fun sheet_about() { shotContent("08_sheet_about") { AboutSheet(onMail = {}) } }

    private companion object {
        val OUTER = ScreenInfo("1248x1972", 428.2f, 5.45f)
        val INNER = ScreenInfo("1848x2448", 428.2f, 7.16f)
    }
}

/** 외부(커버) 화면: 1248x1972 px, 360dpi 기준 → 약 555x876 dp. */
const val COVER = "w555dp-h876dp-360dpi"

/** 내부(메인) 화면: 1848x2448 px, 337dpi 기준 → 약 877x1162 dp. */
const val MAIN = "w877dp-h1162dp-337dpi"
