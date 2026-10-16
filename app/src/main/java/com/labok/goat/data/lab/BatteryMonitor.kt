package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.labok.goat.data.log.LogRepository
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 실험실: 배터리 모니터링. 지금 배터리 상태(잔량·충전·온도·전압·전류)를 읽고, 켜 두면 15분마다 잔량을 기록해
 * 하루 동안의 변화와 시간당 소모량을 보여 준다. 권한 없이 시스템 배터리 방송(ACTION_BATTERY_CHANGED)과
 * BatteryManager만 쓴다. 기록은 기기 안(앱 설정 저장소)에만 둔다.
 */
object BatteryMonitor {
    private const val PREF = "battery_monitor"
    private const val WORK = "battery_monitor_log"

    /** 한 번 읽은 배터리 상태. 읽지 못한 값은 null. */
    data class Snapshot(
        val time: Long,
        /** 잔량(%) */
        val level: Int,
        val charging: Boolean,
        /** 충전 방식: AC·USB·무선, 꽂혀 있지 않으면 null */
        val plug: String?,
        /** 온도(°C) */
        val tempC: Float?,
        /** 전압(mV) */
        val voltageMv: Int?,
        /** 전류(mA). 충전 중이면 +, 쓰는 중이면 -(기기마다 부호가 달라 충전 상태로 맞춘다) */
        val currentMa: Int?,
        /** 건강 상태 문구 */
        val health: String?
    ) {
        /** 순간 전력(W). 전압·전류를 모두 읽었을 때만. */
        val watts: Float? get() = if (voltageMv != null && currentMa != null) voltageMv * currentMa / 1_000_000f else null
    }

    /** 기록 한 줄: 시각·잔량·충전 중. */
    data class Sample(val time: Long, val level: Int, val charging: Boolean)

