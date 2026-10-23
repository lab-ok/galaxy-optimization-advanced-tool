package com.labok.goat.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("1.3", "1.2"))
        assertTrue(Updater.isNewer("1.10", "1.9"))
        assertTrue(Updater.isNewer("v2.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.2", "1.2"))
        assertFalse(Updater.isNewer("1.2.0", "1.2"))
        assertFalse(Updater.isNewer("1.1", "1.2"))
        assertFalse(Updater.isNewer("1.3-beta", "1.3"))
    }

    @Test fun shaFromNotes() {
        val sha = "39B97EDDBDA6072A7ED18949AB9D0D81CB7686C63D2FA727F3419B863ABB37FA"
        assertEquals(sha.lowercase(), Updater.shaOf("### 설치\n- SHA-256: `$sha`\n"))
        assertNull(Updater.shaOf("SHA-256 없음"))
    }

    @Test
    fun testBuildIsLastDigitNotZero() {
        assertEquals(false, Updater.isTestBuild("1.19.0"))
        assertEquals(true, Updater.isTestBuild("1.19.2"))
        assertEquals(false, Updater.isTestBuild("1.19"))
    }

    @Test
    fun newestSkipsPrereleaseUnlessTestChannel() {
        fun rel(tag: String, pre: Boolean) = Updater.Release(tag, "d", 1, null, "", "u", test = pre)
        val list = listOf(rel("1.19.2", true), rel("1.19.0", false), rel("1.18.0", false))
        assertEquals("1.19.0", Updater.newest(list, withTest = false)?.version)
        val t = Updater.newest(list, withTest = true)
        assertEquals("1.19.2", t?.version)
        assertEquals(true, t?.test)
    }
}
