package com.labok.goat.data.rotate

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.widget.Toast
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.domain.PowerTriplePress

/** Reads actual power-key events, including while the display is off. Screen broadcasts are not presses. */
class PowerRotateEngine(private val context: Context, private val onTriplePress: (Int) -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    @Volatile private var generation = 0
    private var process: Process? = null
    private var worker: Thread? = null
    private var rotation = 0
    private val retry = Runnable { refresh() }

    /** Called on the main thread on startup and Shizuku connection changes. */
    fun refresh() {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY || !ShakeRotate.canWrite(context)) {
            stop()
            PowerRotate.status.value = PowerRotate.Status.WAITING
            return
        }
        if (worker != null) return
        main.removeCallbacks(retry)
        val token = generation
        PowerRotate.status.value = PowerRotate.Status.WAITING
        worker = Thread({ monitor(token) }, "GOAT-PowerRotate").also { it.start() }
    }

    private fun monitor(token: Int) {
        var opened: Process? = null
        try {
            // exec lets destroy() terminate getevent itself; stderr is drained along with stdout.
            opened = ShizukuAccess.stream("exec getevent -lt 2>&1") ?: error("Shizuku input stream unavailable")
            synchronized(lock) {
                if (token != generation) return
                process = opened
            }
            val detector = PowerTriplePress()
            opened.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (token != generation) return@forEach
                    // A successful event proves input access; permission errors must not appear as ready.
                    if (line.contains("Permission denied", true) || line.contains("could not open", true)) {
                        error("Input access denied")
                    }
                    if (line.contains("/dev/input/event") && PowerRotate.status.value != PowerRotate.Status.LISTENING) {
                        main.post { if (token == generation) PowerRotate.status.value = PowerRotate.Status.LISTENING }
                    }
                    val event = PowerTriplePress.parse(line) ?: return@forEach
                    // Kernel CLOCK_MONOTONIC pauses in deep sleep; elapsedRealtime must bound the gesture.
                    val result = detector.feed(event, SystemClock.elapsedRealtime())
                    if (result == PowerTriplePress.Result.NONE) return@forEach
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
            }
        } catch (e: Exception) {
            if (token == generation) LogRepository.from(context).add("Power rotation input: ${e.message}")
        } finally {
            opened?.destroy()
            synchronized(lock) { if (process === opened) process = null }
            main.post {
                if (token == generation) {
                    worker = null
                    PowerRotate.status.value = PowerRotate.Status.ERROR
                    main.postDelayed(retry, 5000)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            process?.destroy()
            process = null
        }
        worker?.interrupt()
        worker = null
        main.removeCallbacksAndMessages(null)
        PowerRotate.status.value = PowerRotate.Status.OFF
    }
}
