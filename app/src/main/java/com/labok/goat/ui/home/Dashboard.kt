package com.labok.goat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidShape
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.nav.NavGlyph
import com.labok.goat.ui.nav.NavItem
import com.labok.goat.ui.nav.NavSection
import com.labok.goat.ui.text.Copy

/**
 * 메인 화면: 권한·Shizuku 상태 한 줄과, 기능마다 아이콘·이름·지금 상태를 보여 주는 카드. 카드를 누르면 그 기능으로 간다.
 * 카드는 사이드바와 같은 묶음 순서로 놓고, 넓은 화면에서는 한 줄에 더 많이 놓는다.
 */
@Composable
internal fun Dashboard(contentPadding: PaddingValues, state: UiState, sections: List<NavSection>, onOpen: (NavItem) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val camera by CameraMute.status.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

    // 기능마다 지금 상태 한 줄(돌아올 때마다 다시 읽는다)
    val status: (NavItem) -> String = { item ->
        tick
        when (item) {
            NavItem.DPI_MATCH -> state.target?.let { "외부 화면 $it dpi" } ?: "외부 화면 DPI를 정해 주세요"
            NavItem.APP_SIZE -> DensityShell.savedCount(context).let { if (it == 0) "바꾼 앱 없음" else "바꾼 앱 ${it}개" }
            NavItem.ANIMATION -> AnimationSpeed.label(AnimationSpeed.read(context, AnimationSpeed.Kind.WINDOW))
            NavItem.SHAKE_ROTATE -> if (ShakeRotate.enabled(context)) "켜짐 · 민감도 ${ShakeRotate.level(context)}" else "꺼짐"
            NavItem.CAMERA_MUTE -> when (camera) {
                CameraMute.State.MUTED -> "촬영음 꺼 둠"
                CameraMute.State.FORCED -> "촬영음 강제로 남"
                CameraMute.State.NONE -> "강제 설정 없는 기기"
                CameraMute.State.UNKNOWN -> if (CameraMute.wanted(context)) "꺼 두기로 함" else "Shizuku로 끕니다"
            }
            NavItem.APP_CLEANUP -> "쓰지 않는 기본 앱 끄기"
            else -> ""
        }
    }
    val features = sections.flatMap { it.items }.filter { it != NavItem.HOME && it != NavItem.LAB_TOOLS }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        horizontalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "hello", span = { GridItemSpan(maxLineSpan) }) {
            GlassCard {
                Text(Copy.HOME_TITLE, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleLarge)
                Text(Copy.HOME_SUBTITLE, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Badge(Copy.HOME_PERMISSION, state.hasPermission)
                    Badge(Copy.HOME_SHIZUKU, shizuku == ShizukuAccess.State.READY)
                }
            }
        }
        items(features, key = { it.name }) { item ->
            FeatureCard(item, status(item)) { onOpen(item) }
        }
    }
}

/** 상태 점과 이름: 되어 있으면 잉크 점, 아니면 빈 고리. */
@Composable
private fun Badge(label: String, on: Boolean) {
    val c = LocalLiquid.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c.ink else c.line))
        Spacer(Modifier.width(6.dp))
        Text(label + if (on) "" else " 필요", color = if (on) c.ink else c.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** 기능 카드: 아이콘, 이름, 지금 상태. 누르면 살짝 눌리며 그 기능으로 간다. */
@Composable
private fun FeatureCard(item: NavItem, status: String, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .liquidPress(interaction, sx = 1.03f, sy = 0.96f)
            .clip(RoundedCornerShape(LiquidShape.card))
            .clickable(interaction, null, onClick = onClick),
    ) {
        GlassCard(Modifier.heightIn(min = 128.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.ink), contentAlignment = Alignment.Center) {
                NavGlyph(item.icon, c.onInk)
            }
            Spacer(Modifier.height(Gap.Item))
            Text(item.label, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
