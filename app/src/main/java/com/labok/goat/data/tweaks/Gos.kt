package com.labok.goat.data.tweaks

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * 게임 최적화 서비스(GOS) 끄기: 게임 중 해상도·프레임·CPU/GPU 성능을 낮춰 발열을 줄이는 삼성 서비스를 멈춘다.
 * 끄는 방법은 기본 앱 정리와 같은 '끄기'(pm disable-user, Shizuku)라서 [Bloat.set]으로 끄고 켠다. 그래서 두 화면은
 * 같은 패키지 상태를 읽고, 어느 화면에서 바꾸든 [Bloat.set]이 [noteChange]로 사용자의 뜻(끄기를 원하는지)을 여기 남긴다.
 *
 * GOS는 재부팅·업데이트 뒤 다시 켜지는 기기가 있다는 보고가 있어, [keep]이 켜져 있으면 부팅마다 한 번, Shizuku가 연결될 때
 * 다시 끈다. 같은 부팅 안에서 사용자가 앱 밖(설정 앱 등)에서 다시 켠 것은 덮어쓰지 않는다.
 * 게임 런처·게임 부스터(gamehome·gametools)는 건드리지 않는다.
 */
object Gos {
    /** 기종·버전에 따른 GOS 패키지 이름(최신이 앞). */
    val PACKAGES = listOf("com.samsung.android.game.gos", "com.enhance.gameservice")
    private const val PREF = "gos"

    enum class State {
        /** 켜져 있다(성능 제한이 걸릴 수 있다). */
        ON,
        /** 꺼져 있다. */
        OFF,
        /** 이 기기에 GOS가 없다(삼성이 아니거나 오래된 기기). */
        NONE,
        /** Shizuku가 없어 읽지 못했다. */
        UNKNOWN,
    }

    private val state = MutableStateFlow(State.UNKNOWN)
    val status: StateFlow<State> = state.asStateFlow()

    /** 이 기기에 깔린 GOS 패키지와 꺼져 있는지(작업 스레드). Shizuku가 없거나 목록을 읽지 못하면 null. */
    private fun scan(): Map<String, Boolean>? {
        val all = ShizukuAccess.shell("pm list packages --user 0") ?: return null
        if (!all.ok) return null
        val off = ShizukuAccess.shell("pm list packages -d --user 0") ?: return null
        if (!off.ok) return null
        fun names(text: String) = text.lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val installed = names(all.out)
        val disabled = names(off.out)
        return PACKAGES.filter { it in installed }.associateWith { it in disabled }
    }

    /** 지금 상태를 읽는다(작업 스레드). */
    fun read(): State = classify(scan()).also { state.value = it }

    /** [read]의 판정: 못 읽었으면 UNKNOWN, 깔린 GOS가 없으면 NONE, 모두 꺼졌으면 OFF, 하나라도 켜져 있으면 ON. */
    internal fun classify(found: Map<String, Boolean>?): State = when {
        found == null -> State.UNKNOWN
        found.isEmpty() -> State.NONE
        found.values.all { it } -> State.OFF
        else -> State.ON
    }

    /** 끄거나(off) 다시 켠다(작업 스레드). 깔린 GOS 패키지를 모두 같은 상태로 맞추고 다시 읽어 확인한다. */
    fun set(context: Context, off: Boolean, how: String): Boolean {
        val found = scan()
        if (found == null) {
            LogRepository.from(context).add("GOS ${if (off) "끄기" else "켜기"}($how): Shizuku가 연결되지 않아 바꾸지 못함")
            return false
        }
        if (found.isEmpty()) {
            LogRepository.from(context).add("GOS($how): 이 기기에는 게임 최적화 서비스가 없음")
            return false
        }
        // 바꿀 패키지만 Bloat.set으로 바꾼다(성공하면 Bloat.set이 noteChange로 뜻을 남긴다)
        found.filter { it.value != off }.keys.forEach { pkg -> Bloat.set(context, appOf(pkg), off, how) }
        val ok = read() == if (off) State.OFF else State.ON
        // 이미 원하는 상태였으면 Bloat.set을 부르지 않으므로 여기서 뜻을 맞춘다
        if (ok) noteChange(context, PACKAGES.first(), off)
        return ok
    }

