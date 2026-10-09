package com.local.folddpifix.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 액체 스위치(원본 switch): 손잡이가 이동하는 동안 옆으로 늘어났다가(1.3×0.9) 제자리에서 출렁인다. */
@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    val stretch = remember { Animatable(1f) }
    val track by animateColorAsState(if (checked) c.ink else c.line, label = "track")
    val knob by animateColorAsState(if (checked) c.onInk else c.ink, label = "knob")
    LaunchedEffect(checked) {
        coroutineScope {
            launch { pos.animateTo(if (checked) 1f else 0f, Springs.calm(reduce)) }
            launch {
                if (!reduce) {
                    stretch.snapTo(1.3f)
                    stretch.animateTo(1f, Springs.pop())
                }
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .size(56.dp, 32.dp)
            .liquidPress(interaction)
            .toggleable(checked, interaction, null, enabled, Role.Switch, onCheckedChange),
    ) {
        val h = size.height
        val alpha = if (enabled) 1f else 0.4f
        drawRoundRect(track.copy(alpha = track.alpha * alpha), cornerRadius = CornerRadius(h / 2))
        if (!checked) drawRoundRect(c.line, cornerRadius = CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
        val kh = h - 8.dp.toPx()
        val kw = kh * stretch.value
        val khScaled = kh / (1f + (stretch.value - 1f) / 3f)
        val x = 4.dp.toPx() + (size.width - 8.dp.toPx() - kh) * pos.value - (kw - kh) / 2
        drawRoundRect(
            knob.copy(alpha = alpha),
            topLeft = Offset(x, (h - khScaled) / 2),
            size = Size(kw, khScaled),
            cornerRadius = CornerRadius(khScaled / 2),
        )
    }
}
