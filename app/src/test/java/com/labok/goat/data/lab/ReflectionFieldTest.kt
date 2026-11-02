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
        // 기본 필드는 칸 격자가 보이지 않게 위쪽도 최소 1.3칸 흐린다(11-02) — 그래도 줄무늬가 원본의 25% 넘게 남고, 아래는 30% 아래로 퍼진다.
        // 접는 선 바로 아래 선명함은 근거리 띠(nearRawKeepsSourceDetail)가 맡는다
        val src = ReflectionField.clarity(leftRedRightBlue(), w, 0)
        assertTrue("위쪽 줄무늬가 남아야 함: $top / 원본 $src", top > src * 0.25f)
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
    fun nearRawKeepsSourceDetailFlippedWithFade() {
        val nw = 16
        val nh = 10
        // 맨 아래 줄(접는 선 쪽)만 흰색
        val px = IntArray(nw * nh) { i -> if (i / nw == nh - 1) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val out = ReflectionField.buildNearRaw(px, nw, nh)
        // 뒤집혀 맨 위 줄이 흰색이고(색은 원본 그대로), 아래 끝은 투명으로 사라진다
        assertTrue(out[0] and 0xFFFFFF == 0xFFFFFF)
        assertTrue("위는 보이고(${out[0] ushr 24}) 아래 끝은 투명(${out[(nh - 1) * nw] ushr 24})", (out[0] ushr 24) > 150 && (out[(nh - 1) * nw] ushr 24) == 0)
    }
}
