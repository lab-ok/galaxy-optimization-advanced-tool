package com.labok.goat.data.lab

import android.content.Context
import android.content.res.Resources
import android.os.Build
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.UserId
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 플렉스 모드 패널 제어(폴드8 자동 플렉스가 쓴다).
 * - 반쯤 접힘 강제/해제: 시스템에 HALF_OPENED 상태를 알리거나 푼다(Shizuku `cmd device_state`). 폴드8에서 이렇게 하면 패널이 뜬다(실기 확인).
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

    /** 시스템이 [pkg]에 대해 돌려주는 플렉스 패널 값 그대로(정수 비트 또는 참/거짓). 물어볼 수 없으면 null. */
    fun rawSupport(pkg: String): Any? = runCatching {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return null
        val m = wm().javaClass.methods.firstOrNull { it.name == "getSupportsFlexPanel" && it.parameterTypes.size == 2 } ?: return null
        m.invoke(wm(), UserId.current(), pkg)
    }.getOrNull()

    /** 패널과 관련 있어 보이는 이름(flex panel·flex mode·flexible table·tabletop). 일반 touchpad·tablet 이름은 너무 많아 뺀다. */
    private val PANEL_WORDS = Regex("(?i)flex.?panel|flex_?mode|flexible.?table|flex.?table|tabletop")

    /**
     * 진단 진행 알림: [step]은 한 단계를 시작할 때 그 단계가 차지하는 진행률 구간(from~to)과 할 일을 알리고,
     * [inner]는 그 단계 안에서 얼마나 했는지(0~1)를 알 수 있을 때(파일을 읽은 양) 알린다.
     */
    class Progress(val step: (Float, Float, String) -> Unit = { _, _, _ -> }, val inner: (Float) -> Unit = {})

    /** 패키지 정보([dump]) 속 구성요소(화면·서비스·수신기) 이름 중 패널과 관련 있어 보이는 것. */
    private fun panelComponents(dump: String): List<String> = Regex("""[a-zA-Z][\w.]*/[\w.$]+""").findAll(dump).map { it.value }
        .filter { PANEL_WORDS.containsMatchIn(it) || it.contains("flex", ignoreCase = true) }.distinct().take(80).toList()

    /** 시스템 UI·설정 앱의 패널 관련 구성요소(살리기 시도용, 빠른 길). */
    private fun panelComponents(): List<String> = panelComponents(listOf("com.android.systemui", "com.android.settings").joinToString("\n") { ShizukuAccess.shell("pm dump $it")?.out.orEmpty() })

    /**
     * 터치패널 진단(3차): 폴드8 시스템에 삼성 플렉스 패널 코드가 남아 있는지 본다. 시스템 UI·설정 앱·멀티 창·시스템 서비스 파일의
     * 코드 이름에서 패널 흔적을 찾고, 패널 관련 구성요소·앱별 패널 값·설정·접힘 상태를 함께 모은다(읽기만 한다).
     * 단계마다 [progress]로 번호·할 일·진행률 구간을 알리고, 시스템 파일은 읽은 양만큼 알린다.
     */
    fun diagnose(context: Context, progress: Progress = Progress()): String = buildString {
        val flex = Regex("(?i)flex")
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { it.out.ifBlank { it.err } }?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨"
        // 패키지 정보는 중요한 앱부터 따로 읽고, 전체 앱 검색(기기에 따라 몇 분 걸림)은 따로 한 단계로 보여 준다
        val pkgs = listOf("com.android.systemui" to "시스템 UI", "com.android.settings" to "설정 앱", "com.samsung.android.multistar" to "멀티 창")
        val dumps = pkgs.withIndex().map { (i, p) ->
            progress.step(0.02f + 0.03f * i, 0.05f + 0.03f * i, "${i + 1}/8 ${p.second} 구성요소 읽는 중")
            ShizukuAccess.shell("pm dump ${p.first}")?.out.orEmpty()
        }
        progress.step(0.11f, 0.2f, "4/8 전체 앱 정보에서 패널 이름 찾는 중(1~3분 걸릴 수 있음)")
        val all = ShizukuAccess.shell("dumpsys package | grep -i -E 'flex|tabletop'")?.out.orEmpty()
        val comps = panelComponents(dumps.joinToString("\n") + "\n" + all)
        progress.step(0.2f, 0.22f, "5/8 읽을 시스템 파일 고르는 중")
        val files = pkgs.map { it.first }
            .flatMap { pkg -> ShizukuAccess.shell("pm path $pkg")?.out.orEmpty().lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() } } +
            listOf("/system/framework/services.jar", "/system/framework/framework.jar") +
            ShizukuAccess.shell("ls /system/framework/oat/arm64/")?.out.orEmpty().lines().map { it.trim() }
                .filter { it.endsWith(".vdex") && (it.startsWith("services") || it.startsWith("framework")) }.map { "/system/framework/oat/arm64/$it" }
        // 시스템 파일 읽기는 진행률 22~90%를 파일 수로 나눠 쓰고, 파일 안에서는 읽은 양만큼 오른다
        val traces = files.withIndex().associate { (i, f) ->
            val from = 0.22f + 0.68f * i / files.size
            progress.step(from, 0.22f + 0.68f * (i + 1) / files.size, "6/8 시스템 파일 ${i + 1}/${files.size} ${f.substringAfterLast('/')} 읽는 중")
            f to DexStrings.find(f, PANEL_WORDS, onRead = progress.inner)
        }
        progress.step(0.9f, 0.93f, "7/8 창 관리자·앱별 패널 값 읽는 중")
        val hit = traces.count { (_, v) -> v.none { it.startsWith("(") } }
        appendLine("요약: 패널 관련 구성요소 ${comps.size}개 · 패널 코드 흔적이 있는 파일 $hit/${files.size}개 · 창 관리자 패널 함수 ${if (hasAppApi()) "있음" else "없음"}")
        appendLine()
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        appendLine("Shizuku: ${ShizukuAccess.status.value}")
        appendLine()
        appendLine("== 패널 관련 구성요소(시스템 UI·전체 패키지) ==")
        appendLine(comps.joinToString("\n").ifBlank { "(없음)" })
        appendLine()
        appendLine("== 시스템 파일 속 패널 코드 이름 ==")
        traces.forEach { (file, names) ->
            appendLine("[$file]")
            names.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("== 창 관리자 함수(flex) ==")
        appendLine(runCatching { wm().javaClass.methods.filter { flex.containsMatchIn(it.name) }.map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }}): ${it.returnType.simpleName}" }.distinct().sorted().joinToString("\n") }.getOrElse { "(읽지 못함: ${HiddenApi.describe(it)})" })
        appendLine()
        appendLine("== 앱별 패널 값(getSupportsFlexPanel) ==")
        listOf("com.google.android.youtube", "com.android.chrome", "com.sec.android.app.camera", "com.sec.android.gallery3d", "com.android.settings").forEach { pkg ->
            val v = rawSupport(pkg)
            appendLine("$pkg = ${v ?: "(못 읽음)"}${(v as? Int)?.let { " (0x${Integer.toHexString(it)})" }.orEmpty()}")
        }
        appendLine()
        progress.step(0.93f, 0.96f, "8/8 설정·접힘 상태 모으는 중")
        appendLine("== 설정 키(flex) ==")
        listOf("system", "secure", "global").forEach { table ->
            val r = ShizukuAccess.shell("settings list $table")
            if (r == null) appendLine("[$table] Shizuku 연결 안 됨") else r.out.lineSequence().filter { flex.containsMatchIn(it.substringBefore('=')) }.forEach { appendLine("[$table] $it") }
        }
        appendLine()
        appendLine("== 접힘 상태 ==")
        appendLine(sh("cmd device_state print-states"))
        appendLine()
        appendLine("== 설정 앱의 플렉스 패널 화면 정보 ==")
        appendLine(sh("dumpsys package com.android.settings | grep -i -A6 'FlexModePanel' | head -60"))
    }

    /** 폴드8 시스템 UI 안에 남아 있는 삼성 패널 화면(진단 3차에서 코드 이름으로 찾음). */
    private const val PANEL_ACTIVITY = "com.android.systemui/com.android.wm.shell.controlpanel.activity.FlexPanelActivity"

    /** 플렉스 패널을 부르는 시스템 신호(프레임워크 코드 이름에서 찾음). */
    private val PANEL_ACTIONS = listOf("android.intent.action.AUTORUN_FLEX_PANEL", "android.intent.action.EXPAND_FLEX_PANEL")

    /**
     * 삼성 터치패널 살리기 시도: 패널 설정을 켜고, 크롬(없으면 유튜브)을 앞에 띄운 뒤 반쯤 접힘을 걸고 시스템 UI에 남아 있는 삼성 패널 화면을
     * 직접 열고 패널 신호를 보낸다. 패널 화면이 왜 닫히는지 시스템 로그를 함께 남긴다. 패널이 뜨는지 사용자가 볼 수 있게 10초 동안 그대로 두었다가 접힘 상태를 원래대로 돌린다.
     * 한 일과 결과를 줄마다 돌려준다. 작업 스레드에서 부른다.
     */
    fun revive(context: Context, emit: (String) -> Unit) {
        // 한 줄씩 바로 내보낸다(화면이 다시 만들어지거나 중간에 끊겨도 그때까지의 결과가 파일에 남게)
        fun appendLine(text: String = "") = emit(text + "\n")
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { r -> (if (r.ok) "성공" else "실패") + r.out.ifBlank { r.err }.trim().take(200).let { if (it.isEmpty()) "" else " · $it" } } ?: "Shizuku 연결 안 됨"
        appendLine("패널 설정 켜기 → ${sh("settings put global flex_mode_panel_enabled 1")}")
        // 시스템 리소스에 삼성이 넣어 둔 '플렉스 화면 앱' 목록이 있으면(다른 기종 framework-res에서 확인된 이름) 읽어 둔다.
        // 있으면 GOAT가 플렉스 지원 앱을 구분하는 데 그대로 쓸 수 있다
        listOf("flex_mode_app_list", "flex_panel_default_list", "flex_panel_default_app_list", "flex_mode_panel_default_list").forEach { name ->
            val id = Resources.getSystem().getIdentifier(name, "array", "android")
            val items = if (id == 0) null else runCatching { Resources.getSystem().getStringArray(id).toList() }.getOrNull()
            appendLine("시스템 목록 $name → ${items?.let { "${it.size}개: ${it.joinToString(" | ").take(1500)}" } ?: "(없음)"}")
        }
        listOf("global", "secure", "system").forEach { t ->
            val v = ShizukuAccess.shell("settings get $t flex_mode_apps_to_show_flex_mode_panel_automatically")?.out?.trim()
            if (!v.isNullOrBlank() && v != "null") appendLine("자동 표시 앱 목록($t) = ${v.take(200)}")
        }
        val pm = context.packageManager
        val app = listOf("com.android.chrome", "com.google.android.youtube").firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        if (app != null) {
            appendLine("$app 띄우기 → ${sh("monkey -p $app -c android.intent.category.LAUNCHER 1")}")
            Thread.sleep(1500)
        }
        appendLine("반쯤 접힘 걸기 → ${forceHalfOpened(context) ?: "성공"}")
        Thread.sleep(1000)
        // 패널은 화면 분할의 아래 칸('controlpanel')이 아니면 스스로 닫혔다(4차: 다 그리고 터치패드 창까지 만든 뒤 닫힘).
        // 5차: 크롬을 먼저 분할 칸으로 옮긴 뒤 패널을 열어 아래 칸으로 들어가는지 본다. 로그는 패널이 열린 때부터 빠짐없이 남긴다
        val logFilter = "flexpanel|controlpanel|touchpad|split|stage|wm_finish_activity|wm_create_activity|AndroidRuntime|FATAL"
        fun panelLog(title: String) {
            Thread.sleep(3000)
            val log = ShizukuAccess.shell("logcat -d -b main,system,events,crash -v time | grep -i -E '$logFilter' | grep -v -E 'SurfaceFlinger|BLASTBufferQueue|SurfaceControlRegistry' | head -600")?.out?.trim().orEmpty()
            appendLine("── $title 로그 ──")
            appendLine(log.ifBlank { "(로그 없음)" })
            appendLine("──")
        }
        // 8차: 7차 로그에서 삼성 자동 실행 장치(FlexPanelStartController)가 폴드8에서도 살아 있었고
        // "OnDeviceStateChanged: isTableMode=false"를 찍었다. 탁자 모드는 '물리' 접힘 상태의 속성으로 판정하는데, 지금까지는
        // 덮어쓰는 상태(state)만 바꿨다. 기기가 물리 상태(base-state) 덮어쓰기를 지원하면 반쯤 접힘으로 바꿔 자동 실행을 기다린다
        val help = ShizukuAccess.shell("cmd device_state help 2>&1")?.out.orEmpty()
        appendLine("── 접힘 상태 명령·속성 ──")
        appendLine(help.trim().ifBlank { "(없음)" })
        appendLine(ShizukuAccess.shell("dumpsys device_state 2>&1 | head -60")?.out?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine("──")
        if (help.contains("base-state")) {
            val half = Regex("identifier=(\\d+), name='HALF_OPENED'").find(ShizukuAccess.shell("cmd device_state print-states")?.out.orEmpty())?.groupValues?.get(1) ?: "2"
            // 10차: 9차에서 회전을 0~3 어느 쪽으로 고정해도 isSupportRotation=false였다. 삼성 코드(디컴파일 덤프)에서 이 값은
            // 시스템 UI가 처음 뜰 때의 회전을 한 번만 읽어 두는 final 필드다(mRotation, 0·2만 허용). 그래서 회전을 0으로 고정한 채
            // 시스템 UI를 다시 띄워 그 값을 0으로 새로 읽게 한 뒤 반쯤 접힘을 건다. 화면이 몇 초 깜빡이고 상태 표시줄이 다시 그려진다
            val autoRot0 = ShizukuAccess.shell("settings get system accelerometer_rotation")?.out?.trim()
            val userRot0 = ShizukuAccess.shell("settings get system user_rotation")?.out?.trim()
            appendLine("시도 0: 회전 0°로 고정 → ${sh("settings put system accelerometer_rotation 0 && settings put system user_rotation 0")}")
            Thread.sleep(2000)
            appendLine("지금 화면 회전 → ${sh("dumpsys window displays | grep -m1 -o 'mRotation=[A-Z_0-9]*'")}")
            val before = ShizukuAccess.shell("pidof com.android.systemui")?.out?.trim()
            appendLine("시도 0: 시스템 UI 다시 띄우기 → ${sh("am crash com.android.systemui 2>&1 || am force-stop com.android.systemui 2>&1")}")
            var after: String? = null
            for (i in 0 until 20) {
                Thread.sleep(1000)
                after = ShizukuAccess.shell("pidof com.android.systemui")?.out?.trim()?.takeIf { it.isNotBlank() && it != before }
                if (after != null) break
            }
            appendLine("시스템 UI 프로세스 ${before ?: "-"} → ${after ?: "(바뀌지 않음)"}")
            Thread.sleep(6000)
            ShizukuAccess.shell("logcat -b all -c")
            appendLine("시도 0: 물리 상태를 반쯤 접힘으로 → ${sh("cmd device_state base-state $half")}")
            Thread.sleep(3000)
            val log = ShizukuAccess.shell("logcat -d -b main,system,events,crash -v time | grep -E 'FlexPanelStartController|FlexPanelActivity' | grep -v -E 'at (com|android|java|dalvik)\\.' | head -40")?.out?.trim().orEmpty()
            appendLine("── 시도 0 로그 ──")
            appendLine(log.ifBlank { "(로그 없음)" })
            appendLine("──")
            val opened = log.contains("wm_create_activity") && !log.contains("wm_finish_activity")
            appendLine(
                "시도 0 결과: ${if (opened) {
                    "패널이 떠서 유지됨"
                } else if (log.contains("wm_create_activity")) {
                    "패널이 떴다가 닫힘"
                } else {
                    "패널이 뜨지 않음"
                }}"
            )
            if (opened) {
                appendLine("패널을 10초 동안 그대로 둡니다. 화면을 눌러 보세요")
                Thread.sleep(10_000)
            }
            appendLine("물리 상태 되돌리기 → ${sh("cmd device_state base-state reset")}")
            Thread.sleep(1000)
            appendLine("회전 설정 되돌리기 → ${sh("settings put system accelerometer_rotation ${autoRot0?.takeIf { it != "null" } ?: "1"} && settings put system user_rotation ${userRot0?.takeIf { it != "null" } ?: "0"}")}")
            Thread.sleep(1500)
        } else {
            appendLine("시도 0: 이 기기는 물리 상태 덮어쓰기(base-state)를 지원하지 않아 건너뜀")
        }
        val task = app?.let { pkg ->
            Regex("""Task\{[0-9a-f]+ #(\d+)[^}]*$pkg""").find(ShizukuAccess.shell("dumpsys activity activities")?.out.orEmpty())?.groupValues?.get(1)
        }
        appendLine("앞에 있는 앱 작업 번호 = ${task ?: "(못 찾음)"}")
        // 6차: 화면을 잠시 가로로 고정해 분할이 위아래가 되게 하고(5차는 세로라 좌우로 나뉨), 크롬을 위 칸에 넣은 뒤
        // 패널을 '옆 칸으로 열기'(FLAG_ACTIVITY_LAUNCH_ADJACENT) 플래그로 열어 아래 칸에 들어가는지 본다. 끝나면 회전 설정을 되돌린다
        val autoRot = ShizukuAccess.shell("settings get system accelerometer_rotation")?.out?.trim()
        val userRot = ShizukuAccess.shell("settings get system user_rotation")?.out?.trim()
        appendLine("회전 잠시 가로로 고정 → ${sh("settings put system accelerometer_rotation 0 && settings put system user_rotation 1")}")
        Thread.sleep(1500)
        ShizukuAccess.shell("logcat -b all -c")
        if (task != null) {
            appendLine("시도 1: ${app}을 분할 위 칸으로 옮기기 → ${sh("dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage $task 0 2>&1 | tail -3")}")
            Thread.sleep(1500)
        }
        appendLine("시도 1: 패널을 옆(아래) 칸으로 열기 → ${sh("am start -f 0x18001000 -n '$PANEL_ACTIVITY'")}")
        panelLog("시도 1(가로·분할·옆 칸)")
        if (task != null) appendLine("분할 풀기 → ${sh("dumpsys activity service SystemUIService WMShell splitscreen exitSplitScreen $task 2>&1 | tail -3")}")
        appendLine("회전 설정 되돌리기 → ${sh("settings put system accelerometer_rotation ${autoRot?.takeIf { it != "null" } ?: "1"} && settings put system user_rotation ${userRot?.takeIf { it != "null" } ?: "0"}")}")
        Thread.sleep(1500)
        ShizukuAccess.shell("logcat -b all -c")
        appendLine("시도 2: 멀티 창 모드로 패널 열기 → ${sh("am start --windowingMode 6 -n '$PANEL_ACTIVITY'")}")
        panelLog("시도 2(멀티 창)")
        // 7차: 삼성 시스템이 자동으로 띄울 때와 같은 모양으로 연다(FlexPanelStartController 디컴파일: AUTORUN_FLEX_PANEL 동작,
        // 플래그 0x14241000(새 작업·위 정리·옆 칸 포함), 기본 화면 0번)
        Thread.sleep(1500)
        ShizukuAccess.shell("logcat -b all -c")
        appendLine("시도 3: 시스템과 같은 방식(AUTORUN)으로 열기 → ${sh("am start -a android.intent.action.AUTORUN_FLEX_PANEL -f 0x14241000 --display 0 -n '$PANEL_ACTIVITY'")}")
        panelLog("시도 3(AUTORUN)")
        PANEL_ACTIONS.forEach { appendLine("${it.substringAfterLast('.')} 신호 → ${sh("am broadcast -a $it")}") }
        appendLine("지금 맨 앞 화면 → ${sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")}")
        // 패널이 떴는지 볼 시간을 준 뒤 접힘 상태를 되돌린다
        Thread.sleep(10_000)
        appendLine("접힘 상태 되돌리기 → ${resetPosture(context) ?: "성공"}")
        // 다음 시도를 고르려고, 시스템이 화면 분할·패널을 다룰 때 쓰는 명령과 함수 이름을 모은다
        appendLine()
        appendLine("── WMShell 명령 목록 ──")
        appendLine(ShizukuAccess.shell("dumpsys activity service SystemUIService WMShell help 2>&1 | head -200")?.out?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine("──")
        val sysui = ShizukuAccess.shell("pm path com.android.systemui")?.out.orEmpty().lines().map { it.removePrefix("package:").trim() }.firstOrNull { it.isNotEmpty() }
        if (sysui != null) {
            appendLine("── 시스템 UI 속 패널·분할 함수 이름 ──")
            DexStrings.find(sysui, Regex("(?i)controlpanel|flexpanel|flex_panel|startflex|launchflex|showflex|flexmode"), limit = 300).forEach { appendLine(it) }
            appendLine("──")
        }
        LogRepository.from(context).add("실험실 플렉스: 삼성 터치패널 살리기 시도")
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
