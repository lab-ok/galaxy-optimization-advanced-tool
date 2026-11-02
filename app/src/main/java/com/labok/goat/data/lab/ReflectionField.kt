package com.labok.goat.data.lab

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 공간 반사 필드(GOAT 플렉스 패널, 11-02 소장님 "Spatial Ambient Liquid"). 위 화면을 반사의 원천으로 보고, 아래 패널(바닥 면)에 비치는
 * 반사 이미지를 만든다. 안드로이드에 기대지 않는 순수 계산이라 단위 시험을 한다.
 *
 * - 좌표: 테이블탑에서 위 화면은 세워져 있고 패널은 누워 있다. 바닥에 비친 상은 위아래가 뒤집혀, 위 화면의 아래쪽(접는 선 쪽)이
 *   패널 위쪽(접는 선 쪽)에 오고, 위 화면 위쪽은 패널 아래쪽으로 간다. 좌우는 그대로다.
 * - 거리: 패널 위쪽(d=0)은 광원 가까이라 반사가 또렷하고, 아래(d=1)로 갈수록 흐려지고 퍼진다.
 *   흐림 반지름 r(d) = [blurMin] + ([blurMax] − [blurMin])·d^[gamma] (원본 칸 단위). 흐림 단계 여러 장(다중 해상도 대신 같은 크기의
 *   상자 흐림 3회 = 가우시안 근사)을 미리 만들고, 줄마다 r(d)가 걸친 두 단계를 섞어 단계 경계 없이 연속으로 바뀐다.
 * - 에너지: 반사의 세기(알파)는 d에 따라 [energyTop] → [energyBottom]으로 천천히 준다. 흐려져도 색 분위기는 남는다(검게 죽지 않는다).
 * 결과는 가로 w × 세로 [outH] ARGB(알파 = 반사 세기). 그릴 때 쌍선형 확대로 패널 전체에 깐다.
 */
object ReflectionField {
    data class Spec(
        val outH: Int = 48,
        val blurMin: Float = 0f,
        val blurMax: Float = 10f,
        val gamma: Float = 1.7f,
        val energyTop: Float = 1f,
        val energyBottom: Float = 0.62f
    )

    /** 미리 만들어 두는 흐림 단계(원본 칸 반지름). r(d)는 이 사이를 섞는다. */
    private val LEVELS = floatArrayOf(0f, 1f, 2f, 3.5f, 5.5f, 8f, 11f)

