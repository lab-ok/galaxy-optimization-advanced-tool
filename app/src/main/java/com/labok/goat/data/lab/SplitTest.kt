package com.labok.goat.data.lab

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
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
 * 화면 분할 시험(실험실): 자동 플렉스의 분할 화면이 실기에서 열리지 않거나 좌우로 열렸다 닫혀, 삼성 공식 화면 분할을 여는 방법을
 * 접힘 상태와 함께 모두 돌려 본다. 크롬(쓰던 앱 역할)과 GOAT 앱 고르기(아래 칸 역할) 두 작업으로,
 * 방법마다 두 작업이 분할이 됐는지·위아래/좌우인지·누가 위인지, 삼성 분할 로그(StageCoordinator)를 남기고 화면을 캡처한다.
 * 결과는 Download/GOAT에 한 줄씩 바로 쌓이고(중간에 끊겨도 남게), 끝나면 GOAT를 앞으로 올려 공유 창을 띄운다.
 * 근거: 삼성 SystemUI 디컴파일(oneui-8.0) SplitScreenShellCommandHandler의 숨은 명령들.
 */
object SplitTest {
    private const val PREF = "split_test"
    private const val SPLIT = "dumpsys activity service SystemUIService WMShell splitscreen"
    private const val PICK = "com.labok.goat/.ui.lab.FlexPickActivity"
    private const val SHOTS = "/sdcard/Download/GOAT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [progress]는 0~1, [shareReady]는 방금 끝난 결과 파일(화면이 공유 창을 한 번 띄우고 [consumeShare]로 지운다). */
    data class State(val running: Boolean = false, val progress: Float? = null, val status: String = "", val shareReady: Uri? = null)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    fun consumeShare() {
        stateFlow.value = stateFlow.value.copy(shareReady = null)
    }

    fun lastFile(context: Context): Uri? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("uri", null)?.let(Uri::parse)

    /** 작업 한 개의 지금 상태(창 모드·보임·크기). */
    private data class TaskInfo(val id: String, val mode: String, val visible: Boolean, val bounds: IntArray?)

