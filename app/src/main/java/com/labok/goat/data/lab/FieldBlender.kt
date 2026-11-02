package com.labok.goat.data.lab

import kotlin.math.abs
import kotlin.math.exp

/**
 * 반사 필드의 시간 전환(11-02 최종 통합 지시): 새 목표가 올 때 전환을 처음부터 다시 시작하지 않고, **지금 보이는 상태에서**
 * 새 목표 쪽으로 계속 이어 간다. 매 프레임 `shown += (target - shown)·(1 - e^(-dt/τ))`로 화소마다 다가가고,
 * τ는 목표가 올 때 지금 상태와의 차이로 정한다: 큰 장면 전환은 빠르게(80ms), 작은 변화는 천천히(220ms) 흔들림 없이.
 * 전환 도중 τ를 남은 차이로 다시 정하면 끝부분이 점점 느려져 1초 가까이 끌었다.
 * 화면 그리기와 무관한 순수 계산이라 단위 시험으로 확인한다.
 */
class FieldBlender {
    private var shown: FloatArray? = null
    private var target: IntArray? = null
    private var tau = 120f
    var w = 0
        private set
    var h = 0
        private set

    /** 남은 평균 차이(0~1). 0.002 아래면 다 온 것으로 본다. */
    var remaining = 0f
        private set

    /**
     * 새 목표. 크기가 다르면(처음·해상도 변경) 바로 그 상태로 시작한다. 진행 중인 전환은 끊지 않는다.
     * [tauMs]가 0보다 크면 그 τ로 고정한다(보호 화면 연출처럼 느리게 흐를 때).
     */
    fun setTarget(px: IntArray, w: Int, h: Int, tauMs: Float = 0f) {
        val s = shown
        if (s == null || this.w != w || this.h != h) {
            shown = FloatArray(w * h * 4).also { a -> for (i in 0 until w * h) unpack(px[i], a, i * 4) }
            this.w = w
            this.h = h
        }
        target = px.copyOf()
        remaining = diff()
        tau = if (tauMs > 0f) tauMs else tauFor(remaining)
    }

    /** 목표를 비운다(필드 없음 → 투명으로 사라진다). */
    fun fadeOut() {
        val s = shown ?: return
        target = IntArray(s.size / 4)
        remaining = diff()
        tau = tauFor(remaining)
    }

    val active get() = remaining > DONE

    /** [dtMs]만큼 다가가 결과를 [out]에 쓴다. 아직 움직이는 중이면 true. */
    fun step(dtMs: Float, out: IntArray): Boolean {
        val s = shown ?: return false
        val t = target ?: return false
        val k = (1f - exp(-dtMs / tau)).coerceIn(0f, 1f)
        var acc = 0f
        var j = 0
        for (i in t.indices) {
            val c = t[i]
            for (ch in 0 until 4) {
                val v = ((c ushr (24 - ch * 8)) and 0xFF).toFloat()
                val n = s[j] + (v - s[j]) * k
                s[j] = n
                acc += abs(v - n)
                j++
            }
            out[i] = pack(s, i * 4)
        }
        remaining = acc / (t.size * 4 * 255f)
        if (remaining <= DONE) {
            // 마지막 한 걸음은 목표 그대로(남은 1/255 미만 차이가 영원히 이어지지 않게)
            for (i in t.indices) unpack(t[i], s, i * 4)
            System.arraycopy(t, 0, out, 0, t.size)
            remaining = 0f
        }
        return remaining > 0f
    }

    /** 지금 보이는 상태가 완전히 투명한지(필드를 거둘 때). */
    fun isClear(): Boolean = target?.all { it ushr 24 == 0 } == true && remaining == 0f

    private fun diff(): Float {
        val s = shown ?: return 0f
        val t = target ?: return 0f
        var acc = 0f
        var j = 0
        for (c in t) {
            for (ch in 0 until 4) {
                acc += abs(((c ushr (24 - ch * 8)) and 0xFF) - s[j])
                j++
            }
        }
        return acc / (t.size * 4 * 255f)
    }

    companion object {
        const val DONE = 0.002f

        /** 남은 차이 → τ(ms): 0.12 이상(장면 전환)은 80ms, 그 아래는 220ms에서 120ms로 줄어든다. */
        fun tauFor(d: Float): Float = if (d >= 0.12f) 80f else 220f - 100f * (d / 0.12f)

        private fun unpack(c: Int, a: FloatArray, o: Int) {
            a[o] = (c ushr 24 and 0xFF).toFloat()
            a[o + 1] = (c ushr 16 and 0xFF).toFloat()
            a[o + 2] = (c ushr 8 and 0xFF).toFloat()
            a[o + 3] = (c and 0xFF).toFloat()
        }

        private fun pack(a: FloatArray, o: Int): Int = (ch(a[o]) shl 24) or (ch(a[o + 1]) shl 16) or (ch(a[o + 2]) shl 8) or ch(a[o + 3])

        private fun ch(v: Float) = (v + 0.5f).toInt().coerceIn(0, 255)
    }
}
