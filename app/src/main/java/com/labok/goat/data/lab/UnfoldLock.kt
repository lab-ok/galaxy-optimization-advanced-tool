package com.labok.goat.data.lab

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.util.concurrent.Executors

/**
 * 실험(11-06 소장님): 잠긴 채 펼치면 One UI는 곧바로 PIN·패턴 입력창을 띄운다. 펼친 상태에서 전원 버튼을 누른 것처럼
 * 시계·배경만 있는 잠금화면에 머물게 할 수 있는지 시험한다. 잠금은 풀지 않는다(위로 밀면 그대로 PIN을 묻는다).
 * 삼성 공식 설정이 없어(One UI 기본 동작) 두 방식을 실기에서 비교한다. 결과는 앱 로그에 남기고, 조사 파일로 숨은 설정 키를 찾는다.
 *
 * - [MODE_BACK]: 펼침을 감지하면 곧바로 뒤로 가기를 보내 입력창만 닫는다.
 * - [MODE_SCREEN]: 화면을 한 번 껐다가 다시 켠다(펼친 상태에서 전원 버튼을 두 번 누른 것과 같다).
 */
object UnfoldLock {
    const val MODE_OFF = 0
    const val MODE_BACK = 1
    const val MODE_SCREEN = 2

    /** 화면이 꺼진 채 접혀 있다가 펼쳐 켜지면 곧바로 다시 끈다(잠금 유지). 11-06 소장님: "화면 꺼진 상태에서 펼치면 화면 꺼진 채로" */
    const val MODE_STAY_OFF = 3

    /**
     * 펼쳐도 아예 깨우지 않는다(11-06 소장님 "아예 웨이크업 안 되게"). 펼침으로 화면이 켜지는 것은 기기 접힘 상태가 닫힘 → 펼침으로
     * 바뀔 때 시스템이 깨우기 때문이다. 접힌 채 화면이 꺼지면 접힘 상태를 '닫힘'으로 고정해 두고(셸 device_state 요청, 플렉스 모드가
     * 반쯤 접힘을 거는 것과 같은 방식), 전원 버튼으로 화면이 켜지면 고정을 푼다. 시스템 설정 파일은 건드리지 않는다.
     */
    const val MODE_NO_WAKE = 4

    @Volatile private var pinned = false

    /** 화면이 꺼질 때(힌지 감시가 부른다). 접힌 채였고 '깨우지 않기'면 닫힘 상태로 고정한다. */
    fun onScreenOff(context: Context, closed: Boolean) {
        offWhileClosed = closed
        if (mode(context) != MODE_NO_WAKE || !closed) return
        val app = context.applicationContext
        io.execute {
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return@execute
            val states = ShizukuAccess.shell("cmd device_state print-states")?.out.orEmpty()
            val id = Regex("identifier=(\\d+), name='CLOSED'").find(states)?.groupValues?.get(1) ?: return@execute
            val r = ShizukuAccess.shell("cmd device_state state $id")
            pinned = r?.ok == true
            LogRepository.from(app).add("실험 깨우지 않기: 접힌 채 꺼져 닫힘 상태로 고정 " + if (pinned) "성공" else "실패 ${r?.err?.take(80)}")
        }
    }

    /**
     * 화면이 켜질 때(전원 버튼 등). 고정해 두었으면 푼다(펼쳐 둔 채면 이때 안쪽 화면으로 넘어간다).
     * 1.52.3은 푸는 명령을 `cmd device_state reset`으로 잘못 보내 실패해, 펼친 채 전원을 눌러도 바깥 화면만 켜졌다(11-06 소장님).
     * 앱이 다시 시작돼 고정 여부를 잊어도 풀리도록, '안 깨움'이면 고정 기록과 상관없이 켜질 때마다 푼다.
     */
    fun onScreenOn(context: Context) {
        if (!pinned && mode(context) != MODE_NO_WAKE) return
        unpin(context.applicationContext, "화면이 켜짐")
    }

    private fun unpin(app: Context, why: String) {
        io.execute {
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return@execute
            val r = ShizukuAccess.shell("cmd device_state state reset")
            val was = pinned
            pinned = false
            if (was || r?.ok != true) LogRepository.from(app).add("실험 깨우지 않기: $why · 고정 풂 " + if (r?.ok == true) "성공" else "실패 ${r?.err?.take(80)}")
        }
    }

