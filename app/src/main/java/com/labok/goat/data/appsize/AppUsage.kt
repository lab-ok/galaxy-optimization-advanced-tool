package com.labok.goat.data.appsize

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.DensityStepActivity

/**
 * 사용 기록(UsageStats)으로 앱을 최근 사용 순으로 정렬하고, 지금 화면에 떠 있던 앱을 찾는다.
 * 사용 기록 접근은 Shizuku가 켜져 있으면 GOAT가 스스로 허용하고([ShizukuAccess.grantUsageAccess]),
 * 아니면 설정의 '사용 기록 접근' 화면에서 사용자가 켠다.
 */
object AppUsage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** '지금 쓰는 앱'으로 치지 않는 앱: 시스템 UI(알림창), Shizuku. GOAT 자신은 조절 대상이다. */
    fun notTargets(context: Context) = setOf("com.android.systemui", ShizukuAccess.PACKAGE)

    /** 알림 버튼을 받는 GOAT의 투명 화면. 이 화면이 앞에 와도 '지금 쓰는 앱'을 바꾸지 않는다. */
    val STEP_ACTIVITY: String = DensityStepActivity::class.java.name

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
     * 지금 쓰던 앱: 최근 6시간 사용 기록에서 마지막으로 화면에 올라온 앱(시스템 UI·알림 버튼 화면 제외).
     * 마지막이 홈 화면이면 null(열려 있는 앱이 없음).
     */
    fun foregroundApp(context: Context): String? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        val skip = notTargets(context)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 6 * 3600 * 1000L, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName !in skip && e.className != STEP_ACTIVITY) last = e.packageName
        }
        return last?.takeIf { it != home }
    }
}
