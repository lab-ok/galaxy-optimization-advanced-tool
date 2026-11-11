package com.labok.goat.data.log

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.data.shizuku.ShizukuAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 접기·펼치기 때 앱 화면이 다시 만들어졌는지(새로 고침) 진단 파일에 남긴다.
 * 접은 시각·DPI 변화·앞 앱은 사용 통계 이벤트로, 다시 만들어진 화면과 그 원인(바뀐 설정)은 시스템 이벤트 로그
 * (wm_relaunch_activity, Shizuku 셸)로 본다. 사용 통계의 화면 이벤트에는 다시 만들기가 남지 않는다(에뮬레이터 실측).
 * 11-11 폴드6 신고(DPI를 바꾼 뒤 접고 펼치면 유튜브가 새로 고침)를 이용자 조작 없이 확인하려고 넣었다.
 */
object RelaunchTrace {

    private const val BEFORE_MS = 1_500L
    private const val AFTER_MS = 5_000L
    private const val SHOWN = 15

    private class Fold(val time: Long, val before: Configuration, val after: Configuration, val front: String?)

    private class Relaunch(val time: Long, val component: String, val changes: Int)

    private val LINE = Regex("""^\s*(\d+)\.(\d{3})\s.*wm_relaunch(?:_resume)?_activity: \[\d+,\d+,\d+,([^,]+),([0-9a-fA-F]+)]""")

    fun diagnose(context: Context): String = buildString {
        if (!AppUsage.hasAccess(context)) return "사용 기록 접근 없음(사용 통계 권한이 꺼져 있음)"
        val folds = folds(context)
        val relaunches = relaunches()
        if (relaunches == null) appendLine("다시 만들어진 화면 기록: Shizuku 연결이 없어 읽지 못함")
        if (folds.isEmpty()) return append("최근 24시간 동안 접기·펼치기 기록 없음").toString()
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        appendLine("최근 24시간 접기·펼치기 ${folds.size}건(최근 ${SHOWN}건). 「다시 만들어짐」이면 그 앱이 화면을 새로 띄운 것(새로 고침)")
        folds.takeLast(SHOWN).forEach { f ->
            val near = relaunches.orEmpty().filter { it.time in f.time - BEFORE_MS..f.time + AFTER_MS }
            val kind = if (f.after.smallestScreenWidthDp > f.before.smallestScreenWidthDp) "펼침" else "접힘"
            val verdict = when {
                relaunches == null || f.front == null -> ""
                near.any { it.component.substringBefore('/') == f.front } -> " · 다시 만들어짐"
                else -> " · 그대로 이어짐"
            }
            appendLine(
                "${fmt.format(Date(f.time))} $kind sw ${f.before.smallestScreenWidthDp}→${f.after.smallestScreenWidthDp} " +
                    "dpi ${f.before.densityDpi}→${f.after.densityDpi} · 앞 앱 ${f.front ?: "모름"}$verdict"
            )
            near.take(8).forEach { r ->
                appendLine("    ${"%+.1f".format((r.time - f.time) / 1000.0)}s 다시 만들어짐 ${r.component} (바뀐 설정: ${changeNames(r.changes)})")
            }
        }
    }.trimEnd()

    /** 사용 통계의 설정 변화 이벤트에서 가장 짧은 폭(sw)이 바뀐 순간을 접기·펼치기로 본다. */
    private fun folds(context: Context): List<Fold> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 24 * 3600 * 1000L, now)
        val e = UsageEvents.Event()
        val folds = ArrayList<Fold>()
        // 이벤트의 설정은 바뀐 항목만 담으므로 지금 설정에서 출발해 차례로 덮고, 첫 이벤트는 출발점으로만 쓴다
        var running = Configuration(context.resources.configuration)
        var started = false
        var front: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.CONFIGURATION_CHANGE -> {
                    val delta = e.configuration ?: continue
                    val next = Configuration(running).apply { updateFrom(delta) }
                    if (started && running.smallestScreenWidthDp != next.smallestScreenWidthDp) {
                        folds += Fold(e.timeStamp, running, next, front)
                    }
                    running = next
                    started = true
                }
                UsageEvents.Event.ACTIVITY_RESUMED -> if (e.packageName != context.packageName) front = e.packageName
            }
        }
        return folds
    }

    /** 시스템 이벤트 로그의 화면 다시 만들기 기록. Shizuku가 없으면 null. */
    private fun relaunches(): List<Relaunch>? {
        val r = ShizukuAccess.shell("logcat -d -b events -v epoch -s wm_relaunch_resume_activity wm_relaunch_activity") ?: return null
        return r.out.lineSequence().mapNotNull { line ->
            val m = LINE.find(line) ?: return@mapNotNull null
            val (sec, ms, component, hex) = m.destructured
            Relaunch(sec.toLong() * 1000 + ms.toLong(), component, hex.toLongOrNull(16)?.toInt() ?: 0)
        }.toList()
    }

    /**
     * GOAT 자기 프로세스·화면이 언제 만들어지고 닫혔는지(시스템 이벤트 로그, 최근 [OWN_SHOWN]줄).
     * 업데이트 뒤 메인 화면이 두 번 뜬다는 신고(11-12 소장님)를 실기에서 확인하려고 넣었다. 에뮬레이터에서는 한 번만 만들어졌다.
     */
    fun ownLaunches(pkg: String): String {
        val tags = "am_kill am_proc_start am_proc_died wm_create_activity wm_restart_activity wm_new_intent wm_finish_activity wm_destroy_activity wm_relaunch_activity wm_relaunch_resume_activity"
        val r = ShizukuAccess.shell("logcat -d -b events -v time -s $tags") ?: return "Shizuku 연결이 없어 읽지 못함"
        val lines = r.out.lineSequence().filter { pkg in it }.toList()
        return if (lines.isEmpty()) "(기록 없음)" else lines.takeLast(OWN_SHOWN).joinToString("\n")
    }

    private const val OWN_SHOWN = 60

    /** ActivityInfo.CONFIG_* 비트(이벤트 로그의 마지막 값). */
    private val CHANGE_NAMES = listOf(
        0x1000 to "DPI(밀도)",
        0x0400 to "화면 크기",
        0x0800 to "가장 짧은 폭",
        0x0100 to "화면 배치",
        0x0080 to "방향",
        0x0200 to "UI 모드",
        0x4000 to "색 모드",
        0x0004 to "언어",
        0x40000000 to "글자 크기"
    )

    internal fun changeNames(changes: Int): String {
        val names = CHANGE_NAMES.filter { changes and it.first != 0 }.map { it.second }
        val rest = CHANGE_NAMES.fold(changes) { acc, (bit, _) -> acc and bit.inv() }
        return (names + if (rest != 0) listOf("기타 0x${Integer.toHexString(rest)}") else emptyList()).joinToString(", ").ifEmpty { "0x0" }
    }
}
