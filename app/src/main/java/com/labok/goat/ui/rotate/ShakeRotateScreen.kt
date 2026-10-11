package com.labok.goat.ui.rotate

import com.labok.goat.ui.components.HelpTopic
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.background.ExternalChangeNotifier
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy

/**
 * 흔들어 화면 회전: 켜기 스위치, 민감도, 그림 설명. 회전 잠금은 화면 배율 동기화와 같은 시스템 설정 권한을 쓴다.
 * @param onGuide 권한이 없을 때 권한 설정 방법을 연다.
 */
@Composable
internal fun ShakeRotateScreen(contentPadding: PaddingValues, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ShakeRotate.canWrite(context)) }
    var on by remember { mutableStateOf(ShakeRotate.enabled(context)) }
    var sensitivity by remember { mutableStateOf(ShakeRotate.sensitivity(context)) }
    var message by remember { mutableStateOf("") }

    fun turnOn() {
        on = ShakeRotate.setEnabled(context, true)
        message = if (on) "" else "회전을 잠그지 못했습니다. 권한 설정을 확인해 주세요."
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { turnOn() }

    // 권한 설정에서 돌아오거나 빠른 설정에서 자동 회전을 켜고 돌아와도 바로 보이게 한다
    LifecycleResumeEffect(Unit) {
        permitted = ShakeRotate.canWrite(context)
        on = ShakeRotate.enabled(context)
        if (on) ShakeRotate.start(context)
        onPauseOrDispose { }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = 16.dp) {
                    Text(Copy.SHAKE_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    ShakeTurnArt()
                }
            }
        }
        if (!permitted) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = 16.dp) {
                    Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.FEATURE_SHAKE_ROTATE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                if (on) Copy.SHAKE_ON_BODY else Copy.SHAKE_OFF_BODY,
                                color = c.muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        LiquidSwitch(on, { want ->
                            when {
                                !want -> { ShakeRotate.setEnabled(context, false); on = false }
                                !ExternalChangeNotifier.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> turnOn()
                            }
                        }, enabled = permitted)
                    }
                    AnimatedVisibility(message.isNotEmpty()) {
                        Text(message, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        item(key = "sensitivity") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = 16.dp) {
                    Text(Copy.SHAKE_SENSITIVITY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_SENSITIVITY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    LiquidChips(
                        options = ShakeRotatePolicy.Sensitivity.entries,
                        selected = sensitivity,
                        label = { it.label },
                        onSelect = { sensitivity = it; ShakeRotate.setSensitivity(context, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 사용 방법: 무엇을 하는지, 누워서 볼 때, 다시 세로로, 배터리·알림, 앱이 방향을 정할 때, 끌 때. */
@Composable
internal fun ShakeRotateHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic("1. 무엇을 하나요",
            "자동 회전을 끈 채로 두다가, 폰을 짧게 서너 번 흔들면 그 순간 폰을 든 방향(세로·가로)으로 화면을 돌립니다. " +
                "돌린 뒤에는 그 방향에 다시 잠겨 자세를 바꿔도 저절로 돌아가지 않습니다. 돌아가면 알림 없이 토도독 진동만 합니다.",
        ) { ShakeTurnArt() }
        HelpTopic("2. 누워서 볼 때",
            "옆으로 누워 폰을 보면 자동 회전은 폰이 옆으로 누운 것으로 알고 화면을 가로로 돌려 버립니다. " +
                "흔들어 화면 회전을 켜 두면 흔들기 전까지는 지금 방향 그대로입니다.",
        ) { LyingDownArt() }
        HelpTopic("3. 다시 세로로 돌리기",
            "가로로 본 뒤 폰을 세로로 들고 흔들면 세로로 돌아옵니다. 이미 든 방향과 화면 방향이 같을 때 흔들면 세로와 가로를 맞바꿉니다. " +
                "그래서 누운 자세에서 원하는 방향이 아니면 한 번 더 흔들면 됩니다.",
        ) { ShakeTurnArt(toLandscape = false) }
        HelpTopic("4. 배터리와 알림",
            "흔들기는 가속도 센서로만 감지하고, 화면이 꺼져 있을 때는 센서를 쓰지 않아 배터리를 거의 쓰지 않습니다. " +
                "시스템이 감지를 멈추지 않도록 켜 두는 동안 알림이 하나 떠 있습니다. 알림이 거슬리면 알림 설정에서 이 알림만 숨길 수 있습니다.",
        )
        HelpTopic("5. 동영상처럼 앱이 방향을 정할 때",
            "동영상 앱의 전체 화면처럼 앱이 방향을 직접 정하면 앱이 정한 방향이 먼저입니다. 그 앱을 닫으면 잠가 둔 방향으로 돌아옵니다.",
        )
        HelpTopic("6. 끄면",
            "끄면 켜기 전의 자동 회전 설정으로 되돌립니다. 켜 둔 채 빠른 설정에서 자동 회전을 켜면, 다음에 흔들 때 다시 잠깁니다.",
        )
    }
}

