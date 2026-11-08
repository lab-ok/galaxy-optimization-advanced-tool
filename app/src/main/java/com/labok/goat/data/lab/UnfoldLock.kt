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

    /**
     * 화면이 꺼질 때(힌지 감시가 부른다). 접힌 채였고 '깨우지 않기'면 닫힘 상태로 고정한다.
     * 접혔는지는 시스템 접힘 상태로 판단한다. 힌지 감시의 기록(closed)은 화면이 켜져 있는 동안만 쌓여, 펼쳐 둔 채 꺼진 뒤 접고
     * 바깥 화면을 켰다 끄면 힌지 값이 새로 오지 않아 '펼침'으로 남는다(11-06 소장님 "바깥 화면에서 전원 누르고 접었다 펴면 안 먹힘").
     */
    fun onScreenOff(context: Context, closed: Boolean) {
        offWhileClosed = closed
        if (mode(context) != MODE_NO_WAKE) return
        LogRepository.from(context).add("실험 깨우지 않기: 화면 꺼짐 알림 받음")
        val app = context.applicationContext
        io.execute {
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) {
                LogRepository.from(app).add("실험 깨우지 않기: 화면 꺼짐, Shizuku 연결이 없어 고정하지 못함")
                return@execute
            }
            val now = ShizukuAccess.shell("cmd device_state state")?.out.orEmpty()
            val committed = Regex("Committed state: .*name='(\\w+)'").find(now)?.groupValues?.get(1)
            if (committed != "CLOSED") {
                // 펼친 채 꺼졌다. 꺼진 채 접었다가 다시 펴면 화면 꺼짐 알림이 없어 고정할 때를 놓친다(11-06 23:53 실기 "똑같다").
                // 1.52.11은 힌지를 지켜보다 접히면 고정하려 했지만, 꺼진 동안 접힘 값이 오지 않았다(11-07 00:01 실기, 로그 없음).
                // 그래서 지금 상태(펼침)를 그대로 고정해 둔다. 이미 펼침이라 깨우지 않고, 꺼진 채 접었다 펴도 상태가 바뀌지 않아
                // 깨우기가 일어나지 않는다. 전원으로 켜면 고정을 풀어 접혀 있으면 바깥 화면으로 간다. 접힘 값이 오면 닫힘으로 바꾼다.
                val ok = committed == "OPENED" && pinState("OPENED")
                pinned = ok
                watchHinge(app)
                LogRepository.from(app).add("실험 깨우지 않기: 펼친 채 꺼짐($committed), 펼침으로 고정 " + (if (ok) "성공" else "안 함") + " · 힌지 감시")
                return@execute
            }
            pinned = pinState("CLOSED")
            if (pinned) watchHinge(app)
            LogRepository.from(app).add("실험 깨우지 않기: 접힌 채 꺼져 닫힘 상태로 고정 " + if (pinned) "성공" else "실패")
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
            if (ShizukuAccess.status.value != ShizukuAccess.State.READY) {
                if (pinned) LogRepository.from(app).add("실험 깨우지 않기: $why · Shizuku 연결이 없어 고정을 풀지 못함")
                return@execute
            }
            val r = ShizukuAccess.shell("cmd device_state state reset")
            val was = pinned
            pinned = false
            pinnedName = null
            stopHinge(app)
            LogRepository.from(app).add("실험 깨우지 않기: $why · 고정 풂 " + if (r?.ok == true) "성공" else "실패 ${r?.err?.take(80)}")
        }
    }

    /*
     * 바깥 화면 AOD(11-06 소장님 "안 깨움은 잘 되는데 바깥 화면 AOD가 계속 남는다", "펼치면 AOD는 안쪽 화면에 나와야 한다"):
     * 닫힘으로 고정해 두면 시스템은 접혀 있다고 보고 바깥 화면에 AOD를 계속 띄운다. 고정한 동안 힌지를 따로 지켜보다가 실제로
     * 펼쳐지면(120° 넘음) 고정을 '펼침'으로 바꿔 안쪽 화면으로 넘긴다. 셸 요청으로 바뀐 상태는 펼침 깨우기를 거치지 않기를 기대하지만
     * (추정), 화면이 켜져 버리면 곧바로 다시 꺼서 안쪽 화면 AOD로 돌린다. 펼친 채 다시 접으면 닫힘으로 되돌린다. 결과는 로그에 남긴다.
     */
    private var hingeListener: android.hardware.SensorEventListener? = null
    private var lastZone = -1

    /** 지금 고정한 접힘 상태 이름(CLOSED·OPENED, 고정 안 했으면 null). */
    @Volatile private var pinnedName: String? = null

    private fun pinState(name: String): Boolean {
        val states = ShizukuAccess.shell("cmd device_state print-states")?.out.orEmpty()
        val id = Regex("identifier=(\\d+), name='$name'").find(states)?.groupValues?.get(1) ?: return false
        val ok = ShizukuAccess.shell("cmd device_state state $id")?.ok == true
        if (ok) pinnedName = name
        return ok
    }

    private fun watchHinge(app: Context) {
        if (hingeListener != null) return
        val sm = app.getSystemService(android.hardware.SensorManager::class.java)
        // 화면이 꺼진 동안에도 오도록 깨우는 힌지 센서를 먼저 고른다(없으면 보통 센서)
        val hinge = sm.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE, true) ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE) ?: return
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val a = e.values[0]
                // 조사용: 감시를 시작한 뒤 처음 받은 값과 접힘·펼침 경계를 넘은 값만 남긴다
                val zone = if (a < 20f) {
                    0
                } else if (a > 120f) {
                    2
                } else {
                    1
                }
                if (zone != lastZone) {
                    lastZone = zone
                    LogRepository.from(app).add("실험 깨우지 않기: 힌지 ${a.toInt()}°")
                }
                if (a > 120f && pinned && pinnedName == "CLOSED") io.execute { toInner(app) }
                if (a < 20f && pinnedName != "CLOSED") io.execute { toCover(app) }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        hingeListener = l
        lastZone = -1
        LogRepository.from(app).add("실험 깨우지 않기: 힌지 감시 시작(" + (if (hinge.isWakeUpSensor) "깨우는 센서" else "보통 센서") + ")")
        sm.registerListener(l, hinge, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopHinge(app: Context) {
        hingeListener?.let { app.getSystemService(android.hardware.SensorManager::class.java).unregisterListener(it) }
        hingeListener = null
    }

    /*
     * 1.52.7 실기(11-06 23:27~28, 소장님): 안쪽 화면을 쓰는 후보 중 화면을 깨우지 않은 것은 TENT뿐이었는데, TENT는 바깥 화면에
     * AOD를 띄웠다. CONCURRENT_INNER_DEFAULT·HALF_OPENED·OPENED는 모두 깨웠고, 소장님은 OPENED가 가장 낫다고 했다.
     * 그래서 OPENED로 넘기되, 켜지는 순간을 30ms 간격으로 지켜보다가 곧바로 꺼서 번쩍이는 시간을 줄인다.
     * 끈 뒤 펼침 깨우기가 한 번 더 들어올 수 있어(1.52.5에서 0.09초 뒤 다시 켜짐) 1.5초 동안 최대 세 번까지 다시 끈다.
     */
    private fun toInner(app: Context) {
        if (!pinned || pinnedName != "CLOSED") return
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        val at = SystemClock.elapsedRealtime()
        val ok = pinState("OPENED")
        var wokeAt = -1L
        var offs = 0
        while (SystemClock.elapsedRealtime() - at < 1500 && offs < 3) {
            if (pm.isInteractive) {
                if (wokeAt < 0) wokeAt = SystemClock.elapsedRealtime() - at
                ShizukuAccess.shell("input keyevent 223")
                offs++
                // 꺼짐이 반영될 때까지 기다려야 같은 켜짐을 두 번 세지 않는다
                val sent = SystemClock.elapsedRealtime()
                while (pm.isInteractive && SystemClock.elapsedRealtime() - sent < 300) Thread.sleep(20)
            }
            Thread.sleep(30)
        }
        LogRepository.from(app).add(
            "실험 깨우지 않기: 펼침 감지, OPENED로 넘김 " + (if (ok) "성공" else "실패") + " · " +
                (if (wokeAt >= 0) "${wokeAt}ms에 켜져 ${offs}번 끔, 마지막에 " + (if (pm.isInteractive) "켜져 있음" else "꺼짐") else "화면 켜지지 않음")
        )
    }

    /** 펼친 채 꺼져 있다가 접혔다: 닫힘으로 고정해 다음 펼침에 깨지 않게 하고 바깥 화면 AOD를 쓴다. */
    private fun toCover(app: Context) {
        if (pinnedName == "CLOSED") return
        // 화면을 쓰고 있으면 고정하지 않는다(켜지면 감시를 멈추지만 그 사이에 들어온 값일 수 있다)
        if (app.getSystemService(android.os.PowerManager::class.java).isInteractive) {
            LogRepository.from(app).add("실험 깨우지 않기: 접힘 감지했지만 화면이 켜져 있어 고정하지 않음")
            return
        }
        val ok = pinState("CLOSED")
        pinned = ok
        LogRepository.from(app).add("실험 깨우지 않기: 꺼진 채 다시 접힘, 닫힘으로 고정 " + if (ok) "성공" else "실패")
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
    fun diagnose(context: Context): String = buildString {
        appendLine("# GOAT 펼침 잠금화면 조사 · ${com.labok.goat.BuildConfig.VERSION_NAME}")
        // 실험이 실제로 어떻게 돌았는지 함께 본다(앱 로그 중 이 실험 줄만, 최근 40줄)
        appendLine("## 앱 로그(실험 줄)")
        LogRepository.from(context).read().filter { "깨우지 않기" in it || "펼침 잠금화면" in it }.takeLast(40).forEach { appendLine(it) }
        appendLine("## 화면·전원")
        appendLine(ShizukuAccess.shell("dumpsys power | grep -iE 'mWakefulness=|mLastWakeTime|mLastSleepTime|mLastSleepReason|mLastWakeReason|Display Power' | head -8")?.out ?: "(읽지 못함)")
        for (ns in listOf("system", "secure", "global")) {
            appendLine("## settings $ns (잠금·접힘 관련)")
            appendLine(ShizukuAccess.shell("settings list $ns | grep -iE 'fold|unfold|cover|keyguard|lock|bouncer|flex|posture|continu|aod' ")?.out ?: "(읽지 못함)")
        }
        appendLine("## device_state")
        appendLine(ShizukuAccess.shell("cmd device_state print-states; cmd device_state state")?.out ?: "(읽지 못함)")
        appendLine("## 키가드(dumpsys window)")
        appendLine(ShizukuAccess.shell("dumpsys window | grep -iE 'keyguard|bouncer|occluded|dream' | head -40")?.out ?: "(읽지 못함)")
        appendLine("## 키가드 서비스")
        appendLine(ShizukuAccess.shell("dumpsys activity service com.android.systemui/.keyguard.KeyguardService | head -80")?.out ?: "(읽지 못함)")
    }
}
