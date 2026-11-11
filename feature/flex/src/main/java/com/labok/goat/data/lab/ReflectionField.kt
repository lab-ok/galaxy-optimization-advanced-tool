package com.labok.goat.data.lab

import kotlin.math.abs
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
        val outH: Int = 96,
        val blurMin: Float = 0f,
        val blurMax: Float = 14f,
        val gamma: Float = 1.2f,
        val energyTop: Float = 1f,
        val energyBottom: Float = 0.72f,
        /** 기본 필드 한 칸이 패널에서 몇 dp인지(가로). 블러 곡선(dp)을 칸 단위로 바꿀 때 쓴다. 폴드 안쪽 화면 1848px·128칸·밀도 약 2.6 기준. */
        val cellDp: Float = 5.4f,
        /** 바닥 반사 블러 배율(실험실 설정 「바닥 반사 블러」, 1 = 기본 곡선). */
        val blurScale: Float = 1f
    )

    /**
     * 거리 블러 곡선(11-02 소장님 확정: "5%까지 10dp, 10%에 24dp, 25%에 48dp 이런 식"으로 적극적으로). 패널 위치 [d](0 = 접는 선,
     * 1 = 맨 아래)의 블러 반지름(dp). 점 사이는 직선으로 잇는다. 기본 필드와 근거리 띠가 이 한 곡선을 같이 쓴다.
     */
    private val CURVE_D = floatArrayOf(0f, 0.05f, 0.10f, 0.25f, 1f / 3f, 0.50f, 0.75f, 1f)

    // 11-02 소장님 2차: "맨 위 블러 좀 더 빼서 선명하게, 10%까지는 살짝 더 선명하게"
    // 3차: "전체적으로 블러 좀 더 빼서 선명하게(버튼은 따로 더 흐리게)" — 전체를 약 30% 낮췄다. 버튼 유리 블러는 셰이더가 따로 한다
    // 4차: "그라데이션 블러의 적극성을 낮추자" → B안(거의 일정하게 완만히 커짐)을 기본으로
    // 5차(11-06): "위 1/3까지는 마음에 들고, 그 아래부터는 점점 그라데이션으로 블러를 더 많이" — 1/3 지점(약 16.7dp)까지는 그대로 두고
    // 그 아래는 갈수록 가파르게 커진다(1/2 30dp, 3/4 52dp, 맨 아래 72dp. 예전 24·34·42dp)
    private val CURVE_DP = floatArrayOf(1.5f, 3.5f, 6f, 13f, 13f + 11f / 3f, 30f, 52f, 72f)

    fun blurDp(d: Float): Float {
        val x = d.coerceIn(0f, 1f)
        var i = 0
        while (i < CURVE_D.size - 2 && CURVE_D[i + 1] < x) i++
        val t = (x - CURVE_D[i]) / (CURVE_D[i + 1] - CURVE_D[i])
        return CURVE_DP[i] + (CURVE_DP[i + 1] - CURVE_DP[i]) * t
    }

    /** 미리 만들어 두는 흐림 단계(원본 칸 반지름). r(d)는 이 사이를 섞는다. */
    private val LEVELS = floatArrayOf(0f, 1f, 2f, 3.5f, 5.5f, 8f, 11f, 15f, 19f, 24f)

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
        // 2) 흐림 단계들 — 이번 곡선·배율에서 실제로 쓰는 단계까지만 만든다(11-02 실기: 쓰지 않는 큰 반지름까지 늘 만들어 분석이 느렸다)
        val levels = lazyLevels(LEVELS, (0 until outH).maxOf { radiusAt(it / (outH - 1f), spec) }) { r -> blur(base, w, outH, r) }.let { lv -> List(LEVELS.size) { i -> lv(i) ?: base } }
        // 3) 줄마다 r(d)에 맞는 두 단계를 섞고 에너지를 알파로
        val out = IntArray(w * outH)
        for (y in 0 until outH) {
            val d = y / (outH - 1f)
            // 블러 곡선([blurDp])을 칸 단위로. 접는 선 바로 아래 선명한 부분은 근거리 띠(고해상도)가 덮는다. 단계 사이는 두 흐림 단계를 섞어 경계가 없다
            val r = radiusAt(d, spec).coerceAtMost(LEVELS.last())
            var i = 0
            while (i < LEVELS.size - 2 && LEVELS[i + 1] < r) i++
            val t = ((r - LEVELS[i]) / (LEVELS[i + 1] - LEVELS[i])).coerceIn(0f, 1f)
            val a = levels[i]
            val b = levels[i + 1]
            val alpha = (energyAt(d, spec) * 255f).roundToInt().coerceIn(0, 255)
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

    /** 패널 위치 [d](0=접는 선, 1=맨 아래)의 흐림 반지름(필드 칸 단위) = [blurDp] ÷ 칸 크기(dp). */
    fun radiusAt(d: Float, spec: Spec = Spec()): Float = blurDp(d) * spec.blurScale / spec.cellDp

    /** 패널 위치 [d]의 반사 세기. 접는 선 바로 아래(위 8%)는 번쩍이지 않게 75%에서 부드럽게 올린다(따로 빛 띠를 만들지 않는다). */
    fun energyAt(d: Float, spec: Spec = Spec()): Float {
        val ramp = 0.75f + 0.25f * smooth((d / 0.08f).coerceIn(0f, 1f))
        return (spec.energyTop + (spec.energyBottom - spec.energyTop) * d) * ramp
    }

    /** 근거리 띠가 덮는 패널 높이 비율(접는 선부터). 적극적인 곡선에서는 위 10%까지만 선명해 고해상도가 필요하다(25% → 10%). */
    const val NEAR_FRAC = 0.10f

    /** 근거리 띠 흐림 단계(고해상도 칸). 10%에서 24dp ≈ 폴드 26칸까지. */
    private val NEAR_LEVELS = floatArrayOf(0f, 1.5f, 3f, 5f, 8f, 12f, 18f, 26f)

    /**
     * 근거리 고해상도 반사 띠(11-02 "근거리 반사 화질" 지시). 기본 필드는 96칸이라 접는 선 바로 아래가 흐림 0이어도 한 칸이
     * 약 19px로 "저해상도 영상을 확대한 것"처럼 보였다. 패널 위 [NEAR_FRAC]만큼은 4배 해상도(384칸) 원본으로 따로 만들어
     * 기본 필드 위에 얹는다. [px]는 위 화면에서 접는 선 쪽 끝 띠(w×h, 고해상도 칸, 맨 아래 줄이 접는 선 쪽).
     * 흐림과 세기는 기본 필드와 같은 거리 곡선을 그대로 따르고(반지름은 [scale]배 칸), 띠 아래쪽 절반에서 투명해져
     * 기본 필드로 경계 없이 넘어간다. 반환은 w×h ARGB(알파 = 세기 × 띠 가장자리 페이드).
     */
    fun buildNear(px: IntArray, w: Int, h: Int, cellDp: Float, spec: Spec = Spec()): IntArray {
        require(w > 0 && h > 1 && px.size >= w * h)
        val flipped = IntArray(w * h)
        for (y in 0 until h) System.arraycopy(px, (h - 1 - y) * w, flipped, y * w, w)
        val lv = NEAR_LEVELS
        val need = (0 until h).maxOf { blurDp(it / (h - 1f) * NEAR_FRAC) * spec.blurScale / cellDp }
        val levels = lazyLevels(lv, need) { r -> blur(flipped, w, h, r) }.let { get -> List(lv.size) { i -> get(i) ?: flipped } }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val db = y / (h - 1f)
            val d = db * NEAR_FRAC
            // 줄마다 같은 블러 곡선(dp)을 근거리 칸 단위로: 0%에서 3dp, 5%에서 10dp, 10%에서 24dp — 아래로 갈수록 적극적으로 커진다
            val r = (blurDp(d) * spec.blurScale / cellDp).coerceAtMost(lv.last())
            var i = 0
            while (i < lv.size - 2 && lv[i + 1] < r) i++
            val t = ((r - lv[i]) / (lv[i + 1] - lv[i])).coerceIn(0f, 1f)
            val feather = 1f - smooth(((db - 0.5f) / 0.5f).coerceIn(0f, 1f))
            val alpha = (energyAt(d, spec) * feather * 255f).roundToInt().coerceIn(0, 255)
            val a = levels[i]
            val b = levels[i + 1]
            for (x in 0 until w) {
                val ca = a[y * w + x]
                val cb = b[y * w + x]
                out[y * w + x] = (alpha shl 24) or (lerp(ca shr 16 and 0xFF, cb shr 16 and 0xFF, t) shl 16) or (lerp(ca shr 8 and 0xFF, cb shr 8 and 0xFF, t) shl 8) or lerp(ca and 0xFF, cb and 0xFF, t)
            }
        }
        return out
    }

    /** [px](w×h)를 [k]×[k] 칸 평균으로 줄인다(고해상도 캡처 → 기본 필드용). */
    fun boxDown(px: IntArray, w: Int, h: Int, k: Int): IntArray {
        val ow = w / k
        val oh = h / k
        val out = IntArray(ow * oh)
        for (oy in 0 until oh) {
            for (ox in 0 until ow) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until k) {
                    for (dx in 0 until k) {
                        val c = px[(oy * k + dy) * w + ox * k + dx]
                        r += c shr 16 and 0xFF
                        g += c shr 8 and 0xFF
                        b += c and 0xFF
                    }
                }
                val n = k * k
                out[oy * ow + ox] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    /**
     * 흐림 단계 [lv] 가운데 최대 반지름 [need]를 덮는 데 필요한 것까지만 만든다(그 위 단계는 null → 쓰이지 않음).
     * 반지름 0 단계는 원본이라 만들지 않는다.
     */
    private fun lazyLevels(lv: FloatArray, need: Float, make: (Float) -> IntArray): (Int) -> IntArray? {
        var last = 0
        while (last < lv.size - 1 && lv[last] < need) last++
        val made = arrayOfNulls<IntArray>(lv.size)
        for (i in 1..last.coerceAtMost(lv.size - 1)) made[i] = make(lv[i])
        return { i -> made[i] }
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

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

    /**
     * 상자 흐림 한 번(가로→세로). 미끄러지는 창으로 칸마다 더하고 빼기만 해서 반지름과 무관하게 칸당 비용이 같다
     * (11-02 실기: 칸마다 반지름만큼 다시 더하던 예전 방식은 반지름 15에서 프레임마다 수 ms~수십 ms가 걸렸다).
     */
    private fun box(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        val n = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val c = src[row + k.coerceIn(0, w - 1)]
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = src[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = src[row + (x - r).coerceAtLeast(0)]
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val c = tmp[k.coerceIn(0, h - 1) * w + x]
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
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
