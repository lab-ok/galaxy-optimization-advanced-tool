package com.local.folddpifix.data.lab

import android.content.Context
import android.os.Build
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 실험실: 기기의 시스템 서비스를 모두 훑어 '앱별 화면 크기'와 관련 있어 보이는 함수를 찾는다.
 * 삼성 '앱 화면 크게/작게'는 앱 프로세스에 앱별 밀도(compat={360dpi always-compat})를 주는데,
 * 설정 키·am compat 어디에도 없어 설정 앱이 시스템 서비스 함수를 직접 부르는 것으로 보인다.
 * 서비스마다 인터페이스 이름(descriptor)을 읽고, 그 인터페이스의 함수 중 이름에 density·zoom·scale 등이
 * 들어간 것을 매개변수 형태와 함께 적는다. 함수를 부르지는 않는다(읽기 전용).
 */
object ApiProbe {
    private val KEYWORDS = Regex("(?i)density|zoom|scale|compat|appsize|displaysize|fontsize|dpi")

    /** 서비스 목록 밖에서 따로 살펴볼 클래스(삼성 프레임워크의 공개 래퍼 등). */
    private val EXTRA_CLASSES = listOf(
        "com.samsung.android.view.SemWindowManager",
        "com.samsung.android.multiwindow.MultiWindowManager",
        "com.samsung.android.app.SemActivityManager",
        "android.app.ActivityTaskManager",
        "android.content.res.CompatibilityInfo",
    )

    fun run(context: Context): String = buildString {
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { appendLine("숨은 API 허용 실패: ${it.message}") }
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
                if (KEYWORDS.containsMatchIn(descriptor)) appendLine("[$name] $descriptor (클래스를 찾지 못함)")
                return@forEach
            }
            val matched = cls.declaredMethods.filter { KEYWORDS.containsMatchIn(it.name) }.sortedBy { it.name }
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
            if (cls == null) { appendLine("[$n] 없음"); return@forEach }
            val matched = cls.declaredMethods.filter { KEYWORDS.containsMatchIn(it.name) }.sortedBy { it.name }
            appendLine("[$n] ${matched.size}개")
            matched.forEach { m -> appendLine("    ${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}") }
        }
        appendLine()
        appendLine("관련 함수 ${hits}개")
    }
}
