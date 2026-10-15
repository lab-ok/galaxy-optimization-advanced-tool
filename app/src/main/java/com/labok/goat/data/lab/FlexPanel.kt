package com.labok.goat.data.lab

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
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

    /**
     * 작업 하나를 닫는다(IActivityTaskManager.removeTask, Shizuku). 셸의 'am stack remove'·'cmd activity task remove'는
     * 폴드8에 없어 18차에서 시험이 만든 창이 남았다. 성공하면 "성공", 아니면 이유.
     */
    private fun removeTask(id: String): String = runCatching {
        val atm = HiddenApi.asInterface("android.app.IActivityTaskManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")))
        val m = atm.javaClass.methods.first { it.name == "removeTask" && it.parameterTypes.size == 1 }
        if (m.invoke(atm, id.toInt()) == false) "실패(작업 없음)" else "성공"
    }.getOrElse { "실패(${HiddenApi.describe(it)})" }

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
    /**
     * 삼성 자동 실행 장치의 'not support state' 로그 줄에서 자동 실행을 막은 조건만 골라 한 줄로 만든다
     * (값이 있어야 할 쪽과 반대인 것). 시험할 수 있게 뗐다.
     */
    internal fun panelBlockers(log: String): String {
        val line = log.lineSequence().lastOrNull { "not support state" in it } ?: return "판단 로그 없음"
        val good = mapOf(
            "mMwEnabled" to "true", "mIsTableMode" to "true", "mDesktopMode" to "false", "mFlexModePanelEnabled" to "true",
            "mIsImeShowing" to "false", "mIsQuickPanelShowing" to "false", "mIsTaskBarAppsEnabled" to "false",
            "mIsGlobalActionVisible" to "false", "mOnRecentsAnimation" to "false", "isSupportRotation" to "true",
            "mIsKeyguardVisible" to "false", "mIsInFixedRotation" to "false"
        )
        val bad = Regex("(\\w+)=(true|false)").findAll(line).map { it.groupValues[1] to it.groupValues[2] }
            .filter { (k, v) -> good[k] != null && good[k] != v }.map { (k, v) -> "$k=$v" }.toList()
        return if (bad.isEmpty()) "막은 조건 없음(다른 이유)" else "막은 조건: ${bad.joinToString(", ")}"
    }

    /** 살리기 시도 중 사용자에게 할 일을 알린다(진동 두 번 + 토스트). 작업 스레드에서 불러도 된다. */
    private fun notice(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
        val v = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 120), -1))
    }

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
            // 9~10차: 탁자 모드(isTableMode=true)까지는 되는데 회전 조건(isSupportRotation)만 계속 false였다.
            // 11차: 회전 조건에 대한 가능성을 차례로 시험했다(아래 도구들). 12차는 그중 통과한 방법(90°·270° 재시작)만 쓴다.
            //  가. 회전 정보 수집: 창 관리자·디스플레이·시스템 UI가 보는 회전 값
            //  나. 시스템 UI를 다시 띄우지 않고 회전 0~3 고정(cmd window user-rotation으로 확실히 고정) → 반쯤 접힘
            //      (폴드8 빌드가 회전을 바뀔 때마다 다시 읽는다면 여기서 통과한다)
            //  다. 회전 0~3마다 시스템 UI를 다시 띄운 뒤 반쯤 접힘(덤프처럼 처음 한 번만 읽는다면 여기서 통과한다.
            //      폴드8 안쪽 화면의 기본 방향이 가로라면 90°·270°에서만 통과할 수 있다)
            //  라. 덮어쓰기 없이 실제로: 자동 회전을 켜고, 사용자가 폰을 노트북처럼 가로로 반쯤 접어 세워 두는 동안
            //      시스템이 직접 판단하는 로그를 20초 동안 본다(센서 기준 회전·실제 접힘 상태)
            // 각 시험마다 자동 실행을 막은 조건을 한 줄로 요약한다
            val autoRot0 = ShizukuAccess.shell("settings get system accelerometer_rotation")?.out?.trim()
            val userRot0 = ShizukuAccess.shell("settings get system user_rotation")?.out?.trim()
            val watch = "FlexPanelStartController|FlexPanelActivity|FlexPanelTouchPad"
            fun rotationInfo(title: String) {
                appendLine("── $title: 회전 정보 ──")
                appendLine(ShizukuAccess.shell("dumpsys window displays | grep -E -i 'mRotation=|mFixedRotation|mDisplayRotation|mUserRotation|mLastOrientation|isFixedToUserRotation' | head -12")?.out?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
                appendLine(ShizukuAccess.shell("cmd window user-rotation 2>&1 | head -3")?.out?.trim()?.ifBlank { "(없음)" } ?: "")
                appendLine(ShizukuAccess.shell("dumpsys activity service SystemUIService 2>/dev/null | grep -i -E 'flexpanel|tablemode|isSupportRotation|mRotation' | head -20")?.out?.trim()?.ifBlank { "(시스템 UI 덤프에 관련 줄 없음)" } ?: "")
                appendLine("──")
            }

            // 반쯤 접힘을 걸고(또는 실제 접힘을 기다리고) [wait]ms 뒤 로그를 읽어 결과를 남긴다. 패널이 떠 있으면 true
            fun tryOnce(name: String, override: Boolean, wait: Long): Boolean {
                ShizukuAccess.shell("logcat -b all -c")
                if (override) appendLine("$name: 물리 상태를 반쯤 접힘으로 → ${sh("cmd device_state base-state $half")}")
                Thread.sleep(wait)
                val log = ShizukuAccess.shell("logcat -d -b main,system,events,crash -v time | grep -E '$watch' | grep -v -E 'at (com|android|java|dalvik)\\.' | head -60")?.out?.trim().orEmpty()
                appendLine("── $name 로그 ──")
                appendLine(log.ifBlank { "(로그 없음)" })
                appendLine("──")
                val opened = log.contains("wm_create_activity") && !log.contains("wm_finish_activity")
                val result = if (opened) {
                    "패널이 떠서 유지됨"
                } else if (log.contains("wm_create_activity")) {
                    "패널이 떴다가 닫힘"
                } else {
                    "패널이 뜨지 않음"
                }
                appendLine("$name 결과: $result · ${panelBlockers(log)}")
                if (opened) {
                    appendLine("패널을 10초 동안 그대로 둡니다. 화면을 눌러 보세요")
                    Thread.sleep(10_000)
                }
                if (override) {
                    appendLine("물리 상태 되돌리기 → ${sh("cmd device_state base-state reset")}")
                    Thread.sleep(1000)
                }
                return opened
            }
            fun lock(rot: Int) = sh("settings put system accelerometer_rotation 0 && settings put system user_rotation $rot && cmd window user-rotation lock $rot")
            fun restartSystemUi(name: String) {
                val before = ShizukuAccess.shell("pidof com.android.systemui")?.out?.trim()
                appendLine("$name: 시스템 UI 다시 띄우기 → ${sh("am crash com.android.systemui 2>&1 || am force-stop com.android.systemui 2>&1")}")
                var after: String? = null
                for (i in 0 until 20) {
                    Thread.sleep(1000)
                    after = ShizukuAccess.shell("pidof com.android.systemui")?.out?.trim()?.takeIf { it.isNotBlank() && it != before }
                    if (after != null) break
                }
                appendLine("시스템 UI 프로세스 ${before ?: "-"} → ${after ?: "(바뀌지 않음)"}")
                Thread.sleep(6000)
            }
            rotationInfo("시작")
            appendLine("상태 표시줄 관리자 서비스 → ${sh("service check sem_statusbar; service list | grep -i -E 'statusbar' | head -5")}")
            var done = false

            // 12차: 11차에서 회전 90°·270°(폴드8 안쪽 화면은 기본 방향이 가로)로 고정한 채 시스템 UI를 다시 띄우면
            // 자동 실행 조건이 모두 통과했지만, 삼성 코드 안에서 상태 표시줄 관리자(sem_statusbar 서비스, 폴드8에 없음)가 비어
            // NullPointerException이 나 자동 실행 명령까지 가지 못했다. 반쯤 접힘을 유지한 채 직접 열어도 바로 닫혔다.
            // 13차: 삼성 패널 코드(디컴파일 덤프 FlexPanelActivity)를 보면 패널은 ①반쯤 접힘(탁자 모드)이 풀리면 닫히고
            // ②onResume에서 멀티 창(화면 분할·팝업) 상태가 아니면 스스로 닫힌다. 12차는 ①만 지키고 전체 화면으로 열어 ②에 걸렸다.
            // 그래서 반쯤 접힘을 유지한 채 멀티 창으로 여는 방법을 모두 차례로 시험한다. 하나라도 떠서 유지되면 멈춘다
            fun appTask(): String? = app?.let { pkg ->
                Regex("""Task\{[0-9a-f]+ #(\d+)[^}]*$pkg""").find(ShizukuAccess.shell("dumpsys activity activities")?.out.orEmpty())?.groupValues?.get(1)
            }
            fun panelTask(): String? = Regex("""Task\{[0-9a-f]+ #(\d+)[^}]*com\.android\.systemui[^}]*""").find(
                ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{.*FlexPanelActivity|Task\\{[^}]*A=[0-9]+:com.android.systemui'")?.out.orEmpty()
            )?.groupValues?.get(1)
            val wmshell = "dumpsys activity service SystemUIService WMShell splitscreen"

            // 시험 하나: [prepare]로 분할 등을 준비하고 [cmd]로 패널을 연 뒤, 필요하면 [after]로 패널 작업을 옮기고 결과를 남긴다
            fun launch(name: String, title: String, cmd: String, prepare: (() -> Unit)? = null, after: (() -> Unit)? = null): Boolean {
                prepare?.invoke()
                ShizukuAccess.shell("logcat -b all -c")
                appendLine("$name: 반쯤 접힌 채 $title → ${sh(cmd)}")
                Thread.sleep(800)
                after?.invoke()
                Thread.sleep(2500)
                val log = ShizukuAccess.shell("logcat -d -b main,system,events,crash -v time | grep -i -E '$logFilter|FlexPanelActivity|not top child|dismissSplit' | grep -v -E 'SurfaceFlinger|BLASTBufferQueue|SurfaceControlRegistry|InsetsController|AppCompatDelegate' | head -200")?.out?.trim().orEmpty()
                appendLine("── $name $title 로그 ──")
                appendLine(log.ifBlank { "(로그 없음)" })
                appendLine("──")
                val created = log.contains("wm_create_activity") && log.contains("FlexPanelActivity")
                val finished = Regex("wm_finish_activity[^\\n]*FlexPanelActivity").containsMatchIn(log)
                val alive = ShizukuAccess.shell("dumpsys activity activities | grep -c 'FlexPanelActivity'")?.out?.trim()?.toIntOrNull() ?: 0
                val kept = created && !finished && alive > 0
                val result = if (kept) {
                    "패널이 떠서 유지됨"
                } else if (created) {
                    "패널이 떴다가 닫힘"
                } else {
                    "패널이 뜨지 않음"
                }
                appendLine("$name $title 결과: $result")
                if (kept) {
                    appendLine("패널을 20초 동안 그대로 둡니다. 화면 아래쪽 패널을 눌러 보세요")
                    notice(context, "패널이 떴습니다. 아래쪽 패널을 눌러 보세요(20초)")
                    Thread.sleep(20_000)
                } else {
                    // 다음 시험을 위해 이번에 만든 분할을 푼다
                    appTask()?.let { ShizukuAccess.shell("$wmshell exitSplitScreen $it") }
                    Thread.sleep(800)
                }
                return kept
            }
            // 14차: 13차 '가'(반쯤 접힘 유지 + 멀티 창으로 AUTORUN)에서 패널이 떠서 유지됐다(터치패드 창까지).
            // 다만 패널이 화면 전체를 덮고 앱이 사라졌다(실기 캡처: 홈 배경 위에 터치패드·버튼만). 삼성 플렉스 모드처럼
            // 앱은 위 칸, 패널은 아래 칸에 놓이도록 배치 방법을 모두 시험한다. 시험마다 화면을 캡처해 Download/GOAT에 남긴다.
            // 자동 실행 장치 조건은 직접 띄우기에 필요 없어(13차는 시스템 UI 재시작 없이 성공) 회전만 세로(접는 선 가로)로 고정한다
            val p = "-n '$PANEL_ACTIVITY'"
            val openPanel = "am start --windowingMode 6 -a android.intent.action.AUTORUN_FLEX_PANEL -f 0x14241000 --display 0 $p"
            val shotDir = "/sdcard/Download/GOAT"
            fun state(name: String, tag: String) {
                appendLine("── $name 배치 상태 ──")
                appendLine(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{|mBounds=|windowingMode=' | grep -v 'mBounds=Rect\\(0, 0 - 0, 0\\)' | head -30")?.out?.trim()?.ifBlank { "(없음)" } ?: "")
                appendLine("──")
                appendLine("$name 화면 캡처 → ${sh("screencap -p $shotDir/goat-flex-$tag.png && echo $shotDir/goat-flex-$tag.png")}")
            }
            fun alive() = (ShizukuAccess.shell("dumpsys activity activities | grep -c 'FlexPanelActivity'")?.out?.trim()?.toIntOrNull() ?: 0) > 0

            // 앱 작업이 실제로 보이는지(visible=true). 14차는 창 목록에 이름만 있어도 보인다고 잘못 셌다
            fun appVisible(): Boolean = app != null && ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{.*$app.*visible=true' | head -1")?.out?.isNotBlank() == true
            appendLine("시도 14: 회전 세로로 고정 → ${lock(3)}")
            Thread.sleep(2000)
            appendLine("지금 화면 회전 → ${sh("dumpsys window displays | grep -m1 -o 'mRotation=[A-Z_0-9]*'")}")
            // 배치 하나: 앱을 앞으로 가져오고 반쯤 접힘을 건 뒤 [steps]를 차례로 실행하고 결과를 남긴다
            fun arrange(tag: String, title: String, steps: List<Pair<String, () -> String>>): Boolean {
                val name = "시도 $tag"
                if (app != null) ShizukuAccess.shell("monkey -p $app -c android.intent.category.LAUNCHER 1")
                Thread.sleep(1500)
                ShizukuAccess.shell("logcat -b all -c")
                appendLine("$name($title): 물리 상태를 반쯤 접힘으로(유지) → ${sh("cmd device_state base-state $half")}")
                Thread.sleep(1500)
                for ((what, run) in steps) {
                    appendLine("$name: $what → ${run()}")
                    Thread.sleep(1500)
                }
                Thread.sleep(1500)
                val log = ShizukuAccess.shell("logcat -d -b main,system,events,crash -v time | grep -i -E 'wm_create_activity|wm_finish_activity|FlexPanelActivity|ShellSplitScreen|not top child|dismissSplit' | grep -v -E 'InsetsController|AppCompatDelegate' | head -120")?.out?.trim().orEmpty()
                appendLine("── $name 로그 ──")
                appendLine(log.ifBlank { "(로그 없음)" })
                appendLine("──")
                state(name, tag)
                val panel = alive()
                val shown = appVisible()
                val ok = panel && shown
                appendLine("$name 결과: 패널 ${if (panel) "유지" else "닫힘"} · 앱 ${if (shown) "보임" else "안 보임"}${if (ok) " → 성공 후보" else ""}")
                if (panel) {
                    notice(context, if (ok) "패널과 앱이 함께 보이는지 확인하고 눌러 보세요(15초)" else "패널만 떠 있습니다. 15초 동안 눌러 보세요")
                    Thread.sleep(15_000)
                }
                // 다음 배치를 위해 패널을 닫고 분할을 풀고 접힘을 되돌린다
                ShizukuAccess.shell("am broadcast -a android.intent.action.COLLAPSE_FLEX_PANEL -p com.android.systemui")
                appTask()?.let { ShizukuAccess.shell("$wmshell exitSplitScreen $it") }
                panelTask()?.let { ShizukuAccess.shell("am stack remove $it 2>/dev/null; cmd activity task remove $it 2>/dev/null") }
                appendLine("물리 상태 되돌리기 → ${sh("cmd device_state base-state reset")}")
                Thread.sleep(1500)
                return ok
            }
            // 15차: 14차에서 이 기기의 화면 분할은 좌우로만 나뉘어(세로 화면에서도 918px | 917px) 앱 위·패널 아래가 되지 않았고,
            // 패널은 멀티 창이면 늘 화면 전체를 덮었다. 그래서 창 크기를 직접 정한다: 패널을 팝업(자유 창)이나 멀티 창으로 띄운 뒤
            // 작업 크기를 화면 아래 절반으로 줄인다(am task resize). 앱은 뒤에 그대로 있어 위 절반에 보인다.
            // 앱까지 팝업으로 위 절반에 놓는 경우도 시험한다. 모두 시험하고(중간에 멈추지 않는다) 화면 캡처로 비교한다
            // 16차: 15차는 'wm size'(회전 전 기본 크기 2448x1848)를 써서 세로 화면(1848x2448)에서 패널이 가운데 띠에 놓였다.
            // 17차: 16차는 dumpsys의 첫 cur= 값을 썼는데 그게 커버 화면(1972x1248)이었다. 기본 디스플레이(0번)의 지금 크기를
            // 시스템에서 바로 읽는다(회전 반영)
            // 18차: 시작할 때 한 번만 읽었더니 회전이 아직 덜 바뀐 때 읽혀 가로 크기(2448x1848)가 잡혔고, 패널이 가운데 띠에 놓였다.
            // 그래서 배치할 때마다 다시 읽는다
            var dw = 1848
            var dh = 2448
            var mid = dh / 2
            fun measure(tag: String) {
                val real = Point().also {
                    @Suppress("DEPRECATION")
                    context.getSystemService(DisplayManager::class.java).getDisplay(0)?.getRealSize(it)
                }
                dw = real.x.takeIf { it > 0 } ?: 1848
                dh = real.y.takeIf { it > 0 } ?: 2448
                mid = dh / 2
                appendLine("시도 $tag: 화면 ${dw}x$dh, 접는 선 y=$mid")
            }
            fun resizePanel(): String = panelTask()?.let { sh("am task resize $it 0 $mid $dw $dh 2>&1 | tail -2") } ?: "패널 작업 없음"
            val appComponent = app?.let { pm.getLaunchIntentForPackage(it)?.component?.flattenToShortString() }
            // 18차: 17차 15-3에서 패널은 아래 절반, 앱은 위 절반에 제대로 놓였고 터치패드·버튼도 동작했다(사용자 확인).
            // 다만 15-3은 앱을 '새 작업'으로 다시 열어서 크롬이 쓰던 창이 아니라 새 창(새 탭)이 위에 떴다.
            // 그래서 쓰던 앱 창(같은 작업)을 팝업으로 바꿔 위 절반에 놓고, 패널을 닫은 뒤 다시 전체 화면으로 되돌리는 방법을 시험한다.
            // 패널은 창 손잡이가 없는 15-2 방식으로 띄운다. 각 방법마다 작업 번호가 그대로인지·창 모드·보임 여부를 남기고, 놓은 뒤와 되돌린 뒤를 캡처한다
            fun tasks(): List<Triple<String, String, Boolean>> = app?.let { pkg ->
                Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*A=\d+:${Regex.escape(pkg)}[^\n]*visible=(true|false)[^\n]*mode=(\w+)""")
                    .findAll(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())
                    .map { Triple(it.groupValues[1], it.groupValues[3], it.groupValues[2] == "true") }.toList()
            }.orEmpty()
            fun taskLine() = tasks().joinToString(", ") { "#${it.first} ${it.second}${if (it.third) " 보임" else ""}" }.ifBlank { "(앱 작업 없음)" }
            fun modeOf(id: String) = tasks().firstOrNull { it.first == id }?.second
            appendLine("시도 18 참고: am 명령 중 작업·창 모드 관련 → ${ShizukuAccess.shell("am help 2>&1 | grep -i -E 'windowing|task (resize|move|set)' | head -12")?.out?.trim()?.ifBlank { "(없음)" } ?: "-"}")

            val desk = "dumpsys activity service SystemUIService WMShell desktopmode"
            fun relaunch(mode: Int, flags: String) = appComponent?.let { sh("am start --windowingMode $mode -f $flags -n $it") } ?: "앱 없음"
            val restores = listOf<Pair<String, (String) -> String>>(
                "전체 화면 모드로 다시 앞에 불러오기" to { _ -> relaunch(1, "0x10000000") },
                "데스크에서 빼기" to { id -> sh("$desk moveTaskOutOfDesk $id 2>&1 | tail -3") },
                "작업을 앞으로 옮기기" to { id -> sh("am task move-to-front $id 2>&1 | tail -2; cmd activity task move-to-front $id 2>&1 | tail -2") }
            )
            // [toPopup]으로 쓰던 앱 작업을 팝업으로 바꾸고, [back]으로 전체 화면으로 되돌린다. 둘 다 작업 번호를 받는다
            fun cycle(tag: String, title: String, toPopup: (String) -> String, back: (String) -> String): Boolean {
                val name = "시도 $tag"
                if (app != null) ShizukuAccess.shell("monkey -p $app -c android.intent.category.LAUNCHER 1")
                Thread.sleep(1500)
                val before = appTask()
                appendLine("$name($title): 시작 전 앱 작업 → ${taskLine()}")
                if (before == null) {
                    appendLine("$name 결과: 앱 작업을 찾지 못해 건너뜀")
                    return false
                }
                ShizukuAccess.shell("logcat -b all -c")
                appendLine("$name: 물리 상태를 반쯤 접힘으로(유지) → ${sh("cmd device_state base-state $half")}")
                Thread.sleep(1500)
                measure(tag)
                appendLine("$name: 쓰던 앱 작업 #$before 을 팝업으로 → ${toPopup(before)}")
                Thread.sleep(1500)
                // 새 작업으로 열린 경우(18-5·18-6)는 그 새 작업을 대상으로 삼는다
                val target = tasks().firstOrNull { it.third && it.second != "fullscreen" }?.first ?: before
                val same = modeOf(target)
                appendLine("$name: 바꾼 뒤 앱 작업 → ${taskLine()} · 대상 #$target${if (target == before) "(쓰던 작업)" else "(새 작업)"}")
                appendLine("$name: 앱을 위 절반으로 → ${sh("am task resize $target 0 0 $dw $mid 2>&1 | tail -2")}")
                Thread.sleep(1000)
                // 패널은 15-2(멀티 창)처럼 띄운다: 팝업과 달리 창 손잡이가 없어 가장 깔끔했다(사용자 의견)
                appendLine("$name: 패널 띄우기(15-2 방식) → ${sh(openPanel)}")
                Thread.sleep(1500)
                appendLine("$name: 패널을 아래 절반으로 → ${resizePanel()}")
                Thread.sleep(1500)
                state(name, tag)
                val panel = alive()
                val kept = target == before && same != null && same != "fullscreen" && tasks().any { it.first == before && it.third }
                val ok = panel && kept
                appendLine("$name 결과: 패널 ${if (panel) "유지" else "닫힘"} · 쓰던 앱 작업 ${if (kept) "$same 창으로 보임" else "그대로 아님(모드 ${same ?: "없음"})"}${if (ok) " → 성공 후보" else ""}")
                if (panel) {
                    notice(context, if (ok) "쓰던 앱 화면이 위에 그대로 있는지 확인하고 패널을 눌러 보세요(15초)" else "패널만 떠 있습니다. 15초 동안 눌러 보세요")
                    Thread.sleep(15_000)
                }
                // 패널을 닫고, 쓰던 앱을 전체 화면으로 되돌린 뒤 결과를 남긴다
                ShizukuAccess.shell("am broadcast -a android.intent.action.COLLAPSE_FLEX_PANEL -p com.android.systemui")
                panelTask()?.let { appendLine("$name: 패널 작업 #$it 닫기 → ${removeTask(it)}") }
                appendLine("물리 상태 되돌리기 → ${sh("cmd device_state base-state reset")}")
                Thread.sleep(1000)
                appendLine("$name: 앱을 전체 화면으로 되돌리기 → ${back(target)}")
                Thread.sleep(1500)
                // 이 방법의 되돌리기가 안 먹으면 다른 되돌리기를 차례로 써 보고 어느 것이 됐는지 남긴다
                for ((what, run) in restores) {
                    if (modeOf(target) == "fullscreen") break
                    appendLine("$name: 아직 전체 화면 아님 → $what → ${run(target)}")
                    Thread.sleep(1500)
                }
                val restored = modeOf(target) == "fullscreen"
                appendLine("$name 되돌린 뒤 앱 작업 → ${taskLine()} · ${if (restored) "전체 화면 복귀" else "전체 화면 아님"}")
                appendLine("$name 되돌린 화면 캡처 → ${sh("screencap -p $shotDir/goat-flex-$tag-back.png && echo $shotDir/goat-flex-$tag-back.png")}")
                // 시험이 새로 만든 작업(빈 새 창)은 닫아 다음 시험과 쓰던 앱에 남지 않게 한다
                if (target != before) appendLine("$name: 시험이 만든 새 작업 #$target 닫기 → ${removeTask(target)}")
                return ok && restored
            }
            // 사용자 요청으로 가능성 있는 방법을 모두 넣는다(하나가 성공해도 끝까지 시험한다)
            val back1: (String) -> String = { _ -> relaunch(1, "0x10000000") }
            val cycles = listOf<Triple<String, String, Pair<(String) -> String, (String) -> String>>>(
                Triple("18-1", "같은 앱을 팝업 모드로 다시 앞에 불러오기(새 작업 없이)", { _: String -> relaunch(5, "0x10000000") } to back1),
                Triple("18-2", "같은 앱을 멀티 창 모드로 다시 앞에 불러오기(창 손잡이 없이)", { _: String -> relaunch(6, "0x10000000") } to back1),
                Triple(
                    "18-3", "WMShell 데스크로 옮기기",
                    { id: String -> sh("$desk moveTaskToDesk $id 0 2>&1 | tail -3") } to { id: String -> sh("$desk moveTaskOutOfDesk $id 2>&1 | tail -3") }
                ),
                Triple(
                    "18-4", "전체 화면 앱 작업을 그대로 크기만 줄이기",
                    { id: String -> sh("am task resizeable $id 3 2>&1 | tail -1") } to { id: String -> sh("am task resize $id 0 0 $dw $dh 2>&1 | tail -2") }
                ),
                Triple("18-5", "앱을 멀티 창 모드 새 작업으로 다시 열기(15-3을 멀티 창으로)", { _: String -> relaunch(6, "0x10008000") } to back1),
                Triple("18-6", "앱을 팝업 새 작업으로 다시 열기(15-3 그대로, 비교용)", { _: String -> relaunch(5, "0x10008000") } to back1)
            )
            // 팝업(자유 창)이 꺼진 기기에서도 시험되게 잠시 켠다(에뮬레이터에서 이 설정 없이는 팝업으로 열리지 않음을 확인). 끝나면 되돌린다
            val freeform0 = ShizukuAccess.shell("settings get global enable_freeform_support")?.out?.trim()
            appendLine("시도 18: 팝업 창 허용 → ${sh("settings put global enable_freeform_support 1")} (원래 값 ${freeform0 ?: "-"})")
            for ((tag, title, ways) in cycles) {
                if (cycle(tag, title, ways.first, ways.second)) done = true
            }
            appendLine(
                "팝업 창 설정 되돌리기 → ${sh(if (freeform0 == null || freeform0 == "null") "settings delete global enable_freeform_support" else "settings put global enable_freeform_support $freeform0")}"
            )
            // 라. 덮어쓰기 없이 실제 자세로
            if (!done) {
                appendLine("시도 0-라: 회전 고정 풀기 → ${sh("cmd window user-rotation free && settings put system accelerometer_rotation 1")}")
                notice(context, "지금 폰을 노트북처럼 가로로 반쯤 접어 20초 동안 세워 두세요")
                appendLine("시도 0-라: 사용자에게 노트북 자세로 20초 세워 두라고 알림(진동·토스트)")
                done = tryOnce("시도 0-라(실제 자세)", override = false, wait = 20_000)
                rotationInfo("시도 0-라 끝")
                notice(context, "이제 폰을 원래대로 펴도 됩니다")
            }
            appendLine("회전 고정 풀기 → ${sh("cmd window user-rotation free")}")
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

    /**
     * 자동 플렉스의 앱별 동작([AutoFlex.mode]). 접을 때 앞에 있는 앱에 맞춰 적용하고, 펴면 원래대로 돌린다.
     * - 터치패널(살리기 시도 18-2에서 실기 확인): 앱을 위 절반, 삼성 패널을 아래 절반에 놓는다. 패널은 물리 상태가 반쯤 접힘(탁자 모드)이어야
     *   유지되므로 물리 상태도 덮어쓴다.
     * - 분할 화면: 앱을 위 절반에 두고, 아래 절반에 앱 고르기 화면([com.labok.goat.ui.lab.FlexPickActivity])을 띄워 고른 앱을 놓는다.
     * - 플렉스 모드: 반쯤 접힘만 알리고(앱에 들어 있는 플렉스 모드) 화면은 건드리지 않는다.
     * 켜져 있는 동안 알림에서 모드를 바꿀 수 있다([switchMode]). 셸 명령이 몇 초 걸려 센서 스레드를 막지 않게 따로 한 줄로 돌린다(순서는 지킨다).
     */
    private data class Split(
        val mode: Int,
        val pkg: String,
        val label: String,
        val component: String,
        val original: String,
        val app: String? = null,
        val panel: String? = null,
        val picker: String? = null,
        val bottom: String? = null,
        val bottomComponent: String? = null,
        val freeform0: String? = null
    )

    @Volatile private var split: Split? = null
    private val splitQueue = java.util.concurrent.Executors.newSingleThreadExecutor()
    private const val PICK_ACTIVITY = "com.labok.goat/.ui.lab.FlexPickActivity"
    private const val MODE_CHANNEL = "auto_flex_mode"
    private const val MODE_NOTIFICATION = 3101

    /** 화면에 보이는 전체 화면 일반 앱 작업(번호, 패키지). 시스템 UI·GOAT·홈은 뺀다. */
    private fun frontTask(context: Context): Pair<String, String>? =
        Regex("""Task\{[0-9a-f]+ #(\d+) type=standard A=\d+:([\w.]+)[^\n]*visible=true[^\n]*mode=fullscreen""")
            .findAll(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())
            .map { it.groupValues[1] to it.groupValues[2] }
            .firstOrNull { (_, pkg) -> pkg != "com.android.systemui" && !pkg.startsWith(context.packageName) }

    /** [pkg]의 보이는 작업 중 전체 화면이 아닌 것(멀티 창으로 다시 부른 뒤 찾는다). */
    private fun windowedTask(pkg: String): String? =
        Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*A=\d+:${Regex.escape(pkg)}[^\n]*visible=true[^\n]*mode=(\w+)""")
            .findAll(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())
            .firstOrNull { it.groupValues[2] != "fullscreen" }?.groupValues?.get(1)

    private fun panelTaskId(): String? = Regex("""Task\{[0-9a-f]+ #(\d+)[^}]*com\.android\.systemui[^}]*""").find(
        ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{.*FlexPanelActivity|Task\\{[^}]*A=[0-9]+:com.android.systemui'")?.out.orEmpty()
    )?.groupValues?.get(1)

    private fun atm(): Any = HiddenApi.asInterface("android.app.IActivityTaskManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")))

    /**
     * 작업 [id]의 창 모드와 크기를 한 번에 바꾼다(WindowContainerTransaction, Shizuku 셸 권한). 앱을 다시 띄우지 않으므로
     * 같은 창이 그대로 옮겨지고 중간에 전체 화면으로 깜빡이지 않는다. [bounds]가 빈 Rect면 크기 지정을 푼다. 성공하면 null, 실패하면 이유.
     * 영상(1.9.1): 다시 띄우는 방식은 크롬이 빈 새 창으로 열렸고, 메시지 앱은 펴도 위 절반에 남았다.
     */
    private fun moveTask(id: String, mode: Int, bounds: android.graphics.Rect): String? = runCatching {
        val atm = atm()
        val getTasks = atm.javaClass.methods.filter { it.name == "getTasks" }.maxByOrNull { it.parameterTypes.size }!!
        val args: Array<Any> = when (getTasks.parameterTypes.size) {
            1 -> arrayOf(50)
            3 -> arrayOf(50, false, false)
            else -> arrayOf(50, false, false, -1)
        }
        val info = (getTasks.invoke(atm, *args) as List<*>).firstOrNull { t -> t!!.javaClass.getField("taskId").getInt(t).toString() == id }
            ?: return "작업 #$id 없음"
        val token = info.javaClass.getField("token").get(info)
        val wct = Class.forName("android.window.WindowContainerTransaction").getConstructor().newInstance()
        wct.javaClass.methods.first { it.name == "setWindowingMode" && it.parameterTypes.size == 2 }.invoke(wct, token, mode)
        wct.javaClass.methods.first { it.name == "setBounds" && it.parameterTypes.size == 2 }.invoke(wct, token, bounds)
        val org = atm.javaClass.methods.first { it.name == "getWindowOrganizerController" }.invoke(atm) as android.os.IInterface
        val ctl = HiddenApi.asInterface("android.window.IWindowOrganizerController", ShizukuBinderWrapper(org.asBinder()))
        ctl.javaClass.methods.first { it.name == "applyTransaction" && it.parameterTypes.size == 1 }.invoke(ctl, wct)
        null
    }.getOrElse { HiddenApi.describe(it) }

    /** 작업 [id]의 지금 창 모드(fullscreen·multi·freeform 등). */
    private fun modeOf(id: String): String? = Regex("""Task\{[0-9a-f]+ #$id [^\n]*mode=(\w+)""")
        .find(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())?.groupValues?.get(1)

    private fun displaySize(context: Context): Point = Point().also {
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java).getDisplay(0)?.getRealSize(it)
    }

    /** [pkg]의 가장 위에 있는 보이는 작업 번호. */
    private fun visibleTask(pkg: String): String? =
        Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*A=\d+:${Regex.escape(pkg)}[^\n]*visible=true""")
            .find(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())?.groupValues?.get(1)

    /** 앱 고르기 화면의 작업(따로 둔 작업 이름 com.labok.goat.flexpick). */
    private fun pickerTask(): String? = Regex("""Task\{[0-9a-f]+ #(\d+)[^\n]*A=\d+:com\.labok\.goat\.flexpick""")
        .find(ShizukuAccess.shell("dumpsys activity activities | grep -E 'Task\\{'")?.out.orEmpty())?.groupValues?.get(1)

    /** [find]가 작업을 찾을 때까지 50ms마다 2초 동안 기다린다. */
    private fun waitTask(find: () -> String?): String? {
        repeat(40) {
            find()?.let { return it }
            Thread.sleep(50)
        }
        return null
    }

    /** 작업을 맨 앞으로(최근 앱에서 고른 것처럼). 안 되면 실행 화면을 다시 부른다. */
    private fun bringToFront(id: String, component: String) {
        val ok = runCatching {
            val atm = atm()
            atm.javaClass.methods.first { it.name == "startActivityFromRecents" && it.parameterTypes.size == 2 }.invoke(atm, id.toInt(), null)
        }.isSuccess
        if (!ok) ShizukuAccess.shell("am start -f 0x10000000 -n $component")
    }

    /** 작업 [id]를 전체 화면으로 되돌린다. 차례로 시도하고 무엇이 됐는지 돌려준다. */
    private fun restoreFull(context: Context, id: String, component: String): String {
        val done = mutableListOf<String>()
        moveTask(id, 1, android.graphics.Rect()).let { done += "옮기기 ${it ?: "성공"}" }
        if (modeOf(id) != "fullscreen") {
            ShizukuAccess.shell("am start --windowingMode 1 -f 0x10000000 -n $component")
            Thread.sleep(500)
            done += "다시 불러오기"
        }
        if (modeOf(id) != "fullscreen") {
            // 모드가 안 바뀌어도 화면 전체 크기로는 키운다(아래가 홈 화면으로 비어 보이지 않게)
            val real = displaySize(context)
            ShizukuAccess.shell("am task resize $id 0 0 ${real.x} ${real.y}")
            done += "전체 크기로 키우기"
        }
        return done.joinToString(" → ") + " · 지금 ${modeOf(id) ?: "-"}"
    }

    /** 접을 때: 앞 앱에 고른 모드를 적용한다. */
    fun apply(context: Context) = splitQueue.execute { applyNow(context) }

    /** 펼 때: 적용한 모드를 풀어 원래대로 돌린다. */
    fun release(context: Context) = splitQueue.execute { releaseNow(context) }

    /** 켜져 있는 동안(알림에서) 지금 앱의 모드를 바꿔 바로 다시 적용한다. */
    fun switchMode(context: Context, mode: Int) = splitQueue.execute {
        val s = split ?: return@execute
        AutoFlex.setMode(context, s.pkg, mode)
        if (s.mode == mode) return@execute notifyMode(context, s)
        releaseNow(context)
        Thread.sleep(300)
        applyNow(context)
    }

    private const val SPLIT_CMD = "dumpsys activity service SystemUIService WMShell splitscreen"

    /**
     * 분할 화면(삼성 공식 화면 분할)에서 앱 고르기 칸에 [pkg]를 띄운다(앱 고르기 화면이 부른다).
     * 고르기 화면이 있는 칸에서 앱을 열면 보통 같은 칸에 열린다. 분할이 풀리면 그 앱을 분할 다른 칸으로 다시 넣는다.
     */
    fun pickBottom(context: Context, pkg: String) = splitQueue.execute {
        val s = split?.takeIf { it.mode == AutoFlex.MODE_SPLIT } ?: return@execute
        val component = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString() ?: return@execute
        ShizukuAccess.shell("am start -f 0x10000000 -n $component")
        val task = waitTask { visibleTask(pkg) }
        Thread.sleep(300)
        val log = LogRepository.from(context)
        val before = task?.let { modeOf(it) }
        if (task != null && before == "fullscreen") ShizukuAccess.shell("$SPLIT_CMD moveToSideStage $task 1")
        (s.picker ?: pickerTask())?.let { removeTask(it) }
        split = s.copy(picker = null, bottom = task, bottomComponent = component)
        log.add("자동 플렉스 분할 화면: 다른 칸 $pkg(#${task ?: "-"}) · 열린 뒤 ${before ?: "-"} → 지금 ${task?.let { modeOf(it) } ?: "-"} · 쓰던 앱 ${modeOf(s.original) ?: "-"}")
    }

    private fun applyNow(context: Context) {
        if (split != null || ShizukuAccess.status.value != ShizukuAccess.State.READY) return
        val log = LogRepository.from(context)
        val front = frontTask(context)
        if (front == null) {
            log.add("자동 플렉스: 앞에 앱이 없어 플렉스 모드만 켬")
            return
        }
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(front.second, 0)).toString() }.getOrDefault(front.second)
        val component = pm.getLaunchIntentForPackage(front.second)?.component?.flattenToShortString().orEmpty()
        val mode = AutoFlex.mode(context, front.second)
        // 사용 안 함: 걸어 둔 반쯤 접힘을 풀어 이 앱에서는 아무것도 바뀌지 않게 한다(알림으로 다른 모드로 바꿀 수 있게 알림은 남긴다).
        // 다른 모드는 알림으로 바꿔 들어온 경우를 위해 반쯤 접힘을 다시 건다
        if (mode == AutoFlex.MODE_OFF) {
            resetPosture(context)
            split = Split(mode, front.second, label, component, front.first).also { notifyMode(context, it) }
            return
        }
        if (!forced) forceHalfOpened(context)
        val real = displaySize(context)
        // 화면을 나누는 모드는 접는 선이 가로(화면이 세로로 긴 때)이고 다시 열 수 있는 앱일 때만 쓴다
        if (mode == AutoFlex.MODE_FLEX || real.x <= 0 || real.x >= real.y || component.isEmpty()) {
            if (mode != AutoFlex.MODE_FLEX) log.add("자동 플렉스: ${front.second} 화면을 나눌 수 없어 플렉스 모드만 켬(${real.x}x${real.y})")
            split = Split(AutoFlex.MODE_FLEX, front.second, label, component, front.first).also { notifyMode(context, it) }
            return
        }
        if (mode == AutoFlex.MODE_SPLIT) {
            // 삼성 공식 화면 분할: 쓰던 앱을 분할 첫 칸으로 옮기고, 다른 칸에 GOAT 앱 고르기를 '옆 칸으로 열기'로 띄운다
            ShizukuAccess.shell("$SPLIT_CMD moveToSideStage ${front.first} 0")
            Thread.sleep(600)
            ShizukuAccess.shell("am start -f 0x10001000 -n $PICK_ACTIVITY")
            val picker = waitTask { pickerTask() }
            split = Split(mode, front.second, label, component, front.first, picker = picker).also { notifyMode(context, it) }
            log.add("자동 플렉스 분할 화면(공식): ${front.second}(#${front.first}, 지금 ${modeOf(front.first) ?: "-"}) · 앱 고르기(#${picker ?: "-"}, 지금 ${picker?.let { modeOf(it) } ?: "-"})")
            return
        }
        val (w, h) = real.x to real.y
        val mid = h / 2
        val top = android.graphics.Rect(0, 0, w, mid)
        val bottom = android.graphics.Rect(0, mid, w, h)
        val freeform0 = ShizukuAccess.shell("settings get global enable_freeform_support")?.out?.trim()
        ShizukuAccess.shell("settings put global enable_freeform_support 1")
        // 쓰던 창을 그대로 위 절반으로 옮긴다. 안 되면 18-2처럼 멀티 창으로 다시 불러 크기를 줄인다
        val moveErr = moveTask(front.first, 6, top)
        val app = if (moveErr == null && modeOf(front.first) != "fullscreen") {
            front.first
        } else {
            ShizukuAccess.shell("am start --windowingMode 6 -f 0x10000000 -n $component")
            Thread.sleep(800)
            windowedTask(front.second)?.also { ShizukuAccess.shell("am task resize $it 0 0 $w $mid") }
        }
        val how = if (app == front.first) "쓰던 창 그대로" else "다시 불러옴 · 옮기기 실패(${moveErr ?: "모드 그대로"})"
        val half = Regex("identifier=(\\d+), name='HALF_OPENED'").find(ShizukuAccess.shell("cmd device_state print-states")?.out.orEmpty())?.groupValues?.get(1) ?: "2"
        ShizukuAccess.shell("cmd device_state base-state $half")
        ShizukuAccess.shell("am start --windowingMode 6 -a android.intent.action.AUTORUN_FLEX_PANEL -f 0x14241000 --display 0 -n '$PANEL_ACTIVITY'")
        // 패널은 처음에 화면 전체로 뜨므로, 작업이 보이자마자 아래 절반으로 줄여 깜빡이는 시간을 줄인다
        val panel = waitTask { panelTaskId() }
        panel?.let { if (moveTask(it, 6, bottom) != null) ShizukuAccess.shell("am task resize $it 0 $mid $w $h") }
        split = Split(mode, front.second, label, component, front.first, app = app, panel = panel, freeform0 = freeform0)
        log.add("자동 플렉스 터치패널: ${front.second} 위(#${app ?: "-"}, $how) · 패널 아래(#${panel ?: "-"})")
        notifyMode(context, split!!)
    }

    private fun releaseNow(context: Context) {
        val s = split ?: return
        split = null
        context.getSystemService(android.app.NotificationManager::class.java).cancel(MODE_NOTIFICATION)
        if (s.mode == AutoFlex.MODE_FLEX || s.mode == AutoFlex.MODE_OFF) return
        val log = LogRepository.from(context)
        if (s.mode == AutoFlex.MODE_PANEL) {
            ShizukuAccess.shell("am broadcast -a android.intent.action.COLLAPSE_FLEX_PANEL -p com.android.systemui")
            (s.panel ?: panelTaskId())?.let { removeTask(it) }
            ShizukuAccess.shell("cmd device_state base-state reset")
        } else {
            // 공식 분할을 풀고 쓰던 앱을 위에 남긴다. 다른 칸에 연 앱은 닫지 않고 뒤에 둔다
            (s.picker ?: pickerTask())?.let { removeTask(it) }
            ShizukuAccess.shell("$SPLIT_CMD exitSplitScreen ${s.original}")
            Thread.sleep(300)
            log.add("자동 플렉스 분할 화면 풀기: 쓰던 앱 지금 ${modeOf(s.original) ?: "-"}")
        }
        if (s.app != null && s.app != s.original) {
            // 새로 생긴 창(크롬 등)은 닫는다. 뒤에 있던 원래 창이 전체 화면으로 남는다
            removeTask(s.app)
        } else if (s.app != null) {
            log.add("자동 플렉스 되돌리기(#${s.app}): ${restoreFull(context, s.app, s.component)}")
        }
        // 펼친 뒤에는 접기 전에 보던 앱이 앞에 오게 한다
        if (s.mode == AutoFlex.MODE_SPLIT) bringToFront(s.original, s.component)
        ShizukuAccess.shell(
            if (s.freeform0 == null || s.freeform0 == "null") "settings delete global enable_freeform_support" else "settings put global enable_freeform_support ${s.freeform0}"
        )
        log.add("자동 플렉스 ${AutoFlex.modeLabel(s.mode)} 풀기: ${s.pkg}")
    }

    /** 켜져 있는 동안 지금 앱·모드를 보여 주고, 알림 버튼으로 모드를 바꿀 수 있게 한다. */
    private fun notifyMode(context: Context, s: Split) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel(MODE_CHANNEL, "자동 플렉스 모드 바꾸기", android.app.NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val b = androidx.core.app.NotificationCompat.Builder(context, MODE_CHANNEL)
            .setSmallIcon(com.labok.goat.R.drawable.ic_stat_goat)
            .setContentTitle("${s.label} · ${AutoFlex.modeLabel(s.mode)}")
            .setContentText("접은 동안 이 앱에서 쓸 방식을 바꿀 수 있습니다")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        // 알림 버튼은 세 개까지 보이므로 지금 모드를 뺀 나머지 셋을 둔다(지금 모드는 제목에 보인다)
        AutoFlex.MODES.filter { it != s.mode }.forEach { m ->
            val pi = android.app.PendingIntent.getBroadcast(
                context,
                m,
                android.content.Intent(context, FlexModeReceiver::class.java).putExtra(FlexModeReceiver.EXTRA_MODE, m),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
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
