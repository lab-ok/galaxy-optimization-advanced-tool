package com.labok.goat.ui.appsize

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.appsize.DensityServer
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.domain.AppDensityPolicy
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.DangerConfirmBox
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.copyText
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.shizuku.ShizukuGuide
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 사용 방법: 무엇을 하는지, Shizuku가 왜 필요한지, 왜 단계가 정해져 있는지, 접고 펼 때 달라지는 이유. */
@Composable
internal fun AppSizeHelpSheet(foldable: Boolean = true, onConnect: () -> Unit) {
    SheetColumn(title = "사용 방법") {
        // 순서: 무엇 → 쓰는 방법(단계·폴드·알림창) → 필요한 것(Shizuku) → 되돌리기. 폴드 항목이 빠지면 번호가 당겨진다.
        var no = 0
        fun n() = ++no
        HelpTopic(
            "${n()}. 하는 일",
            "앱마다 화면 크기를 따로 정합니다. 삼성 '설정 → 디스플레이 → 앱 화면 크게/작게'와 같은 설정이므로, " +
                "여기서 바꾸면 그 화면에도 그대로 반영됩니다. 바꾼 값은 재부팅해도 유지됩니다.",
            art = { AppScaleArt() }
        )
        HelpTopic(
            "${n()}. 크기가 여섯 단계로 정해진 이유",
            "삼성 시스템이 ${AppDensityPolicy.STEPS.joinToString("·")} 여섯 값만 허용합니다. 다른 값을 넣으면 시스템이 거부하므로, " +
                "1단위로 조절할 수는 없습니다. '기본'은 앱별 설정을 지우고 기기 전체 크기를 따르게 합니다.",
            art = { StepsArt() }
        )
        // 접고 펼 때 이야기는 폴드에서만
        if (foldable) {
            HelpTopic(
                "${n()}. 접고 펼 때 크기가 달라 보이는 이유",
                "앱별 크기는 하나의 고정된 DPI라서 외부 화면과 내부 화면에 같은 숫자가 쓰입니다. 두 화면은 픽셀 밀도(PPI)가 달라 " +
                    "같은 숫자라도 실제 크기가 다르게 보입니다. 기기 전체 크기는 '고급 화면 배율 설정'이 화면마다 맞춰 주지만, " +
                    "앱별 크기는 시스템상 화면마다 다른 값을 지정할 수 없습니다. 주로 쓰는 화면에 맞춰 고르세요.",
                art = { FoldDiffArt() }
            )
        }
        HelpTopic(
            "${n()}. 알림창에서 바로 바꾸기",
            "'알림창에서 바로 조절'을 켜면 알림에 지금 보고 있는 앱과 크기가 나오고, [작게]·[기본]·[크게]로 한 단계씩 바꿉니다. " +
                "삼성 설정 특성상 크기를 바꾸면 그 앱이 다시 시작되며, GOAT가 바로 다시 열어 줍니다.",
            art = { NotifyArt() }
        )
        HelpTopic(
            "${n()}. Shizuku가 필요한 이유",
            "삼성은 이 설정을 바꾸는 기능에 시스템 전용 권한을 요구하므로 일반 앱은 직접 바꿀 수 없습니다. " +
                "무료 앱 Shizuku는 휴대폰의 무선 디버깅을 이용해 그 권한을 빌려 줍니다. 처음 한 번만 페어링하면 되며, " +
                "Android 13 이상에서는 재부팅 뒤에도 Wi-Fi에 연결되면 자동으로 다시 켜집니다. " +
                "Shizuku가 없으면 PC에서 명령 한 번으로 GOAT 셸 도우미를 켜서 쓸 수도 있습니다.",
            art = { ShizukuBridgeArt() }
        )
        HelpTopic(
            "${n()}. Shizuku가 꺼져 있으면",
            "• 이미 정해 둔 앱 크기: Shizuku가 꺼져 있어도, 재부팅해도 그대로 유지됩니다.\n" +
                "• 목록에서 크기 바꾸기, 알림창 [작게]·[기본]·[크게]: Shizuku가 실행 중일 때만 사용할 수 있습니다.\n" +
                "• 알림에 지금 보고 있는 앱 표시: Shizuku가 실행 중일 때만 실시간으로 바뀝니다.\n" +
                "• '고급 화면 배율 설정'(기기 전체 크기): Shizuku로 권한을 한 번 받아 두면 이후에는 Shizuku가 없어도 됩니다.\n" +
                "• 무선 디버깅은 Shizuku를 시작할 때만 필요합니다. 'Shizuku가 켜지면 무선 디버깅 끄기'를 켜면 Shizuku가 켜진 직후 GOAT가 끕니다. 무선 디버깅을 끄면 Shizuku가 멈추는 기기에서는 이 옵션을 꺼 두세요.",
            art = { PersistArt() }
        )
        HelpTopic(
            "${n()}. 되돌리기",
            "앱을 눌러 '기본'을 고르면 그 앱만, ⋮ → 초기화에서는 모든 앱을 기본 배율로 되돌립니다. " +
                "삼성 설정의 '앱 화면 크게/작게'에서 되돌릴 수도 있습니다.",
            art = { ResetArt() }
        )
        LiquidButton("Shizuku 연결 안내 열기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = onConnect)
    }
}

/** 연결(권한 설정): Shizuku 단계 안내, 예비로 PC 셸 도우미 명령. */
@Composable
internal fun AppSizeGuideSheet() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val shell by DensityShell.connected.collectAsStateWithLifecycle()
    var showPc by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    SheetColumn(title = "권한 설정") {
        if (shizuku == ShizukuAccess.State.READY || shell != null) {
            GlassCard {
                Text("연결되어 있습니다", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    if (shell != null) {
                        "PC 셸 도우미로 연결되어 있습니다. GOAT를 닫거나 ${DensityServer.IDLE_MINUTES}분 동안 쓰지 않으면 꺼집니다."
                    } else {
                        "Shizuku 권한을 받았습니다. 이 창을 닫고 계속 진행하세요."
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@SheetColumn
        }
        GlassCard {
            ShizukuGuide(
                shizuku,
                intro = "이 기능은 무료 앱 Shizuku로 시스템 권한을 빌려 씁니다. 아래 순서대로 버튼만 누르면 되며, 처음 한 번만 설정하면 됩니다."
            )
        }
        Spacer(Modifier.height(Gap.Card))
        Text(
            if (showPc) "PC로 하기 접기" else "Shizuku 대신 PC로 하기",
            color = c.muted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showPc = !showPc }.padding(6.dp)
        )
        if (showPc) {
            Text(
                "PC PowerShell(platform-tools 폴더)에서 아래 명령을 한 번 실행하면 GOAT 셸 도우미가 켜집니다. " +
                    "도우미는 GOAT를 닫거나 ${DensityServer.IDLE_MINUTES}분 동안 쓰지 않으면 자동으로 꺼집니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            val cmd = remember { DensityServer.startCommand() }
            CommandBox(cmd, onCopy = {
                if (copyText(context, cmd, "adb")) Toast.makeText(context, Copy.TOAST_COPIED, Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** 초기화: 배율을 바꾼 앱을 모두 기본으로. 빨간 경고 뒤 한 번 더 확인한다. */
@Composable
internal fun AppSizeResetSheet(onDone: (String) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val shell by DensityShell.connected.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY || shell != null
    // 0 = 처음, 1 = 경고(재확인), 2 = 되돌리는 중
    var step by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) { if (ready) count = withContext(Dispatchers.IO) { runCatching { DensityShell.appliedApps(context).size }.getOrNull() } }
    SheetColumn(title = Copy.APP_SIZE_RESET_TITLE) {
        GlassCard(accent = if (step > 0) c.danger else null) {
            Text(Copy.APP_SIZE_RESET_ACTION, fontWeight = FontWeight.SemiBold, color = c.danger)
            Text(Copy.APP_SIZE_RESET_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            // 개수를 세는 동안은 리퀴드 로딩 방울을 보여 준다
            if (ready && count == null) LiquidLoader("배율을 바꾼 앱을 확인하는 중…", Modifier.padding(vertical = 8.dp)) else Text(
                when {
                    !ready -> "Shizuku가 연결되어 있지 않아 지금은 되돌릴 수 없습니다."
                    // 되돌릴 앱이 없으면 꺼진 버튼 대신 여기서 알린다(꺼진 버튼을 누르면 뜨는 토스트가 시트 맨 아래 버튼을 가렸다, QA 1차)
                    count == 0 -> Copy.DISABLED_NO_SIZED_APPS
                    else -> "현재 배율을 바꾼 앱: ${count}개"
                },
                color = c.ink,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            AnimatedContent(step, label = "reset") { s ->
                when (s) {
                    0 -> LiquidButton(
                        Copy.APP_SIZE_RESET_ACTION,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false,
                        danger = true,
                        enabled = ready && (count ?: 0) > 0,
                        disabledHint = if (!ready) Copy.DISABLED_SHIZUKU else null,
                        onClick = { step = 1 }
                    )
                    1 -> DangerConfirmBox(Copy.APP_SIZE_RESET_WARN) {
                        LiquidButton(Copy.CANCEL, modifier = Modifier.weight(1f), primary = false, onClick = { step = 0 })
                        LiquidButton(Copy.APP_SIZE_RESET_ACTION, modifier = Modifier.weight(1f), danger = true, burst = false, onClick = {
                            step = 2
                            scope.launch {
                                val n = withContext(Dispatchers.IO) { DensityShell.resetAll(context) }
                                onDone("${n}개 앱을 기본 배율로 되돌렸습니다.")
                            }
                        })
                    }
                    else -> LiquidLoader("되돌리는 중…", Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}
