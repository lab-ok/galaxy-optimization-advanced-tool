package com.labok.goat.data.rotate

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.widget.Toast
import com.labok.goat.AppHooks
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.domain.PowerTriplePress

/** Reads actual power keys over a direct local stream that survives Shizuku disconnection. */
class PowerRotateEngine(private val context: Context, private val onTriplePress: (Int) -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    @Volatile private var generation = 0
    private var bridge: PowerInputBridge? = null
    private var worker: Thread? = null
    private var rotation = 0
    private val retry = Runnable { refresh() }

    /** Shizuku bootstraps a reader; losing its Binder must not stop an established session. */
    fun refresh() {
        val state = ShizukuAccess.status.value
        if (!ShakeRotate.canWrite(context) || state == ShizukuAccess.State.NEEDS_PERMISSION) {
            stop()
            PowerRotate.status.value = PowerRotate.Status.WAITING
            return
        }
        if (worker != null) return
        if (state != ShizukuAccess.State.READY) {
            PowerRotate.status.value = PowerRotate.Status.WAITING
            return
        }
        main.removeCallbacks(retry)
        val token = generation
        PowerRotate.status.value = PowerRotate.Status.WAITING
        worker = Thread({ monitor(token) }, "GOAT-PowerRotate").also { it.start() }
    }

    private fun monitor(token: Int) {
        var opened: PowerInputBridge? = null
        try {
            val session = PowerInputBridge()
            opened = session
            synchronized(lock) {
                if (token != generation) return
                bridge = session
            }
            if (ShizukuAccess.shell(session.launchCommand())?.ok != true) error("Power reader launch failed")
            val detector = PowerTriplePress()
            session.read(onConnected = {
                main.post {
                    if (token == generation) {
                        PowerRotate.status.value = PowerRotate.Status.LISTENING
                        AppHooks.shizukuNoticeUpdate(context)
                    }
                }
            }) { line ->
                if (token != generation) return@read
                if (line.contains("Permission denied", true) || line.contains("could not open", true)) error("Input access denied")
                val event = PowerTriplePress.parse(line) ?: return@read
                // Kernel CLOCK_MONOTONIC pauses in deep sleep; elapsedRealtime must bound the gesture.
                val result = detector.feed(event, SystemClock.elapsedRealtime())
                if (result == PowerTriplePress.Result.NONE) return@read
                main.post {
                    if (token != generation || !PowerRotate.enabled(context)) return@post
                    when (result) {
                        PowerTriplePress.Result.FIRST_DOWN -> rotation = ShakeRotate.currentRotation(context)
                        PowerTriplePress.Result.TRIPLE -> {
                            val ok = onTriplePress(rotation)
                            val text = when {
                                !ok -> R.string.power_rotate_failed
                                PowerRotate.mode(context) == PowerRotate.Mode.ONCE -> R.string.power_rotate_once_feedback
                                ShakeRotate.autoRotateOn(context) -> R.string.power_rotate_auto_on
                                else -> R.string.power_rotate_locked
                            }
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                        PowerTriplePress.Result.NONE -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            if (token == generation) LogRepository.from(context).add("Power rotation input: ${e.message}")
        } finally {
            opened?.close()
            synchronized(lock) { if (bridge === opened) bridge = null }
            main.post {
                if (token == generation) {
                    worker = null
                    PowerRotate.status.value = if (ShizukuAccess.status.value == ShizukuAccess.State.READY) PowerRotate.Status.ERROR else PowerRotate.Status.WAITING
                    AppHooks.shizukuNoticeUpdate(context)
                    main.postDelayed(retry, 5000)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            bridge?.close()
            bridge = null
        }
        worker?.interrupt()
        worker = null
        main.removeCallbacksAndMessages(null)
        PowerRotate.status.value = PowerRotate.Status.OFF
    }
}
