package com.local.folddpifix.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 연체(soft body) 물방울: 알약 모양 외곽선을 점 [N]개의 닫힌 곡선으로 두고 힘을 적분한다.
 * 실제 액체처럼 보이게 하는 힘은 다섯 가지다.
 * - 구동력: 목표 자리로 끄는 힘. 이동 방향을 향한 면과 가로 가운데일수록 세게 끌어(가운데를 잡아끄는 것처럼)
 *   앞쪽이 먼저 나가고 가장자리는 늦게 따라와 물처럼 휜다.
 * - 표면장력: 곡률을 줄이려는 힘(이산 라플라시안). 쉬는 모양(알약)의 곡률은 빼고 '넘치는 곡률'에만 작용해,
 *   늘어나거나 울퉁불퉁해진 곳을 둥글게 펴되 알약이 원으로 쪼그라들지는 않게 한다.
 * - 압력(면적 보존): 넓이가 줄면 바깥으로, 늘면 안으로 밀어 부피가 보존된다(늘어나면 가늘어지고 눌리면 두꺼워짐).
 * - 형태 복원: 질량 중심 기준 알약 모양으로 약하게 되돌린다.
 * - 점성: 속도 감쇠와 이웃 점 사이 속도 차 감쇠로 출렁임이 자연스럽게 잦아든다.
 * 계수는 서버에서 파이썬으로 같은 모델을 돌려 프레임을 눈으로 보며 맞췄다(이동 212px, 폭 612px, 높이 108px 기준,
 * 약 0.3초에 도착해 한 번 살짝 출렁이고 멈춤). 모델 뼈대는 압력-스프링 연체(Matyka 2004)와 형태 맞춤(Müller 2005).
 */
class SoftBlob(private val n: Int = N) {
    private val rx = FloatArray(n); private val ry = FloatArray(n)   // 쉬는 모양(중심 기준)
    private val px = FloatArray(n); private val py = FloatArray(n)
    private val vx = FloatArray(n); private val vy = FloatArray(n)
    private val lap0x = FloatArray(n); private val lap0y = FloatArray(n)
    private val mid = FloatArray(n)
    private var area0 = 0f
    private var h = 0f
    private var tx = 0f; private var ty = 0f
    var ready = false; private set

    /** 질량 중심. */
    var cx = 0f; private set
    var cy = 0f; private set

    /**
     * 폭 [w]·높이 [hh], 모서리 반지름 [corner]인 둥근 사각형을 중심 ([x],[y])에 쉬는 상태로 놓는다.
     * [corner]가 음수면 짧은 변의 절반, 곧 알약(또는 원)이다.
     */
    fun reset(x: Float, y: Float, w: Float, hh: Float, corner: Float = -1f) {
        restShape(w, hh, corner)
        for (i in 0 until n) { px[i] = x + rx[i]; py[i] = y + ry[i]; vx[i] = 0f; vy[i] = 0f }
        tx = x; ty = y; cx = x; cy = y
        ready = true
    }

    /**
     * 쉬는 모양만 바꾼다(점의 위치·속도는 그대로). 이후 힘이 새 모양 쪽으로 끌어 방울이 부풀거나
     * 오므라들며 출렁인다(메뉴가 펼쳐질 때, 손잡이를 잡았을 때). 점 번호는 모양과 상관없이 늘
     * 윗변 가운데에서 시계 방향으로 매겨 두어, 모양이 바뀌어도 같은 번호끼리 자연스럽게 이어진다.
     */
    fun reshape(w: Float, hh: Float, corner: Float = -1f) {
        if (!ready) return
        restShape(w, hh, corner)
    }

