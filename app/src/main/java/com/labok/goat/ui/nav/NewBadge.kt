package com.labok.goat.ui.nav

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text

/**
 * 새 기능 점: 새로 들어온 기능은 사이드바 항목 옆과 화면 안 카드 위에 점을 찍고, 그 화면을 보고 나가면 지운다.
 * 사이드바 항목과 카드는 같은 키를 쓴다. 새 기능을 넣을 때 [NAV]에 키를 더한다(오래된 키는 지워도 된다).
 * 새로 설치한 사람에게는 모두 새것이라 점을 찍지 않는다(설치 시각과 업데이트 시각이 같으면 모두 본 것으로 둔다).
 */
object NewBadge {
    const val STATUS_BAR_HIDE = "status_bar_hide"
    const val FLEX_APP_MODES = "flex_app_modes"
    const val INK_TONES = "ink_tones"
    const val SHARE_PROBE = "share_probe"

    val NAV: Map<NavItem, List<String>> = mapOf(
        NavItem.STATUS_ICONS to listOf(STATUS_BAR_HIDE),
        NavItem.LAB_FLEX to listOf(FLEX_APP_MODES),
        NavItem.SETTINGS to listOf(INK_TONES),
        NavItem.LAB_TOOLS to listOf(SHARE_PROBE)
    )

    var seen by mutableStateOf(emptySet<String>())
        private set

    fun load(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean("init", false)) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
            p.edit().putBoolean("init", true).apply { if (fresh) putStringSet("seen", NAV.values.flatten().toSet()) }.apply()
        }
        seen = p.getStringSet("seen", emptySet()).orEmpty()
    }

    fun isNew(key: String) = key !in seen

    fun navNew(item: NavItem) = NAV[item]?.any { isNew(it) } == true

    fun markSeen(context: Context, item: NavItem) {
        val keys = NAV[item] ?: return
        if (keys.none { isNew(it) }) return
        seen = seen + keys
        prefs(context).edit().putStringSet("seen", seen).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("new_badge", Context.MODE_PRIVATE)
}

/** 새 기능 점(사이드바 항목 옆). */
@Composable
fun NewDot(modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    androidx.compose.foundation.layout.Box(modifier.size(8.dp).clip(CircleShape).background(c.danger))
}

/** 카드 위 '새 기능' 표시. 아직 보지 않은 기능일 때만 보인다. */
@Composable
fun NewTag(key: String, modifier: Modifier = Modifier) {
    if (!NewBadge.isNew(key)) return
    val c = LocalLiquid.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        NewDot()
        Spacer(Modifier.width(6.dp))
        Text("새 기능", color = c.danger, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}
