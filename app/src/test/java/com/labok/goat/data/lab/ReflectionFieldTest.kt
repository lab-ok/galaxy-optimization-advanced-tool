package com.labok.goat.data.lab

import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionFieldTest {
    private val w = 64
    private val h = 40
    private fun r(c: Int) = c shr 16 and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun a(c: Int) = c ushr 24

    /** 왼쪽 빨강 / 오른쪽 파랑 + 세로 줄무늬(공간 패턴): 대표색 그라데이션으로는 통과할 수 없는 시험. */
    private fun leftRedRightBlue() = IntArray(w * h) { i ->
        val x = i % w
        val base = if (x < w / 2) 0xFFE02020.toInt() else 0xFF2040E0.toInt()
        if (x % 8 < 2) 0xFF101010.toInt() else base
    }

    @Test
    fun clarityFallsWithDistanceFromScreen() {
        val f = ReflectionField.build(leftRedRightBlue(), w, h)
        val outH = ReflectionField.Spec().outH
        val top = ReflectionField.clarity(f, w, (outH * 0.1f).toInt())
        val mid = ReflectionField.clarity(f, w, (outH * 0.5f).toInt())
        val bot = ReflectionField.clarity(f, w, (outH * 0.9f).toInt())
        assertTrue("선명도 위 $top > 가운데 $mid > 아래 $bot", top > mid && mid > bot)
        // 위가 원본에 가장 가깝고 아래는 30% 아래로 퍼진다(접는 선 바로 아래 선명함은 근거리 띠가 맡는다)
        val src = ReflectionField.clarity(leftRedRightBlue(), w, 0)
        assertTrue("아래는 퍼져야 함: $bot / 원본 $src", bot < src * 0.3f)
    }

    @Test
    fun farRegionKeepsColorMoodAndSpatialSides() {
        val f = ReflectionField.build(leftRedRightBlue(), w, h)
        val y = ReflectionField.Spec().outH - 1
        val left = f[y * w + 4]
        val right = f[y * w + w - 5]
        assertTrue("아래 왼쪽은 빨강 쪽", r(left) > b(left))
        assertTrue("아래 오른쪽은 파랑 쪽", b(right) > r(right))
        assertTrue("아래도 반사 세기가 남아야 함(${a(left)})", a(left) >= 140)
        assertTrue("반사 세기는 위가 더 셈", a(f[0]) > a(left))
    }

    @Test
    fun reflectionIsFlippedVertically() {
        // 위쪽 흰색 / 아래쪽 검정인 화면: 바닥에 비친 상은 뒤집혀, 패널 위(접는 선 쪽)가 어둡고 아래가 밝다
        val src = IntArray(w * h) { i -> if (i / w < h / 2) 0xFFF0F0F0.toInt() else 0xFF000000.toInt() }
        val f = ReflectionField.build(src, w, h)
        val outH = ReflectionField.Spec().outH
        val topL = ReflectionField.luma(ReflectionField.rowMean(f, w, 1))
        val botL = ReflectionField.luma(ReflectionField.rowMean(f, w, outH - 2))
        assertTrue("패널 위 $topL < 아래 $botL", topL < botL)
    }

    @Test
    fun noBandsBetweenBlurLevels() {
        // 줄마다 선명도가 단조롭게 줄어야 한다(단계 경계에서 튀지 않음). 작은 반올림 오차만 허용
        val f = ReflectionField.build(leftRedRightBlue(), w, h)
        val outH = ReflectionField.Spec().outH
        var prev = Float.MAX_VALUE
        for (y in 0 until outH) {
            val c = ReflectionField.clarity(f, w, y)
            assertTrue("줄 $y 선명도 $c > 이전 $prev", c <= prev + 0.004f)
            prev = c
        }
    }

    @Test
    fun allBlackStaysQuietAndSampleWorks() {
        val f = ReflectionField.build(IntArray(w * h) { 0xFF000000.toInt() }, w, h)
        val c = ReflectionField.sample(f, w, ReflectionField.Spec().outH, 0.5f, 0.5f)
        assertTrue(ReflectionField.luma(c) < 0.02f)
    }

    @Test
    fun blurCurveMatchesAgreedTable() {
        // 11-02 소장님 확정(2차): 위 10%까지는 조금 더 선명하게, 25%부터 적극적으로
        val table = listOf(0f to 1.5f, 0.05f to 3.5f, 0.10f to 6f, 0.25f to 13f, 0.50f to 24f, 0.75f to 34f, 1f to 42f)
        for ((d, dp) in table) org.junit.Assert.assertEquals("d=$d", dp, ReflectionField.blurDp(d), 0.01f)
        // 사이도 끊김 없이 커진다
        var prev = 0f
        for (k in 0..100) {
            val v = ReflectionField.blurDp(k / 100f)
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test
    fun nearBandSharpAtHingeAndBlursDownward() {
        val nw = 96
        val nh = 40
        // 세로 줄무늬(원본 맨 아래 줄 = 접는 선 쪽)
        val px = IntArray(nw * nh) { i -> if ((i % nw) % 6 < 3) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val out = ReflectionField.buildNear(px, nw, nh, cellDp = 3f)
        val top = ReflectionField.clarity(out, nw, 0)
        val mid = ReflectionField.clarity(out, nw, nh / 2)
        val src = ReflectionField.clarity(px, nw, 0)
        assertTrue("접는 선 바로 아래는 원본 구조가 남는다: $top / $src", top > src * 0.3f)
        assertTrue("아래로 갈수록 흐려진다: $top > $mid", top > mid)
    }
}
