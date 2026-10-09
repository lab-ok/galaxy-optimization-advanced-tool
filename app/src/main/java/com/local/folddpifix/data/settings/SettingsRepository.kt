package com.local.folddpifix.data.settings

import android.annotation.SuppressLint
import com.local.folddpifix.domain.DpiPolicy
import com.local.folddpifix.domain.ScreenInfo
import com.local.folddpifix.domain.ScreenPolicy
import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences를 감싼 최소 key-value 인터페이스. 테스트에서는 메모리 구현을 쓴다. */
interface KeyValueStore {
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun getString(key: String): String? = prefs.getString(key, null)
    // 문자열 값(학습 화면·외부 변경·적용 기록)은 BroadcastReceiver·Worker에서 쓰고 곧 프로세스가 끝날 수 있어 바로 디스크에 쓴다.
    @SuppressLint("ApplySharedPref")
    override fun putString(key: String, value: String?) { prefs.edit().putString(key, value).commit() }
}

data class LastApply(val success: Boolean, val timeMs: Long, val text: String)

/**
 * 사용자 설정. 이 규모에는 SharedPreferences면 충분하다.
 * Direct Boot(LOCKED_BOOT_COMPLETED)에서도 읽을 수 있도록 device-protected storage에 둔다.
 */
class SettingsRepository(private val store: KeyValueStore) {

    var targetDpi: Int?
        get() = store.getInt(KEY_TARGET)?.takeIf { DpiPolicy.isValid(it) }
        set(value) {
            requireNotNull(value)
            require(DpiPolicy.isValid(value)) { "허용 범위를 벗어난 DPI: $value" }
            store.putInt(KEY_TARGET, value)
        }

    /** 자동 복구 전체 스위치. 끄면 부팅·접기/펼치기 어떤 자동 동작도 하지 않는다. */
    var autoRecover: Boolean
        get() = store.getBoolean(KEY_AUTO, true)
        set(v) = store.putBoolean(KEY_AUTO, v)

    var applyOnBoot: Boolean
        get() = store.getBoolean(KEY_BOOT, true)
        set(v) = store.putBoolean(KEY_BOOT, v)

    /** 접기/펼치기 감시(foreground service). 화면마다 다른 보정 DPI를 적용하려면 필요해 기본은 켬. */
    var watchFold: Boolean
        get() = store.getBoolean(KEY_FOLD, true)
        set(v) = store.putBoolean(KEY_FOLD, v)

    /** 목표 DPI를 정하는 기준 화면. 반대쪽 화면은 PPI 비율로 보정한 DPI를 쓴다. */
    var reference: ScreenPolicy.Role
        get() = store.getString(KEY_REFERENCE)?.let { runCatching { ScreenPolicy.Role.valueOf(it) }.getOrNull() }
            ?: ScreenPolicy.Role.OUTER
        set(v) = store.putString(KEY_REFERENCE, v.name)

    /** 지금까지 본 물리 화면(패널) 정보. */
    var screens: List<ScreenInfo>
        get() = ScreenPolicy.decode(store.getString(KEY_SCREENS))
        set(v) = store.putString(KEY_SCREENS, ScreenPolicy.encode(v))

    /** 화면 정보를 기억한다. 새로 추가되거나 값이 바뀌었으면 true. */
    fun rememberScreen(s: ScreenInfo): Boolean {
        val current = screens
        if (current.contains(s)) return false
        screens = ScreenPolicy.remember(current, s)
        return true
    }

    /** 학습한 화면 정보를 지운다(설정이 꼬였을 때 복구용). 기준 밀도와 스위치는 유지한다. */
    fun clearLearnedScreens() {
        screens = emptyList()
        inactivePpi = emptyMap()
        store.putString(KEY_DIAGONALS, null)
    }

    /**
     * 화면(해상도 key)이 꺼져 있을 때 그 디스플레이 ID가 보고한 PPI.
     * 실기기(SM-F971N)에서 켜진 디스플레이는 두 화면 모두 같은 PPI(428.2)를 보고했고,
     * 꺼진 내부 디스플레이는 실제 크기에 맞는 404.2(7.59")를 보고했다.
     */
    var inactivePpi: Map<String, Float>
        get() = store.getString(KEY_INACTIVE_PPI).orEmpty().split(';').mapNotNull { part ->
            val kv = part.split('=')
            if (kv.size != 2) return@mapNotNull null
            kv[1].toFloatOrNull()?.let { kv[0] to it }
        }.toMap()
        set(v) = store.putString(KEY_INACTIVE_PPI, v.entries.joinToString(";") { "${it.key}=${it.value}" })

