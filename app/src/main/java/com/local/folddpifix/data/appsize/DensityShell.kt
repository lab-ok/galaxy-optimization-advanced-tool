package com.local.folddpifix.data.appsize

import com.local.folddpifix.data.UserId
import com.local.folddpifix.data.shizuku.ShizukuAccess
import android.content.Context
import com.local.folddpifix.domain.AppDensityPolicy
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 앱별 화면 크기 호출 창구. 셸 권한 경로가 둘이다.
 * - Shizuku([ShizukuAccess]): PC 없이 쓰는 기본 경로.
 * - PC 셸 도우미([DensityServer]): 예비 경로. 도우미가 [ShellBridgeProvider]로 바인더를 건네면 연결되고, 끝나면 끊긴다.
 * 도우미가 연결돼 있으면 도우미를, 아니면 Shizuku를 쓴다. 모든 호출은 바인더 통신이라 IO 스레드에서 부른다.
 */
object DensityShell {
    private val binder = MutableStateFlow<IBinder?>(null)

    private val changeCount = MutableStateFlow(0)

    /** 화면 밖(알림창·초기화)에서 값이 바뀔 때마다 1씩 오른다. '앱마다 크기 따로' 화면이 보고 다시 읽는다. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    /** 화면 밖에서 바꿨음을 알린다. */
    fun notifyChanged() = changeCount.update { it + 1 }

    /** 연결된 PC 셸 도우미의 바인더. 없으면 null. */
    val connected: StateFlow<IBinder?> = binder.asStateFlow()

    internal fun attach(b: IBinder) {
        runCatching { b.linkToDeath({ binder.compareAndSet(b, null) }, 0) }.onFailure { return }
        binder.value = b
    }

    private fun user() = UserId.current()

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
        val mine = prefs(context).getInt(pkg, -1).takeIf { it != -1 }
        val shown = AppDensityPolicy.shown(v, mine)
        // 다른 곳(삼성 설정 등)에서 바뀌었으면 FoldFit 기록을 지운다
        if (mine != null && shown != mine) prefs(context).edit().remove(pkg).apply()
        return shown
    }

    /** 앱 화면 크기를 [AppDensityPolicy.STEPS] 중 하나로, 0이면 기본으로 되돌린다. 그 앱은 다시 시작된다. */
    fun set(context: Context, pkg: String, dpi: Int) {
        if (helper()) tx(DensityServer.SET, { writeString(pkg); writeInt(user()); writeInt(dpi) }) { } else ShizukuAccess.set(pkg, user(), dpi)
        prefs(context).edit().apply { if (dpi == 0) remove(pkg) else putInt(pkg, dpi) }.apply()
    }

    // 이전 버전(실험실 시절)과 같은 이름을 유지한다(사용자 기록 보존)
    private fun prefs(context: Context) = context.getSharedPreferences("lab_app_density", Context.MODE_PRIVATE)

    /** 사용자가 크기를 정해 둔 앱(홈 화면에 아이콘이 있는 앱 중). */
    fun appliedApps(context: Context): List<String> =
        AppList.launcherApps(context).map { it.pkg }.filter { runCatching { get(context, it) > 0 }.getOrDefault(false) }

    /** 정해 둔 앱을 모두 기본 크기로 되돌린다. 되돌린 앱 수를 돌려준다. */
    fun resetAll(context: Context): Int =
        appliedApps(context).count { runCatching { set(context, it, 0) }.isSuccess }.also { notifyChanged() }

    fun stop() {
        runCatching { tx(DensityServer.EXIT) { } }
        binder.value = null
    }
}
