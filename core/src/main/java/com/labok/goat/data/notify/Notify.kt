package com.labok.goat.data.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** 알림을 띄울 수 있는지(Android 13부터 알림 권한이 필요). 여러 기능이 함께 쓴다. */
object Notify {
    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
