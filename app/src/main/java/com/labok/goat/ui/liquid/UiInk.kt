package com.labok.goat.ui.liquid

import android.R
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 잉크 색(앱 설정): 방울·선택 표시·버튼에 쓰는 포인트 색. 기본은 따뜻한 회색(라이트 #3D3833, 다크 #EBEBEB)이고,
 * [Mode.THEME]이면 갤럭시 '색상 팔레트'(안드로이드 동적 색)에서 고른 색([tone])을 쓴다. 배경화면·팔레트를 바꾸면 따라 바뀐다.
 */
object UiInk {
    enum class Mode { DEFAULT, THEME }

    /**
     * 팔레트에서 고르는 색: 강조색 1~3([accent]) × 진하기([deep]). 진하게는 라이트 900·다크 100,
     * 중간은 라이트 600·다크 300 단계다(다크 모드는 어두운 바탕에서 보이도록 밝은 쪽을 쓴다). 기본은 강조색 1 진하게(예전과 같다).
     */
    data class Tone(val accent: Int, val deep: Boolean) {
        val key get() = "$accent:${if (deep) "deep" else "mid"}"

        fun colorRes(dark: Boolean): Int {
            val level = when {
                deep && !dark -> 900
                deep -> 100
                !dark -> 600
                else -> 300
            }
            return RES.getValue(accent to level)
        }

        companion object {
            val DEFAULT = Tone(1, true)
            val ALL = listOf(1, 2, 3).flatMap { a -> listOf(Tone(a, true), Tone(a, false)) }

            private val RES = mapOf(
                (1 to 100) to R.color.system_accent1_100, (1 to 300) to R.color.system_accent1_300,
                (1 to 600) to R.color.system_accent1_600, (1 to 900) to R.color.system_accent1_900,
                (2 to 100) to R.color.system_accent2_100, (2 to 300) to R.color.system_accent2_300,
                (2 to 600) to R.color.system_accent2_600, (2 to 900) to R.color.system_accent2_900,
                (3 to 100) to R.color.system_accent3_100, (3 to 300) to R.color.system_accent3_300,
                (3 to 600) to R.color.system_accent3_600, (3 to 900) to R.color.system_accent3_900
            )

            fun of(key: String?): Tone = ALL.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    var mode by mutableStateOf(Mode.DEFAULT)
        private set

    var tone by mutableStateOf(Tone.DEFAULT)
        private set

    fun load(context: Context) {
        mode = runCatching { Mode.valueOf(prefs(context).getString("mode", null) ?: "") }.getOrDefault(Mode.DEFAULT)
        tone = Tone.of(prefs(context).getString("tone", null))
    }

    fun set(context: Context, m: Mode) {
        mode = m
        prefs(context).edit().putString("mode", m.name).apply()
    }

    fun setTone(context: Context, t: Tone) {
        tone = t
        prefs(context).edit().putString("tone", t.key).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_ink", Context.MODE_PRIVATE)
}
