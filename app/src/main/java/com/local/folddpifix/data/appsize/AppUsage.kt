package com.local.folddpifix.data.appsize

import com.local.folddpifix.data.shizuku.ShizukuAccess
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * 사용 기록(UsageStats)으로 앱을 최근 사용 순으로 정렬하고, 지금 화면에 떠 있던 앱을 찾는다.
 * 사용 기록 접근은 Shizuku가 켜져 있으면 FoldFit이 스스로 허용하고([ShizukuAccess.grantUsageAccess]),
 * 아니면 설정의 '사용 기록 접근' 화면에서 사용자가 켠다.
 */
object AppUsage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** 패키지별 마지막 사용 시각(최근 60일). 접근 권한이 없으면 빈 지도. */
    fun lastUsed(context: Context): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return runCatching {
            usm.queryAndAggregateUsageStats(now - 60L * 24 * 3600 * 1000, now).mapValues { maxOf(it.value.lastTimeUsed, it.value.lastTimeVisible) }
        }.getOrDefault(emptyMap())
    }

    /**
     * 지금 쓰던 앱: 최근 6시간 사용 기록에서 마지막으로 화면에 올라온 앱(FoldFit·시스템 UI 제외).
     * 마지막이 홈 화면이면 null(열려 있는 앱이 없음).
     */
    fun foregroundApp(context: Context): String? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        val skip = setOf(context.packageName, "com.android.systemui", ShizukuAccess.PACKAGE)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 6 * 3600 * 1000L, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName !in skip) last = e.packageName
        }
        return last?.takeIf { it != home }
    }
}
