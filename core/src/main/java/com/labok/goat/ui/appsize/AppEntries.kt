package com.labok.goat.ui.appsize

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.labok.goat.core.R
import com.labok.goat.data.appsize.AppList
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.ui.text.Copy

/** 앱 고르기 목록의 한 줄(앱별 배율·실험실 플렉스가 함께 쓴다, 11-11 모듈 분리 때 AppSizeScreen.kt에서 옮김). */
class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap?, val lastUsed: Long)

/** 홈 화면에 아이콘이 있는 앱(GOAT 제외)과 아이콘·마지막 사용 시각. 최근 사용 순(같으면 이름 순). 실험실 플렉스 화면도 쓴다. */
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val used = AppUsage.lastUsed(context)
    val size = (38 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    return AppList.launcherApps(context)
        .filter { it.pkg != context.packageName }
        .map { app ->
            val icon = runCatching { pm.getApplicationIcon(app.pkg).toBitmap(size, size).asImageBitmap() }.getOrNull()
            AppEntry(app.label, app.pkg, icon, used[app.pkg] ?: 0L)
        }
        .sortedWith(compareByDescending<AppEntry> { it.lastUsed }.thenBy { it.label })
}

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> Copy.text(R.string.appsize_used_just_now)
        m < 60 -> Copy.text(R.string.appsize_used_minutes_fmt, m)
        m < 24 * 60 -> Copy.text(R.string.appsize_used_hours_fmt, m / 60)
        else -> Copy.text(R.string.appsize_used_days_fmt, m / (24 * 60))
    }
}
