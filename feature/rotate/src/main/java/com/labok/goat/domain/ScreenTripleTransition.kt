package com.labok.goat.domain

/** Experimental screen-state heuristic. These events do not identify a physical power key. */
class ScreenTripleTransition(initialScreenOn: Boolean) {
    enum class Result { NONE, FIRST, TRIPLE }
    private var screenOn = initialScreenOn
    private var first = 0L
    private var count = 0

    fun feed(on: Boolean, elapsedMs: Long): Result {
        if (on == screenOn) return Result.NONE
        screenOn = on
        if (count == 0 || elapsedMs < first || elapsedMs - first > WINDOW_MS) {
            first = elapsedMs
            count = 1
            return Result.FIRST
        }
        if (++count == 3) {
            count = 0
            return Result.TRIPLE
        }
        return Result.NONE
    }

    companion object { const val WINDOW_MS = 1500L }
}
