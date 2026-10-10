package com.local.folddpifix.data.lab

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException

/**
 * Shizuku 연동(lab 빌드 전용). Shizuku가 켜져 있고 FoldFit을 허용했으면, 삼성 앱별 화면 크기 함수를
 * 셸 권한으로 직접 부른다(ShizukuBinderWrapper). PC 도우미([DensityServer])와 같은 일을 PC 없이 한다.
 * Shizuku 13.6+는 Android 13 이상에서 Wi-Fi에 연결돼 있으면 재부팅 뒤 스스로 다시 켜진다.
 */
object ShizukuAccess {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 7301

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    private val state = MutableStateFlow(State.NOT_RUNNING)
    val status: StateFlow<State> = state.asStateFlow()
    private var installed = false
    private var started = false

    /** 상태 감시를 시작한다. 여러 번 불러도 한 번만 등록한다. */
    fun watch(context: Context) {
        installed = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)
        if (!started) {
            started = true
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { refresh() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
        refresh()
    }

    fun refresh() {
        state.value = when {
            Shizuku.pingBinder() && !Shizuku.isPreV11() ->
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.READY else State.NEEDS_PERMISSION
            installed -> State.NOT_RUNNING
            else -> State.NOT_INSTALLED
        }
    }

    /** Shizuku 허용 창을 띄운다(Shizuku가 켜져 있을 때만). */
    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST)
    }

    private fun atm(): Any {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task"))
        return Class.forName("android.app.IActivityTaskManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun call(name: String, size: Int, vararg args: Any): Any? {
        val svc = atm()
        val m = svc.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == size }
            ?: throw UnsupportedOperationException("이 기기에는 삼성 앱별 화면 크기 함수가 없습니다.")
        return try {
            m.invoke(svc, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    fun supported(): Boolean = runCatching {
        atm().javaClass.methods.any { it.name == "setUserCustomDensity" && it.parameterTypes.size == 4 }
    }.getOrDefault(false)

    fun get(pkg: String, user: Int): Int = call("getCustomDensity", 3, pkg, user, false) as Int

    /** 실기기 코드 기준 인자 순서: (패키지, 사용자, 밀도, 메모). 값은 [DensityServer.STEPS]와 0만. */
    fun set(pkg: String, user: Int, dpi: Int) {
        require(dpi == 0 || dpi in DensityServer.STEPS) { "허용되지 않는 값: $dpi" }
        call("setUserCustomDensity", 4, pkg, user, dpi, "FoldFit")
    }
}
