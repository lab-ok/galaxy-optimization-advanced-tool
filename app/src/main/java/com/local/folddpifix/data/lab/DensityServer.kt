package com.local.folddpifix.data.lab

import android.content.AttributionSource
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.Process
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * 실험실 셸 도우미(lab 빌드 전용). PC에서 adb 셸로 한 번 띄우면 셸 권한(uid 2000)으로 돌면서
 * FoldFit에 바인더 하나를 건네고, 앱별 화면 크기 '읽기·바꾸기' 두 가지만 대신 해 준다.
 * 삼성 함수(IActivityTaskManager.getCustomDensity/setUserCustomDensity)가 앱에는 줄 수 없는
 * MANAGE_ACTIVITY_TASKS 권한을 요구하기 때문이다(adb 셸은 이 권한을 가지고 있다).
 *
 * 안전 장치
 * - 호출자 확인: FoldFit uid가 아닌 요청은 모두 거절한다.
 * - 기능 제한: 임의 명령 실행은 없다. 바꾸는 값도 삼성이 허용하는 단계([STEPS])와 0(기본)만 받는다.
 * - 수명: FoldFit 프로세스가 끝나거나 [IDLE_MS] 동안 요청이 없으면 스스로 끝난다.
 *
 * 띄우는 명령은 [startCommand]가 만든다. 앱 쪽 연결은 [ShellBridgeProvider]·[DensityShell].
 */
object DensityServer {
    const val PKG = "com.local.folddpifix"
    const val AUTHORITY = "$PKG.shell"
    const val DESCRIPTOR = "com.local.folddpifix.IDensityShell"
    const val VERSION = 1
    const val PING = IBinder.FIRST_CALL_TRANSACTION
    const val GET = PING + 1
    const val SET = PING + 2
    const val EXIT = PING + 3

    /** 삼성 MultiTaskingAppCompatDensityOverrides.SUPPORTED_VALUES(One UI 9 실기기 코드에서 확인). 0은 '기본'. */
    val STEPS = listOf(320, 360, 420, 450, 480, 510)
    private const val IDLE_MS = 30 * 60 * 1000L

    /** PC PowerShell(platform-tools 폴더)에서 실행할 명령. 작은따옴표 안이라 $( )를 PowerShell이 건드리지 않는다. */
    fun startCommand(): String =
        ".\\adb shell 'CLASSPATH=\$(pm path $PKG | cut -d: -f2) setsid app_process /system/bin --nice-name=foldfit_shell " +
            "${DensityServer::class.java.name} > /data/local/tmp/foldfit-shell.log 2>&1 &'"

