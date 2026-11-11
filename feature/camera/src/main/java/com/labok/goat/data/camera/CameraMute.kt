package com.labok.goat.data.camera

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * 카메라 촬영음 끄기. 삼성 국내 모델은 숨은 시스템 설정 [KEY]가 1이면 무음 모드에서도 촬영음을 강제로 낸다.
 * 이 값을 0으로 바꾸면 기기 소리 설정(무음·진동)을 따른다. 숨은 시스템 설정은 일반 앱이 읽거나 쓸 수 없어
 * Shizuku 셸 권한으로 `settings get/put system`을 부른다. Shizuku가 없으면 PC 명령([PC_COMMAND])을 안내한다.
 * 업데이트나 재부팅으로 1로 돌아오는 기기가 있어, [keep]이 켜져 있으면 Shizuku가 연결될 때마다 다시 끈다.
 */
object CameraMute {
    const val KEY = "csc_pref_camera_forced_shuttersound_key"
    const val PC_COMMAND = "adb shell settings put system $KEY 0"
    const val PC_UNDO = "adb shell settings put system $KEY 1"
    private const val PREF = "camera_mute"

    /** 읽은 상태. */
    enum class State(val label: String) {
        /** 촬영음 강제가 꺼져 있다(기기 소리 설정을 따른다). */
        MUTED("꺼짐"),

        /** 촬영음이 강제로 난다. */
        FORCED("강제로 울림"),

        /** 이 기기에는 촬영음 강제 설정이 없다(해외 모델 등: 기기 소리 설정을 이미 따른다). */
        NONE("설정 없음"),

        /** Shizuku가 없어 읽지 못했다. */
        UNKNOWN("읽지 못함")
    }

    private val state = MutableStateFlow(State.UNKNOWN)
    val status: StateFlow<State> = state.asStateFlow()

    /** 사용자가 끄기를 원하는지(다시 끄기의 기준). */
    fun wanted(context: Context) = prefs(context).getBoolean("muted", false)

    /**
     * 풀리면 다시 적용할지. 따로 있던 옵션 스위치를 주 스위치로 합쳐(2026-10-23, 켜 두면 늘 다시 적용) 항상 켬이다.
     * 예전에 끈 기록(keep=false)이 남아 있어도 따르지 않는다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun keep(context: Context) = true

    /** 지금 값을 읽는다(작업 스레드). */
    fun read(): State {
        val r = ShizukuAccess.shell("settings get system $KEY") ?: return State.UNKNOWN.also { state.value = it }
        val s = when (r.out.trim()) {
            "0" -> State.MUTED
            "null", "" -> State.NONE
            else -> State.FORCED
        }
        state.value = s
        return s
    }

    /**
     * 촬영음 강제를 끄거나(mute) 되돌린다(작업 스레드). 써 본 뒤 다시 읽어 확인한다. 성공하면 true.
     * 설정이 없는 기기에 끄기를 쓰면 새로 만들어지므로, 없을 때는 끄기를 쓰지 않는다(이미 소리 설정을 따른다).
     */
    fun set(context: Context, mute: Boolean, how: String): Boolean {
        val log = LogRepository.from(context)
        prefs(context).edit().putBoolean("muted", mute).apply()
        val before = read()
        if (before == State.UNKNOWN) {
            log.add("카메라 촬영음 ${if (mute) "끄기" else "되돌리기"}($how): Shizuku가 연결되지 않아 바꾸지 못함")
            return false
        }
        if (before == State.NONE) {
            log.add("카메라 촬영음($how): 이 기기에는 촬영음 강제 설정이 없음")
            return true
        }
        val r = ShizukuAccess.shell("settings put system $KEY ${if (mute) 0 else 1}")
        val after = read()
        val ok = after == (if (mute) State.MUTED else State.FORCED)
        log.add(
            "카메라 촬영음 ${if (mute) "끄기" else "되돌리기"}($how): ${before.label} → ${after.label}" +
                if (ok) "" else " 실패(${r?.err?.ifBlank { null } ?: "결과 다름"})"
        )
        return ok
    }

    /** 끄기를 원하는데 돌아와 있으면 다시 끈다(Shizuku 연결 때·앱 시작 때, 작업 스레드). */
    fun reapply(context: Context) {
        if (!wanted(context) || !keep(context)) return
        if (read() == State.FORCED) set(context, mute = true, how = "다시 끄기")
    }

    private val binder = Shizuku.OnBinderReceivedListener { reapplyAsync?.invoke() }

    @Volatile private var reapplyAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다: Shizuku가 연결되는 순간(재부팅 뒤 자동 시작 포함) 다시 끄기를 확인한다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (!wanted(app) || !keep(app)) return
        reapplyAsync = { Thread { reapply(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
