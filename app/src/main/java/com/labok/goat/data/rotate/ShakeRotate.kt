package com.labok.goat.data.rotate

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.AppInfo
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.MainActivity

/**
 * 흔들어서 화면 회전의 설정과 회전 잠금 쓰기.
 * 회전 잠금은 시스템 설정(accelerometer_rotation·user_rotation)이라, 화면 배율 동기화와 같은 WRITE_SECURE_SETTINGS로 바꾼다.
 */
object ShakeRotate {
    private const val PREF = "shake_rotate"
    private const val CHANNEL_ID = "shake_rotate"
    const val NOTIFICATION_ID = 4

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** 흔들기 민감도 단계([ShakeRotatePolicy.MIN_LEVEL]~[ShakeRotatePolicy.MAX_LEVEL], 클수록 예민). */
    fun level(context: Context): Int =
        prefs(context).getInt("level", ShakeRotatePolicy.DEFAULT_LEVEL).coerceIn(ShakeRotatePolicy.MIN_LEVEL, ShakeRotatePolicy.MAX_LEVEL)

    fun setLevel(context: Context, level: Int, how: String) {
        prefs(context).edit().putInt("level", level).apply()
        LogRepository.from(context).add("흔들어서 화면 회전 민감도: $level($how)")
        if (enabled(context)) start(context)  // 서비스가 새 민감도를 읽게 한다
    }

    /** 흔든 순간 자동 회전을 잠깐 켜 둔 동안 true(화면의 '자동 회전이 켜져 있습니다' 안내를 띄우지 않는다). */
    @Volatile
    var turning = false

    /**
     * 알림을 띄워 두는 방식(true)인지, 알림 없이 접근성 서비스로 도는 방식(false)인지. 처음에는 알림 없이.
     * 접근성 서비스는 시스템이 직접 띄워 두므로 알림이 없어도 화면이 켜져 있는 동안 센서를 쓸 수 있다.
     */
    fun useNotification(context: Context) = prefs(context).getBoolean("notification", false)

    fun setUseNotification(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("notification", on).apply()
        LogRepository.from(context).add("흔들어서 화면 회전: ${if (on) "알림으로 실행" else "알림 없이 실행"}")
        if (enabled(context)) { stopRunners(context); start(context) }
    }

    /** 자동 맞추기 중이면 true. 그동안 서비스는 흔들어도 돌리지 않는다(같은 프로세스 안에서만 본다). */
    @Volatile
    var calibrating = false

    /** 빠른 설정의 자동 회전이 켜져 있으면 true. 켜져 있으면 흔들기 전에도 화면이 저절로 돌아간다. */
    fun autoRotateOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** 자동 회전만 끈다(지금 방향 그대로 잠근다). */
    fun turnOffAutoRotate(context: Context): Boolean =
        lock(context, currentRotation(context)).also { if (it) LogRepository.from(context).add("흔들어서 화면 회전: 자동 회전 끄기(사용자)") }

    /**
     * 켜면 지금 화면 방향 그대로 회전을 잠그고 감시를 시작한다. 끄면 켜기 전의 자동 회전 설정으로 되돌린다.
     * 권한이 없어 잠그지 못하면 켜지 않고 false.
     */
    fun setEnabled(context: Context, on: Boolean): Boolean {
        val log = LogRepository.from(context)
        val cr = context.contentResolver
        if (on) {
            val auto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
            if (!lock(context, currentRotation(context))) return false
            prefs(context).edit().putBoolean("on", true).putInt("auto_before", auto).apply()
            log.add("흔들어서 화면 회전 켜짐(자동 회전 ${if (auto == 1) "켜져 있었음" else "꺼져 있었음"} → 잠금)")
            start(context)
        } else {
            val auto = prefs(context).getInt("auto_before", 1)
            prefs(context).edit().putBoolean("on", false).apply()
            stopRunners(context)
            runCatching { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, auto) }
            log.add("흔들어서 화면 회전 꺼짐(자동 회전 ${if (auto == 1) "켜기" else "끄기"}로 되돌림)")
        }
        return true
    }

    /**
     * 켜 두었으면 감시를 띄운다(부팅·업데이트·앱 재시작 뒤에도). 알림 없이 쓰기로 했으면 접근성 서비스를 켜고,
     * 켜지지 않으면 알림 서비스로 대신한다. 이미 돌고 있으면 민감도만 다시 읽힌다.
     */
    fun start(context: Context) {
        if (!enabled(context) || !canWrite(context)) return
        if (!useNotification(context) && setAccessibility(context, true)) {
            ShakeAccessibilityService.running?.refresh()
            return
        }
        runCatching { context.startForegroundService(Intent(context, ShakeRotateService::class.java)) }
    }

    private fun stopRunners(context: Context) {
        context.stopService(Intent(context, ShakeRotateService::class.java))
        setAccessibility(context, false)
    }

    /**
     * 접근성 서비스 목록에 GOAT 흔들기 감지를 넣거나 뺀다(시스템 설정 권한으로 직접 쓴다). 다른 앱의 접근성 서비스는 그대로 둔다.
     * 넣었으면 true.
     */
    private fun setAccessibility(context: Context, on: Boolean): Boolean = runCatching {
        val cr = context.contentResolver
        val me = android.content.ComponentName(context, ShakeAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
        val next = if (on) list + me else list
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
        if (on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        on
    }.getOrElse {
        LogRepository.from(context).add("접근성 서비스 ${if (on) "켜기" else "끄기"} 실패: ${it.message}")
        false
    }

    /** 지금 화면 방향(기본 디스플레이 기준). 서비스처럼 화면이 없는 곳에서도 읽힌다. */
    fun currentRotation(context: Context): Int =
        runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation }
            .getOrElse { Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0) }

    /** 자동 회전을 끄고 [rotation] 방향으로 잠근다. */
    fun lock(context: Context, rotation: Int): Boolean = runCatching {
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(cr, Settings.System.USER_ROTATION, rotation)
    }.getOrElse {
        LogRepository.from(context).add("회전 잠금 실패: ${it.message}")
        false
    }

    fun label(rotation: Int) = when (rotation) {
        0 -> "세로"
        2 -> "거꾸로 세로"
        else -> "가로"
    }

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "흔들어서 화면 회전", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("${AppInfo.NAME} · 흔들어서 화면 회전")
            .setContentText("회전을 잠가 두었습니다. 폰을 흔들면 지금 든 방향으로 돌립니다.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
