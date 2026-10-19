package com.labok.goat.data.lab

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.accessibility.AccessibilityEvent
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 볼륨 버튼 회전(1.16.1 실험실 → 1.17.0 정식): 화면을 돌려 볼륨 버튼 방향이 뒤집히면(버튼이 위로 가는 가로, 거꾸로 세로) 올리기·내리기를 바꾼다.
 * 아이패드처럼 화면 기준으로 오른쪽(위)이 올리기가 된다. 삼성 기본 설정에는 없고 루트 없이 되는 방법은 접근성 서비스의
 * 키 필터뿐이라 [VolumeRotateService]가 볼륨 키만 받아 처리한다(화면 내용은 읽지 않음). GOAT의 시스템 설정 권한으로
 * 접근성 서비스를 직접 켜고 끈다. 사용자 문의 메일(2026-10-13)에서 나온 기능이다.
 */
object VolumeRotate {
    private const val KEY_INVERT = "invert"
    private const val KEY_CAMERA = "camera_exempt"
    private const val KEY_WANTED = "wanted"

    fun component(context: Context) = ComponentName(context, VolumeRotateService::class.java)

    /** 접근성 설정에 서비스가 켜져 있는지. */
    fun enabled(context: Context): Boolean {
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val c = component(context)
        return list.split(':').any { ComponentName.unflattenFromString(it) == c }
    }

    /** 서비스를 켜거나 끈다(시스템 설정 권한). 성공하면 true. */
    fun setEnabled(context: Context, on: Boolean, why: String? = null): Boolean = runCatching {
        val r = context.contentResolver
        val c = component(context)
        val others = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != c }
        val next = (if (on) others + c.flattenToString() else others).joinToString(":")
        Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
        if (on) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        prefs(context).edit().putBoolean(KEY_WANTED, on).apply()
        LogRepository.from(context).add("볼륨 버튼 회전: ${if (on) "켬" else "끔"}" + (why?.let { " ($it)" } ?: ""))
        enabled(context) == on
    }.getOrDefault(false)

    /**
     * 앱을 강제 종료하면 시스템이 접근성 서비스 목록에서 GOAT를 뺀다(에뮬레이터에서 확인). 사용자가 켜 둔 상태였으면
     * 앱이 다시 뜰 때 되살린다.
     */
    fun restoreIfWanted(context: Context) {
        if (!prefs(context).getBoolean(KEY_WANTED, false) || enabled(context)) return
        runCatching { setEnabled(context, true, "강제 종료로 꺼져 있어 다시 켬") }
    }

    /** 바꾸는 방향을 반대로(폴드 메인 화면처럼 기본 방향이 다른 화면용). */
    fun invert(context: Context) = prefs(context).getBoolean(KEY_INVERT, false)
    fun setInvert(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_INVERT, on).apply()

    /** 카메라 앱이 앞에 있을 때는 바꾸지 않기(볼륨 키 셔터를 그대로 쓰게, 기본 켬). */
    fun cameraExempt(context: Context) = prefs(context).getBoolean(KEY_CAMERA, true)
    fun setCameraExempt(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_CAMERA, on).apply()

    /**
     * [rotation](Surface.ROTATION_*)에서 볼륨 키를 바꿀지. 볼륨 버튼이 기본 방향의 오른쪽 옆면(위가 올리기)에 있다고 보면,
     * 왼쪽으로 돌린 가로(90°)에서는 버튼이 위로 가며 올리기가 왼쪽에 오고, 거꾸로 든 세로(180°)에서는 올리기가 아래에 온다.
     * [invert]면 오른쪽으로 돌린 가로(270°)와 180°에서 바꾼다.
     */
    fun swapFor(rotation: Int, invert: Boolean): Boolean = when (rotation) {
        Surface.ROTATION_180 -> true
        Surface.ROTATION_90 -> !invert
        Surface.ROTATION_270 -> invert
        else -> false
    }

    fun rotationLabel(rotation: Int) = when (rotation) {
        Surface.ROTATION_90 -> "왼쪽으로 돌린 가로"
        Surface.ROTATION_180 -> "거꾸로 든 세로"
        Surface.ROTATION_270 -> "오른쪽으로 돌린 가로"
        else -> "기본 세로"
    }

    fun rotation(context: Context): Int = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    /** 카메라 앱으로 보이는 패키지. */
    fun isCamera(pkg: String?) = pkg != null && pkg.contains("camera", ignoreCase = true)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("volume_rotate", Context.MODE_PRIVATE)

    /** 최근 볼륨 키 처리 기록(진단 로그용, 앱 프로세스 안에서만 둔다). 서비스와 앱은 같은 프로세스에서 돈다. */
    private val recent = ArrayDeque<String>()
    var connectedAt: Long = 0L
        internal set

    internal fun record(line: String) = synchronized(recent) {
        recent.addLast("${SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())} $line")
        while (recent.size > 20) recent.removeFirst()
    }

    /** 진단 로그용: 켜짐 여부, 옵션, 지금 방향, 서비스 연결 시각, 시스템 접근성 상태, 최근 키 기록. 작업 스레드에서 부른다. */
    fun diagnose(context: Context): String = buildString {
        appendLine("켜짐(설정)=${enabled(context)} 켜 두기로 함=${prefs(context).getBoolean(KEY_WANTED, false)} 반대=${invert(context)} 카메라 예외=${cameraExempt(context)}")
        appendLine("지금 방향=${rotationLabel(rotation(context))} · 서비스 연결=${if (connectedAt == 0L) "없음(이 프로세스에서)" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(Date(connectedAt))}")
        appendLine("enabled_accessibility_services=${Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)}")
        ShizukuAccess.shell("dumpsys accessibility | grep -i -E 'goat|Bound services|Enabled services|Enabled features'")?.out?.let { appendLine(it.lines().take(10).joinToString("\n") { l -> l.take(300) }) }
        val keys = synchronized(recent) { recent.toList() }
        appendLine("최근 볼륨 키(${keys.size}건):")
        if (keys.isEmpty()) appendLine("  (없음: 볼륨 키가 서비스에 들어오지 않았거나 앱이 다시 시작됨)") else keys.forEach { appendLine("  $it") }
    }
}

