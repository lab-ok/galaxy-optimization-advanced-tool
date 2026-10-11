package com.labok.goat.ui.tweaks

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.RefreshRate
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 주사율 고정: 지금 화면 주사율(실시간), 방식 고르기(시스템 기본·항상 최고·60Hz 절전), 주의.
 * 최고·최저는 숨은 시스템 설정이라 Shizuku 셸로 바꾼다(시스템 설정 권한만으로는 막히는 기기가 많다).
 * 최고 60Hz인 기기는 바꿀 것이 없다고 알린다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun RefreshScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val canWrite = shizuku == ShizukuAccess.State.READY
    var version by remember { mutableIntStateOf(0) }
    val choice = remember(version) { RefreshRate.read(context) }
    val rates = remember { RefreshRate.rates(context) }
    val top = rates.maxOrNull() ?: 60
    val supported = top > 60
    var nowHz by remember { mutableIntStateOf(RefreshRate.now(context)) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) { version++; nowHz = RefreshRate.now(context); onPauseOrDispose { } }
    // 지금 주사율: 화면이 주사율을 바꿀 때마다(움직임·절전·고정) 바로 다시 읽는다
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) nowHz = RefreshRate.now(context) }
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.REFRESH_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.REFRESH_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    val high = if (supported) top else 120
                    RefreshArt(low = 60, high = high, selected = if (choice == RefreshRate.Choice.SAVER) 60 else high)
                }
            }
        }
        if (!supported) item(key = "unsupported") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.REFRESH_UNSUPPORTED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.REFRESH_UNSUPPORTED_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (!canWrite) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CAMERA_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.REFRESH_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.CAMERA_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (supported) item(key = "choice") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.REFRESH_PICK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text("지금 화면 ${nowHz}Hz · 이 기기 최고 ${top}Hz", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    RefreshRate.Choice.entries.forEach { ch ->
                        val label = if (ch == RefreshRate.Choice.SMOOTH) "항상 ${top}Hz" else ch.label
                        LiquidRadioRow(label, ch.desc, choice == ch, enabled = canWrite && !busy) {
                            if (ch == choice) return@LiquidRadioRow
                            haptics.segment()
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { RefreshRate.set(context, ch, "고르기") }
                                busy = false
                                version++
                                nowHz = RefreshRate.now(context)
                                if (ok) haptics.snap() else { haptics.reject(); Toast.makeText(context, Copy.REFRESH_TOAST_FAILED, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                }
            }
        }
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.REFRESH_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.REFRESH_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun RefreshHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 주사율이 무엇인가요",
            "화면이 1초에 몇 장을 새로 그리는지입니다. 120Hz는 1초에 120장을 그려 60Hz보다 스크롤과 움직임이 두 배 촘촘하고 매끄럽습니다. " +
                "대신 장수만큼 전기를 더 씁니다.",
        ) { RefreshArt(low = 60, high = 120, selected = 120) }
        HelpTopic(
            "2. 세 가지 방식",
            "'시스템 기본'은 갤럭시가 알아서 움직일 때만 높이고 멈추면 낮춥니다. '항상 최고'는 멈춰 있어도 최고 주사율을 유지해 터치 반응까지 " +
                "가장 빠르게 합니다. '60Hz 절전'은 60Hz로 묶어 배터리를 아낍니다.",
        )
        HelpTopic(
            "3. 잠시 내려가는 경우",
            "절전 모드, 화면 밝기가 아주 낮을 때, 일부 게임·동영상 앱은 시스템이 잠시 주사율을 내립니다. 그 상황이 끝나면 고른 방식으로 돌아옵니다. " +
                "삼성 설정이나 다른 앱이 값을 바꿔 놓으면 Shizuku가 연결될 때 다시 맞춥니다.",
        )
        HelpTopic(
            "4. 권한과 되돌리기",
            "주사율의 최고·최저는 숨은 시스템 설정이라 Shizuku 셸 권한으로 바꿉니다. '시스템 기본'을 고르면 처음 값으로 돌아갑니다. " +
                "설정 > 디스플레이 > 화면 움직임 부드럽게에서도 되돌릴 수 있습니다.",
        )
    }
}
