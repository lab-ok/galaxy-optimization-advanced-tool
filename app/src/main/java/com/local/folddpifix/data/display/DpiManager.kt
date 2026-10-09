package com.local.folddpifix.data.display

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.DisplayMetrics
import android.view.Display
import com.local.folddpifix.domain.DpiPolicy
import com.local.folddpifix.domain.ScreenInfo
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * DPI 관련 framework/shell 호출을 한곳에 모은다.
 *
 * `adb shell wm density N`은 결국 `IWindowManager.setForcedDisplayDensityForUser(0, N, user)`를 부르고,
 * AOSP WindowManagerService는 이 호출에 WRITE_SECURE_SETTINGS 권한만 요구한다.
 * 반면 `Settings.Secure.display_density_forced` 값은 WMS가 감시하지 않아 바꿔도 화면에 즉시 반영되지 않는다.
 * 그래서 1순위는 같은 binder 호출을 앱 UID로 직접 하는 것이고, 실패하면 `cmd window density`/`wm density`
 * 프로세스를 실행해 exit code·stdout·stderr를 확인한다. 어느 경로든 적용 뒤 다시 읽어 실제 값을 검증한다.
 */
class DpiManager(context: Context) : DpiController {

    private val appContext = context.applicationContext

    override fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    override fun read(): DpiState {
        readViaWindowManager()?.let { return it }
        readViaShell()?.let { return it }
        // 마지막 수단: 현재 system configuration. 강제값인지 기본값인지는 구분하지 못한다.
        val current = Resources.getSystem().configuration.densityDpi
        val physical = DisplayMetrics.DENSITY_DEVICE_STABLE
        return DpiState(physical, current.takeIf { it != physical }, "Configuration")
    }

    override fun apply(dpi: Int): ApplyResult {
        require(DpiPolicy.isValid(dpi)) { "허용 범위를 벗어난 DPI: $dpi" }
        if (!hasPermission()) return ApplyResult(false, "-", "WRITE_SECURE_SETTINGS 권한 없음")

        val failures = mutableListOf<String>()

        try {
            invoke("setForcedDisplayDensityForUser", Display.DEFAULT_DISPLAY, dpi, userId())
            val after = readViaWindowManager()
            if (after?.current == dpi) return ApplyResult(true, METHOD_BINDER, "적용 후 확인 ${after.current}")
            failures += "$METHOD_BINDER: 호출 후 값 ${after?.current}"
        } catch (t: Throwable) {
            failures += "$METHOD_BINDER: ${describe(t)}"
        }

        for (cmd in listOf(listOf("cmd", "window", "density", dpi.toString()), listOf("wm", "density", dpi.toString()))) {
            val r = runShell(cmd)
            val after = read()
            if (r.exitCode == 0 && after.current == dpi) {
                return ApplyResult(true, cmd.joinToString(" "), "exit=0 적용 후 확인 ${after.current}")
            }
            failures += "${cmd.joinToString(" ")}: ${r.summary()} / 적용 후 값 ${after.current}"
        }
        return ApplyResult(false, "-", failures.joinToString(" | "))
    }

    override fun reset(): ApplyResult {
        if (!hasPermission()) return ApplyResult(false, "-", "WRITE_SECURE_SETTINGS 권한 없음")
        val failures = mutableListOf<String>()
        try {
            invoke("clearForcedDisplayDensityForUser", Display.DEFAULT_DISPLAY, userId())
            val after = readViaWindowManager()
            if (after != null && after.override == null) {
                return ApplyResult(true, METHOD_BINDER, "기본값 ${after.physical}")
            }
            failures += "$METHOD_BINDER: 호출 후 override ${after?.override}"
        } catch (t: Throwable) {
            failures += "$METHOD_BINDER: ${describe(t)}"
        }
        for (cmd in listOf(listOf("cmd", "window", "density", "reset"), listOf("wm", "density", "reset"))) {
            val r = runShell(cmd)
            val after = read()
            if (r.exitCode == 0 && after.override == null) {
                return ApplyResult(true, cmd.joinToString(" "), "기본값 ${after.physical}")
            }
            failures += "${cmd.joinToString(" ")}: ${r.summary()}"
        }
        return ApplyResult(false, "-", failures.joinToString(" | "))
    }

    @Suppress("DEPRECATION")
    override fun currentScreen(): ScreenInfo? = try {
        val display = appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        display.getRealMetrics(m)
        toScreen(m)
    } catch (t: Throwable) {
        null
    }

    override fun displays(): List<DisplayDensity> = (0 until MAX_DISPLAY_ID).mapNotNull { id ->
        try {
            val physical = invoke("getInitialDisplayDensity", id) as Int
            if (physical <= 0) return@mapNotNull null
            val base = try { invoke("getBaseDisplayDensity", id) as Int } catch (t: Throwable) { -1 }
            DisplayDensity(
                id = id,
                screen = screenOf(id),
                physical = physical,
                override = base.takeIf { it > 0 && it != physical },
                readable = base > 0,
            )
        } catch (t: Throwable) {
            null
        }
    }