    fun read(context: Context): Snapshot? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "충전기"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "무선"
            0 -> null
            else -> "기타"
        }
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
        // 배터리가 없는 기기(에뮬레이터 등)는 전압 없이 온도 0을 준다. 그때는 읽지 못한 것으로 본다
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && (it != 0 || volt != null) }?.let { it / 10f }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "좋음"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "과열"
            BatteryManager.BATTERY_HEALTH_COLD -> "저온"
            BatteryManager.BATTERY_HEALTH_DEAD -> "수명 다함"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "과전압"
            else -> null
        }
        return Snapshot(System.currentTimeMillis(), level * 100 / scale, charging, plug, temp, volt, currentMa(context, charging), health)
    }

    /**
     * 순간 전류(mA). BATTERY_PROPERTY_CURRENT_NOW는 µA로 정해져 있지만 삼성 등 일부 기기는 mA로 준다.
     * 절댓값이 10,000을 넘으면 µA로 보고 1,000으로 나눈다(폰 전류가 10A를 넘을 일은 없다). 부호는 충전 상태에 맞춘다.
     */
    internal fun normalizeCurrent(raw: Int, charging: Boolean): Int? {
        if (raw == 0 || raw == Int.MIN_VALUE) return null
        val ma = if (abs(raw) > 10_000) raw / 1000 else raw
        return if (charging) abs(ma) else -abs(ma)
    }

    private fun currentMa(context: Context, charging: Boolean): Int? {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        return normalizeCurrent(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), charging)
    }

    // 기록

    fun logging(context: Context) = prefs(context).getBoolean("logging", false)

    fun setLogging(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("logging", on).apply()
        LogRepository.from(context).add("배터리 모니터링(실험실): 15분 기록 ${if (on) "켜짐" else "꺼짐"}")
        sync(context)
        if (on) record(context)
    }

    /** 기록 켜짐에 맞춰 15분 작업을 걸거나 지운다. */
    fun sync(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (logging(context)) {
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<LogWorker>(15, TimeUnit.MINUTES).build())
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** 지금 상태를 한 줄 기록한다. 기록이 꺼져 있으면 하지 않는다. 같은 분에 두 번 기록하지 않는다. */
    fun record(context: Context) {
        if (!logging(context)) return
        val s = read(context) ?: return
        val list = samples(context)
        if (list.lastOrNull()?.let { s.time - it.time < 60_000 } == true) return
        save(context, trim(list + Sample(s.time, s.level, s.charging), s.time))
    }

    fun samples(context: Context): List<Sample> = decode(prefs(context).getString("samples", null))

    fun clear(context: Context) {
        prefs(context).edit().remove("samples").apply()
        LogRepository.from(context).add("배터리 모니터링(실험실): 기록 지움")
    }

    private fun save(context: Context, list: List<Sample>) = prefs(context).edit().putString("samples", encode(list)).apply()

    /** [KEEP_MS](48시간)보다 오래된 기록은 버린다. */
    internal fun trim(list: List<Sample>, now: Long) = list.filter { now - it.time <= KEEP_MS }

    internal fun encode(list: List<Sample>) = list.joinToString(";") { "${it.time},${it.level},${if (it.charging) 1 else 0}" }

    internal fun decode(raw: String?): List<Sample> = raw.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(',')
        if (f.size != 3) return@mapNotNull null
        Sample(f[0].toLongOrNull() ?: return@mapNotNull null, f[1].toIntOrNull() ?: return@mapNotNull null, f[2] == "1")
    }

    /**
     * 시간당 소모량(%/시간). 충전하지 않은 연속 구간들의 (잔량 감소 합) ÷ (시간 합). 최근 [windowMs] 안의 기록만 쓴다.
     * 쓸 수 있는 구간이 30분이 안 되면 null.
     */
    internal fun drainPerHour(list: List<Sample>, now: Long, windowMs: Long = DAY_MS): Float? {
        val recent = list.filter { now - it.time <= windowMs }.sortedBy { it.time }
        var drop = 0
        var span = 0L
        recent.zipWithNext().forEach { (a, b) ->
            // 두 기록 사이에 충전이 끼었거나 잔량이 올랐으면(충전) 그 구간은 뺀다. 기록이 2시간 넘게 비면(기기 꺼짐 등) 뺀다
            if (a.charging || b.charging || b.level > a.level || b.time - a.time > 2 * HOUR_MS) return@forEach
            drop += a.level - b.level
            span += b.time - a.time
        }
        if (span < 30 * 60_000L) return null
        return drop * HOUR_MS.toFloat() / span
    }

    /**
     * 시간대 한 칸의 사용량. [usedPct]는 그 칸에서 줄어든 잔량(%), [charging]은 그 칸에 충전한 구간이 있었는지,
     * [recorded]는 그 칸에 기록이 있었는지(기록이 없으면 사용량을 알 수 없다).
     */
    data class Slot(val start: Long, val end: Long, val usedPct: Float, val charging: Boolean, val recorded: Boolean)

    /** [now]가 들어 있는 2시간 칸의 끝 시각. 기기 시간대 기준 짝수 정시(0·2·4…시)에 맞춘다(안드로이드 배터리 화면과 같은 칸). */
    internal fun slotEnd(now: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        val offset = zone.getOffset(now)
        val local = now + offset
        return (local / SLOT_MS + 1) * SLOT_MS - offset
    }

    /**
     * 최근 24시간을 2시간씩 12칸으로 나눠 칸마다 줄어든 잔량을 더한다(왼쪽이 오래된 칸). 두 기록 사이에서 줄어든 잔량은
     * 두 기록이 걸친 칸들에 시간 비율대로 나눈다. 충전이 낀 구간은 사용량에 넣지 않고 그 칸을 충전으로 표시한다.
     * 기록이 2시간 넘게 비면(기기 꺼짐 등) 그 사이는 기록 없음으로 둔다.
     */
    internal fun slots(list: List<Sample>, end: Long, count: Int = 12): List<Slot> {
        val start = end - count * SLOT_MS
        val used = FloatArray(count)
        val charging = BooleanArray(count)
        val recorded = BooleanArray(count)
        val sorted = list.sortedBy { it.time }
        sorted.forEach { s -> ((s.time - start) / SLOT_MS).toInt().takeIf { s.time >= start && it in 0 until count }?.let { recorded[it] = true } }
        sorted.zipWithNext().forEach { (a, b) ->
            if (b.time - a.time > 2 * HOUR_MS || b.time <= start || a.time >= end) return@forEach
            val charge = a.charging || b.charging || b.level > a.level
            val drop = (a.level - b.level).coerceAtLeast(0)
            val span = (b.time - a.time).toFloat()
            for (i in 0 until count) {
                val s0 = start + i * SLOT_MS
                val overlap = minOf(b.time, s0 + SLOT_MS) - maxOf(a.time, s0)
                if (overlap <= 0) continue
                recorded[i] = true
                if (charge) charging[i] = true else used[i] += drop * overlap / span
            }
        }
        return List(count) { i -> Slot(start + i * SLOT_MS, start + (i + 1) * SLOT_MS, used[i], charging[i], recorded[i]) }
    }

    class LogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            record(applicationContext)
            // 충전 주기 기록: 시스템 통계를 읽어 넘긴다(Shizuku가 없으면 건너뛴다)
            BatteryStatsReader.read()?.let { s -> BatteryCycles.update(applicationContext, s) { BatteryStatsReader.uidName(applicationContext, it) } }
            return Result.success()
        }
    }

    internal const val HOUR_MS = 3_600_000L
    internal const val DAY_MS = 24 * HOUR_MS
    internal const val SLOT_MS = 2 * HOUR_MS
    private const val KEEP_MS = 48 * HOUR_MS

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
