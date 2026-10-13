package com.labok.goat.ui.liquid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 잉크 색(앱 설정): 방울·선택 표시·버튼에 쓰는 포인트 색. 기본은 따뜻한 회색(라이트 #3D3833, 다크 #EBEBEB)이고,
 * [Mode.THEME]이면 갤럭시 '색상 팔레트'(안드로이드 동적 색)의 강조색 가운데 가장 진한 단계(라이트 accent1_900,
 * 다크는 반대로 가장 밝은 accent1_100)를 쓴다. 배경화면·팔레트를 바꾸면 따라 바뀐다.
 */
object UiInk {
    enum class Mode { DEFAULT, THEME }

    var mode by mutableStateOf(Mode.DEFAULT)
        private set

    fun load(context: Context) {
        mode = runCatching { Mode.valueOf(prefs(context).getString("mode", null) ?: "") }.getOrDefault(Mode.DEFAULT)
    }

    fun set(context: Context, m: Mode) {
        mode = m
        prefs(context).edit().putString("mode", m.name).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_ink", Context.MODE_PRIVATE)
}
