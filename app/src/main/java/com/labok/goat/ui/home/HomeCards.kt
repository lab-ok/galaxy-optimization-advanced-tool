package com.labok.goat.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.display.PermissionReset
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.data.update.UpdateNotice
import com.labok.goat.domain.DpiPolicy
import com.labok.goat.domain.PanelSpec
import com.labok.goat.domain.ScreenGeometry
import com.labok.goat.domain.ScreenPolicy
import com.labok.goat.ui.art.FoldDeviceArt
import com.labok.goat.ui.art.ScreensPreview
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.DangerConfirmBox
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidDots
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.delay
/** 상태: 폴드 기기 그림 + 지금 화면의 크기 + 한 줄 설명. */
@Composable
internal fun StatusCard(state: UiState, onAuto: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    val role = state.effective?.role
    val unfolded = role == ScreenPolicy.Role.INNER
    val line = when {
        state.loading -> ""
        !state.hasPermission -> Copy.STATUS_NEED_SETUP
        state.target == null -> if (state.foldable) Copy.STATUS_NO_TARGET else Copy.STATUS_NO_TARGET_SINGLE
        state.external != null -> Copy.STATUS_EXTERNAL
        state.status == UiState.Status.BLOCKED -> Copy.STATUS_HOLD
        state.foldable && state.rows.size < 2 -> Copy.STATUS_NEED_OTHER
        state.status == UiState.Status.OK -> if (state.foldable) Copy.STATUS_OK else Copy.STATUS_OK_SINGLE
        // 자동 적용이 꺼져 있으면 GOAT가 적용하고 있는 것이 아니다: 설정값과 다르다고만 알린다
        !state.auto -> Copy.STATUS_NOT_APPLIED
        else -> Copy.STATUS_FIXING
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoldDeviceArt(
                unfolded = unfolded,
                height = 118.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    // 폴드가 아니면 화면이 하나라 외부·내부를 말하지 않는다
                    if (!state.foldable) {
                        Copy.CURRENT
                    } else {
                        when (role) {
                            ScreenPolicy.Role.INNER -> Copy.INNER
                            ScreenPolicy.Role.OUTER -> Copy.OUTER
                            null -> Copy.CURRENT
                        }
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.dpi?.current?.toString() ?: "—",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        color = c.ink
                    )
                    Spacer(Modifier.width(4.dp))
                    // 좁은 폭에서 'dp'·'i'로 쪼개져 '360 dp'처럼 읽혔다(조합 점검). 단위는 한 줄로 둔다
                    Text("dpi", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(line, color = c.ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // 켜고 끄기는 다른 기능처럼 그림 카드 안에 둔다(1.19.0 소장님 지정, DPI 화면만 따로 카드에 남아 있었다 10-30)
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow(Copy.AUTO_TITLE, Copy.AUTO_BODY, state.auto, enabled = true, onChange = onAuto)
    }
}

internal enum class TodoAction { SETUP, RELEARN, APPLY, AUTO, REPORT, ADOPT, RESTORE }

internal data class Todo(
    val key: String,
    val title: String,
    val body: String?,
    val action: TodoAction?,
    val actionLabel: String?,
    val blocking: Boolean = false,
    /** 두 번째 선택지(외부 변경의 복원 등). 있으면 버튼을 본문 아래에 나란히 둔다. */
    val second: TodoAction? = null,
    val secondLabel: String? = null
)

/** 사용자가 할 일만. 앱을 못 쓰게 하는 것(준비)을 맨 위에. 할 일이 없으면 아무것도 보여 주지 않는다. */
internal fun todosOf(state: UiState): List<Todo> = buildList {
    if (state.loading) return@buildList
    if (!state.hasPermission) add(Todo("setup", Copy.TODO_SETUP_TITLE, Copy.TODO_SETUP_BODY, TodoAction.SETUP, Copy.TODO_SETUP_ACTION, blocking = true))
    state.external?.let { e ->
        add(
            Todo(
                "external",
                Copy.EXTERNAL_TITLE,
                "다른 설정에서 ${if (state.foldable) "${e.role.label} 화면 " else ""}DPI가 ${e.from} → ${e.to}${UpdateNotice.ro("${e.to}")} 변경되었습니다. ${Copy.EXTERNAL_BODY}",
                TodoAction.ADOPT,
                "${e.to} 기준으로 설정",
                blocking = true,
                second = TodoAction.RESTORE,
                secondLabel = "${e.from}${UpdateNotice.ro("${e.from}")} 복원"
            )
        )
    }
    if (state.hasPermission && state.status == UiState.Status.BLOCKED) {
        add(Todo("hold", Copy.TODO_HOLD_TITLE, Copy.TODO_HOLD_BODY, TodoAction.RELEARN, Copy.TODO_HOLD_ACTION))
    }
    if (state.foldable && state.target != null && state.rows.size < 2) add(Todo("other", Copy.TODO_OTHER_TITLE, Copy.TODO_OTHER_BODY, null, null))
    if (state.hasPermission && state.status == UiState.Status.NEEDS_FIX && state.external == null) {
        add(Todo("apply", Copy.TODO_APPLY_TITLE, null, TodoAction.APPLY, Copy.TODO_APPLY_ACTION))
    }
    if (state.hasPermission && !state.auto && state.target != null) add(Todo("auto", Copy.TODO_AUTO_TITLE, Copy.TODO_AUTO_BODY, TodoAction.AUTO, Copy.TODO_AUTO_ACTION))
    if (state.hasCrash) add(Todo("crash", Copy.TODO_CRASH_TITLE, Copy.TODO_CRASH_BODY, TodoAction.REPORT, Copy.TODO_CRASH_ACTION))
}

/** 할 일 카드 안쪽 폭이 이보다 넓을 때만 버튼을 글 옆에 둔다. */
private val TODO_SIDE_BUTTON = 360.dp

@Composable
internal fun TodoCard(todo: Todo, onAction: (TodoAction) -> Unit) {
    val c = LocalLiquid.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + shrinkVertically()
    ) {
        GlassCard(accent = if (todo.blocking) c.ink else null, padding = 16.dp) {
            if (todo.second != null) {
                Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(Gap.Card))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (todo.action != null && todo.actionLabel != null) LiquidButton(todo.actionLabel, modifier = Modifier.weight(1f), onClick = { onAction(todo.action) })
                    LiquidButton(todo.secondLabel ?: "", modifier = Modifier.weight(1f), onClick = { onAction(todo.second) }, primary = false)
                }
            } else {
                // 넓으면 글 옆에 버튼, 좁으면(제목이 버튼에 밀려 접히는 폭) 버튼을 글 아래 폭 전체로 둔다
                BoxWithConstraints {
                    if (maxWidth >= TODO_SIDE_BUTTON) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                                todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (todo.action != null && todo.actionLabel != null) {
                                Spacer(Modifier.width(10.dp))
                                LiquidButton(todo.actionLabel, onClick = { onAction(todo.action) })
                            }
                        }
                    } else {
                        Column {
                            Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                            todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                            if (todo.action != null && todo.actionLabel != null) {
                                Spacer(Modifier.height(Gap.Card))
                                LiquidButton(todo.actionLabel, modifier = Modifier.fillMaxWidth(), onClick = { onAction(todo.action) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 외부 화면 크기(바로 조절) → 내부 화면은 기기 정보로 계산. 미세조정과 미리보기. */
@Composable
internal fun SizeCard(state: UiState, vm: HomeViewModel, onTest: () -> Unit) {
    val c = LocalLiquid.current
    // 조절 범위: 최소 120(ldpi) ~ 이 기기 화면의 짧은 변으로 정한 최대(앱이 깨지지 않는 범위). 폴드는 외부 화면 기준.
    val shortSide = remember(state.plan, state.activeScreen) {
        val key = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.OUTER)?.key } ?: state.activeScreen?.key
        key?.split('x')?.mapNotNull { it.toIntOrNull() }?.minOrNull()
    }
    val sliderRange = DpiPolicy.MIN_DPI..(shortSide?.let { DpiPolicy.maxForShortSide(it) } ?: DpiPolicy.MAX_DPI)
    var outer by remember(state.target) { mutableIntStateOf(state.target ?: 360) }
    var adjust by remember(state.adjust) { mutableIntStateOf(state.adjust) }
    val outerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.OUTER)?.let { s -> ps.first { it.screen.key == s.key } } }
    val innerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.INNER)?.let { s -> ps.first { it.screen.key == s.key } } }
    // 끄는 동안의 미리보기 값으로 내부 화면 크기를 계산한다.
    val previewPlan = state.plan?.copy(referenceDpi = outer, adjust = adjust)
    val innerDpi = innerPanel?.let { previewPlan?.targetFor(it.screen)?.dpi }

    GlassCard {
        Text(Copy.SIZE_TITLE, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Gap.Item))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.foldable) Copy.SIZE_OUTER else Copy.SIZE_SINGLE, color = c.ink, modifier = Modifier.weight(1f))
            Text("$outer dpi", color = c.ink, fontWeight = FontWeight.SemiBold)
        }
        Text(Copy.SIZE_OUTER_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(
            value = outer,
            range = sliderRange,
            onValueChange = { outer = it },
            onValueChangeFinished = {},
            valueText = { "$it" },
            description = if (state.foldable) Copy.SIZE_OUTER else Copy.SIZE_SINGLE,
            enabled = state.hasPermission
        )
        // 내부 화면 보정은 폴드에서만(화면이 하나면 맞출 다른 화면이 없다)
        if (state.foldable) {
            Spacer(Modifier.height(Gap.Section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Copy.SIZE_INNER_TUNE, color = c.ink, modifier = Modifier.weight(1f))
                Text(innerDpi?.let { "$it dpi" } ?: "—", color = c.ink, fontWeight = FontWeight.SemiBold)
            }
            Text(Copy.SIZE_INNER_TUNE_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidSlider(
                value = adjust,
                range = -SettingsRepository.MAX_ADJUST..SettingsRepository.MAX_ADJUST,
                onValueChange = { adjust = it },
                onValueChangeFinished = {},
                valueText = { if (it > 0) "+$it" else "$it" },
                centered = true,
                description = Copy.SIZE_INNER_TUNE,
                enabled = state.hasPermission && innerPanel != null
            )
        }
        // 조절바는 값만 바꾸고, [적용]을 눌러야 화면에 반영한다(끄는 도중 화면이 계속 바뀌지 않게).
        // 처음 설치해 저장된 값이 없을 때도 지금 값을 바로 적용할 수 있게 버튼을 보인다.
        val pending = state.hasPermission && (state.target == null || outer != state.target || adjust != state.adjust)
        // 처음 적용하면 접히는 동안 목표가 생겨 '되돌리기'가 잠깐 나타났다(1.24.8 점검). 보이는 동안 정한 버튼 구성을 접힐 때도 쓴다
        val revertShown = remember { booleanArrayOf(false) }
        if (pending) revertShown[0] = state.target != null
        AnimatedVisibility(pending, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(Gap.Item))
                Text(Copy.SIZE_PENDING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (revertShown[0]) {
                        LiquidButton(Copy.SIZE_REVERT, modifier = Modifier.weight(1f), primary = false, onClick = {
                            state.target?.let { outer = it }
                            adjust = state.adjust
                        })
                    }
                    LiquidButton(Copy.SIZE_APPLY, modifier = Modifier.weight(1f), onClick = { vm.commitSize(outer, adjust) })
                }
            }
        }
        if (state.foldable && outerPanel != null && innerPanel != null && innerDpi != null) {
            Spacer(Modifier.height(Gap.Item))
            ScreensPreview(
                outerInch = outerPanel.screen.diagonalInch, outerAspect = aspect(outerPanel, outerPanel, false), outerPpi = outerPanel.screen.ppi, outerDpi = outer,
                innerInch = innerPanel.screen.diagonalInch, innerAspect = aspect(innerPanel, outerPanel, true), innerPpi = innerPanel.screen.ppi, innerDpi = innerDpi,
                outerLabel = "${Copy.SIZE_PREVIEW_OUTER} $outer", innerLabel = "${Copy.SIZE_PREVIEW_INNER} $innerDpi",
                innerActive = state.effective?.role?.let { it == ScreenPolicy.Role.INNER }
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.SIZE_TEST, modifier = Modifier.fillMaxWidth(), onClick = onTest, primary = false)
        }
    }
}