    /** 접힌 채 화면이 꺼졌는지(힌지 감시가 화면이 꺼질 때 기록한다). */
    @Volatile var offWhileClosed = false

    private const val PREF = "unfold_lock"
    private val io = Executors.newSingleThreadExecutor()

    fun mode(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("mode", MODE_OFF)

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("mode", mode).apply()
        LogRepository.from(context).add("실험 펼침 잠금화면: 방식 $mode")
        // 다른 방식으로 바꾸면 남아 있을지 모르는 닫힘 고정을 푼다
        if (mode != MODE_NO_WAKE) unpin(context.applicationContext, "방식 변경")
    }

    fun enabled(context: Context) = mode(context) != MODE_OFF

    /** 닫힌 상태에서 펼쳐졌을 때 힌지 감시가 부른다(작업 스레드에서 처리). */
    fun onUnfold(context: Context) {
        val m = mode(context)
        if (m == MODE_OFF || m == MODE_NO_WAKE) return
        val wasOff = offWhileClosed
        offWhileClosed = false
        if (m == MODE_STAY_OFF && !wasOff) {
            LogRepository.from(context).add("실험 펼침 잠금화면: 화면이 켜진 채 펼쳐 건너뜀(꺼진 채 유지)")
            return
        }
        val app = context.applicationContext
        val at = SystemClock.elapsedRealtime()
        io.execute {
            val km = app.getSystemService(KeyguardManager::class.java)
            if (!km.isKeyguardLocked || !km.isDeviceSecure) {
                LogRepository.from(app).add("실험 펼침 잠금화면: 잠겨 있지 않아 건너뜀")
                return@execute
            }
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) {
                LogRepository.from(app).add("실험 펼침 잠금화면: Shizuku 연결이 없어 건너뜀")
                return@execute
            }
            val before = bouncer()
            val r = when (m) {
                MODE_BACK -> ShizukuAccess.shell("input keyevent 4")
                MODE_STAY_OFF -> ShizukuAccess.shell("input keyevent 223")
                else -> ShizukuAccess.shell("input keyevent 223; sleep 0.3; input keyevent 224")
            }
            Thread.sleep(400)
            LogRepository.from(app).add(
                "실험 펼침 잠금화면: 방식 $m · 감지 후 ${SystemClock.elapsedRealtime() - at}ms · 입력창 전 [$before] 후 [${bouncer()}] · " +
                    (if (r?.ok == true) "명령 성공" else "명령 실패 ${r?.err?.take(80)}")
            )
        }
    }

    /** 키가드·입력창 상태 한 줄(조사용). */
    private fun bouncer(): String = ShizukuAccess.shell("dumpsys window | grep -iE 'mKeyguardShowing|isKeyguardShowing|mBouncer|keyguardOccluded' | head -4")
        ?.out?.lines()?.joinToString(" / ") { it.trim() }?.take(240).orEmpty()

    /**
     * 조사 파일 내용: 잠금·접힘과 관련 있어 보이는 시스템 설정 키, 키가드 상태, 기기 접힘 상태 목록.
     * 삼성이 펼칠 때 입력창을 띄우는 동작을 바꾸는 숨은 설정이 있는지 실기 값으로 찾는다.
     */
    fun diagnose(): String = buildString {
        appendLine("# GOAT 펼침 잠금화면 조사")
        for (ns in listOf("system", "secure", "global")) {
            appendLine("## settings $ns (잠금·접힘 관련)")
            appendLine(ShizukuAccess.shell("settings list $ns | grep -iE 'fold|unfold|cover|keyguard|lock|bouncer|flex|posture|continu' ")?.out ?: "(읽지 못함)")
        }
        appendLine("## device_state")
        appendLine(ShizukuAccess.shell("cmd device_state print-states; cmd device_state state")?.out ?: "(읽지 못함)")
        appendLine("## 키가드(dumpsys window)")
        appendLine(ShizukuAccess.shell("dumpsys window | grep -iE 'keyguard|bouncer|occluded|dream' | head -40")?.out ?: "(읽지 못함)")
        appendLine("## 키가드 서비스")
        appendLine(ShizukuAccess.shell("dumpsys activity service com.android.systemui/.keyguard.KeyguardService | head -80")?.out ?: "(읽지 못함)")
    }
}
