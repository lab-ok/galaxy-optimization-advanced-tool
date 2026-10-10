package com.local.folddpifix.ui.appsize

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.local.folddpifix.data.appsize.DensityServer
import com.local.folddpifix.data.appsize.DensityShell
import com.local.folddpifix.data.shizuku.ShizukuAccess
import com.local.folddpifix.ui.components.CommandBox
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.shizuku.ShizukuGuide
import com.local.folddpifix.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SheetColumn(title: String, content: @Composable () -> Unit) {
    val c = LocalLiquid.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun Topic(title: String, body: String) {
    val c = LocalLiquid.current
    GlassCard(padding = 16.dp) {
        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(body, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
}

/** 사용 방법: 무엇을 하는지, Shizuku가 왜 필요한지, 왜 단계가 정해져 있는지, 접고 펼 때 달라지는 이유. */
@Composable
internal fun AppSizeHelpSheet(onConnect: () -> Unit) {
    SheetColumn("사용 방법") {
        Topic(
            "1. 무엇을 하나요",
            "앱마다 화면 크기를 따로 정합니다. 삼성 '설정 → 디스플레이 → 앱 화면 크게/작게'와 같은 설정이라, " +
                "여기서 바꾸면 그 화면에도 그대로 보입니다. 바꾼 값은 재부팅해도 유지됩니다.",
        )
        Topic(
            "2. Shizuku가 필요한 이유",
            "삼성은 이 설정을 바꾸는 기능에 시스템 전용 권한을 걸어 두어, 일반 앱은 직접 바꿀 수 없습니다. " +
                "무료 앱 Shizuku는 폰의 무선 디버깅으로 그 권한을 빌려 줍니다. 처음 한 번 페어링하면 되고, " +
                "Android 13 이상에서는 재부팅 뒤에도 Wi-Fi에 연결되면 스스로 다시 켜집니다. " +
                "Shizuku가 없으면 PC에서 명령 한 번으로 FoldFit 셸 도우미를 켜서 쓸 수도 있습니다.",
        )
        Topic(
            "3. Shizuku가 꺼져 있으면",
            "• 이미 정해 둔 앱 크기: Shizuku가 꺼져 있어도, 재부팅해도 그대로 유지됩니다.\n" +
                "• 목록에서 크기 바꾸기, 알림창 [작게]·[기본]·[크게]: Shizuku가 실행 중일 때만 됩니다.\n" +
                "• 알림에 지금 보고 있는 앱 표시: Shizuku가 실행 중일 때만 실시간으로 바뀝니다.\n" +
                "• '접고 펴도 같은 크기'(기기 전체 크기): Shizuku로 권한을 한 번 받아 두면 이후에는 Shizuku가 없어도 됩니다.",
        )
        Topic(
            "4. 크기가 여섯 단계로 정해진 이유",
            "삼성 시스템이 320·360·420·450·480·510 여섯 값만 받도록 만들어져 있습니다. 다른 값을 넣으면 시스템이 거절해, " +
                "1단위로 조절할 수는 없습니다. '기본'은 앱별 설정을 지우고 기기 전체 크기를 따르게 합니다.",
        )
        Topic(
            "5. 접고 펼 때 크기가 달라 보이는 이유",
            "앱별 크기는 하나의 고정된 DPI라서 외부 화면과 내부 화면에 같은 숫자가 쓰입니다. 두 화면은 픽셀 밀도(PPI)가 달라 " +
                "같은 숫자라도 실제 크기가 다르게 보입니다. 기기 전체 크기는 '접고 펴도 같은 크기'가 화면마다 맞춰 주지만, " +
                "앱별 크기는 시스템이 화면마다 다른 값을 둘 수 없게 되어 있습니다. 주로 쓰는 화면에 맞춰 고르세요.",
        )
        Topic(
            "6. 알림창에서 바로 바꾸기",
            "'알림창에서 바로 조절'을 켜면 알림에 지금 보고 있는 앱과 크기가 나오고, [작게]·[기본]·[크게]로 한 단계씩 바꿉니다. " +
                "삼성 설정 특성상 크기를 바꾸면 그 앱이 다시 시작되며, FoldFit이 바로 다시 열어 줍니다.",
        )
        Topic(
            "7. 되돌리기",
            "앱을 눌러 '기본'을 고르면 그 앱만, ⋮ → 초기화에서는 모든 앱을 기본 크기로 되돌립니다. " +
                "삼성 설정의 '앱 화면 크게/작게'에서 되돌려도 됩니다.",
        )
        LiquidButton("Shizuku 연결 안내 열기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = onConnect)
    }
}

/** 연결(권한 설정): Shizuku 단계 안내, 예비로 PC 셸 도우미 명령. */
@Composable
internal fun AppSizeGuideSheet() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsState()
    val shell by DensityShell.connected.collectAsState()
    var showPc by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    SheetColumn("권한 설정") {
        if (shizuku == ShizukuAccess.State.READY || shell != null) {
            GlassCard(padding = 16.dp) {
                Text("연결되어 있습니다", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    if (shell != null) "PC 셸 도우미로 연결되어 있습니다. FoldFit을 닫거나 30분 동안 쓰지 않으면 꺼집니다."
                    else "Shizuku로 연결되어 있습니다. 이 창을 닫고 앱을 고르면 됩니다.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            return@SheetColumn
        }
        GlassCard(padding = 16.dp) {
            ShizukuGuide(
                shizuku,
                intro = "앱별 크기를 바꾸려면 무료 앱 Shizuku로 시스템 권한을 빌려야 합니다. 아래 순서대로 버튼만 누르면 되고, 처음 한 번만 하면 됩니다.",
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (showPc) "PC로 하기 접기" else "Shizuku 대신 PC로 하기",
            color = c.muted, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showPc = !showPc }.padding(6.dp),
        )
        if (showPc) {
            Text(
                "PC PowerShell(platform-tools 폴더)에서 아래 명령을 한 번 실행하면 FoldFit 셸 도우미가 켜집니다. " +
                    "도우미는 FoldFit을 닫거나 30분 동안 쓰지 않으면 스스로 꺼집니다.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            val cmd = remember { DensityServer.startCommand() }
            CommandBox(cmd, onCopy = {
                context.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
            })
        }
    }
}

/** 초기화: 크기를 정해 둔 앱을 모두 기본으로. 빨간 경고 뒤 한 번 더 확인한다. */
@Composable
internal fun AppSizeResetSheet(onDone: (String) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsState()
    val shell by DensityShell.connected.collectAsState()
    val ready = shizuku == ShizukuAccess.State.READY || shell != null
    // 0 = 처음, 1 = 경고(재확인), 2 = 되돌리는 중
    var step by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) { if (ready) count = withContext(Dispatchers.IO) { runCatching { DensityShell.appliedApps(context).size }.getOrNull() } }
    SheetColumn(Copy.APP_SIZE_RESET_TITLE) {
        GlassCard(padding = 16.dp, accent = if (step > 0) c.danger else null) {
            Text(Copy.APP_SIZE_RESET_ACTION, fontWeight = FontWeight.SemiBold, color = c.danger)
            Text(Copy.APP_SIZE_RESET_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    !ready -> "Shizuku가 연결되어 있지 않아 지금은 되돌릴 수 없습니다."
                    count == null -> "정해 둔 앱을 세는 중…"
                    else -> "지금 크기를 정해 둔 앱: ${count}개"
                },
                color = c.ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            AnimatedContent(step, label = "reset") { s ->
                when (s) {
                    0 -> LiquidButton(Copy.APP_SIZE_RESET_ACTION, modifier = Modifier.fillMaxWidth(), primary = false, danger = true,
                        enabled = ready && (count ?: 0) > 0, onClick = { step = 1 })
                    1 -> Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.danger.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .border(1.dp, c.danger.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Text("⚠ ${Copy.APP_SIZE_RESET_WARN}", color = c.danger, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LiquidButton(Copy.CANCEL, modifier = Modifier.weight(1f), primary = false, onClick = { step = 0 })
                            LiquidButton(Copy.APP_SIZE_RESET_ACTION, modifier = Modifier.weight(1f), danger = true, burst = false, onClick = {
                                step = 2
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { DensityShell.resetAll(context) }
                                    onDone("${n}개 앱을 기본 크기로 되돌렸습니다.")
                                }
                            })
                        }
                    }
                    else -> Text("되돌리는 중…", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
