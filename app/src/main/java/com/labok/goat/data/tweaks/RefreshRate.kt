package com.labok.goat.data.tweaks

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.text.Copy
import kotlin.math.roundToInt

/**
 * 주사율 고정: 화면이 1초에 몇 번 새로 그려지는지(Hz)의 위·아래 한계를 정한다.
 * - 안드로이드 공통 설정 [PEAK](최고)·[MIN](최저)을 같은 값으로 두면 그 주사율로 고정된다(AOSP DisplayModeDirector가 투표로 쓴다).
 * - 삼성 '화면 움직임 부드럽게'는 [MODE](0 표준 60Hz, 1 적응형)를 따로 읽으므로 삼성 기기에서만 함께 맞춘다.
 *
 * 최고·최저는 공개되지 않은 시스템 설정이라 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로는 막히는 기기가 많아
 * (에뮬레이터 Android 15에서 확인) Shizuku 셸 `settings put`으로 한 번에 쓴다. 처음 바꾸기 전 값(없던 키는 없었다는 사실까지)을
 * 남겨 '시스템 기본'을 고르면 그대로 되돌린다(없던 키는 `settings delete`). 앱 밖에서 바꾼 값은 존중해 다시 덮어쓰지 않는다.
 */
object RefreshRate {
    const val PEAK = "peak_refresh_rate"
    const val MIN = "min_refresh_rate"
    const val MODE = "refresh_rate_mode"
    private const val PREF = "refresh_rate"
    private val lock = Any()

    /** 고를 수 있는 방식. */
    enum class Choice(val label: String, val desc: String) {
        SYSTEM(Copy.REFRESH_SYSTEM, Copy.REFRESH_SYSTEM_DESC),
        SMOOTH(Copy.REFRESH_SMOOTH, Copy.REFRESH_SMOOTH_DESC),
        SAVER(Copy.REFRESH_SAVER, Copy.REFRESH_SAVER_DESC),
    }

    /** 이 기기 기본 화면이 지원하는 주사율(정수 Hz, 오름차순). */
    fun rates(context: Context): List<Int> {
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        return d?.supportedModes?.map { it.refreshRate.roundToInt() }?.distinct()?.sorted().orEmpty()
    }

    /** 최고 주사율(바꿀 수 있는 기기인지는 이 값이 60보다 큰지로 본다). */
    fun max(context: Context) = rates(context).maxOrNull() ?: 60

    fun supported(context: Context) = max(context) > 60

    /** 지금 화면이 실제로 그리는 주사율(Hz). */
    fun now(context: Context): Int =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60

    /** 설정에 적힌 값(키가 없으면 null). */
    data class Values(val peak: Float?, val min: Float?, val mode: Int?)

    fun values(context: Context): Values {
        val r = context.contentResolver
        return Values(
            runCatching { Settings.System.getString(r, PEAK)?.toFloat() }.getOrNull(),
            runCatching { Settings.System.getString(r, MIN)?.toFloat() }.getOrNull(),
            runCatching { Settings.Secure.getString(r, MODE)?.toInt() }.getOrNull(),
        )
    }

    fun read(context: Context): Choice = classify(values(context), max(context))

    /** [read]의 판정(시험할 수 있게 시스템과 뗐다). [top]은 기기 최고 주사율. */
    internal fun classify(v: Values, top: Int): Choice = when {
        (v.min ?: 0f) >= top - 0.5f -> Choice.SMOOTH
        v.peak != null && v.peak <= 60.5f -> Choice.SAVER
        else -> Choice.SYSTEM
    }

    /**
     * [choice]로 가려면 쓸 셸 명령들(시험할 수 있게 떼어 둔 판단). [original]은 처음 바꾸기 전 값으로, SYSTEM일 때
     * 그대로 되돌리고 없던 키는 지운다. [samsung]이 아니면 삼성 전용 [MODE]는 건드리지 않는다.
     * SYSTEM인데 처음 값이 없으면(GOAT로 바꾼 적 없음) 아무것도 하지 않는다(빈 목록).
     */
    internal fun plan(choice: Choice, top: Int, samsung: Boolean, original: Values?): List<String> {
        fun put(table: String, key: String, v: Any) = "settings put $table $key $v"
        fun del(table: String, key: String) = "settings delete $table $key"
        return when (choice) {
            Choice.SMOOTH -> listOfNotNull(put("system", PEAK, top.toFloat()), put("system", MIN, top.toFloat()), if (samsung) put("secure", MODE, 1) else null)
            Choice.SAVER -> listOfNotNull(put("system", PEAK, 60f), put("system", MIN, 60f), if (samsung) put("secure", MODE, 0) else null)
            Choice.SYSTEM -> if (original == null) emptyList() else listOfNotNull(
                original.peak?.let { put("system", PEAK, it) } ?: del("system", PEAK),
                original.min?.let { put("system", MIN, it) } ?: del("system", MIN),
                if (samsung) original.mode?.let { put("secure", MODE, it) } ?: del("secure", MODE) else null,
            )
        }
    }

    /** [choice]로 바꾼다(Shizuku 셸, 작업 스레드). 쓰고 나서 다시 읽어 확인한다. 성공하면 true. */
    fun set(context: Context, choice: Choice, how: String): Boolean = synchronized(lock) {
        val log = LogRepository.from(context)
        val before = read(context)
        val top = max(context)
        if (choice != Choice.SYSTEM && !supported(context)) {
            log.add("주사율 고정($how): 이 기기는 최고 ${top}Hz라 바꾸지 않음")
            return false
        }
        if (choice != Choice.SYSTEM) remember(context)
        val cmds = plan(choice, top, isSamsung(), saved(context))
        if (cmds.isEmpty()) {
            log.add("주사율 고정($how): ${before.label} 그대로(GOAT로 바꾼 적 없음)")
            return true
        }
        // 셋을 한 번에 쓴다: 중간에 실패하면 &&로 멈추고, 결과는 아래에서 다시 읽어 판단한다
        val r = ShizukuAccess.shell(cmds.joinToString(" && "))
        val after = read(context)
        val ok = r?.ok == true && after == choice
        // 시스템 기본으로 돌아갔으면 처음 값 기억을 지운다(다음 고정 때 그때 값을 새로 남긴다)
        if (choice == Choice.SYSTEM && ok) prefs(context).edit().clear().apply()
        log.add("주사율 고정($how): ${before.label} → ${after.label}" + when {
            r == null -> " 실패(Shizuku 연결 안 됨)"
            !ok -> " 실패(${r.err.ifBlank { "결과 다름" }.take(80)})"
            else -> ""
        })
        return ok
    }

    /** GOAT로 처음 바꾸기 전 값(없으면 null). 키가 없었으면 그 칸이 null이다. */
    internal fun saved(context: Context): Values? {
        val p = prefs(context)
        if (!p.getBoolean("saved", false)) return null
        return Values(
            if (p.contains("peak")) p.getFloat("peak", 0f) else null,
            if (p.contains("min")) p.getFloat("min", 0f) else null,
            if (p.contains("mode")) p.getInt("mode", 0) else null,
        )
    }

    private fun remember(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("saved", false)) return
        val v = values(context)
        p.edit().apply {
            putBoolean("saved", true)
            v.peak?.let { putFloat("peak", it) }
            v.min?.let { putFloat("min", it) }
            v.mode?.let { putInt("mode", it) }
        }.apply()
    }

    private fun isSamsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
