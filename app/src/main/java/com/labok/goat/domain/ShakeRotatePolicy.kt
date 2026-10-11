package com.labok.goat.domain

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 흔들어서 화면 회전의 판정 규칙: 민감도 단계, 흔들기 판정, 자동 맞추기, 흔든 리듬을 진동으로 바꾸기.
 * 흔든 순간 어느 방향으로 돌릴지는 시스템 자동 회전이 정한다([com.labok.goat.data.rotate.ShakeEngine]).
 */
object ShakeRotatePolicy {
    /** 민감도 단계: 1(세게 흔들어야 함, 둔하게) ~ [MAX_LEVEL](살짝만 흔들어도 됨, 예민하게). */
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 20
    /** 처음 값: 손목으로 가볍게 휙휙 흔들면 돌아가는 정도. */
    const val DEFAULT_LEVEL = 14
    private const val STRONGEST = 25f
    private const val SOFTEST = 5f

    /** 단계 → 흔들림 한 번으로 칠 세기(중력을 뺀 가속도, m/s²). */
    fun thresholdOf(level: Int): Float {
        val l = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return STRONGEST - (l - MIN_LEVEL) * (STRONGEST - SOFTEST) / (MAX_LEVEL - MIN_LEVEL)
    }

    /**
     * 단계 → 흔드는 동안 있어야 할 최소 회전 속도(rad/s). 손목으로 흔들면 폰이 함께 돌아가지만, 바닥에 톡 치거나
     * 부딪힌 충격·주머니 속 덜컹임은 회전이 거의 없다(자이로로 거른다, Motorola 'twist' 제스처와 같은 생각).
     */
    fun gyroMinOf(level: Int): Float {
        val l = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return GYRO_STRONG - (l - MIN_LEVEL) * (GYRO_STRONG - GYRO_SOFT) / (MAX_LEVEL - MIN_LEVEL)
    }
    private const val GYRO_STRONG = 2.4f
    private const val GYRO_SOFT = 0.8f

