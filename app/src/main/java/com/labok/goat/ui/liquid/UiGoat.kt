package com.labok.goat.ui.liquid

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 염소 아이콘 모양(앱 설정): 1.14.0 전의 선 그림 염소([Style.CLASSIC], 기본), 캐릭터 그림([Style.ART]),
 * 흰 염소 옆모습 실루엣([Style.SILHOUETTE], 1.31.0, 시안 6 '걷는 염소').
 * 앱 안 로고([com.labok.goat.ui.nav.goatPainter])와 앱 아이콘을 함께 바꾼다. 앱 아이콘은 런처 항목(activity-alias)
 * 가운데 하나만 켜서 바꾸므로, 런처에 따라 홈 화면에 놓은 아이콘이 빠져 다시 놓아야 할 수 있다.
 */
object UiGoat {
    enum class Style { ART, CLASSIC, SILHOUETTE }

    var style by mutableStateOf(Style.CLASSIC)
        private set

    fun load(context: Context) {
        style = runCatching { Style.valueOf(prefs(context).getString("style", null) ?: "") }.getOrDefault(Style.CLASSIC)
    }

    fun set(context: Context, s: Style) {
        if (s == style) return
        style = s
        prefs(context).edit().putString("style", s.name).apply()
        applyLauncher(context, s)
    }

    /** 고른 모양의 런처 항목만 켠다. 켜는 쪽을 먼저 바꿔 런처 항목이 하나도 없는 순간이 생기지 않게 한다. */
    private fun applyLauncher(context: Context, s: Style) {
        val pm = context.packageManager
        val on = LAUNCHERS.getValue(s)
        pm.setComponentEnabledSetting(ComponentName(context.packageName, on), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        LAUNCHERS.values.filter { it != on }.forEach {
            pm.setComponentEnabledSetting(ComponentName(context.packageName, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private val LAUNCHERS = mapOf(
        Style.ART to "com.labok.goat.LauncherArt",
        Style.CLASSIC to "com.labok.goat.LauncherClassic",
        Style.SILHOUETTE to "com.labok.goat.LauncherSilhouette"
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_goat", Context.MODE_PRIVATE)
}
