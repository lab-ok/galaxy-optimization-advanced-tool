package com.local.folddpifix.data.lab

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 실험실: 삼성 프레임워크의 앱별 화면 밀도 함수를 직접 불러 본다(lab 빌드 전용).
 * 실기기(SM-F971N, One UI 9) 탐색 결과 IActivityTaskManager에 아래 함수가 있었다.
 * - getCustomDensity(String, int, boolean): int
 * - setUserCustomDensity(String, int, int, String): void
 * 매개변수 뜻은 공개돼 있지 않아, 읽기는 가능한 조합을 모두 시도해 결과를 그대로 보여 주고,
 * 쓰기는 (패키지, 밀도, 사용자, 메모)로 부른 뒤 다시 읽어 실제로 바뀌었는지 확인한다.
 * 권한이 모자라면 SecurityException 문구에 필요한 권한이 나오므로 그것도 그대로 남긴다.
 */
object AppDensityProbe {

    data class App(val label: String, val pkg: String)

    fun launcherApps(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label }
    }

    private fun userId() = Process.myUid() / 100_000

    private fun service(): Any {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java).invoke(null, "activity_task") as IBinder
        val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun method(svc: Any, name: String): Method? =
        svc.javaClass.methods.firstOrNull { it.name == name }

    private fun describe(t: Throwable): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    /** 현재 값 읽기: getCustomDensity(패키지, 사용자, true/false). */
    fun read(pkg: String): String = buildString {
        val svc = runCatching { service() }.getOrElse { return "서비스 연결 실패: ${describe(it)}" }
        val m = method(svc, "getCustomDensity") ?: return "getCustomDensity 함수가 없습니다(삼성 One UI 9 이상 필요)."
        appendLine("getCustomDensity(${m.parameterTypes.joinToString { it.simpleName }})")
        for (flag in listOf(false, true)) {
            val r = runCatching { m.invoke(svc, pkg, userId(), flag) }.fold({ "$it" }, { describe(it) })
            appendLine("  ($pkg, user ${userId()}, $flag) → $r")
        }
    }

    /** 값 바꾸기: setUserCustomDensity(패키지, 밀도, 사용자, 메모) 후 다시 읽기. */
    fun write(pkg: String, dpi: Int): String = buildString {
        val svc = runCatching { service() }.getOrElse { return "서비스 연결 실패: ${describe(it)}" }
        val m = method(svc, "setUserCustomDensity") ?: return "setUserCustomDensity 함수가 없습니다."
        appendLine("setUserCustomDensity(${m.parameterTypes.joinToString { it.simpleName }})")
        val r = runCatching { m.invoke(svc, pkg, dpi, userId(), "FoldFit") }.fold({ "성공(반환 없음)" }, { describe(it) })
        appendLine("  ($pkg, $dpi, user ${userId()}, \"FoldFit\") → $r")
        appendLine()
        append(read(pkg))
    }
}
