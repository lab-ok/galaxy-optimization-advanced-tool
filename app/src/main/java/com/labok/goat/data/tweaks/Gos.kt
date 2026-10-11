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

    /** 이 기기에 깔린 GOS 패키지와 꺼져 있는지(작업 스레드). Shizuku가 없으면 null. */
    private fun scan(): Map<String, Boolean>? {
        val all = ShizukuAccess.shell("pm list packages --user 0") ?: return null
        if (!all.ok) return null
        val off = ShizukuAccess.shell("pm list packages -d --user 0")?.out.orEmpty()
        fun names(text: String) = text.lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val installed = names(all.out)
        val disabled = names(off)
        return PACKAGES.filter { it in installed }.associateWith { it in disabled }
    }

    /** 지금 상태를 읽는다(작업 스레드). 깔린 GOS가 하나라도 켜져 있으면 ON. */
    fun read(): State {
        val found = scan()
        val s = when {
            found == null -> State.UNKNOWN
            found.isEmpty() -> State.NONE
            found.values.all { it } -> State.OFF
            else -> State.ON
        }
        state.value = s
        return s
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
        found.filter { it.value != off }.keys.forEach { pkg -> Bloat.set(context, appOf(pkg), off, how) }
        noteChange(context, PACKAGES.first(), off)
        return read() == if (off) State.OFF else State.ON
    }

    /** 기본 앱 정리 목록의 GOS 항목(없는 이전 패키지는 같은 설명으로 만든다). */
    private fun appOf(pkg: String): Bloat.App =
        Bloat.CATALOG.firstOrNull { it.pkg == pkg } ?: Bloat.CATALOG.first { it.pkg == PACKAGES.first() }.copy(pkg = pkg)

    /** [Bloat.set]이 GOS 패키지를 바꿀 때 부른다: 사용자가 끄기를 원하는지 남긴다(다시 끄기의 기준). */
    fun noteChange(context: Context, pkg: String, off: Boolean) {
        if (pkg !in PACKAGES) return
        prefs(context).edit().putBoolean("wanted", off).apply()
    }

    fun wanted(context: Context) = prefs(context).getBoolean("wanted", false)

    /** 재부팅·업데이트로 다시 켜지면 다시 끈다. */
    fun keep(context: Context) = prefs(context).getBoolean("keep", true)

    fun setKeep(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("keep", on).apply()
        LogRepository.from(context).add("GOS 끄기: 다시 켜지면 자동으로 끄기 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 끄기를 원하는데 다시 켜져 있으면 끈다(Shizuku 연결 때, 작업 스레드). 부팅마다 한 번만 확인한다. */
    fun reapply(context: Context) {
        if (!wanted(context) || !keep(context)) return
        val boot = bootCount(context)
        val p = prefs(context)
        if (boot != null && p.getInt("checkedBoot", -1) == boot) return
        val now = read()
        if (now == State.UNKNOWN) return  // Shizuku가 아직 응답하지 않으면 다음 연결 때 다시 확인한다
        if (boot != null) p.edit().putInt("checkedBoot", boot).apply()
        if (now == State.ON) set(context, off = true, how = "재부팅 뒤 다시 켜져서 자동으로 끄기")
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
