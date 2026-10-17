package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 배터리 수명: 설계 용량 대비 지금 실제로 담을 수 있는 용량(%). 흔히 말하는 배터리 수명(웨어율의 반대)이다.
 * 실제 용량은 다음 순서로 구한다(앞의 것이 정확하다).
 * 1) 커널 값: /sys/class/power_supply/battery/charge_full(지금 완충 용량)·charge_full_design(설계 용량), µAh. 기기에 따라 막혀 있다.
 * 2) 삼성 값: `dumpsys battery`의 mSavedBatteryAsoc(새 배터리 대비 용량 %) × 설계 용량.
 * 3) 추정: 지금 남은 전하량(BATTERY_PROPERTY_CHARGE_COUNTER, µAh) ÷ 잔량(%). 잔량이 낮으면 오차가 커서 30% 이상일 때만 쓴다.
 * 설계 용량은 커널 값이 없으면 기기 전력 프로필(`dumpsys batterystats --usage`의 Capacity)을 쓴다.
 * 충전 횟수는 삼성 mSavedBatteryUsage ÷ 100(완충 환산), 없으면 안드로이드 14 이상 배터리 방송의 충전 횟수.
 */
object BatteryHealth {
    enum class Source(val label: String) { KERNEL("시스템 측정값"), SAMSUNG("삼성 수명값으로 계산"), ESTIMATE("남은 전하량으로 추정") }

    data class Info(
        /** 지금 완충 시 담을 수 있는 용량(mAh), 모르면 null */
        val actualMah: Int?,
        /** 설계 용량(mAh), 모르면 null */
        val designMah: Int?,
        val source: Source?,
        /** 완충 환산 충전 횟수, 모르면 null */
        val cycles: Float?
    ) {
        /** 설계 대비 실제 용량(%). 둘 다 알 때만. */
        val percent: Int? get() = if (actualMah != null && designMah != null && designMah > 0) (actualMah * 100f / designMah).toInt() else null
        val empty: Boolean get() = percent == null && cycles == null
    }

    /** 작업 스레드에서 부른다. */
    fun read(context: Context): Info {
        val sh = ShizukuAccess.shell(
            "cat /sys/class/power_supply/battery/charge_full /sys/class/power_supply/battery/charge_full_design 2>/dev/null | tr '\\n' ' '; echo; " +
                "dumpsys battery | grep -E 'mSavedBatteryAsoc|mSavedBatteryUsage'; " +
                "dumpsys batterystats --usage | grep -m1 'Capacity:'"
        )?.out.orEmpty()
        val bm = context.getSystemService(BatteryManager::class.java)
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
        val broadcastCycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
        return compute(sh, counter, level, broadcastCycles)
    }

    /** [shell]은 read()의 셸 출력, [chargeCounterUah]·[level]은 BatteryManager 값. */
    internal fun compute(shell: String, chargeCounterUah: Int?, level: Int?, broadcastCycles: Int?): Info {
        fun num(key: String) = Regex("""$key\s*[:=]\s*(-?\d+)""").find(shell)?.groupValues?.get(1)?.toLongOrNull()
        val kernel = shell.lineSequence().firstOrNull()?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }.orEmpty()
        val profileMah = num("Capacity")?.toInt()?.takeIf { it in 500..20_000 }
        val asoc = num("mSavedBatteryAsoc")?.toInt()?.takeIf { it in 1..150 }
        val usage = num("mSavedBatteryUsage")?.takeIf { it > 0 }
        val cycles = usage?.let { it / 100f } ?: broadcastCycles?.toFloat()

        // 1) 커널 값: 두 값이 다 있고, 설계 값과 똑같지 않으며(같으면 기기가 설계 값을 두 번 넣은 것), 설계의 30~110% 안일 때만
        if (kernel.size >= 2) {
            val full = (kernel[0] / 1000).toInt()
            val design = (kernel[1] / 1000).toInt()
            if (design in 500..20_000 && full != design && full in design * 3 / 10..design * 11 / 10) {
                return Info(full, design, Source.KERNEL, cycles)
            }
        }
        val design = profileMah ?: (kernel.getOrNull(1)?.div(1000)?.toInt()?.takeIf { it in 500..20_000 })
        // 2) 삼성 수명값 × 설계 용량
        if (asoc != null && design != null) return Info(design * asoc / 100, design, Source.SAMSUNG, cycles)
        // 3) 남은 전하량 ÷ 잔량
        if (chargeCounterUah != null && level != null && level >= 30 && design != null) {
            val full = (chargeCounterUah / 1000f / (level / 100f)).toInt()
            if (full in design * 3 / 10..design * 12 / 10) return Info(full, design, Source.ESTIMATE, cycles)
        }
        return Info(null, design, null, cycles)
    }
}
