package com.local.folddpifix.data.lab

import android.content.Context
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
 * 실기기 services.jar를 풀어 본 결과 쓰기 인자는 (패키지, 사용자, 밀도, 메모)이고, 밀도는
 * 320·360·420·450·480·510과 0(기본)만 받는다. 읽기는 두 조합을 모두 시도해 결과를 그대로 보여 준다.
 * 권한이 모자라면 SecurityException 문구에 필요한 권한이 나오므로 그것도 그대로 남긴다.
 */
object AppDensityProbe {

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

    /**
     * PC용 명령. 이 함수들은 MANAGE_ACTIVITY_TASKS(signature|recents)를 요구해 앱에는 adb로도 줄 수 없지만,
     * adb 셸(uid 2000)은 이 권한을 가지고 있다. 그래서 셸의 'service call'로 같은 함수를 번호로 부르는 명령을 만든다.
     * 번호(TRANSACTION_*)는 기기 펌웨어마다 다르므로 이 기기에서 읽은 값을 쓴다.
     */
    fun shellCommands(pkg: String, dpi: Int): String = buildString {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        val stub = runCatching { Class.forName("android.app.IActivityTaskManager\$Stub") }.getOrNull()
            ?: return "IActivityTaskManager를 찾지 못했습니다."
        fun code(name: String) = runCatching { stub.getDeclaredField("TRANSACTION_$name").apply { isAccessible = true }.getInt(null) }.getOrNull()
        val get = code("getCustomDensity")
        val set = code("setUserCustomDensity")
        if (get == null || set == null) return "함수 번호를 찾지 못했습니다(get=$get, set=$set)."
        val user = userId()
        appendLine("읽기:  adb shell service call activity_task $get s16 $pkg i32 $user i32 0")
        appendLine("바꾸기: adb shell service call activity_task $set s16 $pkg i32 $user i32 $dpi s16 FoldFit")
    }

    /** 값 바꾸기: setUserCustomDensity(패키지, 사용자, 밀도, 메모) 후 다시 읽기(실기기 코드에서 확인한 순서). */
    fun write(pkg: String, dpi: Int): String = buildString {
        val svc = runCatching { service() }.getOrElse { return "서비스 연결 실패: ${describe(it)}" }
        val m = method(svc, "setUserCustomDensity") ?: return "setUserCustomDensity 함수가 없습니다."
        appendLine("setUserCustomDensity(${m.parameterTypes.joinToString { it.simpleName }})")
        val r = runCatching { m.invoke(svc, pkg, userId(), dpi, "FoldFit") }.fold({ "성공(반환 없음)" }, { describe(it) })
        appendLine("  ($pkg, user ${userId()}, $dpi, \"FoldFit\") → $r")
        appendLine()
        append(read(pkg))
    }
}