    /** 세기(m/s²) → 가장 가까운 단계. */
    fun levelOf(threshold: Float): Int {
        val raw = MIN_LEVEL + (STRONGEST - threshold) * (MAX_LEVEL - MIN_LEVEL) / (STRONGEST - SOFTEST)
        return Math.round(raw).coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /**
     * 자동 맞추기: 사용자가 '돌릴 때처럼' 흔든 흔들림들의 최고 세기([peaks])로 단계를 정한다.
     * 가장 센 세 번 중 가운데 값의 60%를 기준으로 삼아, 그보다 조금 약하게 흔들어도 돌아가게 한다.
     * 흔들림이 두 번보다 적으면 null(다시 흔들어 달라고 한다).
     */
    fun calibrate(peaks: List<Float>): Int? {
        val strong = peaks.filter { it >= CALIBRATE_MIN }.sortedDescending().take(3)
        if (strong.size < 2) return null
        val middle = strong[strong.size / 2]
        return levelOf(middle * 0.6f)
    }

    /** 자동 맞추기에서 흔들림으로 셀 최소 세기. */
    const val CALIBRATE_MIN = 4f

    /** 흔들림 하나: 시작 시각(ms), 최고 세기(m/s²), 그 순간 움직인 방향(기기 x·y·z, 중력 뺀 가속도). */
    data class Peak(val at: Long, val force: Float, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
        /** 두 흔들림이 반대쪽으로 움직였으면 true(왕복). */
        fun opposes(o: Peak) = x * o.x + y * o.y + z * o.z < 0f
    }

    private const val G = 9.81f

    /**
     * 가속도 센서 값으로 흔들기를 판정한다. 가만히 있을 때 잰 중력 벡터를 빼서(중력 반대쪽으로 흔든 것도 같은 세기로 센다) 그 크기가 [threshold]를 넘었다가 내려오는 것을 흔들림 하나로 세고
     * (그 사이 최고 세기를 기록한다), [WINDOW_MS] 안에 [PEAKS]번이면 흔든 것으로 본다. 한 번 판정하면 [COOLDOWN_MS] 동안 쉰다.
     * 판정한 흔들림들은 [lastPattern]으로 남긴다(흔든 리듬대로 진동할 때 쓴다).
     *
     * 오작동을 줄이려고 두 가지를 더 본다(Square seismic·RIM 탭 특허·Motorola twist 참고):
     * - 왕복: 흔들림 중 적어도 한 쌍은 서로 반대쪽으로 움직여야 한다. 같은 쪽으로 톡톡 치는 것(뒷면 두드리기)은 아니다.
     * - 회전: 자이로가 있으면([feedGyro]) 그 사이 회전 속도가 [gyroMin]을 넘어야 한다. 회전 없는 충격은 아니다.
     * [threshold]를 [CALIBRATE_MIN]으로 두면 자동 맞추기용으로 흔들림만 모은다([peaks]).
     */
    class Detector(var threshold: Float = thresholdOf(DEFAULT_LEVEL), var gyroMin: Float = gyroMinOf(DEFAULT_LEVEL)) {
        private var gyroSeen = false
        private var gyroPeak = 0f
        private var gyroAt = Long.MIN_VALUE / 2

        /** 자이로 값(rad/s). 최근 [WINDOW_MS] 안의 가장 빠른 회전을 기억한다. */
        fun feedGyro(x: Float, y: Float, z: Float, ms: Long) {
            gyroSeen = true
            val w = sqrt(x * x + y * y + z * z)
            if (w >= gyroPeak || ms - gyroAt > WINDOW_MS) { gyroPeak = w; gyroAt = ms }
        }

        private val recent = ArrayDeque<Peak>()
        private var active: Peak? = null
        private var lastEnd = Long.MIN_VALUE / 2
        private var lastShake = Long.MIN_VALUE / 2
        private var gx = 0f
        private var gy = G
        private var gz = 0f

        /** 최근에 끝난 흔들림들(자동 맞추기용, 최대 [MAX_KEPT]개). */
        val peaks = mutableListOf<Peak>()

        /** 마지막으로 판정한 흔들기의 흔들림들(진동으로 되돌려 줄 때 쓴다). */
        var lastPattern: List<Peak> = emptyList()
            private set

        /** 마지막 값에서 중력을 뺀 가속도(기기 x·y, m/s²). 화면에서 움직임을 그대로 보여 줄 때 쓴다. */
        var linearX = 0f; private set
        var linearY = 0f; private set

        /** 새 센서 값([ms]는 단조 증가 시각). 흔들기로 판정되면 true. */
        fun feed(x: Float, y: Float, z: Float, ms: Long): Boolean {
            val calm = abs(sqrt(x * x + y * y + z * z) - G) < CALM
            if (calm) {
                // 가만히 있을 때만 중력 방향을 천천히 따라간다
                gx += (x - gx) * 0.2f
                gy += (y - gy) * 0.2f
                gz += (z - gz) * 0.2f
            }
            val dx = x - gx; val dy = y - gy; val dz = z - gz
            linearX = dx; linearY = dy
            val force = sqrt(dx * dx + dy * dy + dz * dz)
            val a = active
            if (a != null) {
                if (force > a.force) active = a.copy(force = force, x = dx, y = dy, z = dz)
                if (force < threshold * RELEASE) {
                    val done = active!!
                    peaks += done
                    if (peaks.size > MAX_KEPT) peaks.removeAt(0)
                    // 판정 순간 아직 진행 중이던 마지막 흔들림의 최고 세기를 패턴에도 반영한다
                    if (lastPattern.lastOrNull()?.at == done.at) lastPattern = lastPattern.dropLast(1) + done
                    active = null
                    lastEnd = ms
                }
            } else if (force > threshold && ms - lastEnd > PEAK_GAP_MS && ms - lastShake >= COOLDOWN_MS) {
                active = Peak(ms, force, dx, dy, dz)
                recent.addLast(active!!)
            }
            // 진행 중인 흔들림의 최고 세기를 목록에도 반영한다
            active?.let { p -> if (recent.isNotEmpty() && recent.last().at == p.at) recent[recent.size - 1] = p }
            while (recent.isNotEmpty() && ms - recent.first().at > WINDOW_MS) recent.removeFirst()
            if (ms - lastShake >= COOLDOWN_MS && recent.size >= PEAKS && looksLikeShake(ms)) {
                lastPattern = recent.toList()
                recent.clear()
                lastShake = ms
                return true
            }
            return false
        }

        private fun looksLikeShake(ms: Long): Boolean {
            val backAndForth = recent.zipWithNext().any { (p, q) -> p.opposes(q) }
            val turned = !gyroSeen || (gyroPeak >= gyroMin && ms - gyroAt <= WINDOW_MS)
            return backAndForth && turned
        }

        companion object {
            const val PEAKS = 3
            const val WINDOW_MS = 900L
            const val PEAK_GAP_MS = 60L
            const val COOLDOWN_MS = 1500L
            private const val CALM = 1.2f
            /** 세기가 기준의 이 비율 아래로 내려오면 흔들림 하나가 끝난 것으로 본다. */
            private const val RELEASE = 0.5f
            /** [peaks]에 남겨 둘 최대 개수(서비스가 오래 돌아도 쌓이지 않게). */
            private const val MAX_KEPT = 32
        }
    }

    /**
     * 흔든 대로 진동: 흔들림 사이 간격을 그대로 두고, 세기는 가장 센 흔들림을 1로 맞춘 비율(0.35~1)로 바꾼다.
     * 결과는 (앞 진동과의 간격 ms, 세기) 목록. 첫 진동의 간격은 0. 간격은 [MAX_GAP_MS]를 넘지 않게 한다.
     */
    fun vibrationOf(pattern: List<Peak>): List<Pair<Int, Float>> {
        if (pattern.isEmpty()) return emptyList()
        val top = pattern.maxOf { it.force }
        return pattern.mapIndexed { i, p ->
            val gap = if (i == 0) 0 else (p.at - pattern[i - 1].at).toInt().coerceIn(MIN_GAP_MS, MAX_GAP_MS)
            gap to (0.35f + 0.65f * (p.force / top)).coerceIn(0.35f, 1f)
        }
    }

    const val MIN_GAP_MS = 40
    const val MAX_GAP_MS = 400
}
