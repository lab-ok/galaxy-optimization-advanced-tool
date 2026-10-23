package com.labok.goat.data.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
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

    /** 좁힌 앱과 비율. 1.24.1~1.24.2에 고른 앱(목록만 있던 것)은 16:9로 읽는다. */
    fun ratios(context: Context): Map<String, Ratio> {
        val p = prefs(context)
        val legacy = p.getStringSet("apps", emptySet()).orEmpty().associateWith { Ratio.TALL }
        val set = p.all.filterKeys { it.startsWith("ratio:") }
            .mapNotNull { (k, v) -> Ratio.entries.firstOrNull { it.name == v }?.let { k.removePrefix("ratio:") to it } }.toMap()
        return legacy + set
    }

    fun apps(context: Context): Set<String> = ratios(context).keys

    /** 좁힌 앱 옆 여백에 세로 컨트롤러([NarrowBar])를 띄울지. */
    fun controller(context: Context) = prefs(context).getBoolean("controller", false)

    fun setController(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("controller", on).apply()
        LogRepository.from(context).add("좁힌 앱 컨트롤러 ${if (on) "켜짐" else "꺼짐"}")
    }

    fun side(context: Context): Side = Side.entries.getOrNull(prefs(context).getInt("side", Side.CENTER.ordinal)) ?: Side.CENTER

    /**
     * [pkg]를 [ratio]로 좁히거나(null이면 원래대로) 둔다(작업 스레드). 바꾸면 그 앱을 닫아, 다시 열 때 새 모양으로 뜬다.
     * 실패하면 이유(한 줄), 성공하면 null.
     */
    fun set(context: Context, pkg: String, ratio: Ratio?): String? {
        val log = LogRepository.from(context)
        val on = ratio != null
        val r = ShizukuAccess.shell(commands(pkg, ratio, kill = true)) ?: return "Shizuku가 연결되지 않았습니다.".also {
            log.add("앱 폭 좁히기($pkg): Shizuku 없음")
        }
        // 핵심 설정(최소 화면비)이 바뀌었는지 출력으로 확인한다. 안드로이드 15 이하에는 없는 설정(357141415)의 오류는 무시한다
        val ok = if (on) "Enabled change $MIN_ASPECT_RATIO" in r.out else "Reset change $MIN_ASPECT_RATIO" in r.out
        if (!ok) {
            log.add("앱 폭 좁히기($pkg) ${ratio?.label ?: "끄기"} 실패: ${(r.err.ifBlank { r.out }).take(160)}")
            return "바꾸지 못했습니다: ${(r.err.ifBlank { r.out }).lineSequence().firstOrNull().orEmpty().take(80)}"
        }
        val legacy = prefs(context).getStringSet("apps", emptySet()).orEmpty() - pkg
        prefs(context).edit().putStringSet("apps", legacy).apply {
            if (ratio != null) putString("ratio:$pkg", ratio.name) else remove("ratio:$pkg")
        }.apply()
        if (on) {
            applySide(context)
            watch(context)
        }
        notifyControls(context)
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
        notifyControls(context)
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

    /** 셸 명령 한 줄. [kill]이면 바꾼 뒤 앱을 닫아 다시 열 때 새 모양으로 뜨게 한다(다시 걸 때는 열려 있는 앱을 닫지 않는다). */
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

    /** 고른 앱과 여백 위치를 다시 건다(Shizuku 연결 때, 작업 스레드). 열려 있는 앱은 닫지 않는다. */
    fun reapply(context: Context) {
        val ratios = ratios(context)
        if (ratios.isEmpty()) return
        if (ShizukuAccess.shell(ratios.entries.joinToString("; ") { (pkg, r) -> commands(pkg, r, kill = false) }) == null) return
        applySide(context)
        notifyControls(context)
        LogRepository.from(context).add("앱 폭 좁히기: ${ratios.size}개 앱 다시 걸기")
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

    private const val CHANNEL_ID = "narrow_side"
    private const val NOTIFICATION_ID = 31

    /**
     * 알림창 조절: 좁힌 앱이 있는 동안 알림에 [왼쪽·가운데·오른쪽] 버튼을 두어 앱을 바로 옮긴다. 좁힌 앱이 없으면 알림을 지운다.
     * 알림 권한이 없으면 조용히 넘어간다.
     */
    fun notifyControls(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (apps(context).isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "앱 폭 좁히기", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val now = side(context)
        val open = PendingIntent.getActivity(context, 23, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("앱 폭 좁히기 · ${now.label}")
            .setContentText("좁힌 앱을 붙일 쪽을 바로 바꿉니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
        Side.entries.forEach { s ->
            val i = Intent(context, SideReceiver::class.java).putExtra("side", s.name)
            val pi = PendingIntent.getBroadcast(context, 40 + s.ordinal, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, if (s == now) "● ${s.label}" else s.label, pi)
        }
        runCatching { nm.notify(NOTIFICATION_ID, b.build()) }
    }

    /** 알림 버튼을 받아 붙일 쪽을 바꾼다. */
    class SideReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val side = Side.entries.firstOrNull { it.name == intent.getStringExtra("side") } ?: return
            val pending = goAsync()
            Thread {
                runCatching { setSide(context.applicationContext, side) }
                pending.finish()
            }.start()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
