package com.labok.goat.data.lab

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 마이크·카메라 사용 표시(상태 표시줄 오른쪽 위 초록 점) 끄기. Android가 기기 설정(device_config) `privacy/camera_mic_icons_enabled`로
 * 켜고 끄는 값을 Shizuku 셸로 바꾼다. `default`를 붙여 기본값으로 넣어 두어야 서버 동기화에 덮이지 않고 남는다.
 */
object PrivacyDot {
    private const val CMD = "cmd device_config"
    private const val NS = "privacy"
    private const val KEY = "camera_mic_icons_enabled"

    /** 초록 점이 꺼져 있는지. Shizuku가 없거나 읽지 못하면 null. 작업 스레드에서 부른다. */
    fun hidden(): Boolean? {
        val v = ShizukuAccess.shell("$CMD get $NS $KEY")?.out?.trim() ?: return null
        return v == "false"
    }

    /** [hide]면 초록 점을 끄고, 아니면 다시 켠다. 다시 읽어 바뀌었으면 true. 작업 스레드에서 부른다. */
    fun set(context: Context, hide: Boolean): Boolean {
        ShizukuAccess.shell("$CMD put $NS $KEY ${!hide} default")
        val ok = hidden() == hide
        LogRepository.from(context).add("실험실 마이크·카메라 사용 표시 ${if (hide) "끔" else "켬"}" + if (ok) "" else " 실패")
        return ok
    }
}
