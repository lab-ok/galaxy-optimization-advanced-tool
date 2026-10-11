package com.labok.goat.ui.liquid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 리퀴드 요소의 촉각 피드백. 시스템 햅틱(View.performHapticFeedback)이라 기기 터치 진동 설정을 따른다.
 * 동작마다 결이 다른 효과를 쓴다(Android 14 이상 전용 효과는 그 전 기기에서 가까운 효과로 대신한다).
 * - [press]/[release]: 버튼·카드·칩·행을 누르고 뗄 때(가상 키 누름·뗌)
 * - [toggle]: 스위치가 켜지고 꺼질 때(켜짐은 또렷하게, 꺼짐은 가볍게)
 * - [segment]: 칩·사이드바처럼 여러 칸 중 하나를 고를 때
 * - [tick]: 값이 한 칸 바뀌거나 무언가 하나 잡힐 때(가벼운 틱)
 * - [open]/[close]: 메뉴·사이드바·시트가 열리고 닫힐 때
 * - [snap]: 확인·상태가 크게 바뀔 때, [reject]: 거절·실패·화남
 */
class LiquidHaptics(private val view: View) {
    private fun play(c: Int) { view.performHapticFeedback(c) }
    private val api34 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun press() = play(HapticFeedbackConstants.VIRTUAL_KEY)
    fun release() = play(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
    fun toggle(on: Boolean) = play(
        if (api34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else (if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK),
    )
    fun segment() = play(if (api34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK)
    fun open() = play(HapticFeedbackConstants.GESTURE_START)
    fun close() = play(HapticFeedbackConstants.GESTURE_END)
    fun snap() = play(HapticFeedbackConstants.CONFIRM)
    fun reject() = play(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberLiquidHaptics(): LiquidHaptics {
    val view = LocalView.current
    return remember(view) { LiquidHaptics(view) }
}
