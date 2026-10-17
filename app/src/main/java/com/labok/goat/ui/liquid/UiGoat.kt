package com.labok.goat.ui.liquid

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 염소 아이콘 모양(앱 설정): 지금 쓰는 캐릭터 그림([Style.ART]) 또는 1.14.0 전의 선 그림 염소([Style.CLASSIC]).
 * 앱 안 로고([com.labok.goat.ui.nav.goatPainter])와 앱 아이콘을 함께 바꾼다. 앱 아이콘은 런처 항목 둘(activity-alias)
 * 가운데 하나만 켜서 바꾸므로, 런처에 따라 홈 화면에 놓은 아이콘이 빠져 다시 놓아야 할 수 있다.
 */
object UiGoat {
    enum class Style { ART, CLASSIC }

    var style by mutableStateOf(Style.ART)
        private set

    fun load(context: Context) {
        style = runCatching { Style.valueOf(prefs(context).getString("style", null) ?: "") }.getOrDefault(Style.ART)
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
        val on = if (s == Style.CLASSIC) LAUNCHER_CLASSIC else LAUNCHER_ART
        val off = if (s == Style.CLASSIC) LAUNCHER_ART else LAUNCHER_CLASSIC
        pm.setComponentEnabledSetting(ComponentName(context.packageName, on), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(ComponentName(context.packageName, off), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    private const val LAUNCHER_ART = "com.labok.goat.LauncherArt"
    private const val LAUNCHER_CLASSIC = "com.labok.goat.LauncherClassic"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_goat", Context.MODE_PRIVATE)
}