    /** 새 값이면 저장하고 true. */
    fun rememberInactivePpi(key: String, ppi: Float): Boolean {
        val cur = inactivePpi
        if (cur[key]?.let { kotlin.math.abs(it - ppi) < 0.05f } == true) return false
        inactivePpi = cur + (key to ppi)
        return true
    }

    /** 이전 버전의 대각선 직접 입력값(기능 제거됨). 남아 있으면 지운다. */
    fun clearLegacyDiagonalInput(): Boolean {
        if (store.getString(KEY_DIAGONALS).isNullOrEmpty()) return false
        store.putString(KEY_DIAGONALS, null)
        return true
    }

    /** 보정 대상 화면에 더할 미세조정(dpi). */
    var adjust: Int
        get() = store.getInt(KEY_ADJUST) ?: 0
        set(v) = store.putInt(KEY_ADJUST, v.coerceIn(-MAX_ADJUST, MAX_ADJUST))

    var applyTimes: List<Long>
        get() = store.getString(KEY_APPLY_TIMES).orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        set(v) = store.putString(KEY_APPLY_TIMES, v.joinToString(","))

    /** 화면(key)마다 앱이 마지막으로 적용·확인한 DPI. 외부 변경을 알아보는 기준. */
    var appliedDpi: Map<String, Int>
        get() = store.getString(KEY_APPLIED).orEmpty().split(';').mapNotNull { part ->
            val kv = part.split('=')
            if (kv.size != 2) return@mapNotNull null
            kv[1].toIntOrNull()?.let { kv[0] to it }
        }.toMap()
        set(v) = store.putString(KEY_APPLIED, v.entries.joinToString(";") { "${it.key}=${it.value}" })

    fun rememberApplied(key: String, dpi: Int) {
        if (appliedDpi[key] != dpi) appliedDpi = appliedDpi + (key to dpi)
    }

    /** 크기 테스트의 자를 카드로 맞춘 실제 PPI(화면 key별). DPI 계산에는 쓰지 않고 실측 표시에만 쓴다. */
    var rulerPpi: Map<String, Float>
        get() = store.getString(KEY_RULER_PPI).orEmpty().split(';').mapNotNull { part ->
            val kv = part.split('=')
            if (kv.size != 2) return@mapNotNull null
            kv[1].toFloatOrNull()?.let { kv[0] to it }
        }.toMap()
        set(v) = store.putString(KEY_RULER_PPI, v.entries.joinToString(";") { "${it.key}=${it.value}" })

    /** 사용자 선택을 기다리는 외부 변경. */
    var externalChange: com.local.folddpifix.domain.ExternalChange?
        get() = com.local.folddpifix.domain.ExternalChange.parse(store.getString(KEY_EXTERNAL))
        set(v) = store.putString(KEY_EXTERNAL, v?.serialize())

    var lastApply: LastApply?
        get() {
            val raw = store.getString(KEY_LAST) ?: return null
            val parts = raw.split('|', limit = 3)
            if (parts.size != 3) return null
            return LastApply(parts[0] == "1", parts[1].toLongOrNull() ?: return null, parts[2])
        }
        set(v) = store.putString(KEY_LAST, v?.let { "${if (it.success) 1 else 0}|${it.timeMs}|${it.text}" })

    companion object {
        private const val KEY_TARGET = "target_dpi"
        private const val KEY_AUTO = "auto_recover"
        private const val KEY_BOOT = "apply_on_boot"
        private const val KEY_FOLD = "watch_fold"
        private const val KEY_APPLY_TIMES = "apply_times"
        private const val KEY_LAST = "last_apply"
        private const val KEY_REFERENCE = "reference_screen"
        private const val KEY_SCREENS = "screens"
        private const val KEY_DIAGONALS = "diagonals"
        private const val KEY_INACTIVE_PPI = "inactive_ppi"
        private const val KEY_ADJUST = "adjust"
        private const val KEY_APPLIED = "applied_dpi"
        private const val KEY_EXTERNAL = "external_change"
        private const val KEY_RULER_PPI = "ruler_ppi"
        const val MAX_ADJUST = 20

        fun from(context: Context): SettingsRepository =
            SettingsRepository(PrefsStore(Storage.prefs(context)))
    }
}

object Storage {
    private fun dpContext(context: Context): Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    fun prefs(context: Context): SharedPreferences =
        dpContext(context).getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun logFile(context: Context) = java.io.File(dpContext(context).filesDir, "dpi_log.txt")

    fun crashFile(context: Context) = java.io.File(dpContext(context).filesDir, "crash_log.txt")

    /** 잠금 해제 전에 쌓아 둔, 공용 로그 파일로 옮길 내용. */
    fun pendingPublicLog(context: Context) = java.io.File(dpContext(context).filesDir, "pending_public.log")
}
