package com.labok.goat.ui.liquid

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import com.labok.goat.R

/**
 * 염소 아이콘 모양(앱 설정): 1.14.0 전의 선 그림 염소([Style.CLASSIC], 기본), 캐릭터 그림([Style.ART]),
 * 흰 염소 옆모습 실루엣([Style.SILHOUETTE], 1.31.0, 시안 6 '걷는 염소'), 설정 톱니 안의 염소([Style.GEAR], 1.36.0, 설정 톱니 시안 9).
 * 앱 안 로고([com.labok.goat.ui.nav.goatPainter])와 앱 아이콘을 함께 바꾼다. 앱 아이콘은 런처 항목(activity-alias)
 * 가운데 하나만 켜서 바꾸므로, 런처에 따라 홈 화면에 놓은 아이콘이 빠져 다시 놓아야 할 수 있다.
 */
object UiGoat {
    enum class Style { ART, CLASSIC, SILHOUETTE, GEAR }

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
        refreshNotifications(context)
    }

    /** 떠 있는 GOAT 알림(상주 서비스 등)의 큰 아이콘을 새로 고른 모양으로 다시 붙인다. 내용은 그대로 둔다. */
    private fun refreshNotifications(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val icon = notificationIcon(context)?.let { Icon.createWithBitmap(it) } ?: return
        runCatching {
            nm.activeNotifications.forEach { sbn ->
                val n = Notification.Builder.recoverBuilder(context, sbn.notification).setLargeIcon(icon).build()
                nm.notify(sbn.tag, sbn.id, n)
            }
        }
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

    /**
     * 알림 큰 아이콘: 고른 모양의 앱 아이콘 그림. 알림 왼쪽 아이콘은 앱 자체 아이콘(manifest의 application 아이콘)이라
     * 실행 중에 바꿀 수 없어, 알림마다 고른 아이콘을 큰 아이콘으로 붙인다(11-01 소장님: 알림 아이콘도 고른 아이콘으로).
     * 앱 자체 아이콘은 톱니로 고정했다. 백그라운드 서비스에서도 부르므로 [load]를 거치지 않고 저장값을 직접 읽는다.
     */
    fun notificationIcon(context: Context): Bitmap? {
        val s = runCatching { Style.valueOf(prefs(context).getString("style", null) ?: "") }.getOrDefault(Style.CLASSIC)
        iconCache?.let { (cs, bmp) -> if (cs == s) return bmp }
        val res = when (s) {
            Style.ART -> R.mipmap.ic_launcher
            Style.CLASSIC -> R.mipmap.ic_launcher_classic
            Style.SILHOUETTE -> R.mipmap.ic_launcher_silhouette
            Style.GEAR -> R.mipmap.ic_launcher_gear
        }
        val px = (64 * context.resources.displayMetrics.density).toInt()
        val bmp = runCatching { context.getDrawable(res)?.toBitmap(px, px) }.getOrNull()
        iconCache = s to bmp
        return bmp
    }

    @Volatile private var iconCache: Pair<Style, Bitmap?>? = null

    private val LAUNCHERS = mapOf(
        Style.ART to "com.labok.goat.LauncherArt",
        Style.CLASSIC to "com.labok.goat.LauncherClassic",
        Style.SILHOUETTE to "com.labok.goat.LauncherSilhouette",
        Style.GEAR to "com.labok.goat.LauncherGear"
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ui_goat", Context.MODE_PRIVATE)
}
