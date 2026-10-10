package com.local.folddpifix.data.appsize

import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import com.local.folddpifix.data.shizuku.ShizukuAccess
import java.util.concurrent.Executors

/**
 * 지금 화면 맨 앞의 앱을 시스템 이벤트로 따라간다.
 * Shizuku의 셸 권한으로 IActivityTaskManager에 작업 스택 감시자(ITaskStackListener)를 등록하고,
 * 스택이 바뀔 때만 포커스된 작업의 맨 위 앱을 읽는다. 주기적으로 확인(폴링)하지 않아 배터리를 거의 쓰지 않는다.
 * 사용 기록(UsageStats)은 기기에 따라 늦게 반영돼 앱 전환을 놓칠 수 있어 예비로만 쓴다.
 */
object ForegroundWatcher {
    private const val DESCRIPTOR = "android.app.ITaskStackListener"

    /** FoldFit·시스템 UI·홈 화면을 뺀, 마지막으로 맨 앞에 있던 앱. 홈 화면이면 null. */
    @Volatile var current: String? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var proxy: Any? = null
    private var onChange: ((String?) -> Unit)? = null

    val running get() = proxy != null

    /** 감시를 시작한다. Shizuku가 준비되지 않았으면 false. [changed]는 맨 앞 앱이 바뀔 때마다 IO 스레드에서 불린다. */
    fun start(context: Context, changed: (String?) -> Unit): Boolean {
        onChange = changed
        if (proxy != null) return true
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return false
        return runCatching {
            val atm = ShizukuAccess.atm()
            val code = Class.forName("$DESCRIPTOR\$Stub").getDeclaredField("TRANSACTION_onTaskStackChanged")
                .apply { isAccessible = true }.getInt(null)
            val ctx = context.applicationContext
            val refresh = Runnable { io.execute { update(ctx) } }
            val binder = object : Binder() {
                init { attachInterface(null, DESCRIPTOR) }
                override fun onTransact(c: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (c == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
                    // 스택이 바뀌는 순간 여러 번 올 수 있어 잠깐 모았다가 한 번 읽는다
                    if (c == code) { main.removeCallbacks(refresh); main.postDelayed(refresh, 200) }
                    return true
                }
            }
            val p = Class.forName("$DESCRIPTOR\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
            atm.javaClass.methods.first { it.name == "registerTaskStackListener" && it.parameterTypes.size == 1 }.invoke(atm, p)
            proxy = p
            io.execute { update(ctx) }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        val p = proxy ?: return
        proxy = null
        runCatching {
            val atm = ShizukuAccess.atm()
            atm.javaClass.methods.first { it.name == "unregisterTaskStackListener" && it.parameterTypes.size == 1 }.invoke(atm, p)
        }
    }

    /**
     * 포커스된 작업의 맨 위 앱(셸 권한). 홈 화면·최근 앱 화면이면 ""(앱 없음), 읽지 못하면 null.
     * 홈 화면은 패키지 이름이 아니라 작업 종류(topActivityType: 2=홈, 3=최근 앱)로 가린다.
     */
    private fun top(): String? = runCatching {
        val atm = ShizukuAccess.atm()
        val info = atm.javaClass.methods.first { it.name == "getFocusedRootTaskInfo" && it.parameterTypes.isEmpty() }.invoke(atm)
            ?: return null
        val type = runCatching { info.javaClass.getField("topActivityType").getInt(info) }.getOrDefault(1)
        if (type == 2 || type == 3) return ""
        (info.javaClass.getField("topActivity").get(info) as ComponentName?)?.packageName
    }.getOrNull()

    private fun update(context: Context) {
        val pkg = top() ?: return
        if (pkg == context.packageName || pkg == "com.android.systemui" || pkg == ShizukuAccess.PACKAGE) return
        val next = pkg.ifEmpty { null }
        if (next == current) return
        current = next
        onChange?.invoke(next)
    }
}
