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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.R
import com.labok.goat.data.appsize.DensityServer
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.domain.AppDensityPolicy
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.DangerConfirmBox
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.Inset
import com.labok.goat.ui.components.Radius
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
    SheetColumn(title = Copy.MENU_HELP) {
        // 순서: 무엇 → 쓰는 방법(단계·폴드·알림창) → 필요한 것(Shizuku) → 되돌리기. 폴드 항목이 빠지면 번호가 당겨진다.
        var no = 0
        fun n() = ++no
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_what_title)}",
            stringResource(R.string.appsize_help_what_body),
            art = { AppScaleArt() }
        )
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_steps_title)}",
            stringResource(R.string.appsize_help_steps_body_fmt, AppDensityPolicy.STEPS.joinToString(", ")),
            art = { StepsArt() }
        )
        // 접고 펼 때 이야기는 폴드에서만
        if (foldable) {
            HelpTopic(
                "${n()}. ${stringResource(R.string.appsize_help_fold_title)}",
                stringResource(R.string.appsize_help_fold_body_fmt, Copy.FEATURE_DPI),
                art = { FoldDiffArt() }
            )
        }
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_notify_title)}",
            stringResource(R.string.appsize_help_notify_body_fmt, Copy.APP_SIZE_NOTIFY_TITLE),
            art = { NotifyArt() }
        )
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_shizuku_title)}",
            stringResource(R.string.appsize_help_shizuku_body),
            art = { ShizukuBridgeArt() }
        )
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_off_title)}",
            stringResource(R.string.appsize_help_off_body_fmt, Copy.FEATURE_DPI, Copy.APP_SIZE_AUTO_OFF_TITLE),
            art = { PersistArt() }
        )
        HelpTopic(
            "${n()}. ${stringResource(R.string.appsize_help_reset_title)}",
            stringResource(R.string.appsize_help_reset_body),
            art = { ResetArt() }
        )
        LiquidButton(Copy.SHIZUKU_CONNECT, modifier = Modifier.fillMaxWidth(), primary = false, onClick = onConnect)
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
    SheetColumn(title = Copy.MENU_GUIDE) {
        if (shizuku == ShizukuAccess.State.READY || shell != null) {
            GlassCard {
                Text(stringResource(R.string.appsize_guide_connected), fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    if (shell != null) {
                        stringResource(R.string.appsize_guide_pc_idle_fmt, DensityServer.IDLE_MINUTES)
                    } else {
                        stringResource(R.string.appsize_guide_shizuku_granted)
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
                intro = stringResource(R.string.appsize_guide_intro)
            )
        }
        Spacer(Modifier.height(Gap.Card))
        Text(
            if (showPc) stringResource(R.string.appsize_guide_pc_hide) else stringResource(R.string.appsize_guide_pc_show),
            color = c.muted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(Radius.Row)).clickable { showPc = !showPc }.padding(Inset.TextButton)
        )
        if (showPc) {
            Text(
                stringResource(R.string.appsize_guide_pc_body_fmt, DensityServer.IDLE_MINUTES),
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
            if (ready && count == null) {
                LiquidLoader(stringResource(R.string.appsize_reset_counting), Modifier.padding(vertical = Gap.Item))
            } else {
                Text(
                    when {
                        !ready -> stringResource(R.string.appsize_reset_not_ready)
                        // 되돌릴 앱이 없으면 꺼진 버튼 대신 여기서 알린다(꺼진 버튼을 누르면 뜨는 토스트가 시트 맨 아래 버튼을 가렸다, QA 1차)
                        count == 0 -> Copy.DISABLED_NO_SIZED_APPS
                        else -> stringResource(R.string.appsize_reset_count_fmt, count ?: 0)
                    },
                    color = c.ink,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                                onDone(Copy.text(R.string.appsize_reset_done_fmt, n))
                            }
                        })
                    }
                    else -> LiquidLoader(stringResource(R.string.appsize_reset_running), Modifier.padding(vertical = Gap.Item))
                }
            }
        }
    }
}
