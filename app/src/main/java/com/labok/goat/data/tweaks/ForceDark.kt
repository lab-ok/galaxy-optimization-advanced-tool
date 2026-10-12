package com.labok.goat.data.tweaks

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * 앱 다크 모드 강제: 다크 모드를 지원하지 않는 앱도 시스템이 색을 바꿔 어둡게 그리게 한다.
 * 개발자 옵션 'Override force-dark'와 같은 시스템 속성 [PROP]를 Shizuku 셸 `setprop`으로 켠다.
 * 안드로이드는 **시스템 다크 모드가 켜져 있을 때만** 이 속성을 보고(ViewRootImpl.updateForceDarkMode), 화면이 다시 그려질 때
 * (앱을 다시 열거나 화면 설정이 바뀔 때) 적용한다. 그래서 켤 때 시스템 다크 모드가 꺼져 있으면 함께 켜고(`cmd uimode night yes`),
 * 그 전 방식(꺼짐·자동·예약과 예약 시각)을 남겨 끌 때 그대로 되돌린다. 안드로이드 16 이상은 같은 일을 하는 접근성 설정
 * [INVERT]('더 많은 앱을 어둡게')도 함께 켜고 끈다(그 기기에서 이 설정을 쓰는 앱이 많다).
 *
 * 시스템 속성은 재부팅하면 지워진다. [keep]이 켜져 있으면 부팅마다 한 번, Shizuku가 연결될 때 다시 켠다
 * (같은 부팅 안에서 개발자 옵션으로 끈 것은 덮어쓰지 않는다). 앱별로 고르는 공식 경로는 없어 모든 앱에 적용된다.
 */
object ForceDark {
    const val PROP = "debug.hwui.force_dark"
    /** 안드로이드 16 이상의 '더 많은 앱을 어둡게'(확장 다크 테마) 보안 설정. */
    const val INVERT = "accessibility_force_invert_color_enabled"
    private const val PREF = "force_dark"

    /** 읽은 상태. [forced]·[night]가 null이면 Shizuku가 없어 읽지 못했다. */
    data class State(val forced: Boolean?, val night: Boolean)

    private val state = MutableStateFlow(State(null, false))
    val status: StateFlow<State> = state.asStateFlow()

