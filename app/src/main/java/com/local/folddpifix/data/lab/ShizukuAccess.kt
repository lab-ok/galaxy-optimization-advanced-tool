package com.local.folddpifix.data.lab

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
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

    /** 무선 디버깅이 켜져 있는지. */
    fun wirelessDebugOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1

    /** Wi-Fi에 연결돼 있는지(무선 디버깅은 Wi-Fi가 있어야 켜진다). */
    fun wifiConnected(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(true)  // 확인할 수 없으면 막지 않는다

    /**
     * 무선 디버깅을 켠다. FoldFit은 기본 기능 때문에 WRITE_SECURE_SETTINGS를 이미 받아 두었으므로
     * 개발자 옵션 화면에 가지 않고 바로 켤 수 있다. 권한이 없으면 false(개발자 옵션 화면으로 안내).
     */
    fun enableWirelessDebug(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        val cr = context.contentResolver
        return runCatching {
            Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        }.isSuccess
    }

    /**
     * Shizuku의 '부팅 시 시작'이 켜져 있는지. Shizuku는 이 설정을 BootCompleteReceiver 컴포넌트를 켜고 끄는 것으로
     * 저장하고 기본값은 켜짐이다. 셸 권한으로도 다른 앱의 컴포넌트는 바꿀 수 없어(에뮬레이터에서 확인) 확인만 한다.
     */
    fun bootStartOn(context: Context): Boolean = runCatching {
        val cn = ComponentName(PACKAGE, "moe.shizuku.manager.receiver.BootCompleteReceiver")
        context.packageManager.getComponentEnabledSetting(cn) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(true)

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
