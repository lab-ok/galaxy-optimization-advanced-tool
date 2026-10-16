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
import com.labok.goat.data.tweaks.Gos
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
 * GOS 끄기: 지금 상태, 끄기 스위치(끌 때 발열·배터리 확인 창), 다시 켜지면 자동으로 끄기, 주의.
 * 기본 앱 정리와 같은 '끄기'라 그 화면 목록의 GOS와 상태가 같다. 돌아올 때마다 다시 읽는다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun GosScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by Gos.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var keep by remember { mutableStateOf(Gos.keep(context)) }
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
            Toast.makeText(context, if (ok) (if (off) Copy.GOS_TOAST_OFF else Copy.GOS_TOAST_ON) else Copy.GOS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
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
                }
            }
        }
        if (!ready) {
            item(key = "connect") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.GOS_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.GOS_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.GOS_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (state == Gos.State.NONE) {
            item(key = "none") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.GOS_NONE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.GOS_NONE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            item(key = "switch") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Copy.GOS_SWITCH_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                                Text(
                                    when (state) {
                                        Gos.State.OFF -> Copy.GOS_STATE_OFF
                                        Gos.State.ON -> Copy.GOS_STATE_ON
                                        else -> Copy.GOS_STATE_UNKNOWN
                                    },
                                    color = c.muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            LiquidSwitch(state == Gos.State.OFF, { off -> if (off) confirm = true else apply(off = false) }, enabled = ready && !busy && state != Gos.State.UNKNOWN, label = Copy.GOS_SWITCH_TITLE)
                        }
                        Spacer(Modifier.height(Gap.Section))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Copy.GOS_KEEP_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                                Text(Copy.GOS_KEEP_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            LiquidSwitch(keep, { on ->
                                keep = on
                                Gos.setKeep(context, on)
                            }, label = Copy.GOS_KEEP_TITLE)
                        }
                    }
                }
            }
        }
        if (state != Gos.State.NONE) {
            item(key = "caution") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.GOS_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.GOS_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(Copy.GOS_CONFIRM_TITLE) },
            text = { Text(Copy.GOS_CONFIRM_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    apply(off = true)
                }) { Text(Copy.GOS_CONFIRM_OK) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(Copy.GOS_CONFIRM_CANCEL) } }
        )
    }
}

/** 사용 방법. */
@Composable
internal fun GosHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. GOS란",
            "게임 최적화 서비스(GOS)는 게임을 하는 동안 해상도·프레임·처리 성능을 낮춰 발열과 배터리 소모를 줄이는 삼성 서비스입니다. " +
                "끄면 GOS의 조절이 멈춰 같은 게임에서 더 높은 성능을 낼 수 있습니다. 실제 차이는 기기와 게임마다 다릅니다."
        ) { GosArt(off = true) }
        HelpTopic(
            "2. 얻는 것과 잃는 것",
            "성능 조절이 멈추는 대신 오래 플레이하면 휴대폰이 더 뜨거워지고 배터리가 빨리 닳을 수 있습니다. 너무 뜨거워지면 " +
                "시스템이 스스로 성능을 낮춥니다. 게임 런처와 게임 부스터는 그대로 둡니다."
        )
        HelpTopic(
            "3. 다시 켜지는 경우",
            "업데이트나 재부팅 뒤 GOS가 다시 켜지는 기기가 있습니다. '다시 켜지면 자동으로 끄기'를 켜 두면 부팅마다 한 번, Shizuku가 연결될 때 확인해 다시 끕니다. " +
                "같은 부팅 안에서 설정 앱 등으로 직접 다시 켠 것은 그대로 둡니다."
        )
        HelpTopic(
            "4. 되돌리기와 기본 앱 정리",
            "스위치를 끄면 GOS가 다시 켜집니다. 기본 앱 정리 목록의 '게임 최적화 서비스(GOS)'와 같은 설정이라 어느 화면에서 바꿔도 같은 상태가 됩니다. " +
                "Shizuku 셸 권한으로 끄고 켭니다."
        )
    }
}
