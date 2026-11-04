package com.labok.goat.data.lab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.labok.goat.data.appsize.AppList
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.data.shizuku.ShizukuAccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 실험실 '사용 통계'(11-04 소장님 지정: 새 권한 없이 되는 것만 한 페이지로).
 * 대부분은 Android가 남겨 둔 기록에서 바로 센다(상주 서비스 없음):
 * - 사용 기록(UsageEvents, 사용 기록 접근): 화면 켜기·켠 시간·시간대, 잠금 해제, 앱 실행, 알림, 화면 회전, 접기·펴기(화면 폭 변화), 재부팅
 * - 배터리 기록(Shizuku): 충전기 꽂은 횟수와 꽂을 때 잔량
 * 볼륨 버튼만 기록이 남지 않아, 볼륨 버튼 회전의 접근성 서비스가 켜져 있는 동안 날짜별로 직접 센다([countVolume]).
 */
object UsageTally {
    private const val PREF = "usage_tally"

    /** 접은 화면과 펼친 화면을 가르는 가장 짧은 변 폭(dp). 폴드 바깥 화면은 400 안팎, 안쪽 화면은 600 이상이다. */
    private const val UNFOLDED_SW_DP = 500

    data class Result(
        val usageAccess: Boolean,
        val screenOn: Int,
        val screenMs: Long,
        /** 0~23시별 화면 켠 시간(ms). */
        val hourlyMs: LongArray,
        val unlocks: Int,
        val appOpens: Int,
        val notifications: Int,
        val rotations: Int,
        /** 펼친 횟수. 폴더블이 아니면 null. */
        val unfolds: Int?,
        val boots: Int,
        /** 충전기 꽂은 횟수. Shizuku가 없으면 null. */
        val charges: Int?,
        val chargeStartAvg: Int?,
        /** 볼륨 버튼 누른 횟수. 세는 중이 아니었으면 null. */
        val volume: Int?,
        val topApps: List<Pair<String, Int>>,
        val topNotifications: List<Pair<String, Int>>
    )

