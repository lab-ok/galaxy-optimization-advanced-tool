package com.labok.goat.domain

import org.junit.Assert.*
import org.junit.Test

class ScreenTripleTransitionTest {
    @Test fun threeAlternatingTransitionsTriggerExactlyOnce() {
        val detector = ScreenTripleTransition(true)
        assertEquals(ScreenTripleTransition.Result.FIRST, detector.feed(false, 100))
        assertEquals(ScreenTripleTransition.Result.NONE, detector.feed(true, 500))
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(false, 900))
        assertEquals(ScreenTripleTransition.Result.FIRST, detector.feed(true, 1100))
    }

    @Test fun worksFromAnInitiallyDarkScreen() {
        val detector = ScreenTripleTransition(false)
        detector.feed(true, 100)
        detector.feed(false, 400)
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(true, 700))
    }

    @Test fun duplicateStatesCannotCreateExtraTransitions() {
        val detector = ScreenTripleTransition(true)
        assertEquals(ScreenTripleTransition.Result.NONE, detector.feed(true, 50))
        detector.feed(false, 100)
        assertEquals(ScreenTripleTransition.Result.NONE, detector.feed(false, 200))
        detector.feed(true, 300)
        assertEquals(ScreenTripleTransition.Result.NONE, detector.feed(true, 400))
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(false, 500))
    }

    @Test fun aSlowThirdTransitionStartsANewSequence() {
        val detector = ScreenTripleTransition(true)
        detector.feed(false, 100)
        detector.feed(true, 800)
        assertEquals(ScreenTripleTransition.Result.FIRST, detector.feed(false, 1601))
        detector.feed(true, 1800)
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(false, 2000))
    }

    @Test fun boundaryIncludesTheThirdTransition() {
        val detector = ScreenTripleTransition(true)
        detector.feed(false, 100)
        detector.feed(true, 800)
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(false, 1600))
    }

    @Test fun backwardsTimeResetsTheSequence() {
        val detector = ScreenTripleTransition(true)
        detector.feed(false, 1000)
        detector.feed(true, 1100)
        assertEquals(ScreenTripleTransition.Result.FIRST, detector.feed(false, 900))
    }

    @Test fun sixTransitionsProduceTwoActions() {
        val detector = ScreenTripleTransition(true)
        val results = (0..5).map { detector.feed(it % 2 != 0, 100L + it * 200L) }
        assertEquals(2, results.count { it == ScreenTripleTransition.Result.TRIPLE })
    }

    @Test fun nonPowerScreenChangesHaveTheSameResultBecauseKeyIdentityIsUnavailable() {
        val detector = ScreenTripleTransition(true)
        // These could be a timeout, a notification wake and another timeout.
        detector.feed(false, 100)
        detector.feed(true, 300)
        assertEquals(ScreenTripleTransition.Result.TRIPLE, detector.feed(false, 500))
    }
}
