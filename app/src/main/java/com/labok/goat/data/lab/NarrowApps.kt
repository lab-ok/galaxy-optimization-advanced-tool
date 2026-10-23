package com.labok.goat.data.lab

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 앱 폭 좁히기(실험실): 고른 앱을 세로로 길고 좁게(16:9) 띄우고 양옆을 검은 여백으로 둔다.
 * 안드로이드 앱 호환성 설정(`am compat`, Shizuku 셸)으로 그 앱만 세로 방향으로 보고 최소 화면비를 16:9로 둔다.
 * 셸은 다른 앱의 '사용자 화면비'(setUserMinAspectRatio)를 바꿀 수 없어(소유자 확인에 막힘, 에뮬레이터 실측) 이 방법을 쓴다.
 * 비율은 안드로이드가 정해 둔 값(16:9)뿐이라 폭을 마음대로 고를 수는 없다. 가로로 고정한 앱(게임·전체 화면 영상)은 그대로 둔다.
 * 삼성은 부팅 때 이 설정 일부를 지운다고 알려져 있어(Aspectly 실측), Shizuku가 연결될 때마다 다시 건다([watch]).
 */
object NarrowApps {
    private const val PREF = "narrow_apps"

    /** 안드로이드 16 이상에서 큰 화면이면 앱의 방향·비율 고정을 무시하는 설정(끄지 않으면 아래 설정이 먹지 않는다). */
    private const val UNIVERSAL_RESIZABLE_BY_DEFAULT = 357141415L

    /**
     * 최소 화면비를 세로로 고정한 앱에만 쓰는 기본 조건. 폴드 커버 화면에서는 아래 방향 바꾸기가 먹지 않아 이 조건에 막혔다
     * (1.24.1 실기기: 내부 화면만 좁아지고 커버 화면은 그대로). 꺼야 커버 화면에서도 좁아진다(Aspectly와 같은 방식).
     */
    private const val MIN_ASPECT_RATIO_PORTRAIT_ONLY = 203647190L

    /** 방향을 정하지 않은 앱을 세로 방향으로 본다. */
    private const val UNDEFINED_ORIENTATION_TO_PORTRAIT = 265452344L

    /** 최소 화면비 바꾸기(이것을 켜야 아래 비율이 쓰인다). */
    private const val MIN_ASPECT_RATIO = 174042980L

    /** 최소 화면비 16:9. */
    private const val MIN_ASPECT_RATIO_LARGE = 180326787L

    private val ON = listOf(MIN_ASPECT_RATIO, MIN_ASPECT_RATIO_LARGE, UNDEFINED_ORIENTATION_TO_PORTRAIT)
    private val OFF = listOf(UNIVERSAL_RESIZABLE_BY_DEFAULT, MIN_ASPECT_RATIO_PORTRAIT_ONLY)

    /** 검은 여백을 어디에 둘지: 앱이 붙는 쪽(왼쪽·가운데·오른쪽). 값은 `wm set-letterbox-style --horizontalPositionMultiplier`. */
    enum class Side(val label: String, val multiplier: String) { LEFT("왼쪽", "0.0"), CENTER("가운데", "0.5"), RIGHT("오른쪽", "1.0") }

    fun apps(context: Context): Set<String> = prefs(context).getStringSet("apps", emptySet()).orEmpty()

    /** 좁힌 앱 옆 여백에 세로 컨트롤러([NarrowBar])를 띄울지. */
    fun controller(context: Context) = prefs(context).getBoolean("controller", false)

    fun setController(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("controller", on).apply()
        LogRepository.from(context).add("좁힌 앱 컨트롤러 ${if (on) "켜짐" else "꺼짐"}")
    }

    fun side(context: Context): Side = Side.entries.getOrNull(prefs(context).getInt("side", Side.CENTER.ordinal)) ?: Side.CENTER

    /**
     * [pkg]를 좁히거나([on]) 원래대로 둔다(작업 스레드). 바꾸면 그 앱을 닫아, 다시 열 때 새 모양으로 뜬다.
     * 실패하면 이유(한 줄), 성공하면 null.
     */
    fun set(context: Context, pkg: String, on: Boolean): String? {
        val log = LogRepository.from(context)
        val r = ShizukuAccess.shell(commands(pkg, on, kill = true)) ?: return "Shizuku가 연결되지 않았습니다.".also {
            log.add("앱 폭 좁히기($pkg): Shizuku 없음")
        }
        // 핵심 설정(최소 화면비)이 바뀌었는지 출력으로 확인한다. 안드로이드 15 이하에는 없는 설정(357141415)의 오류는 무시한다
        val ok = if (on) "Enabled change $MIN_ASPECT_RATIO" in r.out else "Reset change $MIN_ASPECT_RATIO" in r.out
        if (!ok) {
            log.add("앱 폭 좁히기($pkg) ${if (on) "켜기" else "끄기"} 실패: ${(r.err.ifBlank { r.out }).take(160)}")
            return "바꾸지 못했습니다: ${(r.err.ifBlank { r.out }).lineSequence().firstOrNull().orEmpty().take(80)}"
        }
        val next = apps(context).toMutableSet().apply { if (on) add(pkg) else remove(pkg) }
        prefs(context).edit().putStringSet("apps", next).apply()
        if (on) {
            applySide(context)
            watch(context)
        }
        log.add("앱 폭 좁히기($pkg) ${if (on) "켬" else "끔"}")
        return null
    }

    /** 검은 여백 위치를 바꾼다(작업 스레드, 모든 앱에 함께 쓰인다). 앱을 다시 열면 새 위치로 뜬다. */
    fun setSide(context: Context, side: Side): String? {
        prefs(context).edit().putInt("side", side.ordinal).apply()
        return if (applySide(context)) null else "Shizuku가 연결되지 않았습니다."
    }

    private fun applySide(context: Context): Boolean {
        val r = ShizukuAccess.shell("wm set-letterbox-style --horizontalPositionMultiplier ${side(context).multiplier} --cornerRadius 0")
        return r?.ok == true
    }

    /** 셸 명령 한 줄. [kill]이면 바꾼 뒤 앱을 닫아 다시 열 때 새 모양으로 뜨게 한다(다시 걸 때는 열려 있는 앱을 닫지 않는다). */
    internal fun commands(pkg: String, on: Boolean, kill: Boolean): String {
        val flag = if (kill) "" else " --no-kill"
        val lines = if (on) {
            OFF.map { "am compat disable$flag $it $pkg" } + ON.map { "am compat enable$flag $it $pkg" }
        } else {
            (ON + OFF).map { "am compat reset$flag $it $pkg" }
        }
        // 설정을 되돌릴 때(reset)는 시스템이 앱을 닫지 않아, 열려 있던 앱이 옛 모양으로 남는다. 바꿀 때는 직접 닫는다
        return (if (kill) lines + "am force-stop $pkg" else lines).joinToString("; ")
    }

    /** 고른 앱과 여백 위치를 다시 건다(Shizuku 연결 때, 작업 스레드). 열려 있는 앱은 닫지 않는다. */
    fun reapply(context: Context) {
        val apps = apps(context)
        if (apps.isEmpty()) return
        if (ShizukuAccess.shell(apps.joinToString("; ") { commands(it, on = true, kill = false) }) == null) return
        applySide(context)
        LogRepository.from(context).add("앱 폭 좁히기: ${apps.size}개 앱 다시 걸기")
    }

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }

    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다: Shizuku가 연결되는 순간(재부팅 뒤 포함) 고른 앱을 다시 건다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (apps(app).isEmpty()) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