    private fun restShape(w: Float, hh: Float, corner: Float) {
        h = minOf(w, hh)
        val pi = PI.toFloat()
        val r = max(0.5f, if (corner < 0) h / 2 else minOf(corner, h / 2))
        val lx = max(0f, w - 2 * r); val ly = max(0f, hh - 2 * r)
        val arc = pi / 2 * r
        // 윗변 가운데 → 오른쪽 위 모서리 → 오른쪽 변 → … → 윗변 가운데
        val seg = floatArrayOf(lx / 2, arc, ly, arc, lx, arc, ly, arc, lx / 2)
        val per = seg.sum()
        for (i in 0 until n) {
            var s = per * i / n
            var k = 0
            while (k < seg.size - 1 && s > seg[k]) { s -= seg[k]; k++ }
            val ex = lx / 2; val ey = ly / 2
            when (k) {
                0 -> { rx[i] = s; ry[i] = -ey - r }
                1 -> { val a = -pi / 2 + s / r; rx[i] = ex + r * cos(a); ry[i] = -ey + r * sin(a) }
                2 -> { rx[i] = ex + r; ry[i] = -ey + s }
                3 -> { val a = s / r; rx[i] = ex + r * cos(a); ry[i] = ey + r * sin(a) }
                4 -> { rx[i] = ex - s; ry[i] = ey + r }
                5 -> { val a = pi / 2 + s / r; rx[i] = -ex + r * cos(a); ry[i] = ey + r * sin(a) }
                6 -> { rx[i] = -ex - r; ry[i] = ey - s }
                7 -> { val a = pi + s / r; rx[i] = -ex + r * cos(a); ry[i] = -ey + r * sin(a) }
                else -> { rx[i] = -ex + s; ry[i] = -ey - r }
            }
        }
        // 구동력 가중치: 긴 축 가운데일수록 1, 끝으로 갈수록 0.5
        val longX = w >= hh; val half = maxOf(w, hh) / 2
        for (i in 0 until n) {
            val p = (i - 1 + n) % n; val q = (i + 1) % n
            lap0x[i] = rx[p] + rx[q] - 2 * rx[i]; lap0y[i] = ry[p] + ry[q] - 2 * ry[i]
            val d = abs(if (longX) rx[i] else ry[i]) / half
            mid[i] = 0.5f + 0.5f * cos(d.coerceIn(0f, 1f) * pi / 2)
        }
        var a = 0f
        for (i in 0 until n) { val j = (i + 1) % n; a += rx[i] * ry[j] - rx[j] * ry[i] }
        area0 = abs(a / 2)
    }

    fun target(x: Float, y: Float) { tx = x; ty = y }

    private fun area(): Float {
        var a = 0f
        for (i in 0 until n) { val j = (i + 1) % n; a += px[i] * py[j] - px[j] * py[i] }
        return a / 2
    }

