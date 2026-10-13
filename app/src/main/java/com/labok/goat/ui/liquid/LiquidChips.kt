package com.labok.goat.ui.liquid

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 액체 칩 선택: 고른 칩 아래의 방울이 연체 물리([SoftBlob])로 옮겨 간다. 이동 방향 앞쪽이 먼저 끌려가 길쭉해졌다가
 * 표면장력과 압력으로 둥글게 돌아오며 한 번 출렁인다. 칩 글자는 방울이 덮은 만큼 배경색으로 반전된다.
 */
@Composable
fun <T> LiquidChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLiquidHaptics()
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val lefts = remember(options) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val widths = remember(options) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    // 고른 값이 목록에 없으면(예: 직접 맞춘 값) 방울을 그리지 않는다
    val none = selected !in options
    val index = options.indexOf(selected).coerceAtLeast(0)
    val blob = rememberSoftBlob()
    var trackH by remember { mutableFloatStateOf(0f) }
    // 칸 위치·폭이 바뀌어도(폴드를 펴거나 화면 폭이 달라질 때) 다시 맞춘다. 고른 칸이 바뀐 때만 출렁이며 옮기고,
    // 크기만 바뀐 때는 그 자리에 바로 놓는다(예전 폭으로 남아 옆 칸까지 덮는 일을 막는다).
    val left = lefts.getOrNull(index) ?: 0f
    val width = widths.getOrNull(index) ?: 0f
    var placedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, left, width, trackH) {
        if (width <= 0f || trackH <= 0f) return@LaunchedEffect
        val move = placedIndex != -1 && placedIndex != index
        blob.moveTo(left + width / 2, trackH / 2, width, trackH, animate = move && !reduce)
        placedIndex = index
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(LiquidShape.control)
            .semantics { role = Role.RadioButton }
    ) {
        // 트랙 테두리는 맨 아래 층에 그린다. Modifier.border는 내용 위에 그려져 고른 칩(검은 방울)의 가장자리를 가린다.
        Canvas(Modifier.matchParentSize()) {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                c.line,
                Offset(stroke / 2, stroke / 2),
                Size(size.width - stroke, size.height - stroke),
                CornerRadius(size.height / 2),
                style = Stroke(stroke)
            )
        }
        val path = remember { Path() }
        Canvas(Modifier.matchParentSize().onSizeChanged { trackH = it.height.toFloat() }) {
            blob.frame // 프레임마다 다시 그리기
            if (blob.blob.ready && !none) drawPath(blob.blob.path(path), c.ink)
        }
        Row(Modifier.matchParentSize()) {
            options.forEachIndexed { i, option ->
                val isSel = i == index
                // 방울이 이 칩을 덮은 정도(0~1)만큼 글자를 배경색으로 바꾼다.
                blob.frame
                val cover = if (none) {
                    0f
                } else if (!blob.blob.ready || widths[i] <= 0f) {
                    (if (isSel) 1f else 0f)
                } else {
                    (1f - kotlin.math.abs(blob.blob.cx - (lefts[i] + widths[i] / 2)) / widths[i]).coerceIn(0f, 1f)
                }
                val textColor = lerp(c.ink, c.onInk, cover)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(LiquidShape.control)
                        .onGloballyPositioned {
                            lefts[i] = it.positionInParent().x
                            widths[i] = it.size.width.toFloat()
                        }
                        .liquidPress(interaction, releaseHaptic = false)
                        .clickable(interaction, indication = null) {
                            if (option != selected) haptics.segment()
                            onSelect(option)
                        }
                        .semantics { this.selected = isSel },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(option),
                        color = textColor,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
