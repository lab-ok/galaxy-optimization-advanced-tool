package com.labok.goat.data.lab

import android.content.Context
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

    /** 시스템 UI 구성요소(화면·서비스·수신기) 중 이름이 [PANEL_WORDS]나 flex에 맞는 것. */
    private fun panelComponents(): List<String> {
        val sysui = ShizukuAccess.shell("pm dump com.android.systemui")?.out.orEmpty()
        val all = ShizukuAccess.shell("dumpsys package | grep -i -E 'flex|tabletop'")?.out.orEmpty()
        return Regex("""[a-zA-Z][\w.]*/[\w.$]+""").findAll(sysui + "\n" + all).map { it.value }
            .filter { PANEL_WORDS.containsMatchIn(it) || it.contains("flex", ignoreCase = true) }.distinct().take(80).toList()
    }

    /**
     * 터치패널 진단(3차): 폴드8 시스템에 삼성 플렉스 패널 코드가 남아 있는지 본다. 시스템 UI·설정 앱·멀티 창·시스템 서비스 파일의
     * 코드 이름에서 패널 흔적을 찾고, 패널 관련 구성요소·앱별 패널 값·설정·접힘 상태를 함께 모은다(읽기만 한다).
     */
    fun diagnose(context: Context): String = buildString {
        val flex = Regex("(?i)flex")
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { it.out.ifBlank { it.err } }?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨"
        val comps = panelComponents()
        val files = listOf("com.android.systemui", "com.android.settings", "com.samsung.android.multistar")
            .flatMap { pkg -> ShizukuAccess.shell("pm path $pkg")?.out.orEmpty().lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() } } +
            listOf("/system/framework/services.jar", "/system/framework/framework.jar") +
            ShizukuAccess.shell("ls /system/framework/oat/arm64/")?.out.orEmpty().lines().map { it.trim() }
                .filter { it.endsWith(".vdex") && (it.startsWith("services") || it.startsWith("framework")) }.map { "/system/framework/oat/arm64/$it" }
        val traces = files.associateWith { DexStrings.find(it, PANEL_WORDS) }
        val hit = traces.count { (_, v) -> v.none { it.startsWith("(") } }
        appendLine("요약: 패널 관련 구성요소 ${comps.size}개 · 패널 코드 흔적이 있는 파일 $hit/${files.size}개 · 창 관리자 패널 함수 ${if (hasAppApi()) "있음" else "없음"}")
        appendLine()
        appendLine("기기: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.DISPLAY}")
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

    /**
     * 삼성 터치패널 살리기 시도: 패널 설정을 켜고, 설정 앱에 숨어 있는 '플렉스 모드 패널' 화면을 연다.
     * 패널 관련 시스템 UI 화면이 있으면 반쯤 접힘을 건 채 한 번씩 띄워 본다. 한 일과 결과를 줄마다 돌려준다. 작업 스레드에서 부른다.
     */
    fun revive(context: Context): String = buildString {
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { r -> (if (r.ok) "성공" else "실패") + r.out.ifBlank { r.err }.trim().take(160).let { if (it.isEmpty()) "" else " · $it" } } ?: "Shizuku 연결 안 됨"
        appendLine("패널 설정 켜기 → ${sh("settings put global flex_mode_panel_enabled 1")}")
        // 이름에 $가 있어 작은따옴표로 감싼다(셸이 변수로 읽지 않게)
        appendLine("숨은 '플렉스 모드 패널' 설정 화면 열기 → ${sh("am start -n 'com.android.settings/.Settings\$FlexModePanelSettingsActivity'")}")
        val panels = panelComponents().filter { it.startsWith("com.android.systemui/") && Regex("(?i)activity|panel").containsMatchIn(it.substringAfter('/')) }
        if (panels.isEmpty()) {
            appendLine("시스템 UI에 패널 화면이 없어 직접 띄우기는 건너뜀")
        } else {
            appendLine("반쯤 접힘 걸기 → ${forceHalfOpened(context) ?: "성공"}")
            panels.forEach { appendLine("$it 띄우기 → ${sh("am start -n '$it'")}") }
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
