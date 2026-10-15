package com.labok.goat.data.lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 자동 플렉스가 켜져 있는 동안 알림의 [플렉스 모드]·[터치패널]·[분할 화면] 버튼을 받아 지금 앱의 모드를 바꾼다. */
class FlexModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getIntExtra(EXTRA_MODE, -1).takeIf { it in AutoFlex.MODES } ?: return
        FlexPanel.switchMode(context.applicationContext, mode)
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}
