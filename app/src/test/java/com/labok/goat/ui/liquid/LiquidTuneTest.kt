package com.labok.goat.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidTuneTest {
    @Test fun defaultsGiveDefaultLook() {
        val d = LiquidLook.default
        val t = LiquidTune.look(LiquidTune.defaults())
        assertEquals(d.copy(damping = t.damping, gravity = t.gravity), t)
        assertEquals(d.damping, t.damping, 1e-6f)
        assertEquals(d.gravity, t.gravity, 1e-6f)
        assertEquals(LiquidTune.look(emptyMap()), t)
    }

    @Test fun knobsScaleProperties() {
        val t = LiquidTune.look(mapOf("viscosity" to 200, "tension" to 0, "scatter" to 300, "water" to 9999, "gravity" to 0))
        assertEquals(LiquidLook.default.sigma * 2, t.sigma, 1e-6f)
        assertEquals(0f, t.cohesion, 0f)
        assertEquals(3f, t.scatter, 1e-6f)
        assertEquals(LiquidTune.WATER.range.last, t.particles)
        assertEquals(0f, t.gravity, 0f)
    }

    @Test fun defaultsInsideRanges() {
        LiquidTune.all.forEach { assertTrue(it.key, it.default in it.range) }
    }
}
