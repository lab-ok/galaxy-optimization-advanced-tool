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
    private const val PREF = "privacy_dot"
    private const val NS = "privacy"
    private const val KEY = "camera_mic_icons_enabled"

    /**
     * 이 기기에서 쓸 수 있는지. Android 14부터는 셸(ADB·Shizuku)도 구글 허용 목록에 있는 값만 바꿀 수 있는데 이 값은 목록에 없어
     * 막힌다(폴드8 Android 17 실기: "must add flag to the allowlist"). 그래서 Android 13 이하에서만 켜고, 그 밖에서도 한 번 막히면 끈다.
     */
    fun supported(context: Context): Boolean = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.TIRAMISU && !context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("blocked", false)

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

    /**
     * 셸 오류에서 실제 이유를 고른다. 첫 줄은 "Exception occurred while executing 'put':"뿐이라(폴드8 실기) 그 아래
     * `…Exception: 이유` 줄을 찾아 예외 이름과 이유를 돌려준다(시험할 수 있게 뗐다).
     */
    internal fun reason(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val pat = Regex("""([\w$]+(?:Exception|Error)):\s*(.*)""")
        val ex = lines.firstNotNullOfOrNull { pat.find(it) }
        return (ex?.let { "${it.groupValues[1]}: ${it.groupValues[2]}" } ?: lines.firstOrNull() ?: "바뀌지 않음").take(200)
    }

    /** [hide]면 초록 점을 끄고, 아니면 다시 켠다. 바뀌었으면 null, 못 바꾸면 이유(셸 메시지). 작업 스레드에서 부른다. */
    fun set(context: Context, hide: Boolean): String? {
        var last = "Shizuku 연결이 필요합니다."
        for (cmd in commands(!hide)) {
            val r = ShizukuAccess.shell(cmd) ?: break
            if (hidden() == hide) {
                LogRepository.from(context).add("실험실 마이크·카메라 사용 표시 ${if (hide) "끔" else "켬"} ($cmd)")
                return null
            }
            last = reason(r.err.ifBlank { r.out })
        }
        LogRepository.from(context).add("실험실 마이크·카메라 사용 표시 ${if (hide) "끔" else "켬"} 실패: $last")
        // 허용 목록 때문에 막힌 기기는 다시 시도해도 같으니 기억해 두고 스위치를 잠근다
        if (last.contains("allowlist", ignoreCase = true)) context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("blocked", true).apply()
        return last
    }
}
