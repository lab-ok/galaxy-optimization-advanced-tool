package com.local.folddpifix.data.lab

import android.os.IBinder
import android.os.Parcel
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 셸 도우미([DensityServer]) 클라이언트. 도우미가 [ShellBridgeProvider]로 바인더를 건네면 연결되고,
 * 도우미가 끝나면 자동으로 끊긴다. 모든 호출은 바인더 통신이라 IO 스레드에서 부른다.
 */
object DensityShell {
    private val binder = MutableStateFlow<IBinder?>(null)

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

    /** 도우미가 이 기기에서 삼성 함수를 찾았는지. */
    fun supported(): Boolean = tx(DensityServer.PING) { readInt(); readInt() == 1 }

    /** 앱의 현재 화면 크기(dpi). 0이면 시스템 기본. */
    fun get(pkg: String): Int = tx(DensityServer.GET, { writeString(pkg); writeInt(user()) }) { readInt() }

    /** 앱 화면 크기를 [DensityServer.STEPS] 중 하나로, 0이면 기본으로 되돌린다. 그 앱은 다시 시작된다. */
    fun set(pkg: String, dpi: Int) = tx(DensityServer.SET, { writeString(pkg); writeInt(user()); writeInt(dpi) }) { }

    fun stop() {
        runCatching { tx(DensityServer.EXIT) { } }
        binder.value = null
    }
}