    private fun tasks(): Map<String, TaskInfo> {
        val out = ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{|mBounds='")?.out.orEmpty().lines()
        val map = LinkedHashMap<String, TaskInfo>()
        var last: TaskInfo? = null
        for (line in out) {
            val t = Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*?visible=(true|false)[^\n]*?mode=([\w-]+)""").find(line)
            if (t != null) {
                last = TaskInfo(t.groupValues[1], t.groupValues[3], t.groupValues[2] == "true", null).also { map.putIfAbsent(it.id, it) }
                continue
            }
            val b = Regex("""mBounds=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)""").find(line) ?: continue
            val cur = last ?: continue
            if (map[cur.id]?.bounds == null) map[cur.id] = cur.copy(bounds = b.groupValues.drop(1).map { it.toInt() }.toIntArray())
            last = null
        }
        return map
    }

    private fun taskOf(pattern: String): String? = Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*A=\d+:$pattern""")
        .find(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())?.groupValues?.get(1)

    fun start(context: Context) {
        if (stateFlow.value.running) return
        val app = context.applicationContext
        stateFlow.value = State(running = true, progress = 0f, status = "시험 준비 중")
        scope.launch {
            val uri = PublicLogFile.saveNew(app, PublicLogFile.timestampedName("split-test"), "")
            uri?.let { app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("uri", it.toString()).apply() }
            runCatching {
                run(app) { line -> uri?.let { PublicLogFile.appendTo(app, it, line + "\n") } }
            }.onFailure { e -> uri?.let { PublicLogFile.appendTo(app, it, "\n(중단: ${e.message})\n") } }
            stateFlow.value = State(running = false, shareReady = uri)
            LogRepository.from(app).add("실험실: 화면 분할 시험")
            if (uri != null) ShizukuAccess.shell("am start -n ${app.packageName}/.ui.MainActivity -f 0x20000000")
        }
    }

    private fun progress(p: Float, what: String) {
        stateFlow.value = stateFlow.value.copy(progress = p.coerceIn(0f, 1f), status = what)
    }

    private fun run(context: Context, out: (String) -> Unit) {
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { r -> (if (r.ok) "성공" else "실패") + r.out.ifBlank { r.err }.trim().take(160).let { if (it.isEmpty()) "" else " · $it" } } ?: "Shizuku 연결 안 됨"
        val real = Point().also {
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java).getDisplay(0)?.getRealSize(it)
        }
        out("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        out("화면: ${real.x}x${real.y} · 회전 ${ShizukuAccess.shell("dumpsys window displays | grep -m1 -o 'mRotation=[A-Z_0-9]*'")?.out?.trim() ?: "-"}")
        out("접힘 상태: ${ShizukuAccess.shell("cmd device_state print-state")?.out?.trim() ?: "-"}")
        out("분할 명령 도움말: ${ShizukuAccess.shell("$SPLIT help 2>&1 | grep -i -A1 split | head -20")?.out?.trim()?.replace('\n', ' ') ?: "-"}")
        val chrome = listOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.google.android.youtube").firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
        if (chrome == null) {
            out("시험할 앱(크롬·삼성 인터넷·유튜브)이 없어 멈춤")
            return
        }
        val half = Regex("identifier=(\\d+), name='HALF_OPENED'").find(ShizukuAccess.shell("cmd device_state print-states")?.out.orEmpty())?.groupValues?.get(1) ?: "2"
        val freeform0 = ShizukuAccess.shell("settings get global enable_freeform_support")?.out?.trim()
        out("시험 앱: $chrome · 반쯤 접힘 번호 $half · 팝업 허용 원래 값 ${freeform0 ?: "-"}")
        out("")

        // 접힘 상태 3가지: 덮어쓰기 없음 / 자동 플렉스와 같은 덮어쓰기(state) / 터치패널과 같은 물리 상태 덮어쓰기(base-state)
        val postures = listOf(
            Triple("P0", "접힘 덮어쓰기 없음", { sh("cmd device_state state reset; cmd device_state base-state reset") }),
            Triple("P1", "반쯤 접힘(state, 자동 플렉스와 같음)", { sh("cmd device_state state $half") }),
            Triple("P2", "반쯤 접힘(base-state, 터치패널과 같음)", { sh("cmd device_state base-state $half") })
        )
        // 분할 여는 방법: a는 크롬 작업, b는 앱 고르기 작업
        val methods: List<Pair<String, (String, String) -> String>> = listOf(
            "startTasks 크롬 고르기" to { a, b -> sh("$SPLIT startTasks $a $b") },
            "startTasks 고르기 크롬" to { a, b -> sh("$SPLIT startTasks $b $a") },
            "startTasks 크롬 고르기 + switchSplitPosition" to { a, b -> sh("$SPLIT startTasks $a $b").also { Thread.sleep(700) } + " / " + sh("$SPLIT switchSplitPosition") },
            "startSplitTasks 크롬 고르기 0.5" to { a, b -> sh("$SPLIT startSplitTasks $a $b 0.5") },
            "startSplitTasks 고르기 크롬 0.5" to { a, b -> sh("$SPLIT startSplitTasks $b $a 0.5") },
            "startTaskWithAllApps 크롬" to { a, _ -> sh("$SPLIT startTaskWithAllApps $a") },
            "moveToSideStage 크롬 0" to { a, _ -> sh("$SPLIT moveToSideStage $a 0") },
            "moveToSideStage 크롬 1" to { a, _ -> sh("$SPLIT moveToSideStage $a 1") },
            "크롬 앞에서 고르기를 옆 칸으로(LAUNCH_ADJACENT)" to { _, _ -> sh("monkey -p $chrome -c android.intent.category.LAUNCHER 1").also { Thread.sleep(800) } + " / " + sh("am start -f 0x10001000 -n $PICK") },
            "startIntents 크롬 설정" to { _, _ -> sh("$SPLIT startIntents $chrome com.android.settings 0") },
            "openInSplitWithAllApps 크롬" to { a, _ -> sh("$SPLIT openInSplitWithAllApps 0 $a $chrome") }
        )
        val total = postures.size * methods.size
        val summary = mutableListOf<String>()
        var n = 0
        for ((pTag, pName, applyPosture) in postures) {
            for ((mi, m) in methods.withIndex()) {
                n++
                val tag = "$pTag-${mi + 1}"
                progress((n - 1f) / total, "$n/$total $pName · ${m.first}")
                // 준비: 분할을 풀고, 크롬을 앞에 띄운 뒤 앱 고르기를 일반 새 작업으로 띄워 두 작업 번호를 얻는다
                tasks().values.filter { it.mode.startsWith("multi") }.forEach { ShizukuAccess.shell("$SPLIT exitSplitScreen ${it.id}") }
                ShizukuAccess.shell("cmd device_state state reset; cmd device_state base-state reset")
                ShizukuAccess.shell("monkey -p $chrome -c android.intent.category.LAUNCHER 1")
                Thread.sleep(1200)
                ShizukuAccess.shell("am start -f 0x10000000 -n $PICK")
                Thread.sleep(1000)
                val a = taskOf(Regex.escape(chrome))
                val b = taskOf("com\\.labok\\.goat\\.flexpick")
                applyPosture()
                Thread.sleep(700)
                ShizukuAccess.shell("logcat -c")
                val ran = if (a == null || b == null) "작업을 찾지 못함(크롬 #${a ?: "-"}, 고르기 #${b ?: "-"})" else m.second(a, b)
                Thread.sleep(1500)
                val t = tasks()
                val ta = a?.let { t[it] }
                val tb = b?.let { t[it] }
                fun desc(name: String, x: TaskInfo?) = if (x == null) "$name 없음" else "$name #${x.id} ${x.mode}${if (x.visible) " 보임" else ""} ${x.bounds?.joinToString(",") ?: ""}"
                val split = ta?.mode?.startsWith("multi") == true && tb?.mode?.startsWith("multi") == true
                // 위아래/좌우와 누가 위(왼쪽)인지: 두 작업 크기로 가린다
                val layout = if (split && ta?.bounds != null && tb?.bounds != null) {
                    val ba = ta.bounds
                    val bb = tb.bounds
                    if (ba[0] == bb[0]) {
                        "위아래 · ${if (ba[1] < bb[1]) "크롬 위" else "고르기 위"}"
                    } else {
                        "좌우 · ${if (ba[0] < bb[0]) "크롬 왼쪽" else "고르기 왼쪽"}"
                    }
                } else {
                    "분할 아님"
                }
                val stage = ShizukuAccess.shell("logcat -d -s StageCoordinator SplitScreenController | grep -i -E 'fail|non resizable|not found|didn|toast|dismiss' | tail -3")?.out?.trim().orEmpty()
                out("── $tag $pName · ${m.first}")
                out("실행: $ran")
                out("결과: $layout · ${desc("크롬", ta)} · ${desc("고르기", tb)}")
                if (stage.isNotEmpty()) out("분할 로그: ${stage.replace("\n", " | ")}")
                // 캡처는 자동 플렉스와 같은 접힘(P1)과, 분할이 된 경우만 남긴다(파일이 너무 많아지지 않게)
                if (pTag == "P1" || split) out("캡처: ${sh("screencap -p $SHOTS/goat-split-$tag.png && echo $SHOTS/goat-split-$tag.png")}")
                out("")
                summary += "$tag ${m.first} [$pName] → $layout"
            }
        }
        // 정리: 분할을 풀고 접힘·팝업 설정을 원래대로
        tasks().values.filter { it.mode.startsWith("multi") }.forEach { ShizukuAccess.shell("$SPLIT exitSplitScreen ${it.id}") }
        ShizukuAccess.shell("cmd device_state state reset; cmd device_state base-state reset")
        taskOf("com\\.labok\\.goat\\.flexpick")?.let { FlexPanel.removeTask(it) }
        out("== 요약 ==")
        summary.forEach(out)
        progress(1f, "결과 파일 정리 중")
    }
}
