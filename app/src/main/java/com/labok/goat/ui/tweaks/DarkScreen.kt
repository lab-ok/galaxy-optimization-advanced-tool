package com.labok.goat.ui.tweaks

import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.labok.goat.R
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.ForceDark
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.components.settingsIntent
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 앱 다크 모드 강제: 다크 모드 없는 앱도 어둡게(켜 두면 재부팅 뒤 다시 켬), 시스템 다크 모드 상태(강제 다크의 전제)와
 * 디스플레이 설정 바로 가기, 주의. 강제 다크는 Shizuku 셸로 바꾸고, 돌아올 때마다 다시 읽는다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun DarkScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by ForceDark.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        scope.launch(Dispatchers.IO) { ForceDark.read(context) }
        onPauseOrDispose { }
    }

    fun change(block: () -> Boolean, okText: String) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            busy = false
            Toast.makeText(context, if (ok) okText else Copy.SHIZUKU_FAILED, Toast.LENGTH_SHORT).show()
        }
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
                    Text(Copy.DARK_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DARK_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    DarkArt(on = state.forced == true)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(
                        Copy.DARK_FORCE_TITLE,
                        when {
                            state.forced == null -> Copy.SHIZUKU_STATE_UNKNOWN
                            state.forced == true && !state.night -> Copy.DARK_FORCE_WAITING
                            state.forced == true -> Copy.DARK_FORCE_ON
                            else -> Copy.DARK_FORCE_OFF
                        },
                        state.forced == true,
                        ready && !busy && state.forced != null
                    ) { on -> change({ ForceDark.setForced(context, on, "스위치") }, if (on) Copy.DARK_TOAST_FORCE_ON else Copy.DARK_TOAST_FORCE_OFF) }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.SWITCH_KEEP_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (!ready) {
            item(key = "connect") {
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
        item(key = "night") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DARK_NIGHT_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(if (state.night) Copy.DARK_NIGHT_ON else Copy.DARK_NIGHT_OFF, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    // 강제 다크를 켰는데 다크 모드가 꺼져 있을 때만 강조한다(다크 모드를 원하지 않는 사람에게 권하지 않게)
                    LiquidButton(Copy.DARK_NIGHT_OPEN, primary = state.forced == true && !state.night, modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching { context.startActivity(settingsIntent(Settings.ACTION_DISPLAY_SETTINGS)) }
                            .onFailure { Toast.makeText(context, Copy.DARK_TOAST_NO_SETTINGS, Toast.LENGTH_SHORT).show() }
                    })
                }
            }
        }
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DARK_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DARK_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.DARK_CAUTION_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun DarkHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(
            stringResource(R.string.tweaks_help_what_title),
            stringResource(R.string.tweaks_dark_help_what_body)
        ) { DarkArt(on = true) }
        HelpTopic(
            stringResource(R.string.tweaks_dark_help_system_title),
            stringResource(R.string.tweaks_dark_help_system_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_dark_help_scope_title),
            stringResource(R.string.tweaks_dark_help_scope_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_dark_help_glitch_title),
            stringResource(R.string.tweaks_dark_help_glitch_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_dark_help_reboot_title),
            stringResource(R.string.tweaks_dark_help_reboot_body)
        )
    }
}
