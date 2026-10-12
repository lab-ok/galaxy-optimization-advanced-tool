package com.labok.goat.ui.liquid

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/**
 * 햅틱 세기(앱 설정): 0이면 끄고, 100이 기본, 150까지 올린다. 리퀴드 요소의 촉각 피드백 전체에 곱한다.
 */
object UiHaptics {
    const val MAX = 150
    var level by mutableIntStateOf(100)
        private set

    fun load(context: Context) { level = prefs(context).getInt("level", 100).coerceIn(0, MAX) }

    fun set(context: Context, value: Int) {
        level = value.coerceIn(0, MAX)
        prefs(context).edit().putInt("level", level).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_haptics", Context.MODE_PRIVATE)
}

/**
 * 리퀴드 요소의 촉각 피드백. 동작마다 결이 다른 효과를 쓴다.
 * - [press]/[release]: 버튼·카드·칩·행을 누르고 뗄 때
 * - [toggle]: 스위치가 켜지고 꺼질 때(켜짐은 또렷하게, 꺼짐은 가볍게)
 * - [segment]: 칩·사이드바처럼 여러 칸 중 하나를 고를 때
 * - [tick]: 값이 한 칸 바뀌거나 무언가 하나 잡힐 때(가벼운 틱)
 * - [open]/[close]: 메뉴·사이드바·시트가 열리고 닫힐 때
 * - [snap]: 확인·상태가 크게 바뀔 때, [reject]: 거절·실패·화남
 *
 * 세기를 조절하려고 진동 기본 효과(VibrationEffect.Composition의 클릭·틱 등)에 [UiHaptics.level]을 곱해 울린다.
 * 터치 진동 용도(USAGE_TOUCH)로 울려 기기의 '터치 진동' 설정을 따른다. 기본 효과를 못 쓰는 기기는
 * 시스템 햅틱(View.performHapticFeedback)으로 대신하고, 세기 0이면 울리지 않는다.
 */
class LiquidHaptics(private val view: View) {
    private val api34 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    private val vibrator: Vibrator? = view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** 기본 효과 [primitive]를 [scale](0~1, 세기 100% 기준)로 울린다. 못 쓰면 시스템 햅틱 [fallback]. */
    private fun play(primitive: Int, scale: Float, fallback: Int) {
        val level = UiHaptics.level
        if (level <= 0) return
        val v = vibrator
        if (v != null && v.hasVibrator() && v.areAllPrimitivesSupported(primitive)) {
            val s = (scale * level / 100f).coerceIn(0.05f, 1f)
            val effect = VibrationEffect.startComposition().addPrimitive(primitive, s).compose()
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                else @Suppress("DEPRECATION") v.vibrate(effect)
            }
        } else view.performHapticFeedback(fallback)
    }

    fun press() = play(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, HapticFeedbackConstants.VIRTUAL_KEY)
    fun release() = play(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
    fun toggle(on: Boolean) =
        if (on) play(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, if (api34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONFIRM)
        else play(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, if (api34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CONTEXT_CLICK)
    fun segment() = play(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, if (api34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun tick() = play(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f, HapticFeedbackConstants.CLOCK_TICK)
    fun open() = play(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f, HapticFeedbackConstants.GESTURE_START)
    fun close() = play(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.5f, HapticFeedbackConstants.GESTURE_END)
    fun snap() = play(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, HapticFeedbackConstants.CONFIRM)
    fun reject() = play(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberLiquidHaptics(): LiquidHaptics {
    val view = LocalView.current
    return remember(view) { LiquidHaptics(view) }
}
