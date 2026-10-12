package com.labok.goat.ui.liquid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

/**
 * UI 유연성(앱 설정): 물방울 UI가 움직이며 얼마나 출렁이는지 한곳에서 정한다. [wobble] 1이 기본, [MAX]까지.
 * 줄여도 움직이는 속도는 그대로다. 1보다 작으면 모양이 단단해지고(연체 물방울은 [rigidity]만큼 강체처럼
 * 함께 움직이고, 스프링은 감쇠비를 1 쪽으로 올리는 만큼 강성도 올려 도착 시간을 지킨다), 0이면 출렁임 없이
 * 딱딱하게 움직인다. 1보다 크면 감쇠를 줄여 더 오래 출렁인다.
 */
object UiMotion {
    const val MAX = 1.5f
    var wobble by mutableFloatStateOf(1f)
        private set

    /** 연체 물방울 감쇠 배수: 1 이하에서는 1(속도 그대로), 1.5 → 약 0.58배(더 오래 출렁임). */
    fun dampScale(): Float = if (wobble <= 1f) 1f else 3f.pow(1f - wobble)

    /** 연체 물방울 단단함 0~1: 1 이상이면 0(원래 연체), 0이면 1(점들이 한 몸처럼 움직여 찌그러지지 않는다). */
    fun rigidity(): Float = (1f - wobble).coerceIn(0f, 1f)

    /** 스프링 감쇠비: 1이면 그대로, 0이면 1(넘치지 않음), 클수록 더 통통 튄다. */
    fun ratio(base: Float): Float = (1f - (1f - base) * wobble).coerceIn(0.2f, 1f)

    /** 스프링 강성: 감쇠비를 올린 만큼 강성도 올려 도착하는 데 걸리는 시간을 비슷하게 지킨다. */
    fun stiffness(base: Float, baseRatio: Float): Float {
        val r = ratio(baseRatio)
        return if (r <= baseRatio) base else base * (r / baseRatio).pow(2)
    }

    fun load(context: Context) { wobble = prefs(context).getFloat("wobble", 1f).coerceIn(0f, MAX) }

    fun set(context: Context, value: Float) {
        wobble = value.coerceIn(0f, MAX)
        prefs(context).edit().putFloat("wobble", wobble).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_motion", Context.MODE_PRIVATE)
}
