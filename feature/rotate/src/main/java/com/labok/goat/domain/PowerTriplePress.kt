package com.labok.goat.domain

/** Counts three short, complete presses in elapsed time, including screen-off sleep. */
class PowerTriplePress {
    data class Event(val device: String, val timeMs: Long, val value: String)
    enum class Result { NONE, FIRST_DOWN, TRIPLE }

    private var device: String? = null
    private var first = 0L
    private var down: Long? = null
    private var count = 0

    fun feed(event: Event, elapsedMs: Long = event.timeMs): Result {
        val now = elapsedMs
        if (device != event.device || now < first || now - first > WINDOW_MS) reset()
        when (event.value) {
            "DOWN" -> {
                if (down != null) return Result.NONE
                if (count == 0) {
                    device = event.device
                    first = now
                }
                down = now
                return if (count == 0) Result.FIRST_DOWN else Result.NONE
            }
            "UP" -> {
                val pressed = down ?: return Result.NONE
                down = null
                if (now < pressed || now - pressed > MAX_HOLD_MS) {
                    reset()
                    return Result.NONE
                }
                count++
                if (count == 3) {
                    reset()
                    return Result.TRIPLE
                }
            }
            "REPEAT" -> reset()
        }
        return Result.NONE
    }

    private fun reset() {
        device = null
        first = 0L
        down = null
        count = 0
    }

    companion object {
        const val WINDOW_MS = 1500L
        const val MAX_HOLD_MS = 500L
        private val LINE = Regex("""\[\s*(\d+)\.(\d+)\]\s+(/dev/input/event\d+):\s+EV_KEY\s+KEY_POWER\s+(DOWN|UP|REPEAT)\s*""")

        /** Ignores touch, volume, sync and non-event diagnostic lines. */
        fun parse(line: String): Event? {
            val m = LINE.matchEntire(line.trim()) ?: return null
            val seconds = m.groupValues[1].toLongOrNull() ?: return null
            val fraction = m.groupValues[2].padEnd(3, '0').take(3).toLongOrNull() ?: return null
            return Event(m.groupValues[3], seconds * 1000 + fraction, m.groupValues[4])
        }
    }
}
