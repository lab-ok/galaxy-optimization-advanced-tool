package com.labok.goat.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    /** 뗄 때도 울릴지. 뗄 때 따로 울리는 요소(스위치 켜짐·칩 선택)는 false로 겹치지 않게 한다. */
    releaseHaptic: Boolean = true
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val ax = remember { Animatable(1f) }
    val ay = remember { Animatable(1f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        // 누를 때 또렷하게, 뗄 때 가볍게(처음 그릴 때는 울리지 않는다)
        if (pressed) {
            haptics.press()
        } else if (wasPressed && releaseHaptic) {
            haptics.release()
        }
        wasPressed = pressed
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
