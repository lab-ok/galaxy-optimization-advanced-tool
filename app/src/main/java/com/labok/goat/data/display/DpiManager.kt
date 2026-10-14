package com.labok.goat.data.display

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.labok.goat.data.UserId
import com.labok.goat.domain.DpiPolicy
import com.labok.goat.domain.ScreenInfo
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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

    override fun foldable(): Boolean = DeviceKind.isFoldable(appContext)

    override fun hasPermission(): Boolean = appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
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
        toScreen(m, ResolutionState.base(appContext))
    } catch (t: Throwable) {
        null
    }

    override fun displays(): List<DisplayDensity> = (0 until MAX_DISPLAY_ID).mapNotNull { id ->
        try {
            val physical = invoke("getInitialDisplayDensity", id) as Int
            if (physical <= 0) return@mapNotNull null
            val base = try {
                invoke("getBaseDisplayDensity", id) as Int
            } catch (t: Throwable) {
                -1
            }
            DisplayDensity(
                id = id,
                screen = screenOf(id),
                physical = physical,
                override = base.takeIf { it > 0 && it != physical },
                readable = base > 0
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
            val base = try {
                invoke("getBaseDisplayDensity", displayId) as Int
            } catch (t: Throwable) {
                -1
            }
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
            // 해상도 낮추기는 기본 화면(0)에만 건다
            toScreen(m, if (displayId == Display.DEFAULT_DISPLAY) ResolutionState.base(appContext) else null)
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * 디스플레이의 물리 해상도(강제 해상도 wm size와 상관없는 패널 값). 못 읽으면 null.
     * 화면을 해상도로 구분하므로, 해상도를 낮춰도 같은 화면으로 보려고 물리 값을 쓴다.
     */
    private fun initialSize(displayId: Int): Point? = try {
        val p = Point()
        val m = methods.getOrPut("getInitialDisplaySize") {
            iWindowManagerClass.getMethod("getInitialDisplaySize", Int::class.javaPrimitiveType, Point::class.java)
        }
        m.invoke(windowManagerService, displayId, p)
        p.takeIf { it.x > 0 && it.y > 0 }
    } catch (t: Throwable) {
        null
    }

    /** 강제 해상도를 정한다(wm size와 같은 호출). 성공하면 true. */
    fun setForcedSize(displayId: Int, width: Int, height: Int): Boolean = try {
        val m = methods.getOrPut("setForcedDisplaySize") {
            iWindowManagerClass.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        }
        m.invoke(windowManagerService, displayId, width, height)
        true
    } catch (t: Throwable) {
        false
    }

    /** 강제 해상도를 지운다(wm size reset). 성공하면 true. */
    fun clearForcedSize(displayId: Int): Boolean = try {
        invoke("clearForcedDisplaySize", displayId)
        true
    } catch (t: Throwable) {
        false
    }

    /** 물리 해상도(짧은 변, 긴 변). 못 읽으면 null. */
    fun physicalSize(displayId: Int = Display.DEFAULT_DISPLAY): Pair<Int, Int>? = initialSize(displayId)?.let { minOf(it.x, it.y) to maxOf(it.x, it.y) }

    /** 물리 해상도를 기기의 자연 방향 그대로(가로, 세로). 강제 해상도도 이 방향으로 건다. 못 읽으면 null. */
    fun physicalSizeNatural(displayId: Int = Display.DEFAULT_DISPLAY): Pair<Int, Int>? = initialSize(displayId)?.let { it.x to it.y }

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
                p.errorStream.bufferedReader().readText().trim()
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

        /** Galaxy Fold는 0(외부)·1(내부). 여유 있게 0~3번을 본다. */
        private const val MAX_DISPLAY_ID = 4

        /**
         * 화면 정보: 키·대각선은 지금 논리 해상도로 만든다. GOAT가 해상도를 낮춰 둔 동안([base], [ResolutionState])만
         * 그 직전 크기를 키로 쓰고 지금 크기와의 비율을 [ScreenInfo.scale]에 둔다(같은 화면으로 보고 목표 DPI를 같은 비율로 줄이게).
         * 삼성 공식 해상도 설정(FHD+ 등)처럼 GOAT 밖에서 줄인 크기는 지금처럼 그대로 키가 된다. PPI는 패널 값이라 그대로다.
         */
        internal fun toScreen(m: DisplayMetrics, base: Pair<Int, Int>?): ScreenInfo = screenOf(m.widthPixels, m.heightPixels, m.xdpi, m.ydpi, base)

        /** [toScreen]의 계산(시험할 수 있게 DisplayMetrics와 뗐다). */
        internal fun screenOf(widthPx: Int, heightPx: Int, xdpi: Float, ydpi: Float, base: Pair<Int, Int>?): ScreenInfo {
            val logicalShort = minOf(widthPx, heightPx)
            // GOAT 밖에서 해상도를 더 크게 바꿨으면(비율 > 1) 기록이 낡은 것이라 쓰지 않는다
            val b = base?.takeIf { logicalShort <= it.first }
            val w = b?.first ?: logicalShort
            val h = b?.second ?: maxOf(widthPx, heightPx)
            val ppi = (xdpi + ydpi) / 2f
            val diag = Math.hypot(w / xdpi.toDouble(), h / ydpi.toDouble()).toFloat()
            val scale = if (w > 0) logicalShort.toFloat() / w else 1f
            // 1% 안쪽 차이(반올림)는 해상도를 바꾸지 않은 것으로 본다
            return ScreenInfo("${w}x$h", ppi, diag, if (abs(scale - 1f) < 0.01f) 1f else scale)
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

        /** IWindowManager 계열 숨은 API를 접근 허용 목록에 넣는다(Shizuku·앱별 크기 쪽은 HiddenApi가 전체를 따로 허용한다). */
        fun exemptHiddenApis() {
            if (exempted) return
            // 실제 기기가 아닌 환경(JVM 테스트 등)에서는 실패할 수 있다. 실패하면 셸 경로로 넘어간다.
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/view/IWindowManager",
                    "Landroid/view/WindowManagerGlobal"
                )
            }
            exempted = true
        }

        private fun userId(): Int = UserId.current()

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
