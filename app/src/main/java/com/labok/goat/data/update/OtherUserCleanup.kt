package com.labok.goat.data.update

import android.content.Context
import com.labok.goat.BuildConfig
import com.labok.goat.data.UserId
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 다른 사용자 공간에 깔린 GOAT 지우기: 1.24.x까지 Shizuku 업데이트가 사용자를 정하지 않고 `pm install`을 불러
 * 듀얼 메신저(사용자 95)·보안 폴더 같은 다른 사용자 공간에도 GOAT가 '듀얼' 표시를 달고 깔렸다(1.24.0 사용자 문의).
 * Shizuku가 연결되면 기본 사용자(0)의 GOAT만 다른 공간의 GOAT를 지운다. 복제본 쪽 GOAT가 본 앱을 지우는 일이 없도록
 * 기본 사용자가 아니면 아무것도 하지 않는다(소장님 지정 10-24: 자동으로 지우기). 판마다 한 번, 실패하면 다음 연결 때 다시 한다.
 */
object OtherUserCleanup {
    private const val PREF = "other_user_cleanup"

    @Volatile private var app: Context? = null

    private val binder = Shizuku.OnBinderReceivedListener { app?.let { ctx -> Thread { run(ctx) }.start() } }

    /** 앱 시작 때 부른다. */
    fun watch(context: Context) {
        if (UserId.current() != 0) return
        val ctx = context.applicationContext
        if (prefs(ctx).getInt("checked", -1) == BuildConfig.VERSION_CODE) return
        app = ctx
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun run(context: Context) {
        if (prefs(context).getInt("checked", -1) == BuildConfig.VERSION_CODE) return
        val pkg = context.packageName
        val users = ShizukuAccess.shell("pm list users")?.out ?: return
        var done = true
        var removed = 0
        otherUsers(users).forEach { id ->
            val listed = ShizukuAccess.shell("pm list packages --user $id $pkg")?.out
            if (listed == null) {
                done = false
                return@forEach
            }
            if (listed.lines().none { it.trim() == "package:$pkg" }) return@forEach
            if (ShizukuAccess.shell("pm uninstall --user $id $pkg")?.out?.contains("Success") == true) removed++ else done = false
        }
        if (removed > 0) LogRepository.from(context).add("다른 사용자 공간(듀얼 메신저·보안 폴더 등)에 깔린 GOAT ${removed}개를 지움")
        if (done) prefs(context).edit().putInt("checked", BuildConfig.VERSION_CODE).apply()
    }

    /** `pm list users` 출력에서 기본 사용자(0)를 뺀 사용자 번호들. */
    internal fun otherUsers(out: String): List<Int> = Regex("UserInfo\\{(\\d+):").findAll(out).map { it.groupValues[1].toInt() }.filter { it != 0 }.toList()

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
