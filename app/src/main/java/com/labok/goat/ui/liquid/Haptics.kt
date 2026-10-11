package com.labok.goat.ui.liquid

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 리퀴드 요소의 촉각 피드백. 시스템 햅틱(View.performHapticFeedback)을 써서 기기 진동 설정(터치 진동 세기)을 따른다.
 * - [press]: 버튼·카드·스위치·칩을 누를 때 또렷한 한 번(가상 키 누름).
 * - [tick]: 조절바 값이 한 칸 바뀔 때 가벼운 틱(시계 틱).
 * - [snap]: 세밀 조절이 열리거나 스위치가 켜지는 등 상태가 바뀔 때 조금 더 단단한 한 번.
 */
class LiquidHaptics(private val view: View) {
    fun press() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun snap() { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
}

@Composable
fun rememberLiquidHaptics(): LiquidHaptics {
    val view = LocalView.current
    return remember(view) { LiquidHaptics(view) }
}
