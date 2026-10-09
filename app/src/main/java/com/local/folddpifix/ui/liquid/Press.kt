package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 누름 피드백(원본 press): 누르는 동안 옆으로 퍼지고(×1.14) 위아래로 눌렸다가(×0.84),
 * 떼면 pop 스프링으로 튕겨 돌아온다. 짧은 햅틱을 준다.
 */
@Composable
fun Modifier.liquidPress(
    interaction: MutableInteractionSource,
    sx: Float = 1.06f,
    sy: Float = 0.92f,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val ax = remember { Animatable(1f) }
    val ay = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val tx = if (pressed) sx else 1f
        val ty = if (pressed) sy else 1f
        coroutineScope {
            launch { ax.animateTo(tx, Springs.pop(reduce)) }
            launch { ay.animateTo(ty, Springs.pop(reduce)) }
        }
    }
    return this.graphicsLayer {
        scaleX = ax.value
        scaleY = ay.value
    }
}
