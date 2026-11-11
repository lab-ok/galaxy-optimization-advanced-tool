package com.labok.goat.ui.tweaks

import com.labok.goat.AppBuild
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.core.R
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.RefreshRate
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioGroup
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
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
fun RefreshScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val canWrite = shizuku == ShizukuAccess.State.READY
    var version by remember { mutableIntStateOf(0) }
    val choice = remember(version) { RefreshRate.read(context) }
    // lab 빌드는 에뮬레이터(최고 60Hz 이하)에서도 고르기 화면을 볼 수 있게 120Hz로 흉내 낸다(화면만, 실제 쓰기는 실제 최고값을 따른다)
    val real = remember { RefreshRate.max(context) }
    val top = if (AppBuild.lab && real <= 60) 120 else real
    val supported = top > 60
    var nowHz by remember { mutableIntStateOf(RefreshRate.now(context)) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) {
        version++
        nowHz = RefreshRate.now(context)
        onPauseOrDispose { }
    }
    // 지금 주사율: 화면이 주사율을 바꿀 때마다(움직임·절전·고정) 바로 다시 읽는다
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) nowHz = RefreshRate.now(context)
            }
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
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.REFRESH_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.REFRESH_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    val high = if (supported) top else 120
                    // 시스템 기본은 어느 한 줄을 강조하지 않는다. 바꿀 수 없는 기기는 지금 화면(60Hz 이하) 쪽을 둔다.
                    RefreshArt(
                        low = 60,
                        high = high,
                        selected = when {
                            !supported || choice == RefreshRate.Choice.SAVER -> 60
                            choice == RefreshRate.Choice.SMOOTH -> high
                            else -> 0
                        }
                    )
                }
            }
        }
        if (!supported) {
            item(key = "unsupported") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.REFRESH_UNSUPPORTED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.refreshUnsupported(real), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (!canWrite) {
            item(key = "permission") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.NEED_SHIZUKU_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.SHIZUKU_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (supported) {
            item(key = "choice") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.REFRESH_PICK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.refreshNow(nowHz, top), color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidRadioGroup {
                            RefreshRate.Choice.entries.forEach { ch ->
                                val label = if (ch == RefreshRate.Choice.SMOOTH) Copy.refreshAlways(top) else ch.label
                                LiquidRadioRow(label, ch.desc, choice == ch, enabled = canWrite && !busy) {
                                    if (ch == choice) return@LiquidRadioRow
                                    haptics.segment()
                                    // 앱 밖에서 고정한 값은 GOAT가 처음 값을 모르므로 되돌리지 않고 알린다
                                    if (ch == RefreshRate.Choice.SYSTEM && !RefreshRate.canRestore(context)) {
                                        Toast.makeText(context, Copy.REFRESH_TOAST_EXTERNAL, Toast.LENGTH_LONG).show()
                                        return@LiquidRadioRow
                                    }
                                    busy = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { RefreshRate.set(context, ch, "고르기") }
                                        busy = false
                                        version++
                                        nowHz = RefreshRate.now(context)
                                        if (ok) {
                                            haptics.snap()
                                        } else {
                                            haptics.reject()
                                            Toast.makeText(context, Copy.SHIZUKU_FAILED, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (supported) {
            item(key = "caution") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.REFRESH_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
fun RefreshHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(
            stringResource(R.string.tweaks_refresh_help_what_title),
            stringResource(R.string.tweaks_refresh_help_what_body)
        ) { RefreshArt(low = 60, high = 120, selected = 120) }
        HelpTopic(
            stringResource(R.string.tweaks_refresh_help_modes_title),
            stringResource(R.string.tweaks_refresh_help_modes_body_fmt, Copy.REFRESH_SYSTEM, Copy.REFRESH_SMOOTH, Copy.REFRESH_SAVER)
        )
        HelpTopic(
            stringResource(R.string.tweaks_refresh_help_drop_title),
            stringResource(R.string.tweaks_refresh_help_drop_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_help_permission_undo_title),
            stringResource(R.string.tweaks_refresh_help_permission_body_fmt, Copy.REFRESH_SYSTEM)
        )
    }
}