    /**
     * [px](w×h, 위 화면의 반사에 쓸 부분)로 반사 필드를 만든다. 반환은 w×spec.outH ARGB.
     */
    fun build(px: IntArray, w: Int, h: Int, spec: Spec = Spec()): IntArray {
        require(w > 0 && h > 0 && px.size >= w * h)
        val outH = spec.outH.coerceAtLeast(2)
        // 1) 위아래 뒤집어 패널 세로 해상도로 맞춘다(가까운 줄 고르기 + 이웃 평균)
        val base = IntArray(w * outH)
        for (y in 0 until outH) {
            val sy = ((outH - 1 - y) * (h - 1f) / (outH - 1)).roundToInt().coerceIn(0, h - 1)
            System.arraycopy(px, sy * w, base, y * w, w)
        }
        // 2) 흐림 단계들
        val levels = LEVELS.map { r -> if (r <= 0f) base else blur(base, w, outH, r) }
        // 3) 줄마다 r(d)에 맞는 두 단계를 섞고 에너지를 알파로
        val out = IntArray(w * outH)
        for (y in 0 until outH) {
            val d = y / (outH - 1f)
            val r = spec.blurMin + (spec.blurMax - spec.blurMin) * d.pow(spec.gamma)
            var i = 0
            while (i < LEVELS.size - 2 && LEVELS[i + 1] < r) i++
            val t = ((r - LEVELS[i]) / (LEVELS[i + 1] - LEVELS[i])).coerceIn(0f, 1f)
            val a = levels[i]
            val b = levels[i + 1]
            val energy = spec.energyTop + (spec.energyBottom - spec.energyTop) * d
            val alpha = (energy * 255f).roundToInt().coerceIn(0, 255)
            for (x in 0 until w) {
                val ca = a[y * w + x]
                val cb = b[y * w + x]
                val rr = lerp(ca shr 16 and 0xFF, cb shr 16 and 0xFF, t)
                val gg = lerp(ca shr 8 and 0xFF, cb shr 8 and 0xFF, t)
                val bb = lerp(ca and 0xFF, cb and 0xFF, t)
                out[y * w + x] = (alpha shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return out
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    /** 상자 흐림 3번(가우시안 근사). 반지름 [r]은 소수도 받는다(두 정수 반지름 결과를 섞는다). */
    fun blur(src: IntArray, w: Int, h: Int, r: Float): IntArray {
        val lo = r.toInt()
        val hi = lo + 1
        val f = r - lo
        val a = boxN(src, w, h, lo)
        if (f < 0.01f) return a
        val b = boxN(src, w, h, hi)
        return IntArray(src.size) { i ->
            val ca = a[i]
            val cb = b[i]
            (0xFF shl 24) or (lerp(ca shr 16 and 0xFF, cb shr 16 and 0xFF, f) shl 16) or (lerp(ca shr 8 and 0xFF, cb shr 8 and 0xFF, f) shl 8) or lerp(ca and 0xFF, cb and 0xFF, f)
        }
    }

    private fun boxN(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src
        var cur = src
        repeat(3) { cur = box(cur, w, h, r) }
        return cur
    }

    private fun box(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        // 가로
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (k in -r..r) {
                    val c = src[y * w + (x + k).coerceIn(0, w - 1)]
                    sr += c shr 16 and 0xFF
                    sg += c shr 8 and 0xFF
                    sb += c and 0xFF
                }
                val n = 2 * r + 1
                tmp[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
        // 세로
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (k in -r..r) {
                    val c = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    sr += c shr 16 and 0xFF
                    sg += c shr 8 and 0xFF
                    sb += c and 0xFF
                }
                val n = 2 * r + 1
                out[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
        return out
    }

    /** 줄 [row]의 선명도: 가로 이웃 칸 밝기 차이의 평균(에지가 또렷할수록 크다). */
    fun clarity(field: IntArray, w: Int, row: Int): Float {
        var s = 0f
        for (x in 0 until w - 1) s += abs(luma(field[row * w + x + 1]) - luma(field[row * w + x]))
        return s / (w - 1)
    }

    /** 줄의 평균 색(알파 무시). */
    fun rowMean(field: IntArray, w: Int, row: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        for (x in 0 until w) {
            val c = field[row * w + x]
            r += c shr 16 and 0xFF
            g += c shr 8 and 0xFF
            b += c and 0xFF
        }
        return (0xFF shl 24) or ((r / w) shl 16) or ((g / w) shl 8) or (b / w)
    }

    fun luma(c: Int) = (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    /** 필드의 (fx, fy)(0~1) 자리 색(쌍선형). 잉크·카드 윤곽이 같은 좌표계에서 색을 받는다. */
    fun sample(field: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val x = (fx.coerceIn(0f, 1f) * (w - 1))
        val y = (fy.coerceIn(0f, 1f) * (h - 1))
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(w - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)
        val tx = x - x0
        val ty = y - y0
        fun ch(c: Int, s: Int) = c shr s and 0xFF
        fun mix(s: Int): Int {
            val top = ch(field[y0 * w + x0], s) * (1 - tx) + ch(field[y0 * w + x1], s) * tx
            val bot = ch(field[y1 * w + x0], s) * (1 - tx) + ch(field[y1 * w + x1], s) * tx
            return (top * (1 - ty) + bot * ty).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
