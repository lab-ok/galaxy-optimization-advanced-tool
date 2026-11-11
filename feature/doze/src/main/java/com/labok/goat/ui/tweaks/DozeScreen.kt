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
import com.labok.goat.data.tweaks.Doze
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
 * 잠자기 모드 끄기: 지금 상태, 끄기 스위치(끌 때 배터리 확인 창), 주의. 재부팅하면 다시 켜지는 설정이라
 * 꺼 두기로 했으면 부팅마다 Shizuku가 연결될 때 다시 끈다([Doze.watch]). 돌아올 때마다 다시 읽는다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
fun DozeScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by Doze.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var busy by remember { mutableStateOf(false) }
    // 누른 쪽으로 스위치를 바로 옮겨 두는 값(셸 명령이 끝나고 상태를 다시 읽을 때까지). 끝나면 읽은 상태를 따른다
    var pending by remember { mutableStateOf<Boolean?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        if (ready) scope.launch(Dispatchers.IO) { Doze.read() }
        onPauseOrDispose { }
    }

    fun apply(off: Boolean) {
        busy = true
        pending = off
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Doze.set(context, off, "스위치") }
            busy = false
            pending = null
            Toast.makeText(context, if (ok) (if (off) Copy.DOZE_TOAST_OFF else Copy.DOZE_TOAST_ON) else Copy.SHIZUKU_FAILED, Toast.LENGTH_SHORT).show()
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
                    Text(Copy.DOZE_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DOZE_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    DozeArt(off = state == Doze.State.OFF)
                    Spacer(Modifier.height(Gap.Item))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.FEATURE_DOZE, fontWeight = FontWeight.SemiBold, color = if (ready && state != Doze.State.UNKNOWN) c.ink else c.muted)
                            Text(
                                when (state) {
                                    Doze.State.OFF -> Copy.DOZE_STATE_OFF
                                    Doze.State.ON -> Copy.DOZE_STATE_ON
                                    Doze.State.UNKNOWN -> Copy.SHIZUKU_STATE_UNKNOWN
                                },
                                color = c.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(Space.IconText))
                        LiquidSwitch(pending ?: (state == Doze.State.OFF), { off -> if (off) confirm = true else apply(off = false) }, enabled = ready && !busy && state != Doze.State.UNKNOWN, label = Copy.FEATURE_DOZE)
                    }

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
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DOZE_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.DOZE_CAUTION_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(Copy.FEATURE_DOZE) },
            text = { Text(Copy.DOZE_CONFIRM_BODY) },
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
fun DozeHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(
            stringResource(R.string.tweaks_help_what_title),
            stringResource(R.string.tweaks_doze_help_what_body)
        ) { DozeArt(off = true) }
        HelpTopic(
            stringResource(R.string.tweaks_help_tradeoff_title),
            stringResource(R.string.tweaks_doze_help_tradeoff_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_doze_help_reboot_title),
            stringResource(R.string.tweaks_doze_help_reboot_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_help_permission_undo_title),
            stringResource(R.string.tweaks_doze_help_undo_body)
        )
    }
}
