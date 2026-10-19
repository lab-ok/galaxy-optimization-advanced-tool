package com.labok.goat.data.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.UserId
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.util.concurrent.Executors
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 플렉스 모드 패널 제어(폴드8 자동 플렉스가 쓴다).
 * - 반쯤 접힘 강제/해제: 시스템에 HALF_OPENED 상태를 알리거나 푼다(Shizuku `cmd device_state`). 폴드8에서 이렇게 하면 패널이 뜬다(실기 확인).
 * - 가로 까딱 분할: 삼성 공식 화면 분할을 열고 푼다([sideSplit]).
 * - 앱별 등록: 삼성 IWindowManager.setSupportsFlexPanel(userId, 패키지, 켬). 작업 관리 권한이 필요해 Shizuku(셸 권한)로 부른다.
 *   GOAT가 등록한 앱은 기억해 두었다가 [restoreApps]로 모두 해제한다.
 */
object FlexPanel {
    private const val PREF = "lab_flex_apps"

    private fun wm(): Any = HiddenApi.asInterface("android.view.IWindowManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("window")))

    /** 이 기기 창 관리자에 앱별 등록 함수가 있는지(Shizuku 연결 필요). */
    fun hasAppApi(): Boolean = runCatching { setter() != null }.getOrDefault(false)

    private fun setter() = wm().javaClass.methods.firstOrNull { it.name == "setSupportsFlexPanel" && it.parameterTypes.size == 3 }

    /** [pkg]를 플렉스 패널 대상에 넣거나 뺀다. 성공하면 null, 실패하면 이유. 작업 스레드에서 부른다. */
    fun setApp(context: Context, pkg: String, on: Boolean): String? {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return "Shizuku 연결이 필요합니다."
        val err = runCatching {
            val m = setter() ?: return "이 기기에는 앱별 플렉스 패널 함수가 없습니다."
            m.invoke(wm(), UserId.current(), pkg, on)
        }.exceptionOrNull()?.let { HiddenApi.describe(it) }
        val p = prefs(context)
        if (err == null) {
            val set = registered(context).toMutableSet().apply { if (on) add(pkg) else remove(pkg) }
            p.edit().putStringSet("apps", set).apply()
        }
        LogRepository.from(context).add("실험실 플렉스 패널: $pkg ${if (on) "등록" else "해제"}" + (err?.let { " 실패($it)" } ?: ""))
        return err
    }

    /**
     * 자동 플렉스의 앱별 동작([AutoFlex.mode]). 접을 때 앞에 있는 앱에 맞춰 적용하고, 펴면 푼다.
     * - 플렉스 모드: 반쯤 접힘만 알리고(앱에 들어 있는 플렉스 모드) 화면은 건드리지 않는다.
     * - 사용 안 함: 그 앱에서는 반쯤 접힘을 걸지 않는다.
     * 켜져 있는 동안 알림에서 모드를 바꿀 수 있다([switchMode]). 셸 명령이 몇 초 걸려 센서 스레드를 막지 않게 따로 한 줄로 돌린다(순서는 지킨다).
     * 터치패널·분할 화면 모드는 실기에서 시스템 UI가 재시작되는 문제로 뺐다(1.11.9).
     */
    private data class Applied(val mode: Int, val pkg: String, val label: String)

    @Volatile private var applied: Applied? = null
    private val queue = Executors.newSingleThreadExecutor()
    private const val MODE_CHANNEL = "auto_flex_mode"
    private const val MODE_NOTIFICATION = 3101
    private const val SPLIT_CMD = "dumpsys activity service SystemUIService WMShell splitscreen"

    /** 옆 칸 자리: 1 = 아래·오른쪽(SPLIT_POSITION_BOTTOM_OR_RIGHT). 앞 앱은 위·왼쪽 큰 칸에 남는다. */
    private const val SIDE_POSITION = 1

    /** 화면에 보이는 전체 화면 일반 앱 작업(번호, 패키지). 시스템 UI·GOAT·홈은 뺀다. */
    private fun frontTask(context: Context): Pair<String, String>? = Regex("""Task\{[0-9a-f]+ #(\d+) type=standard A=\d+:([\w.]+)[^\n]*visible=true[^\n]*mode=(?:fullscreen|multi-window)""")
        .findAll(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())
        .map { it.groupValues[1] to it.groupValues[2] }
        .firstOrNull { (_, pkg) -> pkg != "com.android.systemui" && !pkg.startsWith(context.packageName) }

    /** 지금 앞에 있는 앱을 '사용 안 함'으로 두었는지(자동 플렉스가 켜기·진동 전에 본다). */
    fun frontIsOff(context: Context): Boolean = ShizukuAccess.status.value == ShizukuAccess.State.READY && frontTask(context)?.second?.let { AutoFlex.mode(context, it) == AutoFlex.MODE_OFF } == true

    /** 접을 때: 앞 앱에 고른 모드를 적용한다. */
    fun apply(context: Context) = queue.execute { applyNow(context) }

    /** 펼 때: 적용한 모드를 푼다. */
    fun release(context: Context) = queue.execute { releaseNow(context) }

    /** 켜져 있는 동안(알림에서) 지금 앱의 모드를 바꿔 바로 다시 적용한다. */
    fun switchMode(context: Context, mode: Int) = queue.execute {
        val s = applied ?: return@execute
        AutoFlex.setMode(context, s.pkg, mode)
        if (s.mode == mode) return@execute notifyMode(context, s)
        releaseNow(context)
        applyNow(context)
    }

    private fun applyNow(context: Context) {
        if (applied != null || ShizukuAccess.status.value != ShizukuAccess.State.READY) return
        val front = frontTask(context)
        if (front == null) {
            LogRepository.from(context).add("자동 플렉스: 포그라운드 앱이 없어 플렉스 모드만 켬")
            return
        }
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(front.second, 0)).toString() }.getOrDefault(front.second)
        val mode = AutoFlex.mode(context, front.second)
        // 사용 안 함: 걸어 둔 반쯤 접힘을 풀어 이 앱에서는 아무것도 바뀌지 않게 한다(알림으로 다른 모드로 바꿀 수 있게 알림은 남긴다).
        // 플렉스 모드는 알림으로 바꿔 들어온 경우를 위해 반쯤 접힘을 다시 건다
        if (mode == AutoFlex.MODE_OFF) resetPosture(context) else if (!forced) forceHalfOpened(context)
        applied = Applied(mode, front.second, label).also { notifyMode(context, it) }
    }

    private fun releaseNow(context: Context) {
        applied ?: return
        applied = null
        context.getSystemService(NotificationManager::class.java).cancel(MODE_NOTIFICATION)
    }

    /**
     * 가로 까딱 분할(실험실): 화면이 분할돼 있으면 풀고, 아니면 앞에 있는 앱과 바로 전에 쓴 앱으로 화면을 나눈다.
     * 안드로이드 17 삼성 펌웨어에는 예전에 쓰던 startTaskWithAllApps가 없어(명령 목록 확인, 2026-10-18) 실행되지 않았다.
     * 지금은 AOSP 명령으로 한다: 나누기는 전에 쓴 앱을 옆 칸으로 보내는 moveToSideStage(앞 앱은 그대로 큰 칸, 안 들어가는 앱은
     * 다음 최근 앱으로 최대 [MAX_TRIES]번), 풀기는 나누기 전 앞 앱을 남기는 exitSplitScreen(폰), 안 되면 removeFromSideStage
     * (에뮬레이터 AOSP 14). 접힘 상태는 덮어쓰지 않는다.
     * 명령은 시스템 UI 오류에도 종료 코드가 0이라, 1.5초 뒤 실제로 나뉘었는지(풀렸는지) 확인해 기록한다.
     */
    fun sideSplit(context: Context) = queue.execute { sideSplitNow(context) }

    /** [sideSplit]의 본체(작업 스레드에서 바로 실행). 남긴 결과 한 줄을 돌려준다([SideSplitTest]도 쓴다). */
    internal fun sideSplitNow(context: Context): String {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return "Shizuku 연결 안 됨"
        val log = LogRepository.from(context)
        var result = ""
        fun add(line: String) {
            log.add(line)
            result = line
        }
        val splitTasks = visibleSplitTasks()
        if (splitTasks.size >= 2) {
            // 나누기 전에 앞에 있던 앱을 앞에 남긴다(1.17.4 폰: 목록 첫 칸을 남겨 옆 칸 앱이 앞에 남았다)
            val keep = lastFront?.takeIf { it in splitTasks } ?: splitTasks.first()
            val ok = unsplit(keep, splitTasks)
            add("가로 까딱: 화면 분할 풀기 ${if (ok) "됨" else "안 됨"}")
            return result
        }
        val front = frontTask(context)
        if (front == null) {
            add("가로 까딱: 앞에 있는 앱이 없어 나누지 않음")
            return result
        }
        // 최근 앱을 차례로 옆 칸에 보내 본다. 삼성 페이처럼 분할 칸에 못 들어가는 앱은 나뉘지 않으므로 다음 앱으로 넘어간다(1.17.4 폰)
        val candidates = previousTasks(context, front.first).take(MAX_TRIES)
        if (candidates.isEmpty()) {
            add("가로 까딱: 함께 띄울 다른 앱이 없어 나누지 않음(최근 앱 없음)")
            return result
        }
        for ((i, prev) in candidates.withIndex()) {
            // 분할을 푼 뒤 옆 칸에 있던 앱은 분할 칸 상태가 남아 다시 옆 칸으로 못 보내는 일이 있다(에뮬레이터 자동 시험).
            // 첫 앱이 실패하면 그 앱과 앞 앱을 차례로 다시 띄워 보통 전체 화면 작업으로 돌린 뒤 한 번 더 해 본다
            var split = trySide(prev.first)
            if (split.size < 2 && i == 0) {
                bringToFront(prev.first)
                Thread.sleep(700)
                bringToFront(front.first)
                Thread.sleep(700)
                split = trySide(prev.first)
            }
            // 앞 앱이 빠진 채로 엉뚱한 앱끼리 나뉘었으면(에뮬레이터 연속 시험에서 GOAT가 끼어듦) 바로 되돌린다
            if (split.size >= 2 && front.first !in split) {
                unsplit(split.first(), split)
                add("가로 까딱: ${front.second} + ${prev.second} 다른 앱끼리 나뉘어 되돌림")
                return result
            }
            val ok = split.size >= 2
            add("가로 까딱: ${front.second} + ${prev.second} 화면 나누기 ${if (ok) "됨" else "안 됨"}${if (!ok && i < candidates.lastIndex) ", 다음 앱으로" else ""}")
            if (ok) {
                lastFront = front.first
                lastSide = prev.first
                return result
            }
        }
        return result
    }

    /** [taskId]를 옆 칸으로 보내고 1.3초 뒤 보이는 분할 칸 작업을 돌려준다. */
    private fun trySide(taskId: String): List<String> {
        ShizukuAccess.shell("$SPLIT_CMD moveToSideStage $taskId $SIDE_POSITION")
        Thread.sleep(1300)
        return visibleSplitTasks()
    }

    /**
     * 분할을 푼다: [keep]을 남기는 exitSplitScreen(폰) → 안 되면 나머지를 removeFromSideStage(에뮬레이터 AOSP 14) →
     * 그래도 안 되면 모든 칸을 removeFromSideStage. 풀렸으면 true.
     */
    private fun unsplit(keep: String, tasks: List<String>): Boolean {
        ShizukuAccess.shell("$SPLIT_CMD exitSplitScreen $keep")
        Thread.sleep(1200)
        if (visibleSplitTasks().size < 2) return true
        tasks.filter { it != keep }.forEach { ShizukuAccess.shell("$SPLIT_CMD removeFromSideStage $it") }
        Thread.sleep(800)
        if (visibleSplitTasks().size >= 2) {
            visibleSplitTasks().forEach { ShizukuAccess.shell("$SPLIT_CMD removeFromSideStage $it") }
            Thread.sleep(800)
        }
        // removeFromSideStage로 풀면 남은 앱이 분할 칸 모드로 남아 다음 나누기가 실패했다(에뮬레이터 자동 시험).
        // 그 앱을 다시 앞으로 띄워 전체 화면으로 돌려 놓는다
        bringToFront(keep)
        Thread.sleep(800)
        return visibleSplitTasks().size < 2
    }

    /** 작업 [taskId]의 맨 위 화면을 다시 띄워 그 작업을 전체 화면으로 앞에 둔다. */
    private fun bringToFront(taskId: String) {
        val comp = Regex("""ActivityRecord\{[0-9a-f]+ u\d+ ([\w.]+/[\w.$]+) t$taskId\b""")
            .find(ShizukuAccess.shell("dumpsys activity activities | grep -E 'ActivityRecord\\{'")?.out.orEmpty())?.groupValues?.get(1) ?: return
        ShizukuAccess.shell("am start -n $comp -f 0x10200000")
    }

    /** 가로 까딱으로 나눌 때 앞에 있던 앱(풀 때 이 앱을 앞에 남긴다). */
    @Volatile
    private var lastFront: String? = null

    /** 바로 전에 옆 칸에 보냈던 앱(다시 까딱하면 같은 짝으로 나눈다). */
    @Volatile
    private var lastSide: String? = null

    /** 옆 칸에 보내 볼 최근 앱 수. */
    private const val MAX_TRIES = 3

    /** 지금 보이는 분할 칸의 앱 작업 번호. 둘 이상이면 분할 중이다(분할을 푼 뒤에도 남은 앱 하나가 분할 칸 모드로 남을 수 있다). */
    internal fun visibleSplitTasks(): List<String> = Regex("""Task\{[0-9a-f]+ #(\d+) type=standard[^\n]*visible=true[^\n]*mode=multi-window""")
        .findAll(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())
        .map { it.groupValues[1] }.distinct().toList()

    /**
     * 앞 앱([frontId]) 다음으로 최근에 쓴 일반 앱 작업들, 최근 순. 화면 쌓임 순서가 아니라 최근 앱 목록(dumpsys activity recents)을 쓴다
     * (쌓임 순서는 검색창 같은 작업이 앞에 와 엉뚱한 앱을 골랐다, 에뮬레이터 자동 시험). 바로 전에 함께 나눴던 앱이 있으면 맨 앞에 둔다.
     * 시스템 UI·GOAT·공유 창(intentresolver)·같은 앱은 뺀다.
     */
    private fun previousTasks(context: Context, frontId: String): List<Pair<String, String>> {
        val all = Regex("""Recent #\d+: Task\{[0-9a-f]+ #(\d+) type=standard A=\d+:([\w.]+)""")
            .findAll(ShizukuAccess.shell("dumpsys activity recents | grep -E 'Recent #'")?.out.orEmpty())
            .map { it.groupValues[1] to it.groupValues[2] }
            .distinctBy { it.first }
            .toList()
        val frontPkg = all.firstOrNull { it.first == frontId }?.second
        val skip = setOf("com.android.systemui", "com.android.intentresolver", "android")
        val list = all.filter { (id, pkg) -> id != frontId && pkg != frontPkg && pkg !in skip && !pkg.startsWith(context.packageName) }
        val side = lastSide
        return list.sortedByDescending { it.first == side }
    }

    /**
     * 진단 로그용: 시스템 UI(WMShell)가 받는 화면 분할 명령 목록. 안드로이드 17 실기기에서 startTaskWithAllApps를 보내면
     * dump 머리글만 돌아오고 분할되지 않아(1.17.2), 지금 펌웨어가 어떤 명령을 받는지 확인하려고 남긴다. 작업 스레드에서 부른다.
     * splitscreen을 인자 없이 보내면 AOSP 시스템 UI가 ArrayIndexOutOfBounds로 죽어(1.17.5 전수 점검, 문제 신고 때마다) help만 쓴다.
     */
    fun diagnoseSplit(): String {
        val help = ShizukuAccess.shell("dumpsys activity service SystemUIService WMShell help")?.out ?: return "Shizuku가 연결되지 않아 읽지 못했습니다."
        val lines = help.lines()
        // 'split'·'stage'가 들어간 줄과 그 아래 설명 몇 줄을 모은다
        val keep = sortedSetOf<Int>()
        lines.forEachIndexed { i, l -> if (l.contains("split", true) || l.contains("stage", true)) (i..minOf(i + 3, lines.lastIndex)).forEach { keep += it } }
        val picked = keep.map { lines[it].trimEnd() }.take(120)
        return buildString {
            appendLine("[WMShell help 중 분할 관련 ${picked.size}줄]")
            picked.forEach { appendLine(it.take(200)) }
        }
    }

    /** 켜져 있는 동안 지금 앱·모드를 보여 주고, 알림 버튼으로 모드를 바꿀 수 있게 한다. */
    private fun notifyMode(context: Context, s: Applied) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MODE_CHANNEL, "자동 플렉스 모드 바꾸기", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val b = NotificationCompat.Builder(context, MODE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("${s.label} · ${AutoFlex.modeLabel(s.mode)}")
            .setContentText("접은 동안 이 앱에서 쓸 방식을 바꿀 수 있습니다")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        // 알림 버튼은 세 개까지 보이므로 지금 모드를 뺀 나머지 셋을 둔다(지금 모드는 제목에 보인다)
        AutoFlex.MODES.filter { it != s.mode }.forEach { m ->
            val pi = PendingIntent.getBroadcast(
                context,
                m,
                Intent(context, FlexModeReceiver::class.java).putExtra(FlexModeReceiver.EXTRA_MODE, m),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, AutoFlex.modeLabel(m), pi)
        }
        runCatching { nm.notify(MODE_NOTIFICATION, b.build()) }
    }

    /** 반쯤 접힘 강제 중인지(GOAT가 건 것). 재부팅하면 시스템이 풀어 주므로 메모리에만 둔다. */
    @Volatile
    var forced = false
        private set

    /** 시스템에 반쯤 접힘(HALF_OPENED) 상태를 강제로 알린다. 성공하면 null, 실패하면 이유. [reset]으로 되돌린다. */
    fun forceHalfOpened(context: Context): String? {
        // 상태 번호는 기기마다 다르므로(폴드8은 2) 목록에서 HALF_OPENED의 번호를 찾아 쓴다
        val states = ShizukuAccess.shell("cmd device_state print-states") ?: return "Shizuku 연결이 필요합니다."
        val id = Regex("identifier=(\\d+), name='HALF_OPENED'").find(states.out)?.groupValues?.get(1)
            ?: return "이 기기에는 반쯤 접힘 상태가 없습니다."
        val r = ShizukuAccess.shell("cmd device_state state $id") ?: return "Shizuku 연결이 필요합니다."
        val err = if (r.ok) null else r.err.ifBlank { r.out }.trim().take(120).ifBlank { "실패" }
        if (err == null) forced = true
        LogRepository.from(context).add("실험실 플렉스: 반쯤 접힘 강제" + (err?.let { " 실패($it)" } ?: ""))
        return err
    }

    /** 강제한 접힘 상태를 풀어 실제 힌지 상태로 돌아간다. */
    fun resetPosture(context: Context): String? {
        val r = ShizukuAccess.shell("cmd device_state state reset") ?: return "Shizuku 연결이 필요합니다."
        val err = if (r.ok) null else r.err.ifBlank { r.out }.trim().take(120).ifBlank { "실패" }
        if (err == null) forced = false
        LogRepository.from(context).add("실험실 플렉스: 접힘 상태 원래대로" + (err?.let { " 실패($it)" } ?: ""))
        return err
    }

    /** GOAT로 등록해 둔 앱. */
    fun registered(context: Context): Set<String> = prefs(context).getStringSet("apps", emptySet()).orEmpty()

    /** GOAT가 등록한 앱을 모두 해제한다. 실패한 앱 수를 돌려준다. */
    fun restoreApps(context: Context): Int = registered(context).count { setApp(context, it, false) != null }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
