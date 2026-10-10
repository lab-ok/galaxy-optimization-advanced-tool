package com.local.folddpifix.data.lab

import com.local.folddpifix.data.shizuku.ShizukuAccess
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱별 화면 크기 호출 창구. 셸 권한 경로가 둘이다.
 * - Shizuku([ShizukuAccess]): PC 없이 쓰는 기본 경로.
 * - PC 셸 도우미([DensityServer]): 예비 경로. 도우미가 [ShellBridgeProvider]로 바인더를 건네면 연결되고, 끝나면 끊긴다.
 * 도우미가 연결돼 있으면 도우미를, 아니면 Shizuku를 쓴다. 모든 호출은 바인더 통신이라 IO 스레드에서 부른다.
 */
object DensityShell {
    private val binder = MutableStateFlow<IBinder?>(null)

    /** 값이 바뀔 때마다 1씩 오른다(알림창에서 바꿔도 실험실 화면이 바로 다시 읽도록). */
    val changes = MutableStateFlow(0)

    /** 도우미에 연결돼 있으면 true. */
    val connected: StateFlow<IBinder?> = binder.asStateFlow()

    internal fun attach(b: IBinder) {
        runCatching { b.linkToDeath({ binder.compareAndSet(b, null) }, 0) }.onFailure { return }
        binder.value = b
    }

    private fun user() = Process.myUid() / 100_000

    private fun <T> tx(code: Int, write: Parcel.() -> Unit = {}, read: Parcel.() -> T): T {
        val b = binder.value ?: throw IllegalStateException("셸 도우미가 연결돼 있지 않습니다.")
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DensityServer.DESCRIPTOR)
            data.write()
            if (!b.transact(code, data, reply, 0)) throw IllegalStateException("셸 도우미가 요청을 거절했습니다.")
            reply.readException()
            return reply.read()
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    private fun helper() = binder.value != null

    /** 이 기기에서 삼성 함수를 찾았는지. */
    fun supported(): Boolean =
        if (helper()) tx(DensityServer.PING) { readInt(); readInt() == 1 } else ShizukuAccess.supported()

    private fun raw(pkg: String): Int =
        if (helper()) tx(DensityServer.GET, { writeString(pkg); writeInt(user()) }) { readInt() } else ShizukuAccess.get(pkg, user())

    /**
     * 앱의 현재 화면 크기(dpi). 0이면 시스템 기본.
     * 삼성 읽기 함수는 320을 360으로 돌려준다(실기기 코드: getCustomDensity(…, true)에서 320 → 360). 실제 적용은 320이므로,
     * FoldFit이 320으로 바꾼 앱은 기록해 두었다가 360으로 읽히면 320으로 보여 준다.
     */
    fun get(context: Context, pkg: String): Int {
        val v = raw(pkg)
        val mine = prefs(context).getInt(pkg, -1)
        return when {
            v == 360 && mine == 320 -> 320
            else -> { if (mine != -1 && mine != v) prefs(context).edit().remove(pkg).apply(); v }
        }
    }

    /** 앱 화면 크기를 [DensityServer.STEPS] 중 하나로, 0이면 기본으로 되돌린다. 그 앱은 다시 시작된다. */
    fun set(context: Context, pkg: String, dpi: Int) {
        if (helper()) tx(DensityServer.SET, { writeString(pkg); writeInt(user()); writeInt(dpi) }) { } else ShizukuAccess.set(pkg, user(), dpi)
        prefs(context).edit().apply { if (dpi == 0) remove(pkg) else putInt(pkg, dpi) }.apply()
        changes.value++
    }

    private fun prefs(context: Context) = context.getSharedPreferences("lab_app_density", Context.MODE_PRIVATE)

    fun stop() {
        runCatching { tx(DensityServer.EXIT) { } }
        binder.value = null
    }
}
