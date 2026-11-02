package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientColorTest {
    private fun r(c: Int) = c shr 16 and 0xFF
    private fun g(c: Int) = c shr 8 and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** 왼쪽 절반 빨강, 오른쪽 절반 파랑인 그림. */
    private fun split(w: Int = 30, h: Int = 10): IntArray = IntArray(w * h) { i -> if (i % w < w / 2) 0xFFE01010.toInt() else 0xFF1030E0.toInt() }

    @Test
    fun oklabRoundTrip() {
        for (c in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE01010.toInt(), 0xFF10E080.toInt(), 0xFF336699.toInt())) {
            val o = AmbientColor.toOklab(c)
            val back = AmbientColor.fromOklab(o[0], o[1], o[2])
            assertTrue("$c → $back", kotlin.math.abs(r(c) - r(back)) <= 1 && kotlin.math.abs(g(c) - g(back)) <= 1 && kotlin.math.abs(b(c) - b(back)) <= 1)
        }
    }

    @Test
    fun regionsFollowSpatialLayout() {
        val p = AmbientColor.extract(split(), 30, 10)
        assertTrue("왼쪽은 빨강 쪽", r(p.left) > b(p.left))
        assertTrue("오른쪽은 파랑 쪽", b(p.right) > r(p.right))
        assertFalse(p.protectedLike)
    }

    @Test
    fun blackBordersDoNotDominate() {
        // 90%가 검은 여백이고 가운데 띠만 초록인 영상
        val w = 40
        val h = 20
        val px = IntArray(w * h) { i -> if (i / w in 9..10) 0xFF20C040.toInt() else 0xFF000000.toInt() }
        val p = AmbientColor.extract(px, w, h)
        assertTrue("전체 색이 초록 쪽이어야 함", g(p.overall) > r(p.overall) && g(p.overall) > b(p.overall))
        assertFalse(p.protectedLike)
    }

    @Test
    fun allBlackIsProtectedLike() {
        val p = AmbientColor.extract(IntArray(400) { 0xFF000000.toInt() }, 20, 20)
        assertTrue(p.protectedLike)
    }

    @Test
    fun tameLimitsLightnessAndChroma() {
        val white = AmbientColor.toOklab(AmbientColor.tame(0xFFFFFFFF.toInt()))
        val neon = AmbientColor.toOklab(AmbientColor.tame(0xFF00FF00.toInt()))
        assertTrue(white[0] <= 0.721f)
        assertTrue(kotlin.math.hypot(neon[1], neon[2]) <= 0.161f)
    }

    @Test
    fun smootherIgnoresTinyChangesAndChasesSceneCutsFaster() {
        val base = 0xFF404858.toInt()
        val s = AmbientColor.Smoother(base)
        // 데드밴드 안의 작은 변화는 무시
        assertEquals(base, s.step(0xFF414959.toInt(), 0.1f))
        // 장면 전환(큰 변화)은 보통 변화보다 같은 시간에 더 많이 따라간다
        val cut = AmbientColor.Smoother(base)
        val slow = AmbientColor.Smoother(base)
        val far = 0xFFE03020.toInt()
        val near = AmbientColor.fromOklab(AmbientColor.toOklab(base)[0] + 0.05f, 0.02f, -0.02f)
        val dCut = AmbientColor.distance(base, cut.step(far, 0.1f)) / AmbientColor.distance(base, far)
        val dSlow = AmbientColor.distance(base, slow.step(near, 0.1f)) / AmbientColor.distance(base, near)
        assertTrue("장면 전환 $dCut > 보통 $dSlow", dCut > dSlow)
    }

    @Test
    fun smootherConvergesWithoutOvershoot() {
        val s = AmbientColor.Smoother(0xFF202020.toInt())
        val target = 0xFF3060C0.toInt()
        var prev = AmbientColor.distance(s.color, target)
        repeat(100) {
            val d = AmbientColor.distance(s.step(target, 0.05f), target)
            assertTrue("거리가 늘면 안 됨", d <= prev + 1e-4f)
            prev = d
        }
        assertTrue(prev < 0.03f)
    }
}
