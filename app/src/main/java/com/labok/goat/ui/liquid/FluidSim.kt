package com.labok.goat.ui.liquid

import kotlin.math.sqrt

/**
 * 동그란 유리잔 속 2D 액체(입자 [n]개). Clavet·Beaudoin·Poulin(2005) "Particle-based viscoelastic fluid simulation"의
 * 이중 밀도 완화(double density relaxation)를 쓴다: 이웃이 많으면 밀어내고(압력), 아주 가까우면 더 세게 밀어(근접 압력)
 * 입자가 뭉치지 않으면서 표면장력처럼 한 덩어리로 모인다. 위치 기반이라 큰 힘에도 터지지 않는다.
 *
 * 단위는 화면 픽셀, 시간은 1 = 1/60초(한 프레임). 힘은 [step]의 (ax, ay)로 받는다(화면 좌표, 아래가 +y).
 * 잔 중심 ([cx],[cy]), 반지름 [radius]. 입자 반지름은 상호작용 거리 [h]로 정한다.
 */
class FluidSim(val n: Int, val h: Float) {
    val x = FloatArray(n); val y = FloatArray(n)
    private val px = FloatArray(n); private val py = FloatArray(n)
    private val vx = FloatArray(n); private val vy = FloatArray(n)
    var cx = 0f; var cy = 0f; var radius = 1f
    var ready = false; private set

    /** 휴지 밀도(이웃 수 기준), 압력·근접 압력 세기, 점성(선형·제곱). */
    var rest = 6f
    var k = 0.012f
    var kNear = 0.05f
    var sigma = 0.08f
    var beta = 0.01f
    /** 속도 감쇠(프레임당). 1에 가까울수록 오래 출렁인다. */
    var damping = 0.995f

    /** 잔 가운데에 둥글게 채운다(해바라기 배치라 처음부터 고르게). */
    fun reset(cx: Float, cy: Float, radius: Float) {
        this.cx = cx; this.cy = cy; this.radius = radius
        val spread = h * 0.42f
        for (i in 0 until n) {
            val r = spread * sqrt(i + 0.5f)
            val a = i * 2.39996f
            x[i] = cx + r * kotlin.math.cos(a); y[i] = cy + r * kotlin.math.sin(a)
            vx[i] = 0f; vy[i] = 0f
        }
        ready = true
    }

    /** 잔만 옮긴다(크기가 바뀌었을 때). */
    fun container(cx: Float, cy: Float, radius: Float) {
        val dx = cx - this.cx; val dy = cy - this.cy
        for (i in 0 until n) { x[i] += dx; y[i] += dy }
        this.cx = cx; this.cy = cy; this.radius = radius
    }

    /**
     * 한 걸음([dt] 프레임 단위). (ax, ay)는 모든 입자에 걸리는 가속도(중력·관성), [swirl]은 잔 중심을 도는 힘(자이로 z, 양수면 시계 방향).
     * [pull]은 잔 가운데로 끄는 약한 힘(폰을 바닥에 눕혀 기울기가 없을 때 방울로 모여 있게).
     */
    fun step(dt: Float, ax: Float, ay: Float, swirl: Float, pull: Float) {
        if (!ready) return
        // 1) 힘 → 속도
        for (i in 0 until n) {
            val rx = x[i] - cx; val ry = y[i] - cy
            vx[i] = (vx[i] + (ax - ry * swirl - rx * pull) * dt) * damping
            vy[i] = (vy[i] + (ay + rx * swirl - ry * pull) * dt) * damping
        }
        // 2) 점성: 다가오는 이웃끼리 속도를 나눈다(출렁임이 부드럽게 가라앉는다)
        val h2 = h * h
        for (i in 0 until n) for (j in i + 1 until n) {
            val dx = x[j] - x[i]; val dy = y[j] - y[i]
            val r2 = dx * dx + dy * dy
            if (r2 >= h2 || r2 < 1e-6f) continue
            val r = sqrt(r2); val q = r / h
            val ux = dx / r; val uy = dy / r
            val u = (vx[i] - vx[j]) * ux + (vy[i] - vy[j]) * uy
            if (u > 0f) {
                val imp = dt * (1 - q) * (sigma * u + beta * u * u) * 0.5f
                vx[i] -= imp * ux; vy[i] -= imp * uy
                vx[j] += imp * ux; vy[j] += imp * uy
            }
        }
        // 3) 이동
        for (i in 0 until n) { px[i] = x[i]; py[i] = y[i]; x[i] += vx[i] * dt; y[i] += vy[i] * dt }
        // 4) 이중 밀도 완화
        for (i in 0 until n) {
            var rho = 0f; var rhoNear = 0f
            for (j in 0 until n) {
                if (j == i) continue
                val dx = x[j] - x[i]; val dy = y[j] - y[i]
                val r2 = dx * dx + dy * dy
                if (r2 >= h2) continue
                val q = 1 - sqrt(r2) / h
                rho += q * q; rhoNear += q * q * q
            }
            val p = k * (rho - rest); val pNear = kNear * rhoNear
            var mx = 0f; var my = 0f
            for (j in 0 until n) {
                if (j == i) continue
                val dx = x[j] - x[i]; val dy = y[j] - y[i]
                val r2 = dx * dx + dy * dy
                if (r2 >= h2 || r2 < 1e-6f) continue
                val r = sqrt(r2); val q = 1 - r / h
                val d = dt * dt * (p * q + pNear * q * q) * 0.5f
                val ux = dx / r * d; val uy = dy / r * d
                x[j] += ux; y[j] += uy
                mx -= ux; my -= uy
            }
            x[i] += mx; y[i] += my
        }
        // 5) 잔 벽: 밖으로 나가면 벽 안쪽으로 되돌린다
        val wall = radius - h * 0.35f
        for (i in 0 until n) {
            val dx = x[i] - cx; val dy = y[i] - cy
            val r = sqrt(dx * dx + dy * dy)
            if (r > wall) { x[i] = cx + dx / r * wall; y[i] = cy + dy / r * wall }
        }
        // 6) 속도 = 실제로 움직인 거리
        for (i in 0 until n) {
            vx[i] = ((x[i] - px[i]) / dt).coerceIn(-MAX_V, MAX_V)
            vy[i] = ((y[i] - py[i]) / dt).coerceIn(-MAX_V, MAX_V)
        }
    }

    /** 무게 중심(남은 시간 글자 위치 등). */
    fun center(): Pair<Float, Float> {
        var sx = 0f; var sy = 0f
        for (i in 0 until n) { sx += x[i]; sy += y[i] }
        return sx / n to sy / n
    }

    private companion object { const val MAX_V = 60f }
}
