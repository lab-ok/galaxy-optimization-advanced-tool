package com.labok.goat.data.lab

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 가로 까딱 분할 자동 시험(실험실): 까딱 없이 [FlexPanel.sideSplitNow]를 여러 경우로 돌려 안정성을 본다.
 * 시험 앱 두 개(브라우저 A, 설정 B)와 세 번째 앱(C)을 띄워 가며 경우마다 분할 여부·보이는 앱·좌우 배치·걸린 시간을 남기고,
 * 끝나면 분할을 풀고 화면 방향을 원래대로 돌린 뒤 GOAT를 앞으로 올려 결과 파일 공유 창을 자동으로 띄운다([State.shareReady]).
 * 시스템 UI를 다시 띄우는 명령은 쓰지 않는다(1.11.x 분할 시험은 실기에서 시스템 UI를 재시작시켰다). 사용자 요청(2026-10-18 안정화).
 */
object SideSplitTest {
    private const val PREF = "side_split_test"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [progress]는 0~1, [shareReady]는 방금 끝난 결과 파일(화면이 공유 창을 한 번 띄우고 [consumeShare]로 지운다). */
    data class State(val running: Boolean = false, val progress: Float? = null, val status: String = "", val shareReady: Uri? = null)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    fun consumeShare() {
        stateFlow.value = stateFlow.value.copy(shareReady = null)
    }

    fun lastFile(context: Context): Uri? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("uri", null)?.let(Uri::parse)

    fun start(context: Context) {
        if (stateFlow.value.running) return
        val app = context.applicationContext
        stateFlow.value = State(running = true, progress = 0f, status = "시험 준비 중")
        scope.launch {
            val uri = PublicLogFile.saveNew(app, PublicLogFile.timestampedName("side-split-test"), "")
            uri?.let { app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("uri", it.toString()).apply() }
            val rotation0 = ShizukuAccess.shell("settings get system user_rotation")?.out?.trim()
            val accel0 = ShizukuAccess.shell("settings get system accelerometer_rotation")?.out?.trim()
            runCatching {
                run(app) { line -> uri?.let { PublicLogFile.appendTo(app, it, line + "\n") } }
            }.onFailure { e -> uri?.let { PublicLogFile.appendTo(app, it, "\n(중단: ${e.javaClass.simpleName} ${e.message})\n") } }
            // 정리: 분할을 풀고 화면 방향 설정을 원래 값으로
            if (FlexPanel.visibleSplitTasks().size >= 2) FlexPanel.sideSplitNow(app)
            restore("user_rotation", rotation0)
            restore("accelerometer_rotation", accel0)
            stateFlow.value = State(running = false, shareReady = uri)
            LogRepository.from(app).add("실험실: 가로 까딱 분할 자동 시험")
            ShizukuAccess.shell("am start -n ${app.packageName}/.ui.MainActivity -f 0x20000000")
        }
    }

    private fun restore(key: String, value: String?) {
        if (value == null || value == "null") ShizukuAccess.shell("settings delete system $key") else ShizukuAccess.shell("settings put system $key $value")
    }

    private fun progress(p: Float, what: String) {
        stateFlow.value = stateFlow.value.copy(progress = p.coerceIn(0f, 1f), status = what)
    }

    /** 보이는 일반 앱 작업: 번호 → (패키지, 창 모드, 왼쪽·위 좌표). */
    private data class Shown(val id: String, val pkg: String, val mode: String, val left: Int?, val top: Int?)

