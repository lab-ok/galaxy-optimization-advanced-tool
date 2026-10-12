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
    /**
     * 흩어짐(1이 기본): 크면 충격·흔들기에 더 많은 입자가 튀고 옆으로도 넓게 퍼지며, 표면장력이 더 오래·더 많이 풀려
     * 무수한 작은 방울로 갈라졌다가 천천히 다시 모인다. 작으면 가장자리 몇 방울만 튀고 금방 한 덩어리로 돌아온다.
     */
    var scatter = 1f

    /** 튀는 중(1 → 0): 충격 직후 표면장력을 잠깐 풀어 튄 물방울이 떨어져 날아갔다가 천천히 돌아오게 한다. */
    private var loose = 0f

    /**
     * 충격: (dirX, dirY, 화면 좌표) 쪽 가장자리 입자들을 그쪽으로 [power]만큼 튀긴다. 덩어리는 충격에 반대쪽으로 쏠리므로
     * 호출하는 쪽은 액체가 쏠리는 반대 방향(빈 쪽)을 준다: 덩어리 뒤꽁무니에서 물방울이 떨어져 나가 빈 곳으로 날아간다.
     * 입자마다 세기·옆 방향을 달리해 크고 작은 물방울 여러 개로 갈라져 날아가고, 표면장력이 잠깐 풀렸다가 다시 모은다.
     */
    fun splash(dirX: Float, dirY: Float, power: Float) {
        if (!ready) return
        val l = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(1e-4f)
        val ux = dirX / l; val uy = dirY / l
        val (mx, my) = center()
        var front = 1f
        for (i in 0 until n) front = maxOf(front, (x[i] - mx) * ux + (y[i] - my) * uy)
        for (i in 0 until n) {
            val cut = (1f - 0.45f * scatter).coerceAtLeast(-1f)
            val ahead = (((x[i] - mx) * ux + (y[i] - my) * uy) / front - cut) / (1f - cut)
            if (ahead <= 0f) continue
            val h1 = ((i * 2654435761L) ushr 16 and 0xFF) / 255f
            val h2 = (((i * 40503L + 12345L) ushr 4 and 0xFF) / 255f - 0.5f) * scatter
            val k = power * ahead * (0.3f + 1.4f * h1 * h1)   // 몇 개만 멀리, 나머지는 조금
            vx[i] += (ux - uy * h2) * k
            vy[i] += (uy + ux * h2) * k
        }
        loose = 1f
    }

    /** 흔들기 판정: 가장자리 전체가 사방으로 톡 터지듯 튄다([power] dp/프레임). */
    fun burst(power: Float) {
        if (!ready) return
        val (mx, my) = center()
        var far = 1f
        for (i in 0 until n) far = maxOf(far, sqrt((x[i] - mx) * (x[i] - mx) + (y[i] - my) * (y[i] - my)))
        for (i in 0 until n) {
            val ox = x[i] - mx; val oy = y[i] - my
            val d = sqrt(ox * ox + oy * oy).coerceAtLeast(1e-4f)
            val band = (0.25f * scatter).coerceIn(0.05f, 1f)
            val edge = ((d / far - (1f - band)) / band).coerceIn(0f, 1f)
            if (edge <= 0f) continue
            val h1 = ((i * 2654435761L) ushr 16 and 0xFF) / 255f
            val k = power * edge * (0.3f + 1.4f * h1 * h1)
            vx[i] += ox / d * k; vy[i] += oy / d * k
        }
        loose = 1f
    }

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
     * [pull]은 잔 가운데로 끄는 약한 힘(가만히 두면 가운데로 돌아오게), [cohesion]은 방울 자신의 무게 중심으로 모으는 힘
     * (표면장력: 흩어진 물이 다시 한 방울로 둥글게 맺힌다. 방울 전체는 자유롭게 움직이고 모양만 둥글게 되돌린다).
     */
    fun step(dt: Float, ax: Float, ay: Float, swirl: Float, pull: Float, cohesion: Float = 0f) {
        if (!ready) return
        val (mx0, my0) = center()
        // 튄 직후에는 표면장력을 잠깐 약하게(튄 방울이 떨어져 나간다), 0.5초쯤 지나면 다시 모은다
        val glue = cohesion * (1f - (0.9f * scatter).coerceAtMost(1f) * loose)
        val visc = 1f - (0.85f * scatter).coerceAtMost(0.98f) * loose
        loose = (loose - 0.03f / scatter.coerceAtLeast(0.1f) * dt).coerceAtLeast(0f)
        // 1) 힘 → 속도
        for (i in 0 until n) {
            val rx = x[i] - cx; val ry = y[i] - cy
            val ox = x[i] - mx0; val oy = y[i] - my0
            vx[i] = (vx[i] + (ax - ry * swirl - rx * pull - ox * glue) * dt) * damping
            vy[i] = (vy[i] + (ay + rx * swirl - ry * pull - oy * glue) * dt) * damping
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
                val imp = dt * (1 - q) * (sigma * u + beta * u * u) * 0.5f * visc
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
            // 이웃이 적은 가장자리는 서로 끌어당긴다(표면장력). 튄 직후에는 이 끌림도 잠깐 풀어 방울이 떨어져 나가게 한다.
            val p = k * (rho - rest) * (if (rho < rest) visc else 1f); val pNear = kNear * rhoNear
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
        // 혹시 수치가 터지면(NaN) 처음 모양으로 되돌린다
        if (x[0].isNaN() || y[0].isNaN()) { reset(cx, cy, radius); return }
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
