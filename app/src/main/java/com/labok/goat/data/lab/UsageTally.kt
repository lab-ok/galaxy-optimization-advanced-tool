package com.labok.goat.data.lab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.labok.goat.data.appsize.AppList
import com.labok.goat.data.appsize.AppUsage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/**
 * 실험실 '사용 통계'(11-04 소장님 지정: 새 권한 없이 되는 것만 한 페이지로).
 * 대부분은 Android가 남겨 둔 기록에서 바로 센다(상주 서비스 없음):
 * - 사용 기록(UsageEvents, 사용 기록 접근): 화면 켜기·켠 시간·시간대, 잠금 해제, 앱 실행, 알림, 화면 회전, 펼치기, 재부팅
 * - 배터리 기록(Shizuku): 충전기 꽂은 횟수·꽂을 때 잔량, 배터리 사용량
 * 볼륨 버튼만 기록이 남지 않아, 볼륨 버튼 회전의 접근성 서비스가 켜져 있는 동안 날짜별로 직접 센다([countVolume]).
 * Android 사용 기록은 며칠만 남으므로 지난날은 [UsageArchive]가 날짜별로 영구 저장한다.
 */
object UsageTally {
    private const val PREF = "usage_tally"

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
        /** 충전기 꽂은 횟수. 배터리 기록을 못 읽었으면 null. */
        val charges: Int?,
        /** 꽂을 때 잔량의 합(평균은 [chargeStartAvg]). */
        val chargeStartSum: Int,
        /** 볼륨 버튼 누른 횟수. 세는 중이 아니었으면 null. */
        val volume: Int?,
        /** 앱별 실행 횟수(패키지). */
        val apps: Map<String, Int>,
        /** 앱별 알림 수(패키지). */
        val notis: Map<String, Int>,
        /** 배터리를 쓴 양(%)과 그중 화면 켜고 쓴 양, 충전으로 오른 양. 배터리 기록이 없으면 null. */
        val batteryUsed: Float?,
        val batteryScreen: Float?,
        val batteryCharged: Float?,
        /** 기록이 있는 날 수(하루 평균에 쓴다). */
        val days: Int = 1
    ) {
        val chargeStartAvg: Int? get() = charges?.takeIf { it > 0 }?.let { chargeStartSum / it }
    }

    /** [from]~[to] 사이를 센다(작업 스레드). [battery]는 미리 읽은 배터리 기록(없으면 배터리 항목은 null). */
    fun read(context: Context, from: Long, to: Long, foldable: Boolean, battery: List<BatteryHistory.Point>? = null): Result {
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
            var running = Configuration().apply {
                setToDefaults()
                screenWidthDp = 0
                screenHeightDp = 0
                smallestScreenWidthDp = 0
            }
            var screenKnown = false
            fun addOn(start: Long, end: Long) {
                val s = max(start, from)
                val t = min(end, to)
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
                    UsageEvents.Event.CONFIGURATION_CHANGE -> e.configuration?.let { delta ->
                        // 사용 기록의 화면 구성은 바뀐 항목만 든 조각이다(에뮬레이터 실측: 회전은 w828dp-h531dp-land만 남는다).
                        // 조각을 차례로 이어 붙여 전체 모양을 만든 뒤 비교한다
                        val next = Configuration(running).apply { updateFrom(delta) }
                        if (inRange && running.screenWidthDp > 0) {
                            when (shapeChange(running, next)) {
                                Shape.ROTATED -> rotations++
                                Shape.UNFOLDED -> unfolds++
                                else -> Unit
                            }
                        }
                        running = next
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
        var chargeSum = 0
        var used: Float? = null
        var usedOn: Float? = null
        var charged: Float? = null
        if (battery != null && battery.isNotEmpty() && battery.first().time <= to) {
            val inRange = BatteryHistory.charges(battery, to).filter { it.start in from..to }
            charges = inRange.size
            chargeSum = inRange.sumOf { it.startLevel }
            val start = max(from, battery.first().time)
            BatteryHistory.buckets(battery, to, to - start + 1, from = start).firstOrNull()?.let {
                used = it.usedPct
                usedOn = it.screenPct
                charged = it.chargedPct
            }
        }
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
            chargeStartSum = chargeSum,
            volume = volume(context, from, to),
            apps = apps,
            notis = noti,
            batteryUsed = used,
            batteryScreen = usedOn,
            batteryCharged = charged
        )
    }

    enum class Shape { SAME, ROTATED, UNFOLDED, FOLDED }

    /**
     * 화면 모양 변화 판단. 회전은 가장 짧은 변이 그대로이고 가로·세로만 맞바뀐 경우, 펼침은 가장 짧은 변이 크게(25% 이상) 늘면서
     * 화면이 덜 길쭉해진 경우다. 1.50.2는 가장 짧은 변 500dp를 기준으로 했는데, 폴드8 바깥 화면도 GOAT 배율(360dpi)에서
     * 554dp라 펼침을 하나도 세지 못했다(11-04 소장님 진단 파일). 배율과 상관없는 비율로 판단한다.
     */
    internal fun shapeChange(a: Configuration, b: Configuration): Shape = shapeChange(a.smallestScreenWidthDp, a.screenWidthDp, a.screenHeightDp, a.orientation, b.smallestScreenWidthDp, b.screenWidthDp, b.screenHeightDp, b.orientation)

    internal fun shapeChange(swA: Int, wA: Int, hA: Int, oA: Int, swB: Int, wB: Int, hB: Int, oB: Int): Shape {
        if (swA <= 0 || swB <= 0 || wA <= 0 || hA <= 0 || wB <= 0 || hB <= 0) return Shape.SAME
        val ratioA = max(wA, hA).toFloat() / min(wA, hA)
        val ratioB = max(wB, hB).toFloat() / min(wB, hB)
        return when {
            swB >= swA * 1.25f && ratioB < ratioA - 0.1f -> Shape.UNFOLDED
            swA >= swB * 1.25f && ratioA < ratioB - 0.1f -> Shape.FOLDED
            swA == swB && oA != oB && abs(wB - hA) <= SWAP_SLACK_DP -> Shape.ROTATED
            else -> Shape.SAME
        }
    }

    /** 여러 결과(날짜별 기록 등)를 하나로 합친다. 비어 있으면 null. */
    fun merge(list: List<Result>): Result? {
        if (list.isEmpty()) return null
        if (list.size == 1) return list[0]
        fun maps(pick: (Result) -> Map<String, Int>) = HashMap<String, Int>().also { m -> list.forEach { r -> pick(r).forEach { (k, v) -> m[k] = (m[k] ?: 0) + v } } }
        fun ints(pick: (Result) -> Int?): Int? = list.mapNotNull(pick).takeIf { it.isNotEmpty() }?.sum()
        fun floats(pick: (Result) -> Float?): Float? = list.mapNotNull(pick).takeIf { it.isNotEmpty() }?.sum()
        return Result(
            usageAccess = list.any { it.usageAccess },
            screenOn = list.sumOf { it.screenOn },
            screenMs = list.sumOf { it.screenMs },
            hourlyMs = LongArray(24) { h -> list.sumOf { it.hourlyMs[h] } },
            unlocks = list.sumOf { it.unlocks },
            appOpens = list.sumOf { it.appOpens },
            notifications = list.sumOf { it.notifications },
            rotations = list.sumOf { it.rotations },
            unfolds = ints { it.unfolds },
            boots = list.sumOf { it.boots },
            charges = ints { it.charges },
            chargeStartSum = list.sumOf { it.chargeStartSum },
            volume = ints { it.volume },
            apps = maps { it.apps },
            notis = maps { it.notis },
            batteryUsed = floats { it.batteryUsed },
            batteryScreen = floats { it.batteryScreen },
            batteryCharged = floats { it.batteryCharged },
            days = list.sumOf { it.days }
        )
    }

    /** 많은 순서 [n]개를 앱 이름으로. 이름을 못 찾으면 패키지 이름. */
    fun top(context: Context, m: Map<String, Int>, n: Int): List<Pair<String, Int>> {
        if (m.isEmpty()) return emptyList()
        val labels = runCatching { AppList.launcherApps(context).associate { it.pkg to it.label } }.getOrDefault(emptyMap())
        return m.entries.sortedByDescending { it.value }.take(n).map { (labels[it.key] ?: it.key) to it.value }
    }

    /** 날짜별 저장용 JSON. 앱·알림은 많은 순서 30개만 남긴다(파일이 커지지 않게). */
    fun toJson(r: Result): JSONObject = JSONObject().apply {
        put("scr", r.screenOn)
        put("ms", r.screenMs)
        put("h", JSONArray(r.hourlyMs.toList()))
        put("ul", r.unlocks)
        put("op", r.appOpens)
        put("nt", r.notifications)
        put("rot", r.rotations)
        r.unfolds?.let { put("uf", it) }
        put("bt", r.boots)
        r.charges?.let { put("ch", it) }
        put("chs", r.chargeStartSum)
        r.volume?.let { put("vol", it) }
        put("apps", JSONObject(r.apps.entries.sortedByDescending { it.value }.take(30).associate { it.key to it.value }))
        put("nts", JSONObject(r.notis.entries.sortedByDescending { it.value }.take(30).associate { it.key to it.value }))
        r.batteryUsed?.let { put("bu", it.toDouble()) }
        r.batteryScreen?.let { put("bs", it.toDouble()) }
        r.batteryCharged?.let { put("bc", it.toDouble()) }
    }

    fun fromJson(o: JSONObject): Result {
        fun map(key: String): Map<String, Int> = o.optJSONObject(key)?.let { j -> j.keys().asSequence().associateWith { j.optInt(it) } }.orEmpty()
        val h = o.optJSONArray("h")
        return Result(
            usageAccess = true,
            screenOn = o.optInt("scr"),
            screenMs = o.optLong("ms"),
            hourlyMs = LongArray(24) { h?.optLong(it) ?: 0L },
            unlocks = o.optInt("ul"),
            appOpens = o.optInt("op"),
            notifications = o.optInt("nt"),
            rotations = o.optInt("rot"),
            unfolds = if (o.has("uf")) o.optInt("uf") else null,
            boots = o.optInt("bt"),
            charges = if (o.has("ch")) o.optInt("ch") else null,
            chargeStartSum = o.optInt("chs"),
            volume = if (o.has("vol")) o.optInt("vol") else null,
            apps = map("apps"),
            notis = map("nts"),
            batteryUsed = if (o.has("bu")) o.optDouble("bu").toFloat() else null,
            batteryScreen = if (o.has("bs")) o.optDouble("bs").toFloat() else null,
            batteryCharged = if (o.has("bc")) o.optDouble("bc").toFloat() else null
        )
    }

    /**
     * 문제 신고용(작업 스레드): 오늘 숫자와 최근 이틀의 화면 모양 변화 기록(시각·가장 짧은 변·가로×세로·방향·판단).
     * 펼친 횟수·회전이 실기에서 맞게 세어지는지 가리려고 넣었다.
     */
    fun diagnose(context: Context): String = buildString {
        if (!AppUsage.hasAccess(context)) return "사용 기록 접근 없음"
        val today = dayStart()
        val r = read(context, today, System.currentTimeMillis(), true)
        appendLine("오늘: 화면 켬 ${r.screenOn} · 잠금 해제 ${r.unlocks} · 앱 ${r.appOpens} · 알림 ${r.notifications} · 회전 ${r.rotations} · 펼침 ${r.unfolds} · 볼륨 ${r.volume}")
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(today - DAY_MS, System.currentTimeMillis())
        val e = UsageEvents.Event()
        val lines = ArrayList<String>()
        var running = Configuration().apply {
            setToDefaults()
            screenWidthDp = 0
            screenHeightDp = 0
            smallestScreenWidthDp = 0
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.CONFIGURATION_CHANGE) continue
            val delta = e.configuration ?: continue
            val c = Configuration(running).apply { updateFrom(delta) }
            val shape = if (running.screenWidthDp > 0) shapeChange(running, c) else Shape.SAME
            lines += "${fmt.format(Date(e.timeStamp))} sw=${c.smallestScreenWidthDp} ${c.screenWidthDp}x${c.screenHeightDp} o=${c.orientation} $shape"
            running = c
        }
        appendLine("화면 모양 변화 ${lines.size}건(최근 30건):")
        lines.takeLast(30).forEach { appendLine("  $it") }
    }.trimEnd()

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
            val next = min(cal.timeInMillis, end)
            hourly[h] += next - t
            t = next
        }
    }

    /** 볼륨 버튼 한 번(볼륨 버튼 회전의 접근성 서비스가 부른다). 날짜별로 센다. */
    fun countVolume(context: Context) {
        val p = prefs(context)
        val key = "vol_" + dayKey(System.currentTimeMillis())
        p.edit().putInt(key, p.getInt(key, 0) + 1).putBoolean("vol_seen", true).apply()
    }

    /** [from]~[to]에 걸친 날들의 볼륨 버튼 횟수. 한 번도 센 적이 없으면 null. */
    private fun volume(context: Context, from: Long, to: Long): Int? {
        val p = prefs(context)
        if (!p.getBoolean("vol_seen", false)) return null
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        var sum = 0
        while (cal.timeInMillis <= to) {
            sum += p.getInt("vol_" + dayKey(cal.timeInMillis), 0)
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

    /** 날짜 열쇠(yyyyMMdd). */
    fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))

    private const val DAY_MS = 86_400_000L

    /** 회전으로 볼 가로·세로 맞바뀜의 허용 오차(dp). 상태 표시줄·내비게이션 막대 때문에 몇 dp 차이가 난다. */
    private const val SWAP_SLACK_DP = 80

    /** UsageEvents.Event.NOTIFICATION_INTERRUPTION(시스템 전용 상수라 값으로 둔다). */
    private const val NOTIFICATION_INTERRUPTION = 12

    /** UsageEvents.Event.DEVICE_STARTUP(API 29, 공개 상수가 없는 판도 있어 값으로 둔다). */
    private const val DEVICE_STARTUP = 27

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
