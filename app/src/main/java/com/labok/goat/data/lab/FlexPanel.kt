package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.os.Build
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.UserId
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 실험실 '플렉스 모드 패널 시험'. 폴드8(기본형)은 반쯤 접은 채 멈추는 힌지가 빠져 설정의 '플렉스 모드 패널' 항목이 숨겨졌다
 * (울트라는 지원). 숨긴 것이 설정 항목뿐인지 기능 코드까지인지 몰라, 이 기기에서 직접 시험한다.
 * - 기능 스위치: global.flex_mode_panel_enabled(0/1). 시스템 설정 권한으로 바꾼다([LabSetting], 처음 값으로 되돌리기 가능).
 * - 앱별 등록: 삼성 IWindowManager.setSupportsFlexPanel(userId, 패키지, 켬). 작업 관리 권한이 필요해 Shizuku(셸 권한)로 부른다.
 *   GOAT가 등록한 앱은 기억해 두었다가 [restoreApps]로 모두 해제한다.
 * - [diagnose]: 기기 정보, 설정값, 창 관리자의 플렉스 패널 함수, flex가 들어간 설정 키·삼성 기능 표·시스템 속성, 접힘 상태 목록,
 *   관련 패키지를 읽어 파일로 남긴다(읽기만).
 */
object FlexPanel {
    val setting = LabSetting("global", "flex_mode_panel_enabled", "플렉스 모드 패널")
    private const val PREF = "lab_flex_apps"
    private val FLEX = Regex("(?i)flex")

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

    /** GOAT로 등록해 둔 앱. */
    fun registered(context: Context): Set<String> = prefs(context).getStringSet("apps", emptySet()).orEmpty()

    /** GOAT가 등록한 앱을 모두 해제한다. 실패한 앱 수를 돌려준다. */
    fun restoreApps(context: Context): Int = registered(context).count { setApp(context, it, false) != null }

    /** 등록 후보: 홈 화면에 아이콘이 있는 앱(GOAT 제외), 이름순. */
    fun launchableApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    /** 진단(읽기만): 결과 첫 줄은 요약. */
    fun diagnose(context: Context): String = buildString {
        val methods = runCatching {
            wm().javaClass.methods.filter { FLEX.containsMatchIn(it.name) }
                .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }.distinct().sorted()
        }.getOrElse { listOf("(읽지 못함: ${HiddenApi.describe(it)})") }
        appendLine("요약: 설정값 ${setting.read(context) ?: "(없음)"} · 플렉스 패널 함수 ${methods.count { "읽지 못함" !in it }}개")
        appendLine()
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        appendLine("Shizuku: ${ShizukuAccess.status.value}")
        appendLine()
        appendLine("== 창 관리자 함수(flex) ==")
        methods.forEach { appendLine(it) }
        appendLine()
        appendLine("== 설정 키(flex) ==")
        listOf("system", "secure", "global").forEach { table ->
            val r = ShizukuAccess.shell("settings list $table")
            if (r == null) {
                appendLine("[$table] Shizuku 연결 안 됨")
            } else {
                r.out.lineSequence().filter { FLEX.containsMatchIn(it.substringBefore('=')) }.forEach { appendLine("[$table] $it") }
            }
        }
        appendLine()
        appendLine("== 삼성 기능 표(flex) ==")
        val ff = ShizukuAccess.shell("grep -i flex /system/etc/floating_feature.xml")
        appendLine(ff?.out?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine()
        appendLine("== 시스템 속성(flex) ==")
        val props = ShizukuAccess.shell("getprop | grep -i flex")
        appendLine(props?.out?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine()
        // 접힘 상태(기기가 아는 자세 목록과 지금 상태): 반접힘(HALF_OPENED 등)이 목록에 있으면 플렉스 판정 근거가 남아 있다
        appendLine("== 접힘 상태 ==")
        appendLine(ShizukuAccess.shell("cmd device_state print-states")?.let { it.out.ifBlank { it.err } }?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine(ShizukuAccess.shell("cmd device_state state")?.let { it.out.ifBlank { it.err } }?.ifBlank { "(없음)" } ?: "")
        appendLine()
        appendLine("== 관련 패키지(flex·fold·multiwindow) ==")
        val pk = ShizukuAccess.shell("pm list packages")
        appendLine(pk?.out?.lineSequence()?.filter { Regex("(?i)flex|fold|multiwindow|multistar").containsMatchIn(it) }?.joinToString("\n")?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨")
        appendLine()
        appendLine("GOAT가 등록한 앱: ${registered(context).joinToString().ifBlank { "(없음)" }}")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
