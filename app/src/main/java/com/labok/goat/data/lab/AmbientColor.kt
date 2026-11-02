package com.labok.goat.data.lab

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 적응형 앰비언트 색 엔진(GOAT 플렉스 패널, 11-02 소장님 고도화 지시). 안드로이드에 기대지 않는 순수 계산이라 단위 시험을 한다.
 * - 추출: 작게 줄인 화면(픽셀 배열)을 좌·중·우 세 구역으로 나누고, 구역마다 OKLab(지각 균일 색공간)에서 색을 고른다.
 *   단순 평균 대신 [OKLCH 색상 12칸 × 밝기 3칸] 묶음 가운데 가장 무거운 묶음(대표색)과 가중 평균을 섞는다. 무게는 채도가 높을수록,
 *   밝기가 중간일수록 크다(검은 여백·하얀 자막·회색이 색을 지배하지 않게).
 * - 보정: 패널에 쓸 색은 밝기 0.30~0.72, 채도 0.16 안으로 묶는다(눈부심·네온 방지).
 * - 안정화([Smoother]): 작은 변화는 무시(데드밴드), 보통 변화는 천천히(약 1.2초), 장면이 확 바뀌면 빠르게(약 0.3초) 따라가고
 *   밝기 변화 속도를 제한한다. 매 프레임 새 목표로 다시 겨냥하므로 진행 중인 전환을 끊지 않는다.
 */
object AmbientColor {
    /** 구역 색과 전체 정보. 색은 ARGB. [protectedLike]면 거의 검은 화면(보호 콘텐츠·검은 화면)이라 분석할 수 없다. */
    data class Palette(
        val left: Int,
        val center: Int,
        val right: Int,
        val overall: Int,
        val luminance: Float,
        val chroma: Float,
        val protectedLike: Boolean
    )

    // ---- 색공간(sRGB ↔ OKLab) ----

