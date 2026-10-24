package com.labok.goat.ui.camera

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.copyText
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
 * 카메라 촬영음 끄기: 소개 카드의 끄기 스위치(켜 두면 재부팅 뒤 다시 끔), Shizuku가 없을 때 PC 명령, 촬영 예절 안내.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun CameraMuteScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by CameraMute.status.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val ready = shizuku == ShizukuAccess.State.READY

    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    // 돌아올 때마다(설정·카메라 앱을 다녀와도) 지금 값을 다시 읽는다
    LifecycleResumeEffect(ready) {
        if (ready) scope.launch(Dispatchers.IO) { CameraMute.read() }
        onPauseOrDispose { }
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
                    Text(Copy.CAMERA_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.CAMERA_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    ShutterArt()
                    Spacer(Modifier.height(Gap.Item))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.FEATURE_CAMERA_MUTE, fontWeight = FontWeight.SemiBold, color = if (ready && state != CameraMute.State.NONE) c.ink else c.muted)
                            Text(
                                when (state) {
                                    CameraMute.State.MUTED -> Copy.CAMERA_STATE_MUTED
                                    CameraMute.State.FORCED -> Copy.CAMERA_STATE_FORCED
                                    CameraMute.State.NONE -> Copy.CAMERA_STATE_NONE
                                    CameraMute.State.UNKNOWN -> Copy.CAMERA_STATE_UNKNOWN
                                },
                                color = c.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        LiquidSwitch(state == CameraMute.State.MUTED, { want ->
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { CameraMute.set(context, want, "스위치") }
                                busy = false
                                Toast.makeText(context, if (ok) (if (want) Copy.CAMERA_TOAST_MUTED else Copy.CAMERA_TOAST_RESTORED) else Copy.CAMERA_TOAST_FAILED, Toast.LENGTH_SHORT).show()
                            }
                        }, enabled = ready && !busy && state != CameraMute.State.NONE, label = Copy.FEATURE_CAMERA_MUTE)
                    }
                    // 강제 설정이 없어 켤 수 없는 기기에는 '켜 두면' 안내를 보이지 않는다(1.24.8 점검)
                    if (state != CameraMute.State.NONE) {
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
                        Text(Copy.CAMERA_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.CAMERA_NEED_SHIZUKU_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.CAMERA_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Gap.Section))
                        Text(Copy.CAMERA_PC_TITLE, color = c.ink)
                        Text(Copy.CAMERA_PC_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        CommandBox(CameraMute.PC_COMMAND, onCopy = {
                            if (copyText(context, CameraMute.PC_COMMAND)) Toast.makeText(context, Copy.TOAST_COPIED, Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
        item(key = "etiquette") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CAMERA_ETIQUETTE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.CAMERA_ETIQUETTE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법: 무엇을 하는지, 왜 Shizuku가 필요한지, 재부팅·업데이트 뒤, 되돌리기, 주의. */
@Composable
internal fun CameraMuteHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 하는 일",
            "국내 갤럭시는 무음·진동 모드여도 카메라 촬영음이 강제로 납니다. 이 강제 설정을 끄면 촬영음이 기기 소리 설정을 따라, " +
                "무음·진동 모드에서는 소리 없이 찍히고 소리 모드에서는 그대로 납니다. 삼성 기본 카메라와 대부분의 카메라 앱에 적용됩니다."
        ) { ShutterArt() }
        HelpTopic(
            "2. 꼭 지켜 주세요",
            "촬영음은 몰래 촬영을 막기 위한 장치입니다. 다른 사람을 동의 없이 찍거나 몰래 찍으면 법에 따라 처벌받습니다. " +
                "조용한 곳에서 내 물건, 문서, 음식을 찍을 때처럼 예절을 지키는 데만 쓰세요."
        )
        HelpTopic(
            "3. Shizuku가 필요한 이유",
            "촬영음 강제는 삼성이 숨겨 둔 시스템 설정이라, 일반 앱은 시스템 설정 권한이 있어도 읽거나 바꿀 수 없습니다. " +
                "Shizuku가 빌려 주는 셸 권한으로 'settings put system ${CameraMute.KEY} 0'을 실행합니다. " +
                "Shizuku가 없으면 PC에서 같은 명령을 한 번 실행해도 됩니다."
        )
        HelpTopic(
            "4. 재부팅·업데이트 뒤",
            "기기에 따라 업데이트나 재부팅 뒤 촬영음이 다시 켜질 수 있습니다. 스위치를 켜 두면 " +
                "Shizuku가 연결될 때마다(Android 13 이상은 재부팅 뒤 Wi-Fi에 연결되면 자동으로 연결) 확인해 다시 끕니다."
        )
        HelpTopic(
            "5. 되돌리기",
            "스위치를 끄면 촬영음이 다시 강제로 나도록 원래대로 돌아갑니다. PC에서는 '${CameraMute.PC_UNDO}'로 되돌립니다. " +
                "'이 기기에는 강제 설정이 없음'으로 나오면 해외 모델처럼 이미 소리 설정을 따르는 기기입니다."
        )
    }
}
