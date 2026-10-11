package com.labok.goat.data.tweaks

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 충전 상한(배터리 보호): 삼성 설정 > 배터리 > 배터리 보호와 같은 숨은 설정을 바꾼다.
 * - [MODE]: 0 끔(100%까지), 1 최대(상한 [LIMIT]%에서 멈춤), 3 기본(100%까지 충전한 뒤 95% 근처로 유지).
 *   2는 디바이스 케어가 오래 충전할 때 스스로 거는 최대 보호라 GOAT가 고르지 않는다.
 * - [LIMIT]: 80·85·90·95(One UI 6.1 이후). 그 전 One UI는 85% 하나뿐이라 [LIMIT]가 없다.
 *
 * 삼성이 공개하지 않은 키라(오픈소스 앱 amply의 역공학이 유일한 근거, docs/research/galaxy-adb-tweaks.md) 기기마다
 * 어느 표(global·system·secure)에 있는지 확인하고, **이 기기에 키가 이미 있을 때만** 그 표에 쓴다. 키가 없는 기기에서는
 * 새로 만들지 않고 삼성 설정 화면으로 안내한다. 표와 상관없이 쓰려고 Shizuku 셸 `settings`를 쓴다.
 */
object ChargeLimit {
    const val MODE = "protect_battery"
    const val LIMIT = "battery_protection_threshold"
    val LIMITS = listOf(80, 85, 90, 95)
    private val TABLES = listOf("global", "system", "secure")

    enum class Choice(val label: String, val desc: String, val value: Int) {
        OFF(Copy.CHARGE_OFF, Copy.CHARGE_OFF_DESC, 0),
        BASIC(Copy.CHARGE_BASIC, Copy.CHARGE_BASIC_DESC, 3),
        MAXIMUM(Copy.CHARGE_MAX, Copy.CHARGE_MAX_DESC, 1),
    }

    /** 이 기기에서 찾은 설정: 어느 표에 있는지, 지금 값, 상한 키가 있는지. */
    data class Found(val table: String, val mode: Int, val limit: Int?)

    /** 찾은 결과. */
    sealed interface Lookup {
        /** Shizuku가 없어 찾지 못했다. */
        data object NoShizuku : Lookup
        /** 세 표 어디에도 키가 없다(삼성이 아니거나 이 기능이 없는 One UI). */
        data object Missing : Lookup
        data class Present(val found: Found) : Lookup
    }

    private val state = MutableStateFlow<Lookup?>(null)
    /** 마지막으로 찾은 결과(홈 카드가 쓴다). 아직 찾지 않았으면 null. */
    val status: StateFlow<Lookup?> = state.asStateFlow()

    /** 세 표에서 [MODE] 키를 찾는다(작업 스레드). */
    fun find(): Lookup {
        val r = lookup()
        state.value = r
        return r
    }

    private fun lookup(): Lookup {
        for (t in TABLES) {
            val m = ShizukuAccess.shell("settings get $t $MODE") ?: return Lookup.NoShizuku
            val mode = m.out.trim().toIntOrNull() ?: continue
            val limit = ShizukuAccess.shell("settings get $t $LIMIT")?.out?.trim()?.toIntOrNull()
            return Lookup.Present(Found(t, mode, limit))
        }
        return Lookup.Missing
    }

    /** 지금 방식(2 자동 최대도 최대로 본다). 0~3 밖의 값은 확인되지 않은 기기라 null로 단정하지 않는다. */
    fun choiceOf(mode: Int): Choice? = when (mode) {
        0 -> Choice.OFF
        3 -> Choice.BASIC
        1, 2 -> Choice.MAXIMUM
        else -> null
    }

    /**
     * 바꿀 셸 명령(시험할 수 있게 떼어 둔 판단). 상한은 최대일 때만, 이 기기에 상한 키가 있을 때만 쓴다.
     * [limit]은 [LIMITS] 안의 값으로 맞춘다.
     */
    internal fun plan(found: Found, choice: Choice, limit: Int): List<String> {
        val cmds = mutableListOf("settings put ${found.table} $MODE ${choice.value}")
        if (choice == Choice.MAXIMUM && found.limit != null) {
            cmds += "settings put ${found.table} $LIMIT ${nearest(limit)}"
        }
        return cmds
    }

    /** [LIMITS] 가운데 [limit]에 가장 가까운 값. */
    internal fun nearest(limit: Int): Int = LIMITS.minBy { kotlin.math.abs(it - limit) }

    /** 바꾼다(Shizuku, 작업 스레드). 키가 이 기기에 있을 때만 쓰고, 다시 읽어 확인한다. */
    fun set(context: Context, choice: Choice, limit: Int, how: String): Boolean {
        val log = LogRepository.from(context)
        val found = (find() as? Lookup.Present)?.found
        if (found == null) {
            log.add("충전 상한($how): 이 기기에서 배터리 보호 설정을 찾지 못해 바꾸지 않음(Shizuku 연결·키 확인)")
            return false
        }
        val r = ShizukuAccess.shell(plan(found, choice, limit).joinToString(" && "))
        val after = (find() as? Lookup.Present)?.found
        val ok = r?.ok == true && after != null && choiceOf(after.mode) == choice &&
            (choice != Choice.MAXIMUM || found.limit == null || after.limit == nearest(limit))
        log.add(
            "충전 상한($how): ${label(found)} → " + (after?.let { label(it) } ?: "읽지 못함") +
                " (${found.table})" + if (ok) "" else " 실패",
        )
        return ok
    }

    /** 로그·홈 카드용 이름(알 수 없는 값은 숫자 그대로). */
    fun label(f: Found): String = (choiceOf(f.mode)?.label ?: "값 ${f.mode}") + if (choiceOf(f.mode) == Choice.MAXIMUM && f.limit != null) " ${f.limit}%" else ""

    /**
     * 지금 배터리(권한 없이 읽는 마지막 방송). [held]는 충전기에 연결됐는데 충전하지 않는 상태로,
     * 상한에서 충전이 실제로 멈췄는지 눈으로 확인하는 데 쓴다.
     */
    data class Battery(val level: Int, val charging: Boolean, val held: Boolean, val tempC: Float?, val present: Boolean)

    fun battery(context: Context): Battery {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10f)
        val present = i?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return Battery(
            (level * 100 / scale).coerceIn(0, 100),
            status == BatteryManager.BATTERY_STATUS_CHARGING,
            plugged && status == BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            temp, present,
        )
    }

    /**
     * 배터리 설정을 여는 인텐트: 삼성 디바이스 케어의 배터리 화면을 먼저 찾고(버전마다 다를 수 있어 열 수 있는지 확인),
     * 없으면 안드로이드 배터리 설정을 연다. 둘 다 없으면 null.
     */
    fun settingsIntent(context: Context): Intent? = listOf(
        Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
    ).firstOrNull { it.resolveActivity(context.packageManager) != null }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
