package com.local.folddpifix.data.shizuku

import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.HiddenApi
import com.local.folddpifix.data.UserId
import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.content.pm.PackageManager
import com.local.folddpifix.data.appsize.AppUsage
import com.local.folddpifix.domain.AppDensityPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException

/**
 * Shizuku 연동. Shizuku가 켜져 있고 FoldFit을 허용했으면 셸 권한이 필요한 일을 PC 없이 한다(ShizukuBinderWrapper).
 * - 기본 기능: FoldFit에 WRITE_SECURE_SETTINGS를 스스로 부여한다([grantSecureSettings]). 한 번 받으면 Shizuku 없이도 유지된다.
 * - 앱별 화면 배율 설정: 삼성 앱별 화면 크기 함수를 셸 권한으로 부른다. PC 도우미([DensityServer])와 같은 일을 한다.
 * Shizuku 13.6+는 Android 13 이상에서 Wi-Fi에 연결돼 있으면 재부팅 뒤 스스로 다시 켜진다.
 */
object ShizukuAccess {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 7301

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    private val state = MutableStateFlow(State.NOT_RUNNING)
    val status: StateFlow<State> = state.asStateFlow()
    @Volatile private var installed = false
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 상태 감시를 시작한다. 여러 번 불러도 한 번만 등록한다. */
    fun watch(context: Context) {
        installed = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)
        if (started.compareAndSet(false, true)) {
            val app = context.applicationContext
            Shizuku.addBinderReceivedListenerSticky { refresh(); autoDisableWirelessDebug(app) }
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

    /** 'Shizuku가 켜지면 무선 디버깅 끄기' 옵션(기본 꺼짐). */
    fun autoOffEnabled(context: Context) = prefs(context).getBoolean("wireless_auto_off", false)

    fun setAutoOff(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("wireless_auto_off", on).apply()
        LogRepository.from(context).add("Shizuku 시작 뒤 무선 디버깅 끄기 ${if (on) "켜짐" else "꺼짐"}")
        if (on) autoDisableWirelessDebug(context)
    }

    /**
     * 옵션이 켜져 있고 Shizuku가 켜져 있으면, 몇 초 뒤 무선 디버깅을 끈다(WRITE_SECURE_SETTINGS).
     * 무선 디버깅은 Shizuku를 시작할 때만 필요하고, 켜 두면 같은 Wi-Fi의 기기가 페어링을 시도할 수 있다.
     * 기기에 따라 무선 디버깅을 끄면 Shizuku도 멈출 수 있어 기본은 꺼 둔다.
     */
    fun autoDisableWirelessDebug(context: Context) {
        if (!autoOffEnabled(context) || !Shizuku.pingBinder() || !wirelessDebugOn(context)) return
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!Shizuku.pingBinder()) return@postDelayed
            val ok = runCatching { Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0) }.isSuccess
            LogRepository.from(context).add("Shizuku가 켜져 있어 무선 디버깅을 껐습니다: ${if (ok) "성공" else "실패"}")
        }, 5_000)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("shizuku", Context.MODE_PRIVATE)

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
        }.isSuccess.also { LogRepository.from(context).add("무선 디버깅 켜기(사용자 요청): ${if (it) "성공" else "실패"}") }
    }

    /**
     * FoldFit에 WRITE_SECURE_SETTINGS를 셸 권한으로 부여한다(adb의 `pm grant`와 같은 일).
     * IPermissionManager.grantRuntimePermission은 판본마다 인자가 달라(사용자 ID 앞에 기기 ID가 붙기도 함) 형으로 맞춘다.
     */
    fun grantSecureSettings(context: Context): Boolean = runCatching {
        val user = UserId.current()
        val perm = Manifest.permission.WRITE_SECURE_SETTINGS
        val pm = HiddenApi.asInterface("android.permission.IPermissionManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("permissionmgr")))
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
    }.getOrDefault(false).also { LogRepository.from(context).add("Shizuku로 WRITE_SECURE_SETTINGS 받기: ${if (it) "성공" else "실패"}") }

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
        return HiddenApi.asInterface("android.app.IActivityTaskManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")))
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
        val methods = atm().javaClass.methods
        methods.any { it.name == "setUserCustomDensity" && it.parameterTypes.size == 4 } &&
            methods.any { it.name == "getCustomDensity" && it.parameterTypes.size == 3 }
    }.getOrDefault(false)

    /** 사용자가 정한 값만 읽는다(true). false면 삼성이 앱별로 정해 둔 기본값까지 섞여 나온다. */
    fun get(pkg: String, user: Int): Int = call("getCustomDensity", 3, pkg, user, true) as Int

    /** FoldFit에 사용 기록 접근(최근 사용 순 정렬·지금 앱 찾기용)을 셸 권한으로 허용한다. */
    fun grantUsageAccess(context: Context): Boolean = runCatching {
        val ops = HiddenApi.asInterface("com.android.internal.app.IAppOpsService", ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE)))
        val code = runCatching {
            AppOpsManager::class.java.getMethod("strOpToOp", String::class.java).invoke(null, AppOpsManager.OPSTR_GET_USAGE_STATS) as Int
        }.getOrDefault(43)
        val m = ops.javaClass.methods.first { it.name == "setMode" && it.parameterTypes.size == 4 }
        m.invoke(ops, code, android.os.Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        AppUsage.hasAccess(context)
    }.getOrDefault(false).also { LogRepository.from(context).add("Shizuku로 사용 기록 접근 허용: ${if (it) "성공" else "실패"}") }

    /** 실기기 코드 기준 인자 순서: (패키지, 사용자, 밀도, 메모). 값은 [AppDensityPolicy.STEPS]와 0만. */
    fun set(pkg: String, user: Int, dpi: Int) {
        require(AppDensityPolicy.isAllowed(dpi)) { "허용되지 않는 값: $dpi" }
        call("setUserCustomDensity", 4, pkg, user, dpi, "FoldFit")
    }
}
