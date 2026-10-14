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

    /**
     * 터치패널 진단: 폴드8에서 플렉스를 지원하지 않는 앱에 시스템 터치패널을 띄울 수 있는지 가리는 데 필요한 것을 모은다(읽기만 하고,
     * 마지막에 터치패널 화면을 한 번 열어 본다). 시스템 UI에 패널 화면이 남아 있는지, 앱마다 시스템이 주는 패널 값, 관련 설정·기능 표.
     */
    fun diagnose(context: Context): String = buildString {
        val flex = Regex("(?i)flex")
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { it.out.ifBlank { it.err } }?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨"
        val sysui = ShizukuAccess.shell("pm dump com.android.systemui")?.out.orEmpty()
        // 시스템 UI뿐 아니라 모든 패키지에서 이름에 flex가 든 화면·서비스를 찾는다(폴드8 시스템 UI에는 없었다)
        val all = ShizukuAccess.shell("dumpsys package | grep -i flex")?.out.orEmpty()
        val comps = Regex("""[a-zA-Z][\w.]*/[\w.$]*[Ff]lex[\w.$]*""").findAll(sysui + "\n" + all).map { it.value }.distinct().take(80).toList()
        appendLine("요약: 플렉스 구성요소(전체 패키지) ${comps.size}개 · 창 관리자 패널 함수 ${if (hasAppApi()) "있음" else "없음"}")
        appendLine()
        appendLine("기기: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.DISPLAY}")
        appendLine("Shizuku: ${ShizukuAccess.status.value}")
        appendLine()
        appendLine("== 이름에 flex가 든 구성요소(전체 패키지) ==")
        appendLine(comps.joinToString("\n").ifBlank { "(없음)" })
        appendLine()
        appendLine("== 시스템 UI 정보 중 flex 줄과 앞뒤 ==")
        // 'FeatureName=flexModeApp, Size=74'처럼 삼성이 둔 플렉스 앱 목록이 있으면 그 내용까지 보이게 앞뒤 줄을 함께 싣는다
        val lines = sysui.lines()
        val shown = sortedSetOf<Int>()
        lines.forEachIndexed { i, l -> if (flex.containsMatchIn(l)) (maxOf(0, i - 15)..minOf(lines.size - 1, i + 90)).forEach { shown.add(it) } }
        appendLine(shown.take(400).joinToString("\n") { lines[it] }.ifBlank { "(없음)" })
        appendLine()
        appendLine("== 멀티 창(multistar) 정보 중 flex 줄 ==")
        appendLine(sh("pm dump com.samsung.android.multistar | grep -i -B3 -A3 flex | head -80"))
        appendLine()
        appendLine("== 창 관리자 함수(flex) ==")
        appendLine(runCatching { wm().javaClass.methods.filter { flex.containsMatchIn(it.name) }.map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }}): ${it.returnType.simpleName}" }.distinct().sorted().joinToString("\n") }.getOrElse { "(읽지 못함: ${HiddenApi.describe(it)})" })
        appendLine()
        appendLine("== 앱별 패널 값(getSupportsFlexPanel) ==")
        val apps = (listOf("com.google.android.youtube", "com.android.chrome", "com.sec.android.app.camera", "com.sec.android.gallery3d", "com.android.settings") + com.labok.goat.data.appsize.AppList.launcherApps(context).map { it.pkg }.take(20)).distinct()
        apps.forEach { pkg ->
            val v = rawSupport(pkg)
            val hex = (v as? Int)?.let { " (0x${Integer.toHexString(it)})" }.orEmpty()
            appendLine("$pkg = ${v ?: "(못 읽음)"}$hex · 플렉스 화면 앱 추정 ${if (FlexAppScan.supports(context, pkg)) "예" else "아니오"}")
        }
        appendLine()
        // 플렉스 전용 화면이 되는 앱(갤러리·유튜브·카메라)과 잘못 '지원'으로 나온 앱을 나란히 놓고, 설치 파일 흔적·메타데이터·크기 조절 여부를 비교한다
        appendLine("== 앱 비교(설치 파일 흔적·메타데이터) ==")
        val compare = listOf(
            "com.sec.android.gallery3d",
            "com.google.android.youtube",
            "com.sec.android.app.camera",
            "com.android.settings",
            "com.microsoft.office.excel",
            "com.adguard.android",
            "co.kr.cgv.cjcgv",
            "com.android.chrome"
        )
        compare.forEach { pkg -> appendLine("$pkg: ${FlexAppScan.inspect(context, pkg)}") }
        appendLine()
        appendLine("== 앱 비교(시스템이 아는 크기 조절·플렉스 정보) ==")
        compare.forEach { pkg ->
            appendLine("[$pkg]")
            appendLine(sh("dumpsys package $pkg | grep -i -E 'resiz|flex|fold|samsung' | head -12"))
        }
        appendLine()
        appendLine("== 설정 키(flex) ==")
        listOf("system", "secure", "global").forEach { table ->
            val r = ShizukuAccess.shell("settings list $table")
            if (r == null) appendLine("[$table] Shizuku 연결 안 됨") else r.out.lineSequence().filter { flex.containsMatchIn(it.substringBefore('=')) }.forEach { appendLine("[$table] $it") }
        }
        appendLine()
        appendLine("== 삼성 기능 표(flex) ==")
        appendLine(sh("grep -i flex /system/etc/floating_feature.xml"))
        appendLine()
        appendLine("== 시스템 속성(flex) ==")
        appendLine(sh("getprop | grep -i flex"))
        appendLine()
        appendLine("== 접힘 상태 ==")
        appendLine(sh("cmd device_state print-states"))
        appendLine()
        appendLine("== 창 정보 중 flex 줄 ==")
        appendLine(sh("dumpsys window | grep -i flex | head -40"))
        appendLine()
        // 패널 화면을 셸에서 직접 열어 본다: 열리면 시스템에 패널이 살아 있다는 뜻이다
        appendLine("== 터치패널 화면 직접 열어 보기 ==")
        val panel = comps.firstOrNull { it.contains("Panel", ignoreCase = true) && it.contains("Activity") } ?: comps.firstOrNull { it.contains("Activity") }
        appendLine(if (panel == null) "(열 화면을 찾지 못함)" else "$panel → ${sh("am start -n $panel")}")
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
