package com.labok.goat.data.tweaks

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * 잠자기 모드(Doze) 끄기: 화면이 꺼진 채 가만히 두면 안드로이드가 앱의 백그라운드 동작·네트워크를 몰아서 늦게 처리하는
 * 잠자기 모드를 끈다(`adb shell dumpsys deviceidle disable`과 같다). 알림이 늦게 오는 일은 줄지만 대기 배터리 소모가 는다.
 *
 * `dumpsys deviceidle`은 셸 권한(DEVICE_POWER)이 필요해 Shizuku 셸로 부른다. 이 설정은 메모리에만 있어 재부팅하면
 * 다시 켜지므로, [wanted]면 부팅마다 한 번 Shizuku가 연결될 때 다시 끈다. 같은 부팅 안에서 앱 밖(adb 등)에서
 * 다시 켠 것은 덮어쓰지 않는다. 깊은 잠자기(deep)와 가벼운 잠자기(light)를 함께 끄고 켠다.
 */
object Doze {
    private const val PREF = "doze"

    enum class State {
        /** 잠자기 모드가 켜져 있다(안드로이드 기본). */
        ON,

        /** 잠자기 모드가 꺼져 있다(깊은·가벼운 잠자기 모두). */
        OFF,

        /** Shizuku가 없어 읽지 못했다. */
        UNKNOWN
    }

    private val state = MutableStateFlow(State.UNKNOWN)
    val status: StateFlow<State> = state.asStateFlow()

    /** 지금 상태를 읽는다(작업 스레드). */
    fun read(): State {
        val deep = ShizukuAccess.shell("dumpsys deviceidle enabled deep")
        val light = ShizukuAccess.shell("dumpsys deviceidle enabled light")
        return classify(deep?.takeIf { it.ok }?.out, light?.takeIf { it.ok }?.out).also { state.value = it }
    }

    /** [read]의 판정: 둘 중 하나라도 못 읽으면 UNKNOWN, 둘 다 꺼졌으면(0) OFF, 하나라도 켜져 있으면 ON. */
    internal fun classify(deep: String?, light: String?): State {
        val d = deep?.trim()?.lineSequence()?.lastOrNull()?.trim()
        val l = light?.trim()?.lineSequence()?.lastOrNull()?.trim()
        if (d !in setOf("0", "1") || l !in setOf("0", "1")) return State.UNKNOWN
        return if (d == "0" && l == "0") State.OFF else State.ON
    }

    /** 끄거나(off) 다시 켠다(작업 스레드). 바꾼 뒤 다시 읽어 확인하고, 성공하면 사용자의 뜻을 남긴다. */
    fun set(context: Context, off: Boolean, how: String): Boolean {
        val log = LogRepository.from(context)
        val r = ShizukuAccess.shell("dumpsys deviceidle ${if (off) "disable" else "enable"}")
        if (r == null) {
            log.add("잠자기 모드 ${if (off) "끄기" else "켜기"}($how): Shizuku가 연결되지 않아 바꾸지 못함")
            return false
        }
        val now = read()
        val ok = r.ok && now == if (off) State.OFF else State.ON
        if (ok) {
            prefs(context).edit().putBoolean("wanted", off).apply()
            markBoot(context)
        }
        log.add("잠자기 모드 ${if (off) "끄기" else "켜기"}($how)" + if (ok) "" else " 실패(${r.err.ifBlank { "결과 다름" }.take(80)})")
        return ok
    }

    /** 사용자가 잠자기 모드를 꺼 두기로 했는지(다시 끄기의 기준). */
    fun wanted(context: Context) = prefs(context).getBoolean("wanted", false)

    /** 다시 끄기에서 할 일. */
    internal enum class Action { SKIP, RETRY_LATER, MARK, DISABLE }

    /**
     * [reapply]의 판단(시험할 수 있게 뗐다). [boot]는 지금 부팅 횟수(없는 기기면 null: 그때는 연결될 때마다 확인),
     * [checkedBoot]는 마지막으로 확인한 부팅 횟수, [now]는 지금 상태.
     */
    internal fun reapplyAction(wanted: Boolean, boot: Int?, checkedBoot: Int?, now: State?): Action = when {
        !wanted -> Action.SKIP
        boot != null && checkedBoot == boot -> Action.SKIP
        now == null || now == State.UNKNOWN -> Action.RETRY_LATER
        now == State.ON -> Action.DISABLE
        else -> Action.MARK
    }

    /** 꺼 두기로 했는데 재부팅으로 다시 켜져 있으면 끈다(Shizuku 연결 때, 작업 스레드). 부팅마다 한 번만 확인한다. */
    fun reapply(context: Context) {
        val p = prefs(context)
        val boot = bootCount(context)
        val checked = p.getInt("checkedBoot", -1).takeIf { it >= 0 }
        if (reapplyAction(wanted(context), boot, checked, State.OFF) == Action.SKIP) return
        when (reapplyAction(wanted(context), boot, checked, read())) {
            Action.DISABLE -> set(context, off = true, how = "재부팅 뒤 다시 켜져서 자동으로 끄기")
            Action.MARK -> markBoot(context)
            else -> Unit
        }
    }

    private fun markBoot(context: Context) {
        bootCount(context)?.let { prefs(context).edit().putInt("checkedBoot", it).apply() }
    }

    private fun bootCount(context: Context): Int? = runCatching { android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrNull()

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }

    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다: Shizuku가 연결되는 순간(재부팅 뒤 자동 시작 포함) 다시 끄기를 확인한다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (!wanted(app)) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
