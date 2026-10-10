package com.local.folddpifix.data

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 숨은 시스템 API(AIDL 인터페이스)를 리플렉션으로 쓰는 공통 도구.
 * Shizuku 호출·앱별 크기·조사 도구가 IActivityTaskManager, IPermissionManager, IAppOpsService 등 여러 인터페이스를 쓰므로
 * 처음 한 번 전체를 허용한다. (기본 기능의 DpiManager는 IWindowManager 계열만 따로 허용한다.)
 * PC 셸 도우미(DensityServer, app_process)에서도 같은 클래스를 쓴다.
 */
object HiddenApi {
    private val exempted = AtomicBoolean(false)

    fun exemptAll() {
        if (exempted.compareAndSet(false, true)) runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
    }

    /** [iface]`$Stub.asInterface(binder)` — 바인더를 그 인터페이스 프록시로 바꾼다. */
    fun asInterface(iface: String, binder: IBinder): Any {
        exemptAll()
        return Class.forName("$iface\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    /** ServiceManager.getService([name]). */
    fun service(name: String): IBinder {
        exemptAll()
        return Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder
    }

    /** 리플렉션 호출에서 난 예외를 "종류: 메시지"로. 감싼 예외(InvocationTargetException)는 벗긴다. */
    fun describe(t: Throwable): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}
