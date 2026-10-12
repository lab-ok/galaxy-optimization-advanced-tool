package com.labok.goat.ui.liquid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

/**
 * 원본 엔진(core/engine.js)의 스프링 수치. 원본도 질량 1 스프링이라 Compose spring과 1:1로 대응한다.
 * - calm: 선택 UI(칩·탭)        k=320, ζ=0.82
 * - pop : 눌림·출렁임(jelly)    k=560, ζ=0.38
 * - water: 기본 물 방울         k=240, ζ=0.42
 * - lag : 꼬리 방울(강성 ×0.5)
 */
object Springs {
    fun <T> calm(reduce: Boolean = false): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = UiMotion.ratio(0.82f), stiffness = UiMotion.stiffness(320f, 0.82f))
    fun <T> pop(reduce: Boolean = false): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = UiMotion.ratio(0.38f), stiffness = UiMotion.stiffness(560f, 0.38f))
    fun <T> water(reduce: Boolean = false): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = UiMotion.ratio(0.42f), stiffness = UiMotion.stiffness(240f, 0.42f))
    fun <T> lag(reduce: Boolean = false): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = UiMotion.ratio(0.82f), stiffness = UiMotion.stiffness(160f, 0.82f))
}
