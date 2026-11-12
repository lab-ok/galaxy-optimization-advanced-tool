package com.labok.goat.data.rotate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.app.ActivityManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.AppHooks
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.domain.ScreenTripleTransition
import kotlinx.coroutines.flow.MutableStateFlow

/** Experimental app-level screen-transition shortcut, independent of shake detection. */
object PowerRotate {
    enum class Mode { TOGGLE, ONCE }
    enum class Window(val millis: Long, val seconds: String) {
        SHORT(1500, "1.5"), MEDIUM(2500, "2.5"), LONG(4000, "4")
    }

    data class Detection(
        val events: Int = 0, val candidates: Int = 0, val count: Int = 0,
        val screenOn: Boolean? = null, val intervalMs: Long? = null,
        val decision: ScreenTripleTransition.Decision? = null,
        val actionSucceeded: Boolean? = null, val history: List<String> = emptyList()
    )
    val detection = MutableStateFlow(Detection())
    val changed = MutableStateFlow(0)

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

    fun window(context: Context): Window {
        val saved = context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).getString("window", null)
        return Window.entries.firstOrNull { it.name == saved } ?: Window.SHORT
    }

    fun setWindow(context: Context, value: Window) {
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putString("window", value.name).apply()
        changed.value++
        LogRepository.from(context).add("Power rotation window: ${value.millis}ms")
        if (enabled(context)) AppHooks.syncService(context)
    }

    /** Main-thread receiver updates; immutable snapshots can also be read by report workers. */
    internal fun record(context: Context, message: String, update: (Detection) -> Detection = { it }) {
        val next = update(detection.value)
        val line = "pid=${android.os.Process.myPid()} $message"
        detection.value = next.copy(history = (next.history + "${SystemClock.elapsedRealtime()}ms $line").takeLast(20))
        LogRepository.from(context).add("Power rotation: $line")
    }

    /** Accessible from the app without ADB, including when Samsung blocks debugging. */
    fun diagnose(context: Context): String = buildString {
        val power = context.getSystemService(PowerManager::class.java)
        val state = detection.value
        appendLine("GOAT screen-input diagnostics preview2")
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        appendLine("app=${info?.versionName} (${info?.longVersionCode})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}; Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}")
        appendLine("pid=${android.os.Process.myPid()} enabled=${enabled(context)} status=${status.value} mode=${mode(context)} windowMs=${window(context).millis}")
        appendLine("settingsPermission=${ShakeRotate.canWrite(context)} interactive=${power.isInteractive}")
        appendLine("autoRotate=${ShakeRotate.autoRotateOn(context)} currentRotation=${ShakeRotate.currentRotation(context)} userRotation=" + Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0))
        appendLine("displayState=" + runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state }.getOrNull())
        appendLine("backgroundRestricted=" + runCatching { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrNull())
        appendLine("ignoringBatteryOptimizations=" + runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrNull())
        val importance = runCatching {
            val process = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(process)
            process.importance
        }.getOrNull()
        appendLine("processImportance=$importance")
        appendLine("events=${state.events} candidates=${state.candidates} count=${state.count}/3 lastScreenOn=${state.screenOn} intervalMs=${state.intervalMs} decision=${state.decision} actionSucceeded=${state.actionSucceeded}")
        appendLine("Recent receiver records (maximum 20):")
        state.history.forEach(::appendLine)
        appendLine("Persisted lifecycle/input records (maximum 20):")
        // These match existing persisted log text; they are not localized UI messages.
        LogRepository.from(context).read().filter { "Power rotation" in it || "Power triple press" in it || "GOAT 상주 서비스" in it || "회전 잠금 실패" in it || "흔들어서 화면 회전" in it }.takeLast(20).forEach(::appendLine)
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
        }.onFailure {
            LogRepository.from(context).add("Power rotation: settings_write_failed ${it.javaClass.simpleName}: ${it.message}")
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