    private fun shown(): List<Shown> {
        val lines = ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{|mBounds='")?.out.orEmpty().lines()
        val out = LinkedHashMap<String, Shown>()
        var last: Shown? = null
        for (line in lines) {
            val t = Regex("""Task\{[0-9a-f]+ #(\d+) type=standard A=\d+:([\w.]+)[^\n]*?visible=true[^\n]*?mode=([\w-]+)""").find(line)
            if (t != null) {
                last = Shown(t.groupValues[1], t.groupValues[2], t.groupValues[3], null, null).also { out.putIfAbsent(it.id, it) }
                continue
            }
            if (Regex("""Task\{""").containsMatchIn(line)) {
                last = null
                continue
            }
            val b = Regex("""mBounds=Rect\((-?\d+), (-?\d+) - """).find(line) ?: continue
            val cur = last ?: continue
            if (out[cur.id]?.left == null) out[cur.id] = cur.copy(left = b.groupValues[1].toInt(), top = b.groupValues[2].toInt())
            last = null
        }
        return out.values.toList()
    }

    private fun describe(list: List<Shown>) = if (list.isEmpty()) "보이는 앱 없음" else list.joinToString(" · ") { "${it.pkg.substringAfterLast('.')}(${it.mode}${it.left?.let { l -> " $l,${it.top}" } ?: ""})" }

    /** [pkg]의 첫 화면을 셸에서 띄운다(이미 있으면 그 작업을 앞으로). monkey는 Shizuku 셸에서 앱을 띄우지 못했다(에뮬레이터 시험). */
    private fun launch(context: Context, pkg: String) {
        val comp = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
        if (comp != null) ShizukuAccess.shell("am start -n $comp -f 0x10200000") else ShizukuAccess.shell("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        Thread.sleep(1500)
    }

    private fun run(context: Context, out: (String) -> Unit) {
        val pm = context.packageManager
        fun has(p: String) = pm.getLaunchIntentForPackage(p) != null
        val a = listOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.google.android.youtube", "org.chromium.webview_shell").firstOrNull(::has)
        val b = listOf("com.android.settings").firstOrNull(::has)
        val c = listOf("com.samsung.android.calendar", "com.google.android.calendar", "com.sec.android.app.myfiles", "com.google.android.apps.photos", "com.android.contacts").firstOrNull(::has)
        out("가로 까딱 분할 자동 시험 · GOAT ${runCatching { pm.getPackageInfo(context.packageName, 0).versionName }.getOrNull()}")
        out("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        out("접힘 상태: ${ShizukuAccess.shell("cmd device_state print-state")?.out?.trim() ?: "-"}")
        out("시험 앱: A=${a ?: "-"} B=${b ?: "-"} C=${c ?: "-"}")
        if (a == null || b == null) {
            out("시험할 앱(브라우저·설정)이 없어 멈춤")
            return
        }
        out("")
        var pass = 0
        var total = 0
        fun case(name: String, step: Float, expect: (List<Shown>) -> Boolean, body: () -> String) {
            progress(step, name)
            val t0 = SystemClock.elapsedRealtime()
            val r = runCatching(body).getOrElse { "오류 ${it.javaClass.simpleName}: ${it.message}" }
            val now = shown()
            val ok = expect(now)
            total++
            if (ok) pass++
            out("[${if (ok) "통과" else "실패"}] $name · ${SystemClock.elapsedRealtime() - t0}ms")
            out("    결과: $r")
            out("    화면: ${describe(now)}")
        }
        fun isSplit(l: List<Shown>) = l.count { it.mode.startsWith("multi") } >= 2
        fun front(l: List<Shown>) = l.firstOrNull()?.pkg

        // 준비: 분할이 남아 있으면 풀고, B를 띄운 뒤 A를 앞으로(A 앞, B 최근)
        if (FlexPanel.visibleSplitTasks().size >= 2) FlexPanel.sideSplitNow(context)
        launch(context, b)
        launch(context, a)

        case("1. 기본 나누기(A 앞, B 최근)", 0.05f, { isSplit(it) && it.any { s -> s.pkg.startsWith(a) } && it.any { s -> s.pkg.startsWith(b) } }) { FlexPanel.sideSplitNow(context) }
        case("2. 풀기 뒤 원래 앱(A)이 앞에 남음", 0.15f, { !isSplit(it) && front(it)?.startsWith(a) == true }) { FlexPanel.sideSplitNow(context) }

        // 3. 반복 5번
        var cycles = 0
        case("3. 나누기·풀기 5번 반복", 0.25f, { cycles == 5 && !isSplit(it) }) {
            repeat(5) { i ->
                progress(0.25f + 0.05f * i, "3. 반복 ${i + 1}/5")
                // 사람이 까딱하는 간격처럼 1초씩 쉰다
                FlexPanel.sideSplitNow(context)
                val s = isSplit(shown())
                Thread.sleep(1000)
                FlexPanel.sideSplitNow(context)
                val u = !isSplit(shown())
                Thread.sleep(1000)
                if (s && u) cycles++
            }
            "성공 $cycles/5"
        }

        // 4. 세 번째 앱으로 바꾼 뒤 나누기: C 앞, 최근은 A
        if (c != null) {
            launch(context, c)
            case("4. 다른 앱(C)으로 바꾼 뒤 나누기", 0.55f, { isSplit(it) && it.any { s -> s.pkg.contains(c.substringAfterLast('.')) } }) { FlexPanel.sideSplitNow(context) }
            case("5. 분할 중 C 쪽에서 풀기", 0.62f, { !isSplit(it) && front(it)?.contains(c.substringAfterLast('.')) == true }) { FlexPanel.sideSplitNow(context) }
        } else {
            out("[건너뜀] 4·5. 세 번째 앱이 없음")
        }

        // 6. 가로 화면(왼쪽으로 돌린 가로)에서 나누기·풀기
        launch(context, b)
        launch(context, a)
        ShizukuAccess.shell("settings put system accelerometer_rotation 0")
        ShizukuAccess.shell("settings put system user_rotation 1")
        Thread.sleep(1500)
        case("6. 가로 화면에서 나누기", 0.72f, { isSplit(it) }) { FlexPanel.sideSplitNow(context) }
        case("7. 가로 화면에서 풀기", 0.82f, { !isSplit(it) && front(it)?.startsWith(a) == true }) { FlexPanel.sideSplitNow(context) }
        ShizukuAccess.shell("settings put system user_rotation 0")
        Thread.sleep(1000)

        // 8. 빠르게 두 번(나누는 중 다시 까딱): 두 번째는 앞의 결과를 보고 풀어야 한다
        case("8. 연달아 두 번(나누기 → 바로 풀기)", 0.9f, { !isSplit(it) }) {
            FlexPanel.sideSplitNow(context) + " / " + FlexPanel.sideSplitNow(context)
        }

        out("")
        out("요약: $pass/$total 통과")
        progress(1f, "정리 중")
    }
}
