package com.labok.goat.data.lab

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * 사용 통계 영구 저장(11-04 소장님 "기록은 영구저장해주고 1년단위 전체단위로도, 월별로도"). Android 사용 기록은 며칠만 남으므로
 * 지난날을 날짜별 숫자로 앱 안 파일(files/usage_days_v2.json)에 남긴다. 하루 한 번 [Worker]가, 그리고 통계 화면을 열 때 [sync]가
 * 아직 저장하지 않은 지난날을 채운다. 오늘은 저장하지 않고 늘 새로 센다. 파일은 휴대폰 밖으로 보내지 않는다.
 */
object UsageArchive {
    private const val FILE = "usage_days_v2.json"

    /** 처음 켤 때 거슬러 올라가 채우는 날 수(Android 사용 기록이 남아 있는 만큼만 실제로 채워진다). */
    private const val BACKFILL_DAYS = 30
    private val lock = Any()

    /** 저장된 날짜별 기록(yyyyMMdd → 결과). */
    fun days(context: Context): Map<String, UsageTally.Result> = synchronized(lock) {
        load(context).let { j -> j.keys().asSequence().associateWith { UsageTally.fromJson(j.getJSONObject(it)) } }
    }

    /**
     * 아직 저장하지 않은 지난날(어제까지)을 저장한다(작업 스레드). 사용 기록이 지워진 날은 활동이 하나도 없게 나오므로
     * 0으로 잘못 남지 않게 건너뛴다. 저장한 날 수를 돌려준다.
     */
    fun sync(context: Context): Int = synchronized(lock) {
        if (!AppUsage.hasAccess(context)) return 0
        val json = load(context)
        val missing = (BACKFILL_DAYS downTo 1).filter { !json.has(UsageTally.dayKey(UsageTally.dayStart(it))) }
        if (missing.isEmpty()) return 0
        val foldable = DeviceKind.isFoldable(context)
        val battery = if (ShizukuAccess.status.value == ShizukuAccess.State.READY) BatteryHistory.read() else null
        var added = 0
        for (i in missing) {
            val from = UsageTally.dayStart(i)
            val to = UsageTally.dayStart(i - 1) - 1
            // 배터리 기록이 그날 전체를 덮을 때만 배터리 숫자를 남긴다(일부만 있으면 적게 잡힌다)
            val b = battery?.takeIf { it.isNotEmpty() && it.first().time <= from }
            val r = UsageTally.read(context, from, to, foldable, b)
            if (r.screenOn == 0 && r.appOpens == 0 && r.notifications == 0 && r.screenMs == 0L) continue
            json.put(UsageTally.dayKey(from), UsageTally.toJson(r))
            added++
        }
        if (added > 0) {
            save(context, json)
            LogRepository.from(context).add("사용 통계: 지난날 ${added}일 저장(모두 ${json.length()}일)")
        }
        added
    }

    /** [from]~[to]에 드는 저장된 날들. */
    fun between(context: Context, from: Long, to: Long): List<Pair<String, UsageTally.Result>> {
        val a = UsageTally.dayKey(from)
        val b = UsageTally.dayKey(to)
        return days(context).filterKeys { it in a..b }.toSortedMap().map { it.key to it.value }
    }

    /** 가장 오래된 저장 날짜의 0시. 저장한 날이 없으면 null. */
    fun firstDay(context: Context): Long? = days(context).keys.minOrNull()?.let { k ->
        Calendar.getInstance().apply {
            clear()
            set(k.substring(0, 4).toInt(), k.substring(4, 6).toInt() - 1, k.substring(6, 8).toInt())
        }.timeInMillis
    }

    /** 저장된 날 수와 파일 크기(진단용). */
    fun summary(context: Context): String {
        val f = File(context.filesDir, FILE)
        val d = days(context).keys.sorted()
        return "저장된 날 ${d.size}일" + (if (d.isNotEmpty()) " (${d.first()}~${d.last()})" else "") + " · 파일 ${f.length() / 1024}KB"
    }

    private fun load(context: Context): JSONObject {
        val f = File(context.filesDir, FILE)
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    private fun save(context: Context, json: JSONObject) {
        val f = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(f)
    }

    /** 하루 한 번 지난날을 저장한다(앱을 열지 않아도 사용 기록이 지워지기 전에 남기려고). */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private const val WORK_NAME = "usage_archive"

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            runCatching { sync(applicationContext) }
            return Result.success()
        }
    }
}