    /** 바꾼 부팅을 확인한 것으로 남긴다: 같은 부팅 안에서 사용자가 앱 밖에서 되돌려도 다시 끄지 않는다. */
    private fun markBoot(context: Context) {
        bootCount(context)?.let { prefs(context).edit().putInt("checkedBoot", it).apply() }
    }

    /** 기본 앱 정리 목록의 GOS 항목(없는 이전 패키지는 같은 설명으로 만든다). */
    private fun appOf(pkg: String): Bloat.App =
        Bloat.CATALOG.firstOrNull { it.pkg == pkg } ?: Bloat.CATALOG.first { it.pkg == PACKAGES.first() }.copy(pkg = pkg)

    /** [Bloat.set]이 GOS 패키지를 바꿀 때 부른다: 사용자가 끄기를 원하는지 남긴다(다시 끄기의 기준). */
    fun noteChange(context: Context, pkg: String, off: Boolean) {
        if (pkg !in PACKAGES) return
        prefs(context).edit().putBoolean("wanted", off).apply()
        markBoot(context)
    }

    fun wanted(context: Context) = prefs(context).getBoolean("wanted", false)

    /** 재부팅·업데이트로 다시 켜지면 다시 끈다. */
    fun keep(context: Context) = prefs(context).getBoolean("keep", true)

    fun setKeep(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("keep", on).apply()
        LogRepository.from(context).add("GOS 끄기: 다시 켜지면 자동으로 끄기 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 다시 끄기에서 할 일. */
    internal enum class Action {
        /** 아무것도 하지 않는다(원하지 않음·keep 꺼짐·이번 부팅에 이미 확인함). */
        SKIP,
        /** 상태를 못 읽었다: 확인 기록을 남기지 않고 다음 Shizuku 연결 때 다시 확인한다. */
        RETRY_LATER,
        /** 이미 꺼져 있다(또는 GOS가 없다): 이번 부팅은 확인한 것으로 남긴다. */
        MARK,
        /** 다시 켜져 있다: 끄고 확인한 것으로 남긴다. */
        DISABLE,
    }

    /**
     * [reapply]의 판단(시험할 수 있게 뗐다). [boot]는 지금 부팅 횟수(없는 기기면 null: 그때는 연결될 때마다 확인),
     * [checkedBoot]는 마지막으로 확인한 부팅 횟수, [now]는 지금 상태(읽기 전이면 null).
     */
    internal fun reapplyAction(wanted: Boolean, keep: Boolean, boot: Int?, checkedBoot: Int?, now: State?): Action = when {
        !wanted || !keep -> Action.SKIP
        boot != null && checkedBoot == boot -> Action.SKIP
        now == null || now == State.UNKNOWN -> Action.RETRY_LATER
        now == State.ON -> Action.DISABLE
        else -> Action.MARK
    }

    /** 끄기를 원하는데 다시 켜져 있으면 끈다(Shizuku 연결 때, 작업 스레드). 부팅마다 한 번만 확인한다. */
    fun reapply(context: Context) {
        val p = prefs(context)
        val boot = bootCount(context)
        val checked = p.getInt("checkedBoot", -1).takeIf { it >= 0 }
        // 이미 확인한 부팅이면 셸을 부르기 전에 끝낸다
        if (reapplyAction(wanted(context), keep(context), boot, checked, State.OFF) == Action.SKIP) return
        when (reapplyAction(wanted(context), keep(context), boot, checked, read())) {
            Action.DISABLE -> {
                // 끄는 데 성공했을 때만 이번 부팅을 확인한 것으로 남긴다(실패하면 다음 연결 때 다시 시도)
                if (set(context, off = true, how = "재부팅 뒤 다시 켜져서 자동으로 끄기")) boot?.let { p.edit().putInt("checkedBoot", it).apply() }
            }
            Action.MARK -> boot?.let { p.edit().putInt("checkedBoot", it).apply() }
            else -> Unit
        }
    }

    /** 부팅 횟수(Settings.Global.BOOT_COUNT). 없는 기기면 null(그때는 연결될 때마다 확인한다). */
    private fun bootCount(context: Context): Int? =
        runCatching { android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrNull()

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }
    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다: Shizuku가 연결되는 순간(재부팅 뒤 자동 시작 포함) 다시 끄기를 확인한다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (!wanted(app) || !keep(app)) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
