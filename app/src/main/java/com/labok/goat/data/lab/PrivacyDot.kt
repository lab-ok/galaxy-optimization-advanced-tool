package com.labok.goat.data.lab

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 마이크·카메라 사용 표시(상태 표시줄 오른쪽 위 초록 점) 끄기. Android 기기 설정(device_config)의 `privacy/camera_mic_icons_enabled`를
 * Shizuku 셸로 바꾼다. Android 버전마다 받아 주는 명령이 달라(에뮬레이터 Android 14는 `put … default`로 됐지만 폴드8 Android 17은 거부)
 * 여러 방식을 차례로 시도하고, 다시 읽어 바뀐 방식에서 멈춘다. 모두 실패하면 마지막 셸 메시지를 돌려준다.
 */
object PrivacyDot {
    private const val NS = "privacy"
    private const val KEY = "camera_mic_icons_enabled"

    /** 초록 점이 꺼져 있는지. Shizuku가 없거나 읽지 못하면 null. 작업 스레드에서 부른다. */
    fun hidden(): Boolean? {
        val v = ShizukuAccess.shell("cmd device_config get $NS $KEY")?.out?.trim() ?: return null
        return v == "false"
    }

    /** 시도할 명령들(앞에서부터). override는 Android 15부터 있는 기기 안 덮어쓰기다. */
    internal fun commands(value: Boolean) = listOf(
        "cmd device_config put $NS $KEY $value default",
        "cmd device_config put $NS $KEY $value",
        "cmd device_config override $NS $KEY $value",
        "device_config put $NS $KEY $value"
    )

    /** [hide]면 초록 점을 끄고, 아니면 다시 켠다. 바뀌었으면 null, 못 바꾸면 이유(셸 메시지). 작업 스레드에서 부른다. */
    fun set(context: Context, hide: Boolean): String? {
        var last = "Shizuku 연결이 필요합니다."
        for (cmd in commands(!hide)) {
            val r = ShizukuAccess.shell(cmd) ?: break
            if (hidden() == hide) {
                LogRepository.from(context).add("실험실 마이크·카메라 사용 표시 ${if (hide) "끔" else "켬"} ($cmd)")
                return null
            }
            last = r.err.ifBlank { r.out }.trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "바뀌지 않음"
        }
        LogRepository.from(context).add("실험실 마이크·카메라 사용 표시 ${if (hide) "끔" else "켬"} 실패: $last")
        return last
    }
}
