package com.labok.goat.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.rowListEdges
import com.labok.goat.ui.liquid.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 라디오 묶음 안의 줄들이 자기 동그라미 위치·선택·눌림을 알리는 곳([LiquidRadioGroup]이 읽어 방울을 그린다). */
internal class RadioGroupState {
    val centers = mutableStateMapOf<String, Float>()
    var selected by mutableStateOf<String?>(null)
    var pressed by mutableStateOf<String?>(null)
}

internal val LocalRadioGroup = staticCompositionLocalOf<RadioGroupState?> { null }

/**
 * 라디오 묶음(리퀴드 엔진, Trafix radio와 같은 움직임): 위에서 내려다본 물방울 하나가 고른 줄의 동그라미에 앉아 있다.
 * 다른 줄을 고르면 방울이 그쪽으로 끌려가며 진행 방향으로 길게 늘어나고, 꼬리 방울이 목(goo)으로 이어져 늦게 따라와
 * 합쳐진 뒤, 도착하면 한 번 출렁인다. 누르는 동안 누른 줄의 동그라미 안에 작은 방울이 맺힌다.
 * 움직임은 공용 스프링([Springs])이라 앱 설정의 UI 유연성을 따르고, 동작 줄이기면 바로 옮긴다.
 * 안에는 [LiquidRadioRow]를 둔다(화면 읽기용 selectableGroup도 여기서 단다).
 */
@Composable
fun LiquidRadioGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val state = remember { RadioGroupState() }
    val target = state.selected?.let { state.centers[it] }
    val headY = remember { Animatable(Float.NaN) }
    val tailY = remember { Animatable(Float.NaN) }
    val wobble = remember { Animatable(0f) }
    val bead = remember { Animatable(0f) }
    var beadKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target, reduce) {
        val t = target ?: return@LaunchedEffect
        if (headY.value.isNaN() || reduce) {
            headY.snapTo(t)
            tailY.snapTo(t)
            return@LaunchedEffect
        }
        if (headY.targetValue == t) return@LaunchedEffect
        coroutineScope {
            launch { tailY.animateTo(t, Springs.lag()) }
            headY.animateTo(t, Springs.calm())
            // 도착: 늘어났던 방울이 둥글게 돌아오며 옆으로 한 번 출렁인다
            wobble.snapTo(1f)
            wobble.animateTo(0f, Springs.pop())
        }
    }
    // 누른 줄에 맺히는 작은 방울(고른 줄이 아닐 때만)
    LaunchedEffect(state.pressed) {
        val k = state.pressed
        if (k != null && k != state.selected) {
            beadKey = k
            bead.animateTo(1f, Springs.pop(reduce))
        } else {
            bead.animateTo(0f, Springs.calm(reduce))
        }
    }
    // 규칙 5: 목록 위아래 줄 여백을 바깥과 겹쳐 카드 여백·위 설명과의 간격을 다른 요소와 맞춘다
    Box(modifier.rowListEdges()) {
        Canvas(Modifier.matchParentSize().goo(c.ink, 6.dp)) {
            val x = RING_X.dp.toPx()
            val d = DOT.dp.toPx()
            beadKey?.let { k -> state.centers[k]?.let { y -> if (bead.value > 0.01f) drawCircle(c.ink, 7.5.dp.toPx() * bead.value, Offset(x, y)) } }
            val hy = headY.value
            val ty = tailY.value
            if (hy.isNaN()) return@Canvas
            val t = headY.targetValue
            // 끌려가는 동안 진행 방향으로 늘어나되, 앞쪽 끝이 목표 동그라미 가장자리를 넘지 않는다
            val dist = kotlin.math.abs(t - hy)
            val stretch = (dist * 0.35f).coerceAtMost(dist + RING_R.dp.toPx() * 0.4f)
            val h = d + stretch
            val w = d * (1f - 0.25f * (stretch / (stretch + d))) * (1f + 0.18f * wobble.value)
            val hh = h * (1f - 0.12f * wobble.value)
            drawOval(c.ink, Offset(x - w / 2, hy - hh / 2), Size(w, hh))
            if (!ty.isNaN()) drawCircle(c.ink, d * 0.38f, Offset(x, ty))
        }
        CompositionLocalProvider(LocalRadioGroup provides state) {
            Column(Modifier.selectableGroup(), content = content)
        }
    }
}

/**
 * 여럿 중 하나를 고르는 한 줄(라디오): 왼쪽 동그라미와 이름·설명. 줄 전체를 누를 수 있다. 진동은 부르는 쪽이 [LiquidHaptics.segment]로 낸다.
 * [LiquidRadioGroup] 안에서는 고른 표시를 묶음의 물방울이 그리고(줄 사이를 흘러 다닌다), 밖에서 쓰면 동그라미 안에 방울이 차오른다.
 */
@Composable
fun LiquidRadioRow(label: String, desc: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val group = LocalRadioGroup.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    if (group != null) {
        if (selected) SideEffect { group.selected = label }
        LaunchedEffect(pressed) {
            if (pressed) {
                group.pressed = label
            } else if (group.pressed == label) {
                group.pressed = null
            }
        }
    }
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = UiMotion.ratio(0.45f), stiffness = UiMotion.stiffness(Spring.StiffnessMediumLow, 0.45f)),
        label = "radio"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onGloballyPositioned { if (group != null) group.centers[label] = it.positionInParent().y + it.size.height / 2f }
            // 라디오 의미(선택됨·안 됨)를 접근성 서비스가 읽도록 selectable로 둔다
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Gap.RowPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            // 다크 모드의 선(line)은 바탕과 거의 같아 동그라미가 묻히므로, 안 고른 동그라미는 흐린 글자색으로 그린다
            drawCircle(
                if (selected) {
                    c.ink
                } else if (c.dark) {
                    c.muted
                } else {
                    c.line
                },
                r - 1.dp.toPx(),
                style = Stroke(2.dp.toPx())
            )
            if (group == null && fill > 0.01f) drawCircle(c.ink, (r - 5.dp.toPx()) * fill.coerceAtMost(1.15f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled || selected) c.ink else c.muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (desc != null) Text(desc, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 동그라미 중심의 x(줄 왼쪽에서, dp)와 반지름, 선택 방울 지름. */
private const val RING_X = 12f
private const val RING_R = 11f
private const val DOT = 14f