/** 세로 ÷ 가로(미리보기용). 내부 화면은 실제 방향(Fold8은 가로로 긴 화면)으로 계산한다. */
private fun aspect(p: PanelSpec.Resolved, cover: PanelSpec.Resolved, isInner: Boolean): Float = ScreenGeometry.orientedSize(p.screen.key, cover.screen.key, isInner)?.let { (w, h) -> h / w } ?: 2f

@Composable
internal fun ResetSheet(
    canReset: Boolean,
    foldable: Boolean = true,
    onRelearn: () -> Unit,
    onDefault: () -> Unit,
    onResetPermission: () -> Unit,
    onPoll: () -> Unit,
    onCopy: (String) -> Unit
) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    // 권한 초기화 단계: 0 = 닫힘, 1 = 경고, 2 = 재확인, 3 = 앱 쪽 초기화 완료(PC 회수 대기)
    var step by remember { mutableIntStateOf(0) }
    // PC에서 회수하는 즉시 알아채도록 대기 중에는 권한 상태를 확인한다.
    LaunchedEffect(step) {
        if (step == 3) {
            while (true) {
                delay(2_000)
                onPoll()
            }
        }
    }
    SheetColumn {
        Text(Copy.RESET_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(Gap.Section))
        // 화면 학습 초기화는 두 화면을 배우는 폴드에서만
        if (foldable) {
            GlassCard {
                Text(Copy.RESET_RELEARN, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.RESET_RELEARN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(Copy.RESET_RELEARN, modifier = Modifier.fillMaxWidth(), onClick = onRelearn)
            }
            Spacer(Modifier.height(Gap.Card))
        }
        GlassCard {
            Text(Copy.RESET_DEFAULT, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(if (foldable) Copy.RESET_DEFAULT_BODY else Copy.RESET_DEFAULT_BODY_SINGLE, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.RESET_DEFAULT, modifier = Modifier.fillMaxWidth(), onClick = onDefault, primary = false, enabled = canReset, disabledHint = Copy.DISABLED_NO_PERMISSION)
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(accent = if (step > 0) c.danger else null) {
            Text(Copy.RESET_PERMISSION, fontWeight = FontWeight.SemiBold, color = c.danger)
            Text(Copy.RESET_PERMISSION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text(Copy.RESET_PERMISSION_LIMIT, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            AnimatedContent(step, label = "revoke") { s ->
                when (s) {
                    0 -> LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.fillMaxWidth(), onClick = { step = 1 }, primary = false, enabled = canReset, danger = true, disabledHint = Copy.DISABLED_NO_PERMISSION)
                    3 -> Column(Modifier.fillMaxWidth()) {
                        Text(Copy.RESET_PERMISSION_STEP, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Gap.Item))
                        val cmd = PermissionReset.command(context.packageName)
                        CommandBox(cmd, onCopy = { onCopy(cmd) })
                        Spacer(Modifier.height(Gap.Item))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 대기 표시: 방울이 세 칸을 차례로 흘러 다닌다.
                            var dot by remember { mutableIntStateOf(0) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(600)
                                    dot = (dot + 1) % 3
                                }
                            }
                            LiquidDots(count = 3, index = dot)
                            Spacer(Modifier.width(8.dp))
                            Text(Copy.RESET_PERMISSION_WAITING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> DangerConfirmBox(
                        if (s == 1) Copy.RESET_PERMISSION_WARN else Copy.RESET_PERMISSION_CONFIRM,
                        note = Copy.RESET_PERMISSION_KEEP
                    ) {
                        LiquidButton(Copy.CANCEL, modifier = Modifier.weight(1f), onClick = { step = 0 }, primary = false)
                        if (s == 1) {
                            LiquidButton(Copy.CONTINUE, modifier = Modifier.weight(1f), onClick = { step = 2 }, primary = false, danger = true)
                        } else {
                            LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.weight(1f), onClick = {
                                onResetPermission()
                                step = 3
                            }, danger = true, burst = false)
                        }
                    }
                }
            }
        }
    }
}
