package com.labok.goat.data.lab

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 앱 폭 좁히기(실험실): 고른 앱을 세로로 길고 좁게(16:9 또는 3:2) 띄우고 양옆을 검은 여백으로 둔다.
 * 안드로이드 앱 호환성 설정(`am compat`, Shizuku 셸)으로 그 앱만 세로 방향으로 보고 최소 화면비를 정한다.
 * 셸은 다른 앱의 '사용자 화면비'(setUserMinAspectRatio)를 바꿀 수 없어(소유자 확인에 막힘, 에뮬레이터 실측) 이 방법을 쓴다.
 * 비율은 안드로이드가 정해 둔 값뿐이라 폭을 마음대로 고를 수는 없다. 가로로 고정한 앱(게임·전체 화면 영상)은 그대로 둔다.
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

    /**
     * 고를 수 있는 비율. [change]는 그 비율을 켜는 호환성 설정 번호. 3:2는 16:9보다 넓어 커버 화면(10:16)에서는 달라지지 않고
     * 펼친 화면에서만 덜 좁은 세로 창이 된다.
     */
    enum class Ratio(val label: String, val change: Long, val value: Float) {
        TALL("16:9", 180326787L, 16f / 9),
        MEDIUM("3:2", 180326845L, 3f / 2)
    }

    private val COMMON_ON = listOf(MIN_ASPECT_RATIO, UNDEFINED_ORIENTATION_TO_PORTRAIT)
    private val OFF = listOf(UNIVERSAL_RESIZABLE_BY_DEFAULT, MIN_ASPECT_RATIO_PORTRAIT_ONLY)

    /** 검은 여백을 어디에 둘지: 앱이 붙는 쪽(왼쪽·가운데·오른쪽). 값은 `wm set-letterbox-style --horizontalPositionMultiplier`. */
    enum class Side(val label: String, val multiplier: String) { LEFT("왼쪽", "0.0"), CENTER("가운데", "0.5"), RIGHT("오른쪽", "1.0") }

    /**
     * 앱별 설정은 없앴다(소장님 지정 10-24: 앱별로 하지 말고 꾹 누르면 전체 적용). 늘 빈 지도이고, 예전에 앱별로 걸어 둔 설정은
     * Shizuku가 연결될 때 [cleanupPerApp]이 지운다. 아래 앱별 코드는 이 빈 지도를 거쳐 아무 일도 하지 않는다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun ratios(context: Context): Map<String, Ratio> = emptyMap()

    /** 1.24.1~1.24.5에 앱별로 고른 앱(정리할 대상). */
    private fun perAppLeftovers(context: Context): Set<String> {
        val p = prefs(context)
        return p.getStringSet("apps", emptySet()).orEmpty() + p.all.keys.filter { it.startsWith("ratio:") }.map { it.removePrefix("ratio:") }
    }

    /** 예전 앱별 설정을 지운다(작업 스레드, Shizuku 연결 때). 전체 좁히기가 건 앱은 건드리지 않는다. */
    private fun cleanupPerApp(context: Context) {
        val left = perAppLeftovers(context)
        if (left.isEmpty()) return
        val keep = if (global(context)) globalApplied(context) else emptySet()
        left.filter { it !in keep }.forEach { ShizukuAccess.clearCompat(it) }
        val e = prefs(context).edit().remove("apps")
        prefs(context).all.keys.filter { it.startsWith("ratio:") }.forEach { e.remove(it) }
        e.apply()
        nudge()
        LogRepository.from(context).add("앱 폭 좁히기: 예전 앱별 설정 ${left.size}개 정리(이제 전체 적용만)")
    }

    fun apps(context: Context): Set<String> = ratios(context).keys

    /**
     * 폴드8(기본형, SM-F971) 전용(소장님 지정). 커버 화면 비율(10:16)과 여백 컨트롤러 배치를 이 기기에 맞췄다.
     * 시험용으로 `setprop debug.goat.narrow 1`이면 다른 기기(에뮬레이터)에서도 켤 수 있다(일반 사용자는 설정할 수 없는 속성).
     */
    fun supported(): Boolean = (Build.MANUFACTURER.equals("samsung", true) && Build.MODEL.uppercase().startsWith("SM-F971")) ||
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "debug.goat.narrow") == "1" }.getOrDefault(false)

    /** 좁힌 앱 옆 여백에 세로 컨트롤러([NarrowBar])를 띄울지. */
    fun controller(context: Context) = prefs(context).getBoolean("controller", false)

    fun setController(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("controller", on).apply()
        LogRepository.from(context).add("좁힌 앱 컨트롤러 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 앱이 붙은 쪽. 가운데는 1.24.3까지 고를 수 있었는데 커버 화면에서 뺐다(소장님 지정): 남아 있으면 오른쪽으로 읽는다. */
    fun side(context: Context): Side = Side.entries.getOrNull(prefs(context).getInt("side", Side.RIGHT.ordinal))?.takeIf { it != Side.CENTER } ?: Side.RIGHT

    /**
     * 앱 폭 좁히기 전체 켜고 끄기(소장님 지정 '온오프'). 끄면 좁힌 앱을 모두 원래대로 돌리고 컨트롤러·상단바 꾹 누르기도 멈춘다.
     * 앱 목록·비율은 그대로 남겨 다시 켜면 다시 걸린다. 1.24.4까지 쓰던 사람은 켜진 상태로 읽는다.
     */
    fun on(context: Context) = prefs(context).getBoolean("on", true)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) {
            reapply(context)
            nudge()
        } else {
            if (global(context)) stopGlobal(context, null)
            ratios(context).keys.forEach { ShizukuAccess.clearCompat(it) }
            nudge()
            NarrowBar.hide(context)
        }
        LogRepository.from(context).add("앱 폭 좁히기 ${if (on) "켬" else "끔"}")
    }

    /**
     * [pkg]에 [ratio]를 걸거나(null이면 지운다) 앱을 닫지 않고 바꾼다(작업 스레드). 시스템 호환성 서비스로 앱마다 한 번 호출하고
     * (1.24.4까지는 `am compat` 명령을 설정마다 따로 불러 앱 171개에 20초 넘게 걸렸다, 실기기 로그), 안 되면 명령으로 한다.
     */
    private fun override(pkg: String, ratio: Ratio?): Boolean {
        ShizukuAccess.clearCompat(pkg)
        if (ratio == null) return true
        return ShizukuAccess.setCompat(pkg, (COMMON_ON + ratio.change).toSet(), OFF.toSet(), optional = setOf(UNIVERSAL_RESIZABLE_BY_DEFAULT)) ||
            ShizukuAccess.shell(commands(pkg, ratio, kill = false))?.out?.contains("Enabled change $MIN_ASPECT_RATIO") == true
    }

    /**
     * [pkg]를 [ratio]로 좁히거나(null이면 원래대로) 둔다(작업 스레드). 앱을 닫지 않고 설정만 바꾼 뒤 화면 크기를 아주 잠깐 바꿔
     * 열려 있는 앱도 바로 새 모양으로 바뀌게 한다(에뮬레이터 실측: 켜고 끌 때 모두 같은 프로세스로 바로 바뀜).
     * 실패하면 이유(한 줄), 성공하면 null.
     */
    fun set(context: Context, pkg: String, ratio: Ratio?): String? {
        val log = LogRepository.from(context)
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) ShizukuAccess.refresh()
        if (on(context) && !override(pkg, ratio)) {
            log.add("앱 폭 좁히기($pkg) ${ratio?.label ?: "끄기"} 실패")
            return "바꾸지 못했습니다. Shizuku 연결을 확인하세요."
        }
        val legacy = prefs(context).getStringSet("apps", emptySet()).orEmpty() - pkg
        prefs(context).edit().putStringSet("apps", legacy).apply {
            if (ratio != null) putString("ratio:$pkg", ratio.name) else remove("ratio:$pkg")
        }.apply()
        if (ratio != null) {
            applySide(context)
            watch(context)
        }
        nudge()
        log.add("앱 폭 좁히기($pkg) ${ratio?.label ?: "끔"}")
        return null
    }

    /** 앱 폭 좁히기를 모두 끈다(작업 스레드). 실패한 앱 수를 돌려준다. */
    fun clearAll(context: Context): Int = apps(context).count { set(context, it, null) != null }

    /**
     * 검은 여백 위치를 바꾼다(작업 스레드, 모든 앱에 함께 쓰인다). 위치는 앱이 화면을 새로 잡을 때 정해지므로, 화면 크기를 2px
     * 바꿨다가 곧바로 되돌려 지금 열린 앱도 앱을 닫지 않고 바로 옮긴다(에뮬레이터 실측 약 90ms, 앱 다시 만들기 없음).
     */
    fun setSide(context: Context, side: Side): String? {
        prefs(context).edit().putInt("side", side.ordinal).apply()
        if (!applySide(context)) return "Shizuku가 연결되지 않았습니다."
        nudge()
        NarrowBar.relayout(context)
        LogRepository.from(context).add("앱 폭 좁히기: 붙일 쪽 ${side.label}")
        return null
    }

    private fun applySide(context: Context): Boolean {
        val r = ShizukuAccess.shell("wm set-letterbox-style --horizontalPositionMultiplier ${side(context).multiplier} --cornerRadius 0")
        return r?.ok == true
    }

    /**
     * 맨 앞 앱이 있는 화면의 크기를 아주 조금 바꿨다 되돌려 앱 배치를 다시 계산하게 한다. 원래 강제 해상도(해상도 낮추기 등)가
     * 있으면 그 크기로, 없으면 기본으로 되돌린다. 크기를 읽지 못하면 하지 않는다.
     */
    private fun nudge() {
        val display = ShizukuAccess.focusedDisplay() ?: 0
        val out = ShizukuAccess.shell("wm size -d $display")?.out ?: return
        val (physical, override) = parseSize(out) ?: return
        val now = override ?: physical
        val back = override?.let { "wm size ${it.first}x${it.second} -d $display" } ?: "wm size reset -d $display"
        ShizukuAccess.shell("wm size ${now.first}x${now.second - 2} -d $display; $back")
    }

    /** `wm size` 출력에서 (물리 크기, 강제 크기 또는 null). 물리 크기가 없으면 null. */
    internal fun parseSize(out: String): Pair<Pair<Int, Int>, Pair<Int, Int>?>? {
        fun find(label: String) = Regex("$label size: (\\d+)x(\\d+)").find(out)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return find("Physical")?.let { it to find("Override") }
    }

    /** 셸 명령 한 줄(호환성 서비스를 못 쓸 때의 예비). [kill]이면 바꾼 뒤 앱을 닫는다. */
    internal fun commands(pkg: String, ratio: Ratio?, kill: Boolean): String {
        val flag = if (kill) "" else " --no-kill"
        val ratioOff = Ratio.entries.filter { it != ratio }.map { "am compat reset$flag ${it.change} $pkg" }
        val lines = if (ratio != null) {
            OFF.map { "am compat disable$flag $it $pkg" } + (COMMON_ON + ratio.change).map { "am compat enable$flag $it $pkg" } + ratioOff
        } else {
            (COMMON_ON + OFF).map { "am compat reset$flag $it $pkg" } + ratioOff
        }
        // 설정을 되돌릴 때(reset)는 시스템이 앱을 닫지 않아, 열려 있던 앱이 옛 모양으로 남는다. 바꿀 때는 직접 닫는다
        return (if (kill) lines + "am force-stop $pkg" else lines).joinToString("; ")
    }

    /**
     * 전체 좁히기(상단바 꾹 누르기로 켠다): 켜 두면 앱이 바뀌어도 맨 앞에 오는 앱을 차례로 16:9로 좁혀 계속 유지한다(소장님 지정).
     * 이 모드가 좁힌 앱은 따로 기억해([globalApplied]) 끌 때 그 앱들만 되돌린다. 앱 목록에서 직접 고른 앱은 건드리지 않는다.
     */
    fun global(context: Context) = prefs(context).getBoolean("global", false)

    private fun globalApplied(context: Context): Set<String> = prefs(context).getStringSet("global_applied", emptySet()).orEmpty()

    /** 전체 좁히기에서 빼는 앱: 다른 앱 위에 잠깐 뜨는 시스템 창(권한 요청 등). */
    private val SKIP = setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.systemui", "com.android.intentresolver")

    /** 좁혀 있는(또는 좁혀야 하는) 앱인지: 직접 고른 앱이거나 전체 좁히기가 켜져 있으면. 앱 폭 좁히기를 꺼 두면 늘 false. */
    fun isNarrow(context: Context, pkg: String) = on(context) && (pkg in ratios(context) || (global(context) && pkg != context.packageName && pkg !in SKIP))

    /** 앱 비율: 직접 고른 비율, 없으면 전체 좁히기의 16:9. */
    fun ratioOf(context: Context, pkg: String): Ratio? = ratios(context)[pkg] ?: Ratio.TALL.takeIf { global(context) }

    /** 전체 좁히기를 켜고 끄는 중이면 true: 꾹 누르기가 처리 중에 겹쳐 쌓이지 않게 한다(실기기 로그: 1초마다 켬이 쌓였다). */
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 전체 좁히기를 켠다(작업 스레드): [side] 쪽에 앱을 붙이고, 지금 맨 앞 앱부터 좁힌 뒤 홈 화면 앱 전부에 미리 건다.
     * 이미 켜져 있으면 붙일 쪽만 바꾼다. 처리 중이면 무시하고 false.
     */
    fun startGlobal(context: Context, side: Side, pkg: String?): Boolean {
        if (!on(context) || !busy.compareAndSet(false, true)) return false
        try {
            val already = global(context)
            prefs(context).edit().putBoolean("global", true).putInt("side", side.ordinal).apply()
            if (!controller(context)) setController(context, true)
            applySide(context)
            if (already) {
                nudge()
                NarrowBar.relayout(context)
                LogRepository.from(context).add("앱 폭 좁히기: 붙일 쪽 ${side.label}")
                return true
            }
            // 보이는 앱만 바로 좁히고, 다른 앱은 앞에 올 때 좁힌다(소장님 지정 10-24: 굳이 전체에 미리 걸 이유가 없다)
            if (pkg != null) onForeground(context, pkg, force = true) else nudge()
            watch(context)
            LogRepository.from(context).add("앱 폭 좁히기: 전체 좁히기 켬(${side.label})")
            return true
        } finally {
            busy.set(false)
        }
    }

    /** 전체 좁히기를 끈다(작업 스레드): 이 모드가 좁힌 앱을 모두 되돌린다. [pkg](지금 앱)를 직접 고른 앱이면 그 앱도 끈다. */
    fun stopGlobal(context: Context, pkg: String?): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        try {
            val applied = globalApplied(context)
            val wasOn = global(context)
            prefs(context).edit().putBoolean("global", false).putStringSet("global_applied", emptySet()).apply()
            applied.filter { it !in ratios(context) }.forEach { ShizukuAccess.clearCompat(it) }
            if (pkg != null && pkg in ratios(context)) set(context, pkg, null) else nudge()
            if (wasOn || applied.isNotEmpty()) LogRepository.from(context).add("앱 폭 좁히기: 전체 좁히기 끔(${applied.size}개 앱 되돌림)")
            return true
        } finally {
            busy.set(false)
        }
    }

    /**
     * 맨 앞 앱이 [pkg]로 바뀌었을 때(작업 스레드): 전체 좁히기가 켜져 있고 아직 좁히지 않은 앱이면 앱을 닫지 않고 바로 좁힌다.
     * 직접 고른 앱은 이미 걸려 있어 건너뛴다.
     */
    fun onForeground(context: Context, pkg: String, force: Boolean = false) {
        if (!on(context) || !global(context) || pkg == context.packageName || pkg in SKIP || pkg in ratios(context)) return
        if (!force && pkg in globalApplied(context)) return
        if (!override(pkg, Ratio.TALL)) return
        prefs(context).edit().putStringSet("global_applied", globalApplied(context) + pkg).apply()
        nudge()
        // 설정 없이 처음 열린 화면은 설정을 바꿔도 좁아지지 않을 때가 있다(에뮬레이터 연락처: 화면 크기를 바꿔도 그대로, 다시 열면 좁아짐).
        // 잠시 뒤 좁혀졌는지 보고, 아니면 그 앱만 다시 연다(가로 고정 앱처럼 원래 좁아지지 않는 앱은 다시 열어도 그대로라 한 번만)
        Thread.sleep(500)
        if (ShizukuAccess.focusedTask()?.basePkg == pkg && ShizukuAccess.focusedLetterboxed() == false) restart(context, pkg)
    }

    /**
     * [pkg]의 화면만 새로 연다(작업 스레드). 앱 프로세스는 닫지 않고 작업을 비운 뒤 첫 화면을 다시 띄운다(--activity-clear-task).
     * 설정 없이 처음 열린 화면은 설정을 걸어도 그대로지만, 화면을 새로 만들면 좁게 뜬다(에뮬레이터 시계 앱, 같은 프로세스).
     */
    private fun restart(context: Context, pkg: String) {
        val comp = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString() ?: return
        // 백그라운드에서 화면을 여는 제한이 있어 셸로 연다
        ShizukuAccess.shell("am start --activity-clear-task -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp")
        LogRepository.from(context).add("앱 폭 좁히기: $pkg 화면이 좁아지지 않아 화면만 새로 엶")
    }

    /** 고른 앱과 여백 위치를 다시 건다(Shizuku 연결 때, 작업 스레드). 열려 있는 앱은 닫지 않는다. */
    fun reapply(context: Context) {
        cleanupPerApp(context)
        if (!on(context)) return
        val ratios = ratios(context) + (if (global(context)) globalApplied(context).associateWith { Ratio.TALL } else emptyMap())
        if (ratios.isEmpty()) return
        val ok = ratios.count { (pkg, r) -> override(pkg, r) }
        applySide(context)
        LogRepository.from(context).add("앱 폭 좁히기: ${ok}/${ratios.size}개 앱 다시 걸기")
    }

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }

    @Volatile private var reapplyAsync: (() -> Unit)? = null

    @Volatile private var watching = false

    /**
     * 앱 시작 때 부른다: Shizuku가 연결되는 순간(재부팅 뒤 포함) 고른 앱을 다시 건다. 감시는 한 번만 등록한다
     * (1.24.4는 부를 때마다 겹쳐 등록돼, 한 번 연결에 173개 다시 걸기가 여러 번 돌았다, 실기기 로그).
     */
    fun watch(context: Context) {
        val app = context.applicationContext
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(OLD_NOTIFICATION_ID) }
        if (!global(app) && perAppLeftovers(app).isEmpty()) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        if (watching) return
        watching = true
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    /** 1.24.3에 있던 알림창 붙일 쪽 버튼(31번 알림)을 없앴다. 남아 있으면 지운다. */
    private const val OLD_NOTIFICATION_ID = 31

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
