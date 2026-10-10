package com.local.folddpifix.data.shizuku

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.content.pm.PackageManager
import com.local.folddpifix.data.lab.AppUsage
import com.local.folddpifix.data.lab.DensityServer
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
 * Shizuku 연동. Shizuku가 켜져 있고 FoldFit을 허용했으면 셸 권한이 필요한 일을 PC 없이 한다(ShizukuBinderWrapper).
 * - 기본 기능: FoldFit에 WRITE_SECURE_SETTINGS를 스스로 부여한다([grantSecureSettings]). 한 번 받으면 Shizuku 없이도 유지된다.
 * - 실험실: 삼성 앱별 화면 크기 함수를 셸 권한으로 부른다. PC 도우미([DensityServer])와 같은 일을 한다.
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
     * FoldFit에 WRITE_SECURE_SETTINGS를 셸 권한으로 부여한다(adb의 `pm grant`와 같은 일).
     * IPermissionManager.grantRuntimePermission은 판본마다 인자가 달라(사용자 ID 앞에 기기 ID가 붙기도 함) 형으로 맞춘다.
     */
    fun grantSecureSettings(context: Context): Boolean = runCatching {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        val user = android.os.Process.myUid() / 100_000
        val perm = Manifest.permission.WRITE_SECURE_SETTINGS
        val pm = Class.forName("android.permission.IPermissionManager\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService("permissionmgr")))!!
        val m = pm.javaClass.methods.first { it.name == "grantRuntimePermission" && it.parameterTypes.size >= 3 }
        val types = m.parameterTypes
        val args = arrayOfNulls<Any>(types.size)
        args[0] = context.packageName; args[1] = perm
        for (k in 2 until types.size) {
            args[k] = when {
                k == types.lastIndex -> user
                types[k] == String::class.java -> "default:0"  // VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
                else -> 0                                     // Context.DEVICE_ID_DEFAULT
            }
        }
        try { m.invoke(pm, *args) } catch (e: InvocationTargetException) { throw e.targetException }
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

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

    internal fun atm(): Any {
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

    /** 사용자가 정한 값만 읽는다(true). false면 삼성이 앱별로 정해 둔 기본값까지 섞여 나온다. */
    fun get(pkg: String, user: Int): Int = call("getCustomDensity", 3, pkg, user, true) as Int

    /** FoldFit에 사용 기록 접근(최근 사용 순 정렬·지금 앱 찾기용)을 셸 권한으로 허용한다. */
    fun grantUsageAccess(context: Context): Boolean = runCatching {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        val ops = Class.forName("com.android.internal.app.IAppOpsService\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE)))!!
        val code = runCatching {
            AppOpsManager::class.java.getMethod("strOpToOp", String::class.java).invoke(null, AppOpsManager.OPSTR_GET_USAGE_STATS) as Int
        }.getOrDefault(43)
        val m = ops.javaClass.methods.first { it.name == "setMode" && it.parameterTypes.size == 4 }
        m.invoke(ops, code, android.os.Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        AppUsage.hasAccess(context)
    }.getOrDefault(false)

    /** 실기기 코드 기준 인자 순서: (패키지, 사용자, 밀도, 메모). 값은 [DensityServer.STEPS]와 0만. */
    fun set(pkg: String, user: Int, dpi: Int) {
        require(dpi == 0 || dpi in DensityServer.STEPS) { "허용되지 않는 값: $dpi" }
        call("setUserCustomDensity", 4, pkg, user, dpi, "FoldFit")
    }
}
