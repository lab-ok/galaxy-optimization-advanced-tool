package com.local.folddpifix.data.appsize

import android.content.Context
import android.content.Intent

/** 홈 화면에 아이콘이 있는 앱 목록(앱별 화면 크기에서 고를 대상). */
object AppList {
    data class App(val label: String, val pkg: String)

    fun launcherApps(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label }
    }
}
