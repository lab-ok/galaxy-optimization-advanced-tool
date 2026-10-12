package com.labok.goat.ui.liquid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

/**
 * UI 낭창거림(앱 설정): 물방울 UI가 움직인 뒤 얼마나 출렁이는지 한곳에서 정한다. [wobble] 1이 기본,
 * 0이면 출렁임 없이 바로 멈추고 [MAX]면 더 오래·크게 출렁인다. 연체 물방울([SoftBlob])의 감쇠와
 * 공용 스프링([Springs])의 감쇠비에 곱해 쓰므로, 바꾸면 스위치·조절바·메뉴·카드·사이드바가 함께 바뀐다.
 */
object UiMotion {
    const val MAX = 1.5f
    var wobble by mutableFloatStateOf(1f)
        private set

    /** 연체 물방울 감쇠 배수: 0 → 3배(바로 멈춤), 1 → 1배, 1.5 → 약 0.58배. */
    fun dampScale(): Float = 3f.pow(1f - wobble)

    /** 스프링 감쇠비: 1이면 그대로, 0이면 출렁임 없이(1), 클수록 1에서 더 멀어진다(더 통통 튄다). */
    fun ratio(base: Float): Float = (1f - (1f - base) * wobble).coerceIn(0.2f, 1f)

    fun load(context: Context) { wobble = prefs(context).getFloat("wobble", 1f).coerceIn(0f, MAX) }

    fun set(context: Context, value: Float) {
        wobble = value.coerceIn(0f, MAX)
        prefs(context).edit().putFloat("wobble", wobble).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_motion", Context.MODE_PRIVATE)
}
