package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.labok.goat.data.log.LogRepository
import kotlin.math.abs

/**
 * 실험실: 배터리 모니터링. 지금 배터리 상태(잔량·충전·온도·전압·전류)를 읽는다. 권한 없이 시스템 배터리 방송
 * (ACTION_BATTERY_CHANGED)과 BatteryManager만 쓴다.
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

    /**
     * 예전 '15분 기록'(잔량 기록·충전 주기 자동 기록)을 정리한다. 시스템 배터리 기록([BatteryHistory])으로 대신해 옵션을 없앴다.
     * 켜 둔 적이 있으면 예약 작업과 저장한 잔량 기록을 지운다. 배터리 화면을 열 때 부른다.
     */
    fun retireLogging(context: Context) {
        val p = prefs(context)
        if (!p.contains("logging") && !p.contains("samples")) return
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        p.edit().remove("logging").remove("samples").apply()
        LogRepository.from(context).add("배터리 모니터링(실험실): 15분 기록 옵션을 없애 예약 작업과 잔량 기록을 지움")
    }

    /** 예전 15분 기록 작업. 이미 예약된 작업이 남아 있어도 아무 일도 하지 않고 끝나도록 클래스만 둔다. */
    class LogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = Result.success()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
