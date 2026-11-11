package com.labok.goat

import android.content.Context
import android.graphics.Bitmap
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.feature.statusbar.StatusBarHost
import com.labok.goat.ui.liquid.UiGoat

/** 상단바 숨기기 모듈([StatusBarHost])에 앱 공통 기능을 이어 준다. */
object AppStatusBarHost : StatusBarHost.Host {
    override fun shizukuReady() = ShizukuAccess.status.value == ShizukuAccess.State.READY
    override fun shell(command: String): String? = ShizukuAccess.shell(command)?.out
    override fun note(context: Context, text: String) = LogRepository.from(context).add(text)
    override val smallIcon: Int get() = R.drawable.ic_stat_goat
    override fun largeIcon(context: Context): Bitmap? = UiGoat.notificationIcon(context)
}
