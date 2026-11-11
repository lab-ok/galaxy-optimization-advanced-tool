package com.labok.goat.ui.tweaks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import com.labok.goat.core.R
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.Gos
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GOS 끄기: 소개 카드의 끄기 스위치(끌 때 발열·배터리 확인 창, 켜 두면 다시 켜져도 자동으로 끔), 주의.
 * 기본 앱 정리와 같은 '끄기'라 그 화면 목록의 GOS와 상태가 같다. 돌아올 때마다 다시 읽는다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
fun GosScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by Gos.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        if (ready) scope.launch(Dispatchers.IO) { Gos.read() }
        onPauseOrDispose { }
    }

    fun apply(off: Boolean) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Gos.set(context, off, "스위치") }
            busy = false
            Toast.makeText(context, if (ok) (if (off) Copy.GOS_TOAST_OFF else Copy.GOS_TOAST_ON) else Copy.SHIZUKU_FAILED, Toast.LENGTH_SHORT).show()
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
                    Text(Copy.GOS_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.GOS_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    GosArt(off = state == Gos.State.OFF, none = state == Gos.State.NONE)
                    // GOS가 없는 기기에는 끌 것이 없어 스위치를 두지 않는다
                    if (state != Gos.State.NONE) {
                        Spacer(Modifier.height(Gap.Item))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Copy.GOS_SWITCH_TITLE, fontWeight = FontWeight.SemiBold, color = if (ready && state != Gos.State.UNKNOWN) c.ink else c.muted)
                                Text(
                                    when (state) {
                                        Gos.State.OFF -> Copy.GOS_STATE_OFF
                                        Gos.State.ON -> Copy.GOS_STATE_ON
                                        else -> Copy.SHIZUKU_STATE_UNKNOWN
                                    },
                                    color = c.muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(Space.IconText))
                            LiquidSwitch(state == Gos.State.OFF, { off -> if (off) confirm = true else apply(off = false) }, enabled = ready && !busy && state != Gos.State.UNKNOWN, label = Copy.GOS_SWITCH_TITLE)
                        }
                        Spacer(Modifier.height(Gap.Item))
                        Text(Copy.SWITCH_KEEP_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall)
                    }
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
        if (state == Gos.State.NONE) {
            item(key = "none") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.GOS_NONE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                }
            }
        }
        if (state != Gos.State.NONE) {
            item(key = "caution") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.GOS_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(Copy.FEATURE_GOS) },
            text = { Text(Copy.GOS_CONFIRM_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    apply(off = true)
                }) { Text(Copy.CONFIRM_TURN_OFF) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(Copy.CANCEL) } }
        )
    }
}

/** 사용 방법. */
@Composable
fun GosHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(
            stringResource(R.string.tweaks_gos_help_what_title),
            stringResource(R.string.tweaks_gos_help_what_body)
        ) { GosArt(off = true) }
        HelpTopic(
            stringResource(R.string.tweaks_help_tradeoff_title),
            stringResource(R.string.tweaks_gos_help_tradeoff_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_gos_help_return_title),
            stringResource(R.string.tweaks_gos_help_return_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_gos_help_undo_title),
            stringResource(R.string.tweaks_gos_help_undo_body)
        )
    }
}