    /** dt초 적분(반암시적 오일러). 쉬는 상태가 되면 false. */
    fun step(dt: Float): Boolean {
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += px[i]; my += py[i] }
        mx /= n; my /= n
        val dx = tx - mx; val dy = ty - my
        val dist = sqrt(dx * dx + dy * dy)
        val ux = if (dist > 1e-3f) dx / dist else 0f; val uy = if (dist > 1e-3f) dy / dist else 0f
        val a = area(); val sign = if (a < 0) -1f else 1f
        val pressure = KP * ((area0 - abs(a)) / area0) * h
        var energy = 0f
        for (i in 0 until n) {
            val p = (i - 1 + n) % n; val q = (i + 1) % n
            // 바깥 법선
            var nx = (py[q] - py[p]); var ny = -(px[q] - px[p])
            val nl = sqrt(nx * nx + ny * ny) + 1e-6f; nx = nx / nl * sign; ny = ny / nl * sign
            val lead = max(0f, nx * ux + ny * uy)
            val w = (LEAD0 + (1 - LEAD0) * lead) * mid[i]
            var fx = KD * w * (tx + rx[i] - px[i]); var fy = KD * w * (ty + ry[i] - py[i])
            fx += KS * (mx + rx[i] - px[i]); fy += KS * (my + ry[i] - py[i])
            fx += KT * ((px[p] + px[q] - 2 * px[i]) - lap0x[i]); fy += KT * ((py[p] + py[q] - 2 * py[i]) - lap0y[i])
            fx += pressure * nx; fy += pressure * ny
            fx -= C * vx[i]; fy -= C * vy[i]
            fx += CV * (vx[p] + vx[q] - 2 * vx[i]); fy += CV * (vy[p] + vy[q] - 2 * vy[i])
            vx[i] += fx * dt; vy[i] += fy * dt
            energy += vx[i] * vx[i] + vy[i] * vy[i]
        }
        for (i in 0 until n) { px[i] += vx[i] * dt; py[i] += vy[i] * dt }
        cx = mx; cy = my
        val moving = energy / n > 0.5f || dist > 0.5f
        if (!moving) {
            // 거의 멈췄으면 정확히 제자리에 붙인다
            for (i in 0 until n) { px[i] = tx + rx[i]; py[i] = ty + ry[i]; vx[i] = 0f; vy[i] = 0f }
            cx = tx; cy = ty
        }
        return moving
    }

    /** 점들을 지나는 매끈한 닫힌 곡선(Catmull–Rom → 3차 베지어). */
    fun path(out: Path = Path()): Path {
        out.reset()
        if (!ready) return out
        out.moveTo(px[0], py[0])
        for (i in 0 until n) {
            val p0 = (i - 1 + n) % n; val p1 = i; val p2 = (i + 1) % n; val p3 = (i + 2) % n
            val c1x = px[p1] + (px[p2] - px[p0]) / 6f; val c1y = py[p1] + (py[p2] - py[p0]) / 6f
            val c2x = px[p2] - (px[p3] - px[p1]) / 6f; val c2y = py[p2] - (py[p3] - py[p1]) / 6f
            out.cubicTo(c1x, c1y, c2x, c2y, px[p2], py[p2])
        }
        out.close()
        return out
    }

    companion object {
        const val N = 72
        // 서버 시뮬레이션으로 맞춘 계수(단위: 1/s², 압력은 높이 배수)
        private const val KD = 240f
        private const val LEAD0 = 0.25f
        private const val KS = 150f
        private const val KT = 3700f
        private const val KP = 135f
        private const val C = 13f
        private const val CV = 55f
    }
}

/**
 * [SoftBlob]을 프레임마다 적분하는 상태. [frame]이 바뀔 때마다 다시 그리면 된다.
 * 목표가 바뀌면 깨어나 움직이고, 쉬는 상태가 되면 프레임 갱신을 멈춘다(배터리).
 */
class SoftBlobState {
    val blob = SoftBlob()
    var frame by mutableLongStateOf(0L)
        internal set
    internal var wake by mutableLongStateOf(0L)
}

@Composable
fun rememberSoftBlob(): SoftBlobState {
    val s = remember { SoftBlobState() }
    // 적분 루프는 하나만 둔다. 끄는 동안처럼 목표가 매 프레임 바뀌어도 루프를 다시 시작하지 않고
    // 돌던 루프가 새 목표를 그대로 따라간다(다시 시작하면 프레임마다 취소돼 방울이 멈춰 있게 된다).
    LaunchedEffect(s) {
        snapshotFlow { s.wake }.collect {
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                // 실제 흐른 시간만큼 적분한다(프레임이 밀려도 물리 시간은 실제와 같게). 한 번에 1/240초 이하로 쪼갠다.
                val dt = if (last == 0L) 1f / 60 else ((now - last) / 1e9f).coerceIn(1f / 480, 1f / 12)
                last = now
                val sub = kotlin.math.ceil(dt * 240f).toInt().coerceAtLeast(1)
                var moving = false
                repeat(sub) { moving = s.blob.step(dt / sub) or moving }
                s.frame++
                if (!moving) break
            }
        }
    }
    return s
}

/** 목표를 바꾸고 적분을 깨운다. 처음이면 그 자리에 놓는다. 크기가 달라졌으면 모양도 바꾼다. */
fun SoftBlobState.moveTo(x: Float, y: Float, w: Float, h: Float, animate: Boolean, corner: Float = -1f) {
    if (!blob.ready || !animate) { blob.reset(x, y, w, h, corner); frame++; return }
    blob.reshape(w, h, corner)
    blob.target(x, y)
    wake++
}