    private fun lin(c: Int): Float {
        val v = c / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun gam(v: Float): Int {
        val x = v.coerceIn(0f, 1f)
        val s = if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
        return (s * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    /** ARGB → [L, a, b]. */
    fun toOklab(argb: Int): FloatArray {
        val r = lin(argb shr 16 and 0xFF)
        val g = lin(argb shr 8 and 0xFF)
        val b = lin(argb and 0xFF)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        )
    }

    /** [L, a, b] → ARGB(불투명). */
    fun fromOklab(L: Float, a: Float, b: Float): Int {
        val l = (L + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val m = (L - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val s = (L - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bb = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
        return (0xFF shl 24) or (gam(r) shl 16) or (gam(g) shl 8) or gam(bb)
    }

    // ---- 추출 ----

    /** [px](w×h ARGB)에서 구역 색을 뽑는다. */
    fun extract(px: IntArray, w: Int, h: Int): Palette {
        require(px.size >= w * h && w > 0 && h > 0)
        val third = max(1, w / 3)
        val left = region(px, w, h, 0, third)
        val center = region(px, w, h, third, min(w, third * 2))
        val right = region(px, w, h, min(w, third * 2), w)
        val all = region(px, w, h, 0, w)
        var lum = 0f
        for (i in 0 until w * h) lum += toOklab(px[i])[0]
        lum /= (w * h)
        // 거의 모든 칸이 아주 어두우면(검은 화면·보호 콘텐츠) 분석 불가로 본다
        var dark = 0
        for (i in 0 until w * h) {
            val c = px[i]
            if ((c shr 16 and 0xFF) < 10 && (c shr 8 and 0xFF) < 10 && (c and 0xFF) < 10) dark++
        }
        val protectedLike = dark > w * h * 0.97f
        return Palette(tame(left), tame(center), tame(right), tame(all), lum, chromaOf(all), protectedLike)
    }

    private fun chromaOf(argb: Int): Float {
        val o = toOklab(argb)
        return hypot(o[1], o[2])
    }

    /** 한 구역(x0..x1)의 색: 대표 묶음 평균 60% + 전체 가중 평균 40%. */
    private fun region(px: IntArray, w: Int, h: Int, x0: Int, x1: Int): Int {
        // 색 있는 칸 12색상×3밝기 = 36, 무채색 3밝기 = 3
        val bins = 39
        val wSum = FloatArray(bins)
        val sumL = FloatArray(bins)
        val sumA = FloatArray(bins)
        val sumB = FloatArray(bins)
        var tw = 0f
        var tl = 0f
        var ta = 0f
        var tb = 0f
        for (y in 0 until h) {
            for (x in x0 until x1) {
                val o = toOklab(px[y * w + x])
                val c = hypot(o[1], o[2])
                // 채도가 높을수록, 밝기가 중간일수록 무겁게. 아주 어두운 칸·아주 밝은 칸은 거의 무시
                val lw = when {
                    o[0] < 0.12f -> 0.02f
                    o[0] > 0.96f -> 0.1f
                    else -> 1f - abs(o[0] - 0.6f)
                }
                val wt = (0.08f + c * 6f) * lw
                val hue = ((atan2(o[2], o[1]) / (2 * Math.PI.toFloat()) + 1f) % 1f)
                val hb = (hue * 12).toInt().coerceIn(0, 11)
                val lb = (o[0] * 3).toInt().coerceIn(0, 2)
                val bi = if (c < 0.02f) 36 + lb else hb * 3 + lb
                wSum[bi] += wt
                sumL[bi] += o[0] * wt
                sumA[bi] += o[1] * wt
                sumB[bi] += o[2] * wt
                tw += wt
                tl += o[0] * wt
                ta += o[1] * wt
                tb += o[2] * wt
            }
        }
        if (tw <= 0f) return fromOklab(0.2f, 0f, 0f)
        val top = wSum.indices.maxByOrNull { wSum[it] } ?: 0
        val dw = wSum[top].coerceAtLeast(1e-6f)
        val lightness = 0.6f * (sumL[top] / dw) + 0.4f * (tl / tw)
        val a = 0.6f * (sumA[top] / dw) + 0.4f * (ta / tw)
        val b = 0.6f * (sumB[top] / dw) + 0.4f * (tb / tw)
        return fromOklab(lightness, a, b)
    }

    /** 패널용으로 길들인다: 밝기 0.30~0.72, 채도 0.16 이하. */
    fun tame(argb: Int): Int {
        val o = toOklab(argb)
        val c = hypot(o[1], o[2])
        val k = if (c > 0.16f) 0.16f / c else 1f
        return fromOklab(o[0].coerceIn(0.30f, 0.72f), o[1] * k, o[2] * k)
    }

    /**
     * 반사 필드용 색 다듬기: 어두운 곳은 그대로 어둡게, 밝기는 0.84까지. 채도는 1.15배로 살리고 0.22까지(11-02 최종: 색 존재감 강화,
     * 눈부심·네온 방지 상한은 유지).
     */
    fun grade(argb: Int): Int {
        val o = toOklab(argb)
        val c = hypot(o[1], o[2]) * 1.15f
        val k = (if (c > 0.22f) 0.22f / c else 1f) * 1.15f
        // 대비 1.12배(가운데 0.48 기준): 반사가 넓은 색 얼룩이 아니라 밝고 어두운 구조로 보이게(11-02 실사용 검수)
        val l = (0.48f + (o[0] * 0.97f - 0.48f) * 1.12f).coerceIn(0f, 0.84f)
        return fromOklab(l, o[1] * k, o[2] * k)
    }

    /** 두 색의 지각 거리(OKLab ΔE). */
    fun distance(x: Int, y: Int): Float {
        val a = toOklab(x)
        val b = toOklab(y)
        return hypot(hypot(a[0] - b[0], a[1] - b[1]), a[2] - b[2])
    }

    /**
     * 시간 안정화. [step]마다 목표 쪽으로 다가간 색을 돌려준다.
     * [deadband] 안의 차이는 무시, [sceneCut]보다 크면 빠른 시간상수 [fastTau], 아니면 [slowTau]. 밝기는 초당 [maxDl]까지만 바뀐다.
     */
    class Smoother(
        start: Int,
        private val deadband: Float = 0.012f,
        private val sceneCut: Float = 0.18f,
        private val slowTau: Float = 1.2f,
        private val fastTau: Float = 0.3f,
        private val maxDl: Float = 0.6f
    ) {
        private var cur = toOklab(start)
        var color: Int = start
            private set

        fun step(target: Int, dt: Float): Int {
            val t = toOklab(target)
            val d = hypot(hypot(t[0] - cur[0], t[1] - cur[1]), t[2] - cur[2])
            if (d < deadband) return color
            val tau = if (d > sceneCut) fastTau else slowTau
            val k = 1f - exp(-dt / tau)
            val dl = ((t[0] - cur[0]) * k).coerceIn(-maxDl * dt, maxDl * dt)
            cur = floatArrayOf(cur[0] + dl, cur[1] + (t[1] - cur[1]) * k, cur[2] + (t[2] - cur[2]) * k)
            color = fromOklab(cur[0], cur[1], cur[2])
            return color
        }
    }
}
