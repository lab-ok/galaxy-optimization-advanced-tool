package com.labok.goat.domain

/** Experimental screen-state heuristic. These events do not identify a physical power key. */
class ScreenTripleTransition(initialScreenOn: Boolean, private val windowMs: Long = WINDOW_MS) {
    enum class Result { NONE, FIRST, TRIPLE }
    enum class Decision { DUPLICATE, FIRST, SECOND, TRIPLE, WINDOW_EXPIRED, CLOCK_RESET }
    private var screenOn = initialScreenOn
    private var first = 0L
    var count = 0
        private set
    var decision = Decision.FIRST
        private set

    init { require(windowMs > 0) }

    fun feed(on: Boolean, elapsedMs: Long): Result {
        if (on == screenOn) {
            decision = Decision.DUPLICATE
            return Result.NONE
        }
        screenOn = on
        if (count == 0 || elapsedMs < first || elapsedMs - first > windowMs) {
            decision = when {
                count == 0 -> Decision.FIRST
                elapsedMs < first -> Decision.CLOCK_RESET
                else -> Decision.WINDOW_EXPIRED
            }
            first = elapsedMs
            count = 1
            return Result.FIRST
        }
        if (++count == 3) {
            decision = Decision.TRIPLE
            count = 0
            return Result.TRIPLE
        }
        decision = Decision.SECOND
        return Result.NONE
    }

    companion object { const val WINDOW_MS = 1500L }
}
