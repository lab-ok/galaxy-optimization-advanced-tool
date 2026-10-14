package com.labok.goat.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 액체 스위치: 손잡이가 연체 물방울([SoftBlob])이라, 건너가는 동안 앞쪽이 먼저 나가며 길쭉해지고
 * 도착하면 표면장력으로 다시 동그랗게 모이며 한 번 출렁인다.
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 화면 읽기(TalkBack)가 읽을 이름(보통 옆 제목). 없으면 '스위치'만 읽혀 무엇을 켜는지 알 수 없다. */
    label: String? = null
) {
    val haptics = rememberLiquidHaptics()
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val inset = with(LocalDensity.current) { 4.dp.toPx() }
    val track by animateColorAsState(if (checked) c.ink else c.line, label = "track")
    val knob by animateColorAsState(if (checked) c.onInk else c.ink, label = "knob")
    var box by remember { mutableStateOf(IntSize.Zero) }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    LaunchedEffect(checked, box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val kh = box.height - 2 * inset
        val x = if (checked) box.width - inset - kh / 2 else inset + kh / 2
        blob.moveTo(x, box.height / 2f, kh, kh, animate = !reduce)
    }
    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .size(56.dp, 32.dp)
            .onSizeChanged { box = it }
            .liquidPress(interaction, releaseHaptic = false)
            .semantics { if (label != null) contentDescription = label }
            .toggleable(checked, interaction, null, enabled, Role.Switch) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            }
    ) {
        blob.frame
        val h = size.height
        val alpha = if (enabled) 1f else 0.4f
        drawRoundRect(track.copy(alpha = track.alpha * alpha), cornerRadius = CornerRadius(h / 2))
        if (!checked) drawRoundRect(c.line, cornerRadius = CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
        drawPath(blob.blob.path(path), knob.copy(alpha = alpha))
    }
}
