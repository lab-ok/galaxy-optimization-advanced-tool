package com.labok.goat.data.rotate

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.labok.goat.AppInfo
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.MainActivity

/**
 * 흔들어 화면 회전의 설정과 회전 잠금 쓰기.
 * 회전 잠금은 시스템 설정(accelerometer_rotation·user_rotation)이라, 화면 배율 동기화와 같은 WRITE_SECURE_SETTINGS로 바꾼다.
 */
object ShakeRotate {
    private const val PREF = "shake_rotate"
    private const val CHANNEL_ID = "shake_rotate"
    const val NOTIFICATION_ID = 4

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    fun sensitivity(context: Context): ShakeRotatePolicy.Sensitivity =
        runCatching { ShakeRotatePolicy.Sensitivity.valueOf(prefs(context).getString("sensitivity", null)!!) }
            .getOrDefault(ShakeRotatePolicy.Sensitivity.NORMAL)

    fun setSensitivity(context: Context, s: ShakeRotatePolicy.Sensitivity) {
        prefs(context).edit().putString("sensitivity", s.name).apply()
        LogRepository.from(context).add("흔들어 화면 회전 민감도: ${s.label}")
        if (enabled(context)) start(context)  // 서비스가 새 민감도를 읽게 한다
    }

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
            log.add("흔들어 화면 회전 켜짐(자동 회전 ${if (auto == 1) "켜져 있었음" else "꺼져 있었음"} → 잠금)")
            start(context)
        } else {
            val auto = prefs(context).getInt("auto_before", 1)
            prefs(context).edit().putBoolean("on", false).apply()
            context.stopService(Intent(context, ShakeRotateService::class.java))
            runCatching { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, auto) }
            log.add("흔들어 화면 회전 꺼짐(자동 회전 ${if (auto == 1) "켜기" else "끄기"}로 되돌림)")
        }
        return true
    }

    /** 켜 두었으면 감시 서비스를 띄운다(부팅·업데이트·앱 재시작 뒤에도). */
    fun start(context: Context) {
        if (!enabled(context) || !canWrite(context)) return
        runCatching { context.startForegroundService(Intent(context, ShakeRotateService::class.java)) }
    }

    /** 지금 잠긴(또는 보이는) 화면 방향. */
    fun currentRotation(context: Context): Int =
        runCatching { context.display.rotation }.getOrElse { Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0) }

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
            NotificationChannel(CHANNEL_ID, "흔들어 화면 회전", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("${AppInfo.NAME} · 흔들어 화면 회전")
            .setContentText("회전을 잠가 두었습니다. 폰을 흔들면 지금 든 방향으로 돌립니다.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
