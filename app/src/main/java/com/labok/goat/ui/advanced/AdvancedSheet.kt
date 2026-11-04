package com.labok.goat.ui.advanced

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.R
import com.labok.goat.data.display.DisplayDensity
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.domain.ScreenPolicy
import com.labok.goat.ui.art.FoldDeviceArt
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.InfoRow
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.home.UiState
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidShape
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.liquidPress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 고급 정보: 기술 세부 정보는 여기에만 모은다(디스플레이 ID·해상도·PPI·설정 DPI·적용 기록·로그). */
@Composable
internal fun AdvancedSheet(state: UiState, onClearLogs: () -> Unit) {
    val c = LocalLiquid.current
    SheetColumn {
        Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(Gap.Section))

        if (state.displays.isEmpty()) {
            GlassCard { Text(if (state.hasPermission) stringResource(R.string.advanced_no_displays) else stringResource(R.string.advanced_need_permission), color = c.muted) }
            Spacer(Modifier.height(Gap.Item))
        }
        state.displays.forEach { d ->
            DisplayItem(d, state)
            Spacer(Modifier.height(Gap.Item))
        }

        GlassCard {
            Text(stringResource(R.string.advanced_apply_history), fontWeight = FontWeight.SemiBold, color = c.ink)
            state.lastApply?.let {
                InfoRow(stringResource(R.string.advanced_last_apply), "${if (it.success) stringResource(R.string.advanced_apply_success) else stringResource(R.string.advanced_apply_failure)} · ${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(it.timeMs))}")
                InfoRow(stringResource(R.string.advanced_apply_detail), it.text)
            } ?: Text(stringResource(R.string.advanced_no_history), color = c.muted)
            if (state.foldable) InfoRow(stringResource(R.string.advanced_inner_adjust), "${if (state.adjust > 0) "+" else ""}${state.adjust}")
        }
        Spacer(Modifier.height(Gap.Item))
        GlassCard {
            Text(stringResource(R.string.advanced_logs), fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                if (PublicLogFile.enabled(LocalContext.current)) {
                    stringResource(R.string.advanced_logs_public_fmt, PublicLogFile.displayPath)
                } else {
                    stringResource(R.string.advanced_logs_private_fmt, PublicLogFile.displayPath)
                },
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(stringResource(R.string.advanced_clear_logs), modifier = Modifier.fillMaxWidth(), onClick = onClearLogs, primary = false)
        }
    }
}

/** 디스플레이 한 개. 외부/내부 기기 그림(사용 중이면 테두리가 숨 쉼)과 ID 방울, 누르면 세부 정보가 펼쳐진다. */
@Composable
private fun DisplayItem(d: DisplayDensity, state: UiState) {
    val c = LocalLiquid.current
    var open by remember { mutableStateOf(false) }
    val key = d.screen?.key
    val role = key?.let(state::roleOf)
    val active = key != null && key == state.activeScreen?.key
    val panel = state.plan?.panels?.find { it.screen.key == key }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(LiquidShape.card)
    // 누름 표시는 카드 모양(둥근 모서리) 안에서만: 카드가 살짝 눌리고 물결도 카드 곡률로 잘린다.
    GlassCard(
        modifier = Modifier
            .liquidPress(interaction, sx = 1.015f, sy = 0.985f)
            .clip(shape)
            .clickable(interaction, ripple(color = c.ink)) { open = !open },
        padding = 14.dp,
        accent = if (active) c.ink else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 외부/내부 화면 그림. 사용 중인 화면은 테두리가 숨 쉬고, 아래에 디스플레이 ID 방울.
            Box(Modifier.size(width = 72.dp, height = 56.dp), contentAlignment = Alignment.TopCenter) {
                FoldDeviceArt(
                    unfolded = role == ScreenPolicy.Role.INNER,
                    height = 46.dp,
                    still = !active
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(c.ink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // 글꼴 위아래 여백을 빼고 줄 높이 안에서 가운데 정렬해 숫자가 원의 정중앙에 오게 한다.
                    Text(
                        "${d.id}",
                        color = c.onInk,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                        )
                    )
                }
            }
            Spacer(Modifier.width(Space.IconText))
            Column(Modifier.weight(1f)) {
                Text(
                    // 화면이 하나뿐인 기기는 외부·내부 구분이 없으니 그냥 '화면'이라고 쓴다
                    (role?.let { if (it.name == "OUTER") stringResource(R.string.advanced_display_outer) else stringResource(R.string.advanced_display_inner) } ?: if (state.foldable) stringResource(R.string.advanced_display_unknown) else stringResource(R.string.advanced_display_plain))
                        .let { if (active) stringResource(R.string.advanced_display_in_use_fmt, it) else it },
                    fontWeight = FontWeight.SemiBold,
                    color = c.ink
                )
                // 숫자와 단위 사이는 줄바꿈하지 않는 공백(좁은 폭에서 'dpi'만 다음 줄로 떨어졌다)
                Text(stringResource(R.string.advanced_display_dpi_fmt, d.current?.let { "$it\u00A0dpi" } ?: stringResource(R.string.advanced_display_off), d.physical), color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (open) stringResource(R.string.advanced_collapse) else stringResource(R.string.advanced_details), color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(Gap.Item))
                InfoRow(stringResource(R.string.advanced_display_id), "${d.id}")
                key?.let { InfoRow(stringResource(R.string.advanced_resolution), it.replace("x", " × ")) }
                if (panel != null) {
                    InfoRow("PPI", "%.1f".format(panel.screen.ppi))
                    state.plan?.targetFor(panel.screen)?.let { t ->
                        InfoRow(stringResource(R.string.advanced_target_dpi), if (t.blocked != null) stringResource(R.string.advanced_target_held) else "${t.dpi} dpi", emphasize = true)
                        t.blocked?.let { b -> Text(b, color = c.danger, style = MaterialTheme.typography.bodySmall) }
                    }
                } else {
                    Text(stringResource(R.string.advanced_display_unseen), color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