    /** [from]~[to] 사이를 센다(작업 스레드). */
    fun read(context: Context, from: Long, to: Long, foldable: Boolean): Result {
        val access = AppUsage.hasAccess(context)
        val hourly = LongArray(24)
        var screenOn = 0
        var screenMs = 0L
        var unlocks = 0
        var opens = 0
        var notifications = 0
        var rotations = 0
        var unfolds = 0
        var boots = 0
        val apps = HashMap<String, Int>()
        val noti = HashMap<String, Int>()
        if (access) {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            // 범위 시작 때의 화면 상태·화면 모양을 알려고 하루 앞부터 읽는다
            val events = usm.queryEvents(from - DAY_MS, to)
            val skip = skipped(context)
            val e = UsageEvents.Event()
            var onSince: Long? = null
            var lastPkg: String? = null
            var prev: Configuration? = null
            var wide: Boolean? = null
            var screenKnown = false
            fun addOn(start: Long, end: Long) {
                val s = maxOf(start, from)
                val t = minOf(end, to)
                if (t <= s) return
                screenMs += t - s
                spread(hourly, s, t)
            }
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val t = e.timeStamp
                val inRange = t in from..to
                when (e.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (onSince == null) onSince = t
                        if (inRange) screenOn++
                        screenKnown = true
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        // 기록 앞쪽에 켜짐이 없는데 꺼짐이 먼저 오면, 읽기 시작 때부터 켜져 있던 것이다
                        (onSince ?: if (!screenKnown) from - DAY_MS else null)?.let { addOn(it, t) }
                        onSince = null
                        screenKnown = true
                    }
                    UsageEvents.Event.KEYGUARD_HIDDEN -> if (inRange) unlocks++
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        val pkg = e.packageName
                        if (pkg != lastPkg && pkg !in skip) {
                            if (inRange) {
                                opens++
                                apps[pkg] = (apps[pkg] ?: 0) + 1
                            }
                            lastPkg = pkg
                        } else if (pkg in skip) {
                            // 홈 화면에 갔다가 같은 앱으로 돌아오면 다시 연 것으로 센다
                            lastPkg = null
                        }
                    }
                    NOTIFICATION_INTERRUPTION -> if (inRange) {
                        notifications++
                        noti[e.packageName] = (noti[e.packageName] ?: 0) + 1
                    }
                    UsageEvents.Event.CONFIGURATION_CHANGE -> e.configuration?.let { c ->
                        // 회전은 가장 짧은 변은 그대로이고 가로·세로 폭만 맞바뀐 경우만 센다(글자 크기·다크 모드·화면 크기 변경은 빼려고)
                        val p0 = prev
                        if (inRange &&
                            p0 != null &&
                            c.orientation != p0.orientation &&
                            c.smallestScreenWidthDp == p0.smallestScreenWidthDp &&
                            abs(c.screenWidthDp - p0.screenHeightDp) <= SWAP_SLACK_DP
                        ) {
                            rotations++
                        }
                        prev = Configuration(c)
                        if (c.smallestScreenWidthDp > 0) {
                            val w = c.smallestScreenWidthDp >= UNFOLDED_SW_DP
                            if (inRange && wide == false && w) unfolds++
                            wide = w
                        }
                    }
                    DEVICE_STARTUP -> if (inRange) boots++
                }
            }
            onSince?.let { addOn(it, to) }
            // 읽은 구간에 화면 켜짐·꺼짐 기록이 하나도 없으면(하루 넘게 켜 둔 경우) 그 뒤 첫 기록으로 판단한다: 꺼짐이 먼저 오면 내내 켜져 있었다
            if (!screenKnown) {
                val later = usm.queryEvents(to, System.currentTimeMillis())
                while (later.hasNextEvent()) {
                    later.getNextEvent(e)
                    if (e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) break
                    if (e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                        addOn(from, to)
                        break
                    }
                }
            }
        }
        var charges: Int? = null
        var chargeAvg: Int? = null
        if (ShizukuAccess.status.value == ShizukuAccess.State.READY) {
            BatteryHistory.read(to)?.let { points ->
                val inRange = BatteryHistory.charges(points, to).filter { it.start in from..to }
                charges = inRange.size
                chargeAvg = inRange.takeIf { it.isNotEmpty() }?.map { it.startLevel }?.average()?.toInt()
            }
        }
        val labels = runCatching { AppList.launcherApps(context).associate { it.pkg to it.label } }.getOrDefault(emptyMap())
        fun top(m: Map<String, Int>, n: Int) = m.entries.sortedByDescending { it.value }.take(n).map { (labels[it.key] ?: it.key) to it.value }
        return Result(
            usageAccess = access,
            screenOn = screenOn,
            screenMs = screenMs,
            hourlyMs = hourly,
            unlocks = unlocks,
            appOpens = opens,
            notifications = notifications,
            rotations = rotations,
            unfolds = unfolds.takeIf { foldable },
            boots = boots,
            charges = charges,
            chargeStartAvg = chargeAvg,
            volume = volume(context, from, to),
            topApps = top(apps, 5),
            topNotifications = top(noti, 3)
        )
    }

    /** 앱 실행으로 세지 않는 앱: 홈 화면(런처), 알림창, 공유 창, 권한 창, GOAT 자신. */
    private fun skipped(context: Context): Set<String> {
        val pm = context.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return home.toSet() + setOf(
            context.packageName,
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
    }

    /** [start]~[end] 구간을 0~23시 칸에 나눠 더한다. */
    internal fun spread(hourly: LongArray, start: Long, end: Long) {
        val cal = Calendar.getInstance()
        var t = start
        while (t < end) {
            cal.timeInMillis = t
            val h = cal.get(Calendar.HOUR_OF_DAY)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val next = minOf(cal.timeInMillis, end)
            hourly[h] += next - t
            t = next
        }
    }

    /** 볼륨 버튼 한 번(볼륨 버튼 회전의 접근성 서비스가 부른다). 날짜별로 센다. */
    fun countVolume(context: Context) {
        val p = prefs(context)
        val key = "vol_" + DAY.format(Date())
        p.edit().putInt(key, p.getInt(key, 0) + 1).putBoolean("vol_seen", true).apply()
    }

    /** [from]~[to]에 걸친 날들의 볼륨 버튼 횟수. 한 번도 센 적이 없으면 null. */
    private fun volume(context: Context, from: Long, to: Long): Int? {
        val p = prefs(context)
        if (!p.getBoolean("vol_seen", false)) return null
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        var sum = 0
        while (cal.timeInMillis <= to) {
            sum += p.getInt("vol_" + DAY.format(cal.time), 0)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return sum
    }

    /** 오늘 0시. [daysAgo]만큼 앞날의 0시. */
    fun dayStart(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -daysAgo)
    }.timeInMillis

    private val DAY get() = SimpleDateFormat("yyyyMMdd", Locale.US)

    private const val DAY_MS = 86_400_000L

    /** 회전으로 볼 가로·세로 맞바뀜의 허용 오차(dp). 상태 표시줄·내비게이션 막대 때문에 몇 dp 차이가 난다. */
    private const val SWAP_SLACK_DP = 80

    /** UsageEvents.Event.NOTIFICATION_INTERRUPTION(시스템 전용 상수라 값으로 둔다). */
    private const val NOTIFICATION_INTERRUPTION = 12

    /** UsageEvents.Event.DEVICE_STARTUP(API 29, 공개 상수가 없는 판도 있어 값으로 둔다). */
    private const val DEVICE_STARTUP = 27

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
