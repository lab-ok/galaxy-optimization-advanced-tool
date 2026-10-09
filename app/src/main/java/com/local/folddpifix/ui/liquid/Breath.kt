package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * 숨쉬기 값(0~1). [key]가 바뀔 때(켜진 화면이 바뀌는 등) [times]번만 숨 쉬고 0.5에서 멈춘다.
 * 끝없이 반복하면 앱을 켜 둔 동안 화면을 계속 다시 그려 배터리를 쓰므로 횟수를 제한한다.
 */
@Composable
fun rememberBreath(key: Any?, times: Int = 3): Float {
    val reduce = LocalReduceMotion.current
    val v = remember { Animatable(0.5f) }
    LaunchedEffect(key, reduce) {
        if (reduce) { v.snapTo(0.5f); return@LaunchedEffect }
        repeat(times) {
            v.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
            v.animateTo(0f, tween(1300, easing = FastOutSlowInEasing))
        }
        v.animateTo(0.5f, tween(650))
    }
    return v.value
}
