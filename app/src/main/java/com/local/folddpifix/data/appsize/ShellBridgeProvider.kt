package com.local.folddpifix.data.appsize

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * 셸 도우미([DensityServer])가 바인더를 건네는 통로.
 * 셸(uid 2000)이나 root가 부른 "bind"만 받고, 그 밖의 호출과 데이터 접근은 모두 무시한다.
 */
class ShellBridgeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) return null
        if (method != "bind") return null
        extras?.getBinder("binder")?.let(DensityShell::attach)
        return Bundle()
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
