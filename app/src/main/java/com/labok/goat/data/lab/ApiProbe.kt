package com.labok.goat.data.lab

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IBinder
import com.labok.goat.data.HiddenApi

/**
 * 실험실: 기기의 시스템 서비스를 모두 훑어 '앱별 화면 크기'(또는 [FONT_KEYWORDS]로 앱별 글자 크기)와 관련 있어 보이는 함수를 찾는다.
 * 삼성 '앱 화면 크게/작게'는 앱 프로세스에 앱별 밀도(compat={360dpi always-compat})를 주는데,
 * 설정 키·am compat 어디에도 없어 설정 앱이 시스템 서비스 함수를 직접 부르는 것으로 보인다.
 * 서비스마다 인터페이스 이름(descriptor)을 읽고, 그 인터페이스의 함수 중 이름에 density·zoom·scale 등이
 * 들어간 것을 매개변수 형태와 함께 적는다. 함수를 부르지는 않는다(읽기 전용).
 */
object ApiProbe {
    private val DENSITY_KEYWORDS = Regex("(?i)density|zoom|scale|compat|appsize|displaysize|fontsize|dpi")

    /** 숨은 기능 찾기(실험실): 앱별 글자 크기와 관련 있어 보이는 이름. */
    val FONT_KEYWORDS = Regex("(?i)font|textsize|textscale|fontscale")

    /** 서비스 목록 밖에서 따로 살펴볼 클래스(삼성 프레임워크의 공개 래퍼 등). */
    private val EXTRA_CLASSES = listOf(
        "com.samsung.android.view.SemWindowManager",
        "com.samsung.android.multiwindow.MultiWindowManager",
        "com.samsung.android.app.SemActivityManager",
        "android.app.ActivityTaskManager",
        "android.content.res.CompatibilityInfo"
    )

    // 숨은 시스템 서비스 목록을 훑는 조사 도구라 일부러 숨은 API를 부른다
    @SuppressLint("PrivateApi")
    fun run(context: Context, keywords: Regex = DENSITY_KEYWORDS): String = buildString {
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        HiddenApi.exemptAll()
        val sm = runCatching { Class.forName("android.os.ServiceManager") }.getOrNull()
        val names = runCatching { sm?.getMethod("listServices")?.invoke(null) as? Array<*> }.getOrNull().orEmpty()
        appendLine("서비스 ${names.size}개")
        appendLine()
        var hits = 0
        names.filterIsInstance<String>().sorted().forEach { name ->
            val binder = runCatching { sm!!.getMethod("checkService", String::class.java).invoke(null, name) as? IBinder }.getOrNull()
            val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull() ?: return@forEach
            val cls = runCatching { Class.forName(descriptor) }.getOrNull()
            if (cls == null) {
                if (keywords.containsMatchIn(descriptor)) appendLine("[$name] $descriptor (클래스를 찾지 못함)")
                return@forEach
            }
            val matched = cls.declaredMethods.filter { keywords.containsMatchIn(it.name) }.sortedBy { it.name }
            if (matched.isNotEmpty()) {
                appendLine("[$name] $descriptor")
                matched.forEach { m ->
                    appendLine("    ${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}")
                    hits++
                }
            }
        }
        appendLine()
        appendLine("== 추가 클래스 ==")
        EXTRA_CLASSES.forEach { n ->
            val cls = runCatching { Class.forName(n) }.getOrNull()
            if (cls == null) {
                appendLine("[$n] 없음")
                return@forEach
            }
            val matched = cls.declaredMethods.filter { keywords.containsMatchIn(it.name) }.sortedBy { it.name }
            appendLine("[$n] ${matched.size}개")
            matched.forEach { m -> appendLine("    ${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}") }
        }
        appendLine()
        appendLine("관련 함수 ${hits}개")
    }

    /**
     * 숨은 기능 찾기: [FONT_KEYWORDS]로 시스템 함수를 훑고, Shizuku가 연결돼 있으면 시스템 설정(system·secure·global)에서
     * 이름에 font가 들어간 키와 값도 함께 적는다. 결과 첫 줄에 찾은 함수·설정 수를 요약한다. 읽기만 한다.
     */
    fun findFontScale(context: Context): String {
        val methods = run(context, FONT_KEYWORDS)
        val settings = buildString {
            listOf("system", "secure", "global").forEach { table ->
                val r = com.labok.goat.data.shizuku.ShizukuAccess.shell("settings list $table")
                if (r == null) {
                    appendLine("[$table] Shizuku 연결 안 됨")
                    return@forEach
                }
                r.out.lineSequence().filter { FONT_KEYWORDS.containsMatchIn(it.substringBefore('=')) }.forEach { appendLine("[$table] $it") }
            }
        }
        val fnCount = Regex("""관련 함수 (\d+)개""").find(methods)?.groupValues?.get(1) ?: "0"
        val keyCount = settings.lineSequence().count { it.isNotBlank() && "연결 안 됨" !in it }
        return "요약: 글자 관련 시스템 함수 ${fnCount}개 · 설정 키 ${keyCount}개\n\n== 시스템 함수 ==\n$methods\n== 설정 키 ==\n$settings"
    }
}
