package com.labok.goat.data.lab

import kotlin.math.abs
import kotlin.math.exp

/**
 * 반사 필드의 시간 전환(11-02 최종 통합 → 근거리·시간 보간 지시).
 *
 * 화소마다 **임계 감쇠 스프링**(위치와 속도)으로 목표를 따라간다. 새 목표가 와도 위치와 속도를 그대로 이어 가므로
 * 전환을 처음부터 다시 시작하지도, 매번 감속했다가 멈추지도 않는다.
 *
 * 1.42.9~11은 지수 감쇠(τ 80~220ms)였다. 실기에서 새 반사가 약 250ms마다 오는데, 지수 감쇠는 도착 직후가 가장 빠르고
 * 다음 샘플 전에 거의 멈춰서 "변화 → 멈춤 → 변화"로 보였다(11-02 소장님). 이제 스프링 강성 ω를 **실제 샘플 간격**
 * (최근 간격의 중앙값)에 맞춰, 다음 샘플이 올 때 아직 40%쯤 남아 움직이는 중이 되게 한다(ωT = 2.1).
 * 큰 장면 전환은 ω를 1.3배로 빠르게, 작은 변화는 0.8배로 천천히. 실제로 화면이 멈추면 목표에 닿고 멈춘다.
 *
 * 그리기와 무관한 순수 계산이라 단위 시험으로 확인한다.
 */
class FieldBlender {
    private var shown: FloatArray? = null
    private var vel: FloatArray? = null
    private var target: IntArray? = null
    var w = 0
        private set
    var h = 0
        private set

    /** 남은 평균 차이(0~1). */
    var remaining = 0f
        private set

    /** 스프링 강성(1/ms). */
    private var omega = 0.01f

    /** 최근 목표 도착 간격(ms, 최대 5개). */
    private val gaps = ArrayDeque<Float>()
    private var lastArrival = -1L

    /** 지금 추정한 샘플 간격(ms). */
    val interval: Float
        get() = if (gaps.isEmpty()) DEFAULT_INTERVAL else gaps.sorted()[gaps.size / 2]

    /**
     * 새 목표. 크기가 다르면(처음·해상도 변경) 바로 그 상태로 시작한다. 진행 중인 움직임(위치·속도)은 끊지 않는다.
     * [nowMs]는 도착 시각(샘플 간격 추정용). [slow]면 보호 화면 연출처럼 약 0.9초에 걸쳐 흐르게 한다.
     */
    fun setTarget(px: IntArray, w: Int, h: Int, nowMs: Long, slow: Boolean = false) {
        val s = shown
        if (s == null || this.w != w || this.h != h) {
            // 첫 목표(또는 크기 변경)는 색은 그대로, 세기(알파)는 0에서 시작해 스프링으로 나타난다(첫 등장 때 반사가 툭 바뀌지 않게, 11-02)
            shown = FloatArray(w * h * 4).also { a ->
                for (i in 0 until w * h) {
                    unpack(px[i], a, i * 4)
                    a[i * 4] = 0f
                }
            }
            vel = FloatArray(w * h * 4)
            this.w = w
            this.h = h
        }
        if (lastArrival >= 0) {
            // 화면이 멈췄다 다시 움직인 긴 공백은 간격 추정에 넣지 않는다(정지 뒤 첫 샘플 하나로 전환이 늘어지지 않게)
            val gap = (nowMs - lastArrival).toFloat()
            if (gap in 12f..MAX_INTERVAL) {
                gaps.addLast(gap)
                if (gaps.size > 5) gaps.removeFirst()
            }
        }
        lastArrival = nowMs
        target = px.copyOf()
        remaining = diff()
        omega = if (slow) SLOW_OMEGA else omegaFor(interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL), remaining)
    }

    /** 목표를 비운다(필드 없음 → 투명으로 사라진다). */
    fun fadeOut() {
        val s = shown ?: return
        target = IntArray(s.size / 4)
        remaining = diff()
        omega = omegaFor(interval.coerceIn(MIN_INTERVAL, MAX_INTERVAL), remaining)
    }

    val active get() = remaining > DONE || speed > DONE

    /** 평균 속도(0~1 / 프레임 16ms 기준). */
    var speed = 0f
        private set

    /** [dtMs]만큼 나아가 결과를 [out]에 쓴다. 아직 움직이는 중이면 true. */
    fun step(dtMs: Float, out: IntArray): Boolean {
        val s = shown ?: return false
        val v = vel ?: return false
        val t = target ?: return false
        val wd = omega * dtMs
        val ex = exp(-wd)
        var acc = 0f
        var sp = 0f
        var j = 0
        for (i in t.indices) {
            val c = t[i]
            for (ch in 0 until 4) {
                val tv = ((c ushr (24 - ch * 8)) and 0xFF).toFloat()
                // 임계 감쇠의 정확한 한 걸음(dt가 커도 넘치지 않는다): e' = (e + (v + ωe)dt)·e^(-ωdt), v' = (v - ω(v + ωe)dt)·e^(-ωdt)
                val e = s[j] - tv
                val k = v[j] + omega * e
                val ne = (e + k * dtMs) * ex
                val nv = (v[j] - omega * k * dtMs) * ex
                s[j] = tv + ne
                v[j] = nv
                acc += abs(ne)
                sp += abs(nv)
                j++
            }
            out[i] = pack(s, i * 4)
        }
        val n = t.size * 4 * 255f
        remaining = acc / n
        speed = sp * 16f / n
        if (remaining <= DONE && speed <= DONE) {
            // 마지막 한 걸음은 목표 그대로(남은 1/255 미만 차이가 영원히 이어지지 않게) — 진짜 정지 화면은 여기서 멈춘다
            for (i in t.indices) unpack(t[i], s, i * 4)
            v.fill(0f)
            System.arraycopy(t, 0, out, 0, t.size)
            remaining = 0f
            speed = 0f
        }
        return remaining > 0f || speed > 0f
    }

    /** 시험용: 화소 [i]의 파랑 채널 실수값(반올림 전). */
    fun shownBlue(i: Int): Float = shown?.get(i * 4 + 3) ?: 0f

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
        const val DEFAULT_INTERVAL = 250f
        const val MIN_INTERVAL = 25f
        const val MAX_INTERVAL = 1200f

        /** 보호 화면 연출: ωT = 2.4가 약 0.9초가 되게. */
        private const val SLOW_OMEGA = 2.4f / 900f

        /**
         * 샘플 간격 [intervalMs]와 장면 변화량 [d](0~1)로 정한 스프링 강성(1/ms).
         * 기본 ωT = 2.1: 다음 샘플이 올 때 처음 차이의 약 38%가 남아 아직 움직이는 중이다(멈춤 구간이 없다).
         * 큰 장면 전환(d ≥ 0.12)은 1.3배 빠르게, 작은 변화(d < 0.01)는 0.8배로 천천히.
         * 11-03: ωT 2.4·큰 전환 1.6배였을 때 큰 전환은 다음 샘플 전에 닿아 멈췄다(에뮬레이터 계속 움직이는 무늬에서 22번 중 3번,
         * 평균 0.45초 멈춤). 간격이 들쭉날쭉해도 다음 샘플까지 움직임이 이어지게 조금 늦췄다
         */
        fun omegaFor(intervalMs: Float, d: Float): Float {
            val base = 2.1f / intervalMs
            return base * when {
                d >= 0.12f -> 1.3f
                d < 0.01f -> 0.8f
                else -> 1f
            }
        }

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
