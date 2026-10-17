package com.labok.goat.data.lab

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 숨은 실험실: 염소를 쉬지 않고 [TAPS]번 누르면 사이드바에 '실험실'이 나타난다. 한 번 열면 계속 보인다.
 * 화면이 바로 바뀌도록 Compose 상태로 들고 있고, 앱을 다시 켜도 남도록 저장한다.
 */
object LabUnlock {
    const val TAPS = 10
    private const val PREF = "lab_unlock"

    var unlocked by mutableStateOf(false)
        private set

    fun load(context: Context) {
        unlocked = prefs(context).getBoolean("unlocked", false)
        // '텍스트 파일로 공유'는 실험실을 연 경우에만 다른 앱 공유 메뉴에 보인다
        runCatching { MemoShare.setShareTarget(context, unlocked) }
    }

    /** 처음 열었으면 true. */
    fun unlock(context: Context): Boolean {
        if (unlocked) return false
        unlocked = true
        prefs(context).edit().putBoolean("unlocked", true).apply()
        runCatching { MemoShare.setShareTarget(context, true) }
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
