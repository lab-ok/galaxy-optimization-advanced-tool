package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 배터리 수명(웨어율). 삼성은 설정 화면에 보여 주지 않지만 `dumpsys battery`(셸 권한)에 값을 남긴다.
 * - mSavedBatteryAsoc: 새 배터리 대비 지금 담을 수 있는 용량(%). 흔히 말하는 배터리 수명이다.
 * - mSavedBatteryBsoh: 삼성이 따로 계산하는 건강 지수(%). 뜻이 공개되지 않아 참고값으로만 쓴다.
 * - mSavedBatteryUsage: 누적 충전량을 100으로 나누면 완충 환산 충전 횟수다(33248 → 332.48회).
 * 삼성 값이 없으면 안드로이드 14 이상 공통 방송의 충전 횟수(EXTRA_CYCLE_COUNT)만 보여 준다.
 */
object BatteryHealth {
    data class Info(
        /** 새 배터리 대비 용량(%), 모르면 null */
        val asoc: Int?,
        /** 삼성 건강 지수(%), 모르면 null */
        val bsoh: Int?,
        /** 완충 환산 충전 횟수, 모르면 null */
        val cycles: Float?
    ) {
        val empty: Boolean get() = asoc == null && bsoh == null && cycles == null
    }

    /** 작업 스레드에서 부른다. 셸이 없으면 공통 방송 값만 쓴다. */
    fun read(context: Context): Info {
        val out = ShizukuAccess.shell("dumpsys battery")?.out.orEmpty()
        val parsed = parse(out)
        if (parsed.cycles != null) return parsed
        // 안드로이드 14 이상: 충전 횟수는 권한 없이 배터리 방송에 실린다(삼성은 값을 안 넣는 경우가 많다)
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val cycle = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
        return parsed.copy(cycles = cycle?.toFloat())
    }

    internal fun parse(out: String): Info {
        fun num(key: String) = Regex("""$key\s*[:=]\s*(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
        val asoc = num("mSavedBatteryAsoc")?.takeIf { it in 1..150 }
        val bsoh = num("mSavedBatteryBsoh")?.takeIf { it in 1..150 }
        val usage = num("mSavedBatteryUsage")?.takeIf { it > 0 }
        return Info(asoc, bsoh, usage?.let { it / 100f })
    }
}
