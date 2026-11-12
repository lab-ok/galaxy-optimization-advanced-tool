package com.labok.goat.data.rotate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.labok.goat.AppHooks
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.liquid.UiGoat
import kotlinx.coroutines.flow.MutableStateFlow

/** Experimental app-level screen-transition shortcut, independent of shake detection. */
object PowerRotate {
    enum class Mode { TOGGLE, ONCE }

    enum class Status { OFF, WAITING, LISTENING, ERROR }
    val status = MutableStateFlow(Status.OFF)

    fun enabled(context: Context) = context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).getBoolean("on", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        AppHooks.syncService(context)
        AppHooks.shizukuNoticeUpdate(context)
    }

    fun mode(context: Context): Mode {
        val saved = context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).getString("mode", null)
        return Mode.entries.firstOrNull { it.name == saved } ?: Mode.TOGGLE
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
        if (enabled(context)) AppHooks.syncService(context)
    }

    /** Runs on the main thread, alongside ShakeEngine's rotation writes. */
    fun toggle(context: Context, rotation: Int): Boolean {
        if (!ShakeRotate.canWrite(context)) return false
        val auto = ShakeRotate.autoRotateOn(context)
        val ok = runCatching {
            if (auto) {
                // Save the visible direction before switching the accelerometer off.
                Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, rotation) &&
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            } else {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            }
        }.getOrDefault(false)
        LogRepository.from(context).add("Power triple press: autoRotate=${!auto}, success=$ok, rotation=$rotation")
        return ok && ShakeRotate.autoRotateOn(context) != auto
    }

    fun buildNotification(context: Context): Notification {
        val channel = "power_rotate_quiet"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, context.getString(R.string.power_rotate_title), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(context, 23, AppHooks.mainIntent(context), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(context.getString(R.string.power_rotate_title))
            .setContentText(context.getString(R.string.power_rotate_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }
}