/**
 * [VolumeRotate]의 접근성 서비스. 볼륨 키만 걸러 받고, 바꿀 방향일 때 원래 키를 소비하고 반대 방향으로 볼륨을 조절한다.
 * 맨 앞 앱은 창 전환 이벤트의 패키지 이름으로만 알아 둔다(카메라 예외용).
 */
class VolumeRotateService : AccessibilityService() {
    private var foreground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        VolumeRotate.connectedAt = System.currentTimeMillis()
        Log.i(TAG, "서비스 연결")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            // 상단바·키보드 창은 맨 앞 앱이 아니므로 건너뛴다
            if (pkg != null && pkg != "com.android.systemui" && !pkg.contains("inputmethod") && !pkg.contains("honeyboard")) foreground = pkg
        }
    }

    override fun onInterrupt() = Unit

    private fun note(line: String) {
        VolumeRotate.record(line)
        Log.i(TAG, line)
    }

    private companion object {
        const val TAG = "GOAT-VolumeRotate"
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }
        if (VolumeRotate.cameraExempt(this) && VolumeRotate.isCamera(foreground)) {
            if (event.action == KeyEvent.ACTION_DOWN) note("${if (up) "올리기" else "내리기"} · 카메라 앞($foreground) · 그대로")
            return false
        }
        val rotation = VolumeRotate.rotation(this)
        if (!VolumeRotate.swapFor(rotation, VolumeRotate.invert(this))) {
            if (event.action == KeyEvent.ACTION_DOWN) note("${if (up) "올리기" else "내리기"} · ${VolumeRotate.rotationLabel(rotation)} · 그대로")
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) note("${if (up) "올리기→내리기" else "내리기→올리기"} · ${VolumeRotate.rotationLabel(rotation)} · 앱 $foreground")
        // 누를 때(길게 누르면 반복)마다 반대 방향으로 조절하고, 원래 키는 누름·뗌 모두 소비한다
        if (event.action == KeyEvent.ACTION_DOWN) {
            getSystemService(AudioManager::class.java)?.adjustSuggestedStreamVolume(
                if (up) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        return true
    }
}
