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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.Doze
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
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
internal fun DozeScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by Doze.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        if (ready) scope.launch(Dispatchers.IO) { Doze.read() }
        onPauseOrDispose { }
    }

    fun apply(off: Boolean) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Doze.set(context, off, "스위치") }
            busy = false
            Toast.makeText(context, if (ok) (if (off) Copy.DOZE_TOAST_OFF else Copy.DOZE_TOAST_ON) else Copy.GOS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
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
                }
            }
        }
        if (!ready) {
            item(key = "connect") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.DOZE_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.DOZE_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.DOZE_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.DOZE_SWITCH_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                when (state) {
                                    Doze.State.OFF -> Copy.DOZE_STATE_OFF
                                    Doze.State.ON -> Copy.DOZE_STATE_ON
                                    Doze.State.UNKNOWN -> Copy.DOZE_STATE_UNKNOWN
                                },
                                color = c.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        LiquidSwitch(state == Doze.State.OFF, { off -> if (off) confirm = true else apply(off = false) }, enabled = ready && !busy && state != Doze.State.UNKNOWN, label = Copy.DOZE_SWITCH_TITLE)
                    }
                }
            }
        }
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DOZE_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DOZE_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.DOZE_CAUTION_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(Copy.DOZE_CONFIRM_TITLE) },
            text = { Text(Copy.DOZE_CONFIRM_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    apply(off = true)
                }) { Text(Copy.DOZE_CONFIRM_OK) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(Copy.GOS_CONFIRM_CANCEL) } }
        )
    }
}

/** 사용 방법. */
@Composable
internal fun DozeHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "화면을 끄고 폰을 가만히 두면 안드로이드는 배터리를 아끼려고 앱의 백그라운드 동작과 네트워크를 잠깐씩 몰아서 처리합니다(잠자기 모드, Doze). " +
                "그래서 메신저·메일 알림이 늦게 오기도 합니다. 이 기능은 'adb shell dumpsys deviceidle disable'과 같은 방법으로 잠자기 모드를 끕니다."
        ) { DozeArt(off = true) }
        HelpTopic(
            "2. 얻는 것과 잃는 것",
            "알림과 백그라운드 작업이 늦어지는 일이 줄어듭니다. 대신 화면이 꺼진 동안에도 앱이 계속 깨어 있어 대기 배터리 소모가 꽤 늘어납니다. " +
                "특정 앱의 알림만 늦다면 그 앱의 배터리 설정을 '제한 없음'으로 바꾸는 편이 배터리에 낫습니다."
        )
        HelpTopic(
            "3. 재부팅 뒤",
            "이 설정은 재부팅하면 다시 켜집니다. 꺼 두었다면 부팅마다 한 번, Shizuku가 연결될 때 GOAT가 다시 끕니다. " +
                "같은 부팅 안에서 PC 등으로 직접 다시 켠 것은 그대로 둡니다."
        )
        HelpTopic(
            "4. 권한과 되돌리기",
            "잠자기 모드는 셸 권한이 필요해 Shizuku로 끄고 켭니다. 스위치를 끄면 원래대로 켜지고, PC에서는 'adb shell dumpsys deviceidle enable'로 되돌립니다."
        )
    }
}
