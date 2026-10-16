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

    /** 화면에 보이는 전체 화면 일반 앱 작업(번호, 패키지). 시스템 UI·GOAT·홈은 뺀다. */
    private fun frontTask(context: Context): Pair<String, String>? = Regex("""Task\{[0-9a-f]+ #(\d+) type=standard A=\d+:([\w.]+)[^\n]*visible=true[^\n]*mode=fullscreen""")
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
     * 가로 까딱 분할(실험실): 화면이 분할돼 있으면 풀고, 아니면 앞에 있는 앱으로 삼성 공식 화면 분할을 연다.
     * 다른 칸은 삼성의 앱 고르기가 맡는다(startTaskWithAllApps, 삼성 SystemUI SplitScreenShellCommandHandler).
     * 분할 시험(1.11.2·1.11.6)에서 접힘 상태를 덮어쓰지 않으면 공식 분할은 좌우로 안정적으로 열렸다. 그래서 반쯤 접힘은 걸지 않는다.
     */
    fun sideSplit(context: Context) = queue.execute {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return@execute
        val log = LogRepository.from(context)
        val dump = ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty()
        // 분할 중이면 보이는 분할 작업 중 맨 위(포커스) 작업을 남기고 푼다
        val inSplit = Regex("""Task\{[0-9a-f]+ #(\d+) type=standard[^\n]*visible=true[^\n]*mode=multi-window""").find(dump)?.groupValues?.get(1)
        if (inSplit != null) {
            val r = ShizukuAccess.shell("$SPLIT_CMD exitSplitScreen $inSplit")
            log.add("가로 까딱: 분할 풀기(#$inSplit) ${if (r?.ok == true) "성공" else "실패"}")
            return@execute
        }
        val front = frontTask(context)
        if (front == null) {
            log.add("가로 까딱: 포그라운드 앱이 없어 분할하지 않음")
            return@execute
        }
        val r = ShizukuAccess.shell("$SPLIT_CMD startTaskWithAllApps ${front.first}")
        log.add("가로 까딱: ${front.second}(#${front.first}) 분할 열기 ${if (r?.ok == true) "성공" else "실패"}")
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
