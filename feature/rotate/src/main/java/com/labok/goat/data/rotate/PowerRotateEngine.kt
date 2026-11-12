package com.labok.goat.data.rotate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ScreenTripleTransition

/** App-only experiment: screen transitions approximate presses without opening input devices. */
class PowerRotateEngine(private val context: Context, private val onTriplePress: (Int) -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    private var registered = false
    private var detector: ScreenTripleTransition? = null
    private var rotation = 0

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> true
                Intent.ACTION_SCREEN_OFF -> false
                else -> return
            }
            if (!registered || !PowerRotate.enabled(context)) return
            if (!ShakeRotate.canWrite(context)) {
                stop()
                PowerRotate.status.value = PowerRotate.Status.WAITING
                return
            }
            when (detector?.feed(on, SystemClock.elapsedRealtime())) {
                ScreenTripleTransition.Result.FIRST -> rotation = ShakeRotate.currentRotation(context)
                ScreenTripleTransition.Result.TRIPLE -> {
                    val captured = rotation
                    // A freshly lit screen needs sensor samples for the one-shot direction path.
                    val delay = if (on && PowerRotate.mode(context) == PowerRotate.Mode.ONCE) 250L else 0L
                    main.postDelayed({ perform(captured) }, delay)
                }
                else -> Unit
            }
        }
    }

    /** No Shizuku state check: the already granted settings permission is sufficient. */
    fun refresh() {
        if (!ShakeRotate.canWrite(context)) {
            stop()
            PowerRotate.status.value = PowerRotate.Status.WAITING
            return
        }
        if (registered) return
        detector = ScreenTripleTransition(context.getSystemService(PowerManager::class.java).isInteractive)
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(screen, filter)
            registered = true
            PowerRotate.status.value = PowerRotate.Status.LISTENING
            LogRepository.from(context).add("Power rotation: experimental app screen-event detection started")
        } catch (e: Exception) {
            detector = null
            PowerRotate.status.value = PowerRotate.Status.ERROR
            LogRepository.from(context).add("Power rotation screen events: ${e.message}")
        }
    }

    private fun perform(captured: Int) {
        if (!registered || !PowerRotate.enabled(context) || !ShakeRotate.canWrite(context)) return
        val ok = onTriplePress(captured)
        val text = when {
            !ok -> R.string.power_rotate_failed
            PowerRotate.mode(context) == PowerRotate.Mode.ONCE -> R.string.power_rotate_once_feedback
            ShakeRotate.autoRotateOn(context) -> R.string.power_rotate_auto_on
            else -> R.string.power_rotate_locked
        }
        LogRepository.from(context).add("Power rotation screen-event candidate: action=${PowerRotate.mode(context)}, success=$ok")
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        if (registered) runCatching { context.unregisterReceiver(screen) }
        registered = false
        detector = null
        main.removeCallbacksAndMessages(null)
        PowerRotate.status.value = PowerRotate.Status.OFF
    }
}