    @JvmStatic
    fun main(args: Array<String>) {
        log("시작 uid=${Process.myUid()} pid=${Process.myPid()}")
        Looper.prepareMainLooper()
        val handler = Handler(Looper.getMainLooper())
        val appUid = runCatching { packageUid(PKG) }.getOrElse { quit("FoldFit을 찾지 못했습니다: ${describe(it)}") }
        val atm = runCatching { asInterface("activity_task", "android.app.IActivityTaskManager") }.getOrNull()
        val getM = atm?.javaClass?.methods?.firstOrNull { it.name == "getCustomDensity" && it.parameterTypes.size == 3 }
        val setM = atm?.javaClass?.methods?.firstOrNull { it.name == "setUserCustomDensity" && it.parameterTypes.size == 4 }
        log("FoldFit uid=$appUid · 삼성 함수 ${if (getM != null && setM != null) "있음" else "없음"}")

        val idle = Runnable { quit("${IDLE_MS / 60000}분 동안 요청이 없어 끝냅니다.") }
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
                if (getCallingUid() != appUid) {
                    log("거절: uid ${getCallingUid()}")
                    return false
                }
                data.enforceInterface(DESCRIPTOR)
                handler.removeCallbacks(idle)
                handler.postDelayed(idle, IDLE_MS)
                try {
                    when (code) {
                        PING -> { reply?.writeNoException(); reply?.writeInt(VERSION); reply?.writeInt(if (getM != null && setM != null) 1 else 0) }
                        GET -> {
                            val pkg = data.readString()!!; val user = data.readInt()
                            if (getM == null) throw UnsupportedOperationException("이 기기에는 삼성 앱별 화면 크기 함수가 없습니다.")
                            // (패키지, 사용자, 사용자 설정만): true면 사용자가 정한 값만, false면 삼성이 정해 둔 앱별 기본값까지 돌려준다.
                            val v = unwrap { getM.invoke(atm, pkg, user, true) } as Int
                            reply?.writeNoException(); reply?.writeInt(v)
                        }
                        SET -> {
                            val pkg = data.readString()!!; val user = data.readInt(); val dpi = data.readInt()
                            if (setM == null) throw UnsupportedOperationException("이 기기에는 삼성 앱별 화면 크기 함수가 없습니다.")
                            require(dpi == 0 || dpi in STEPS) { "허용되지 않는 값: $dpi" }
                            // 실기기 코드 기준 인자 순서는 (패키지, 사용자, 밀도, 메모). 바뀌면 그 앱을 다시 띄워 바로 적용된다.
                            unwrap { setM.invoke(atm, pkg, user, dpi, "FoldFit") }
                            log("바꿈: $pkg → ${if (dpi == 0) "기본" else dpi}")
                            reply?.writeNoException()
                        }
                        EXIT -> { reply?.writeNoException(); handler.post { quit("FoldFit이 끝내기를 요청했습니다.") } }
                        else -> return false
                    }
                } catch (e: Exception) {
                    log("오류: ${describe(e)}")
                    reply?.writeException(e as? RuntimeException ?: IllegalStateException(describe(e)))
                }
                return true
            }
        }

        // FoldFit의 ShellBridgeProvider를 셸 권한으로 열어 바인더를 건넨다. FoldFit이 꺼져 있으면 시스템이 띄운다.
        val provider = runCatching { openProvider() }.getOrElse { quit("FoldFit(실험실 빌드)에 연결하지 못했습니다: ${describe(it)}") }
        runCatching { callProvider(provider, Bundle().apply { putBinder("binder", binder) }) }
            .onFailure { quit("바인더를 건네지 못했습니다: ${describe(it)}") }
        // FoldFit 프로세스가 끝나면 도우미도 함께 끝낸다.
        (provider as IInterface).asBinder().linkToDeath({ quit("FoldFit이 종료돼 함께 끝냅니다.") }, 0)
        handler.postDelayed(idle, IDLE_MS)
        log("연결됨")
        Looper.loop()
    }

    private fun log(msg: String) = println("[foldfit_shell] $msg")

    private fun quit(msg: String): Nothing {
        log(msg)
        exitProcess(0)
    }

    private fun describe(t: Throwable): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    private inline fun unwrap(block: () -> Any?): Any? = try {
        block()
    } catch (e: InvocationTargetException) {
        throw (e.targetException as? RuntimeException ?: IllegalStateException(describe(e)))
    }

    private fun service(name: String): IBinder =
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder

    private fun asInterface(service: String, iface: String): Any =
        Class.forName("$iface\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, service(service))!!

    /** IPackageManager.getPackageUid(String, long|int flags, int userId). 판본마다 flags 형이 달라 둘 다 받는다. */
    private fun packageUid(pkg: String): Int {
        val pm = asInterface("package", "android.content.pm.IPackageManager")
        val m = pm.javaClass.methods.first { it.name == "getPackageUid" && it.parameterTypes.size == 3 }
        val flags: Any = if (m.parameterTypes[1] == Long::class.javaPrimitiveType) 0L else 0
        return (m.invoke(pm, pkg, flags, 0) as Int).also { require(it > 0) { "uid 없음" } }
    }

    private fun openProvider(): Any {
        val am = asInterface("activity", "android.app.IActivityManager")
        val m: Method = am.javaClass.methods.first { it.name == "getContentProviderExternal" && it.parameterTypes.size == 4 }
        val holder = m.invoke(am, AUTHORITY, 0, Binder(), "foldfit_shell")
            ?: error("$AUTHORITY 제공자가 없습니다. 실험실 빌드가 설치돼 있는지 확인하세요.")
        return holder.javaClass.getField("provider").get(holder)!!
    }

    /** IContentProvider.call: API 31+은 (AttributionSource, authority, method, arg, extras). */
    private fun callProvider(provider: Any, extras: Bundle) {
        val m = provider.javaClass.methods.first {
            it.name == "call" && it.parameterTypes.size == 5 && it.parameterTypes[0] == AttributionSource::class.java
        }
        val source = AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build()
        m.invoke(provider, source, AUTHORITY, "bind", null, extras)
    }
}
