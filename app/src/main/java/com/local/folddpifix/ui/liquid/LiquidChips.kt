package com.local.folddpifix.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 액체 칩 선택(원본 liquid_chips): 고른 칩 아래로 방울(head)이 옮겨 가고, 늦게 따라오는 꼬리(tail)와
 * goo로 이어져 늘어났다 합쳐진다. 고른 칩 글자는 방울 위에서 배경색으로 반전된다.
 */
@Composable
fun <T> LiquidChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val lefts = remember(options) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val widths = remember(options) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val headX = remember { Animatable(0f) }
    val headW = remember { Animatable(0f) }
    val tailX = remember { Animatable(0f) }
    val tailW = remember { Animatable(0f) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val measured = widths.getOrNull(index)?.let { it > 0f } == true

    LaunchedEffect(index, measured) {
        if (!measured) return@LaunchedEffect
        val tx = lefts[index]
        val tw = widths[index]
        if (headW.value == 0f) {
            headX.snapTo(tx); headW.snapTo(tw); tailX.snapTo(tx); tailW.snapTo(tw)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { headX.animateTo(tx, Springs.calm(reduce)) }
            launch { headW.animateTo(tw, Springs.calm(reduce)) }
            launch { tailX.animateTo(tx, Springs.lag(reduce)) }
            launch { tailW.animateTo(tw, Springs.lag(reduce)) }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(LiquidShape.control)
            .semantics { role = Role.RadioButton },
    ) {
        // 트랙 테두리는 맨 아래 층에 그린다. Modifier.border는 내용 위에 그려져 고른 칩(검은 방울)의 가장자리를 가린다.
        Canvas(Modifier.matchParentSize()) {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                c.line, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke),
                CornerRadius(size.height / 2), style = Stroke(stroke),
            )
        }
        Canvas(Modifier.matchParentSize().goo(c.ink)) {
            val h = size.height
            val r = CornerRadius(h / 2, h / 2)
            if (headW.value > 0f) {
                drawRoundRect(c.ink, Offset(headX.value, 0f), Size(headW.value, h), r)
                // 꼬리는 조금 작게: 이동 중 목처럼 늘어나 보인다.
                val tw = tailW.value * 0.7f
                drawRoundRect(c.ink, Offset(tailX.value + (tailW.value - tw) / 2, h * 0.15f), Size(tw, h * 0.7f), r)
            }
        }
        Row(Modifier.matchParentSize()) {
            options.forEachIndexed { i, option ->
                val isSel = i == index
                val textColor by animateColorAsState(if (isSel) c.onInk else c.ink, label = "chipText")
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(LiquidShape.control)
                        .onGloballyPositioned {
                            lefts[i] = it.positionInParent().x
                            widths[i] = it.size.width.toFloat()
                        }
                        .liquidPress(interaction)
                        .clickable(interaction, indication = null) { onSelect(option) }
                        .semantics { this.selected = isSel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = textColor,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