    /** 시스템 다크 모드가 켜져 있는지(권한 없이 읽는다). 자동(시간·일몰)이면 지금 화면 기준. */
    fun night(context: Context): Boolean {
        val ui = context.getSystemService(UiModeManager::class.java)
        return when (ui?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> true
            UiModeManager.MODE_NIGHT_NO -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** 강제 다크가 켜져 있는지(Shizuku, 작업 스레드). 못 읽으면 null. */
    private fun forced(): Boolean? {
        val r = ShizukuAccess.shell("getprop $PROP") ?: return null
        if (!r.ok) return null
        return r.out.trim() == "true"
    }

    fun read(context: Context): State = State(forced(), night(context)).also { state.value = it }

    /** 강제 다크를 켜거나 끈다(Shizuku, 작업 스레드). 시스템 다크 모드와 확장 다크 테마도 함께 맞추고, 다시 읽어 확인한다. */
    fun setForced(context: Context, on: Boolean, how: String): Boolean {
        val log = LogRepository.from(context)
        val p = prefs(context)
        val cmds = mutableListOf("setprop $PROP ${if (on) "true" else "false"}")
        if (Build.VERSION.SDK_INT >= 36) cmds += "settings put secure $INVERT ${if (on) 1 else 0}"
        if (on && !night(context)) {
            // 시스템 다크 모드가 꺼져 있으면 강제 다크가 보이지 않으므로 함께 켜고, 그 전 방식을 남긴다
            val ui = context.getSystemService(UiModeManager::class.java)
            val prev = ui?.nightMode ?: UiModeManager.MODE_NIGHT_NO
            p.edit().putInt("prevNight", prev).apply {
                if (Build.VERSION.SDK_INT >= 30 && prev == UiModeManager.MODE_NIGHT_CUSTOM) {
                    putString("prevStart", ui?.customNightModeStart?.toString()); putString("prevEnd", ui?.customNightModeEnd?.toString())
                }
            }.apply()
            cmds += "cmd uimode night yes"
        } else if (!on && p.contains("prevNight")) {
            // GOAT가 켰던 시스템 다크 모드는 그 전 방식으로 되돌린다
            cmds += restoreNightCommands(p.getInt("prevNight", UiModeManager.MODE_NIGHT_NO), p.getString("prevStart", null), p.getString("prevEnd", null))
            p.edit().remove("prevNight").remove("prevStart").remove("prevEnd").apply()
        }
        val r = ShizukuAccess.shell(cmds.joinToString(" ; "))
        val after = read(context).forced
        val ok = r?.ok == true && after == on
        // 바꾼 부팅을 확인한 것으로 남긴다: 같은 부팅 안에서 사용자가 개발자 옵션으로 되돌려도 다시 켜지 않는다
        if (ok) prefs(context).edit().putBoolean("wanted", on).apply { bootCount(context)?.let { putInt("checkedBoot", it) } }.apply()
        log.add(
            "앱 다크 모드 강제($how): ${if (on) "켜기" else "끄기"} " + (if (ok) "성공" else "실패(${r?.err?.ifBlank { null } ?: if (r == null) "Shizuku 없음" else "결과 다름"})") +
                " · 시스템 다크 모드 ${if (night(context)) "켜짐" else "꺼짐"}",
        )
        return ok
    }

    /** 시스템 다크 모드를 [mode](UiModeManager 값)로 되돌리는 명령. 예약이면 남긴 시각도 함께 건다(시험할 수 있게 뗐다). */
    internal fun restoreNightCommands(mode: Int, start: String?, end: String?): List<String> = when (mode) {
        UiModeManager.MODE_NIGHT_YES -> listOf("cmd uimode night yes")
        UiModeManager.MODE_NIGHT_AUTO -> listOf("cmd uimode night auto")
        UiModeManager.MODE_NIGHT_CUSTOM -> listOfNotNull(
            "cmd uimode night custom_schedule",
            start?.let { "cmd uimode time start ${it.take(5)}" },
            end?.let { "cmd uimode time end ${it.take(5)}" },
        )
        else -> listOf("cmd uimode night no")
    }

    fun wanted(context: Context) = prefs(context).getBoolean("wanted", false)

    /** 재부팅으로 꺼지면 다시 켠다. */
    fun keep(context: Context) = prefs(context).getBoolean("keep", true)

    fun setKeep(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("keep", on).apply()
        LogRepository.from(context).add("앱 다크 모드 강제: 재부팅 뒤 다시 켜기 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 다시 켜기 판단(시험할 수 있게 뗐다): 원하고, keep이고, 이번 부팅에 아직 확인하지 않았고, 지금 꺼져 있을 때만 켠다. */
    internal fun shouldReapply(wanted: Boolean, keep: Boolean, boot: Int?, checkedBoot: Int?, forced: Boolean?): Boolean? = when {
        !wanted || !keep -> false
        boot != null && boot == checkedBoot -> false
        forced == null -> null  // 못 읽음: 다음 연결 때 다시
        else -> !forced
    }

    /** 부팅마다 한 번, Shizuku가 연결될 때 다시 켠다(작업 스레드). */
    fun reapply(context: Context) {
        val p = prefs(context)
        val boot = bootCount(context)
        val checked = p.getInt("checkedBoot", -1).takeIf { it >= 0 }
        if (shouldReapply(wanted(context), keep(context), boot, checked, false) == false) return
        when (shouldReapply(wanted(context), keep(context), boot, checked, forced())) {
            true -> if (setForced(context, true, "재부팅 뒤 다시 켜기")) boot?.let { p.edit().putInt("checkedBoot", it).apply() }
            false -> boot?.let { p.edit().putInt("checkedBoot", it).apply() }
            null -> Unit
        }
    }

    /** 부팅 횟수(Settings.Global.BOOT_COUNT). 없는 기기면 null. */
    private fun bootCount(context: Context): Int? =
        runCatching { android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrNull()

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }
    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (!wanted(app) || !keep(app)) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