    override fun applyTo(displayId: Int, dpi: Int): ApplyResult {
        require(DpiPolicy.isValid(dpi)) { "허용 범위를 벗어난 DPI: $dpi" }
        if (!hasPermission()) return ApplyResult(false, "-", "WRITE_SECURE_SETTINGS 권한 없음")
        return try {
            invoke("setForcedDisplayDensityForUser", displayId, dpi, userId())
            val base = try { invoke("getBaseDisplayDensity", displayId) as Int } catch (t: Throwable) { -1 }
            when {
                base == dpi -> ApplyResult(true, METHOD_BINDER, "-d $displayId 적용 후 확인 $base")
                base <= 0 -> ApplyResult(true, METHOD_BINDER, "-d $displayId 적용(꺼진 화면이라 값 확인 불가)")
                else -> ApplyResult(false, METHOD_BINDER, "-d $displayId 호출 후 값 $base")
            }
        } catch (t: Throwable) {
            val r = runShell(listOf("cmd", "window", "density", dpi.toString(), "-d", displayId.toString()))
            ApplyResult(r.exitCode == 0, "cmd window density -d $displayId", "${describe(t)} → ${r.summary()}")
        }
    }

    /** 디스플레이 번호의 해상도·PPI. DisplayManager에 없으면 null. */
    @Suppress("DEPRECATION")
    private fun screenOf(displayId: Int): ScreenInfo? = try {
        val display = appContext.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        display?.let {
            val m = DisplayMetrics()
            it.getRealMetrics(m)
            toScreen(m)
        }
    } catch (t: Throwable) {
        null
    }

    private fun readViaWindowManager(): DpiState? = try {
        val physical = invoke("getInitialDisplayDensity", Display.DEFAULT_DISPLAY) as Int
        val base = invoke("getBaseDisplayDensity", Display.DEFAULT_DISPLAY) as Int
        if (physical <= 0 || base <= 0) {
            null
        } else {
            DpiState(physical, base.takeIf { it != physical }, METHOD_BINDER)
        }
    } catch (t: Throwable) {
        null
    }

    private fun readViaShell(): DpiState? {
        val r = runShell(listOf("cmd", "window", "density"))
        if (r.exitCode != 0) return null
        return parseWmDensity(r.stdout)?.copy(source = "cmd window density")
    }

    private fun invoke(name: String, vararg args: Int): Any? {
        val m = methods.getOrPut(name) {
            iWindowManagerClass.getMethod(name, *Array(args.size) { Int::class.javaPrimitiveType!! })
        }
        return try {
            m.invoke(windowManagerService, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun runShell(cmd: List<String>): ShellResult = try {
        val p = ProcessBuilder(cmd).start()
        val finished = p.waitFor(SHELL_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            ShellResult(-1, "", "timeout ${SHELL_TIMEOUT_SEC}s")
        } else {
            ShellResult(
                p.exitValue(),
                p.inputStream.bufferedReader().readText().trim(),
                p.errorStream.bufferedReader().readText().trim(),
            )
        }
    } catch (t: Throwable) {
        ShellResult(-1, "", describe(t))
    }

    private data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
        fun summary(): String = "exit=$exitCode out='${stdout.take(120)}' err='${stderr.take(160)}'"
    }

    // 화면 밀도를 바꾸는 공개 API가 없어 시스템 내부 IWindowManager를 쓴다(adb의 wm density와 같은 호출).
    // 실패하면 cmd window density로 넘어가므로 기기마다 막혀 있어도 기능은 유지된다.
    @SuppressLint("PrivateApi")
    companion object {
        const val METHOD_BINDER = "IWindowManager"
        private const val SHELL_TIMEOUT_SEC = 5L
        private const val PER_USER_RANGE = 100_000
        /** Galaxy Fold는 0(외부)·1(내부). 여유 있게 0~3번을 본다. */
        private const val MAX_DISPLAY_ID = 4

        private fun toScreen(m: DisplayMetrics): ScreenInfo {
            val w = minOf(m.widthPixels, m.heightPixels)
            val h = maxOf(m.widthPixels, m.heightPixels)
            val ppi = (m.xdpi + m.ydpi) / 2f
            val diag = Math.hypot(m.widthPixels / m.xdpi.toDouble(), m.heightPixels / m.ydpi.toDouble()).toFloat()
            return ScreenInfo("${w}x$h", ppi, diag)
        }

        private val methods = mutableMapOf<String, Method>()

        private val iWindowManagerClass: Class<*> by lazy {
            exemptHiddenApis()
            Class.forName("android.view.IWindowManager")
        }

        private val windowManagerService: Any by lazy {
            exemptHiddenApis()
            Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null) ?: error("WindowManagerGlobal.getWindowManagerService() == null")
        }

        @Volatile
        private var exempted = false

        /** IWindowManager 계열 숨은 API만 접근 허용 목록에 넣는다. */
        fun exemptHiddenApis() {
            if (exempted) return
            // 실제 기기가 아닌 환경(JVM 테스트 등)에서는 실패할 수 있다. 실패하면 셸 경로로 넘어간다.
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/view/IWindowManager",
                    "Landroid/view/WindowManagerGlobal",
                )
            }
            exempted = true
        }

        private fun userId(): Int = Process.myUid() / PER_USER_RANGE

        private fun describe(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message}"

        /** `wm density` / `cmd window density` 출력 파싱. */
        fun parseWmDensity(output: String): DpiState? {
            val physical = Regex("""Physical density:\s*(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
            val override = Regex("""Override density:\s*(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
            if (physical == null) return null
            return DpiState(physical, override, "wm density")
        }
    }
}
