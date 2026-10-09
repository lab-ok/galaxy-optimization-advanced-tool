package com.local.folddpifix.ui.advanced

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import com.local.folddpifix.ui.liquid.liquidPress
import com.local.folddpifix.ui.liquid.LiquidShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.folddpifix.data.display.DisplayDensity
import com.local.folddpifix.data.log.PublicLogFile
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.ui.art.FoldDeviceArt
import com.local.folddpifix.ui.art.FoldGeometry
import com.local.folddpifix.ui.components.InfoRow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.local.folddpifix.ui.home.UiState
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 고급 정보: 기술 세부 정보는 여기에만 모은다(디스플레이 ID·해상도·PPI·설정 DPI·적용 기록·로그). */
@Composable
internal fun AdvancedSheet(state: UiState, onClearLogs: () -> Unit) {
    val c = LocalLiquid.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
    ) {
        Text("고급 정보", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Text("디스플레이별 DPI·해상도·PPI와 적용 기록입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))

        if (state.displays.isEmpty()) {
            GlassCard { Text(if (state.hasPermission) "읽을 수 있는 디스플레이가 없습니다." else "권한을 설정하면 표시됩니다.", color = c.muted) }
            Spacer(Modifier.height(10.dp))
        }
        state.displays.forEach { d ->
            DisplayItem(d, state)
            Spacer(Modifier.height(10.dp))
        }

        GlassCard {
            Text("적용 기록", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            state.lastApply?.let {
                InfoRow("마지막 적용", "${if (it.success) "성공" else "실패"} · ${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(it.timeMs))}")
                InfoRow("내용", it.text)
            } ?: Text("기록 없음", color = c.muted)
            InfoRow("내부 화면 보정", "${if (state.adjust > 0) "+" else ""}${state.adjust}")
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text("로그", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text("내장 저장소 ${PublicLogFile.displayPath}에 날짜별로 자동 저장됩니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            LiquidButton("앱 내부 로그 삭제", modifier = Modifier.fillMaxWidth(), onClick = onClearLogs, primary = false)
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
        accent = if (active) c.ink else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 외부/내부 화면 그림. 사용 중인 화면은 테두리가 숨 쉬고, 아래에 디스플레이 ID 방울.
            Box(Modifier.size(width = 72.dp, height = 56.dp), contentAlignment = Alignment.TopCenter) {
                FoldDeviceArt(
                    unfolded = role == ScreenPolicy.Role.INNER,
                    height = 46.dp,
                    coverAspect = state.aspectOf(ScreenPolicy.Role.OUTER) ?: FoldGeometry.COVER_ASPECT,
                    innerAspect = state.aspectOf(ScreenPolicy.Role.INNER) ?: FoldGeometry.INNER_ASPECT,
                    still = !active,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(c.ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // 글꼴 위아래 여백을 빼고 줄 높이 안에서 가운데 정렬해 숫자가 원의 정중앙에 오게 한다.
                    Text(
                        "${d.id}", color = c.onInk,
                        style = TextStyle(
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${role?.let { if (it.name == "OUTER") "외부" else "내부" } ?: "미확인"} 화면${if (active) " · 사용 중" else ""}",
                    fontWeight = FontWeight.SemiBold, color = c.ink,
                )
                Text("현재 ${d.current?.let { "$it dpi" } ?: "꺼짐"} · 기본 ${d.physical} dpi", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (open) "접기" else "상세", color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 10.dp)) {
                InfoRow("디스플레이 ID", "${d.id}")
                key?.let { InfoRow("해상도", it.replace("x", " × ")) }
                if (panel != null) {
                    InfoRow("PPI", "%.1f".format(panel.screen.ppi))
                    state.plan?.targetFor(panel.screen)?.let { t ->
                        InfoRow("설정 DPI", if (t.blocked != null) "보류" else "${t.dpi} dpi", emphasize = true)
                        t.blocked?.let { b -> Text(b, color = c.danger, style = MaterialTheme.typography.bodySmall) }
                    }
                } else {
                    Text("아직 활성 상태로 확인되지 않은 화면입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
