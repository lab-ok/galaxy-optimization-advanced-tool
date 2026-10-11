package com.labok.goat.data.tweaks

import android.content.Context
import android.provider.Settings
import com.labok.goat.data.log.LogRepository

/**
 * 애니메이션 속도: 개발자 옵션의 창·전환·애니메이터 배율(전역 설정)을 바꾼다. 값이 작을수록 빠르고(0이면 끔), 1이 기본이다.
 * 전역 설정이라 고급 화면 배율 설정과 같은 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로 바꾼다. 재부팅해도 유지된다.
 */
object AnimationSpeed {
    /** 바꾸는 세 설정과 화면 이름. */
    enum class Kind(val key: String, val label: String) {
        WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE, "창 애니메이션"),
        TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE, "화면 전환 애니메이션"),
        ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE, "앱 안 움직임 길이"),
    }


    fun read(context: Context, kind: Kind): Float =
        runCatching { Settings.Global.getFloat(context.contentResolver, kind.key, 1f) }.getOrDefault(1f)

    /** 하나를 바꾼다. 성공하면 true. */
    fun set(context: Context, kind: Kind, value: Float, how: String): Boolean {
        val before = read(context, kind)
        val ok = runCatching { Settings.Global.putFloat(context.contentResolver, kind.key, value) }.getOrDefault(false)
        LogRepository.from(context).add("애니메이션 속도($how): ${kind.label} ${label(before)} → ${label(value)}${if (ok) "" else " 실패"}")
        return ok
    }

    /** 셋을 같은 값으로. */
    fun setAll(context: Context, value: Float, how: String): Boolean =
        Kind.entries.map { set(context, it, value, how) }.all { it }

    fun label(v: Float) = when {
        v == 0f -> "끔"
        v == 1f -> "기본"
        else -> "${"%.2f".format(v).trimEnd('0').trimEnd('.')}배"
    }
}
