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
    private const val STRONGEST = 22f
    private const val SOFTEST = 2.5f

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
    private const val GYRO_SOFT = 0.5f

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
    const val CALIBRATE_MIN = 2f

    /** 흔들림 하나: 시작 시각(ms), 최고 세기(m/s²), 그 순간 움직인 방향(기기 x·y·z, 중력 뺀 가속도). */
    data class Peak(val at: Long, val force: Float, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
        /** 두 흔들림이 반대쪽으로 움직였으면 true(왕복). */
        fun opposes(o: Peak) = x * o.x + y * o.y + z * o.z < 0f
    }

    private const val G = 9.81f

    /**
     * 가속도 센서 값으로 흔들기를 판정한다.
     *
     * 1. 중력 빼기: 가속도를 저역 통과(α=[GRAVITY_ALPHA], 50Hz에서 약 2Hz 아래)해 중력으로 보고 빼서, 움직임만 남긴다
     *    (Android 공식 문서의 방식). 중력 반대쪽으로 흔든 것도 같은 세기로 잡힌다.
     * 2. 휘두름(스윙) 세기: 움직임 크기가 [threshold]를 넘으면 휘두름이다. 같은 방향이 이어지는 동안은 한 휘두름이고
     *    (최고 세기만 갱신), 방향이 뒤집히면 새 휘두름이다. 그래서 계속 흔들어도 방향이 바뀔 때마다 하나씩 세고,
     *    같은 쪽으로 톡톡 치는 것은 아무리 여러 번이어도 한 번으로 남는다.
     * 3. 시간: 휘두름 사이가 [MIN_GAP_MS]~[MAX_GAP_MS]이고, 첫 휘두름부터 [PEAKS]번째까지 [WINDOW_MS] 안이어야 한다.
     *    한 번 판정하면 [COOLDOWN_MS] 동안 쉰다.
     * 4. 회전: 자이로가 있으면([feedGyro]) 그 사이 손목 회전이 [gyroMin]을 넘어야 한다. 회전 없는 충격·진동은 아니다.
     *
     * 판정한 휘두름들은 [lastPattern](흔든 리듬대로 진동), 모든 휘두름은 [peaks](자동 맞추기)로 남긴다.
     */
    class Detector(var threshold: Float = thresholdOf(DEFAULT_LEVEL), var gyroMin: Float = gyroMinOf(DEFAULT_LEVEL)) {
        private val swings = ArrayDeque<Peak>()
        private var lastShake = Long.MIN_VALUE / 2
        private var gx = 0f
        private var gy = 0f
        private var gz = 0f
        private var started = false
        private var gyroSeen = false
        private var gyroPeak = 0f
        private var gyroAt = Long.MIN_VALUE / 2

        /** 최근 휘두름들(자동 맞추기용, 최대 [MAX_KEPT]개). 진행 중인 휘두름의 최고 세기도 바로 반영된다. */
        val peaks = mutableListOf<Peak>()

        /** 마지막으로 판정한 흔들기의 휘두름들(진동으로 되돌려 줄 때 쓴다). */
        var lastPattern: List<Peak> = emptyList()
            private set

        /** 마지막 값에서 중력을 뺀 가속도(기기 x·y, m/s²). 화면에서 움직임을 그대로 보여 줄 때 쓴다. */
        var linearX = 0f; private set
        var linearY = 0f; private set

        /** 방금 값에서 놓친 이유(로그용): 휘두름은 충분했지만 회전이 없었으면 "회전 없음". 다음 값을 넣으면 지워진다. */
        var lastMiss: String? = null; private set

        /** 자이로 값(rad/s). 최근 [WINDOW_MS] 안의 가장 빠른 회전을 기억한다. */
        fun feedGyro(x: Float, y: Float, z: Float, ms: Long) {
            gyroSeen = true
            val w = sqrt(x * x + y * y + z * z)
            if (w >= gyroPeak || ms - gyroAt > WINDOW_MS) { gyroPeak = w; gyroAt = ms }
        }

        /** 새 가속도 값([ms]는 단조 증가 시각). 흔들기로 판정되면 true. */
        fun feed(x: Float, y: Float, z: Float, ms: Long): Boolean {
            lastMiss = null
            if (!started) { gx = x; gy = y; gz = z; started = true }
            gx = GRAVITY_ALPHA * gx + (1 - GRAVITY_ALPHA) * x
            gy = GRAVITY_ALPHA * gy + (1 - GRAVITY_ALPHA) * y
            gz = GRAVITY_ALPHA * gz + (1 - GRAVITY_ALPHA) * z
            val dx = x - gx; val dy = y - gy; val dz = z - gz
            linearX = dx; linearY = dy
            val force = sqrt(dx * dx + dy * dy + dz * dz)
            if (ms - lastShake < COOLDOWN_MS) return false
            if (force <= threshold) return false

            val last = swings.lastOrNull()
            if (last != null && ms - last.at > MAX_GAP_MS) swings.clear()
            val cur = swings.lastOrNull()
            val sameWay = cur != null && cur.x * dx + cur.y * dy + cur.z * dz > 0f
            if (cur != null && (sameWay || ms - cur.at < MIN_GAP_MS)) {
                // 같은 휘두름: 최고 세기와 그때의 방향만 갱신
                if (force > cur.force) update(cur.copy(force = force, x = dx, y = dy, z = dz))
                return false
            }
            val p = Peak(ms, force, dx, dy, dz)
            swings.addLast(p)
            peaks += p
            if (peaks.size > MAX_KEPT) peaks.removeAt(0)
            while (swings.size > PEAKS || (swings.size > 1 && ms - swings.first().at > WINDOW_MS)) swings.removeFirst()
            if (swings.size < PEAKS) return false
            val turned = !gyroSeen || (gyroPeak >= gyroMin && ms - gyroAt <= WINDOW_MS)
            if (!turned) { lastMiss = "회전 없음(${"%.1f".format(gyroPeak)}rad/s)"; return false }
            lastPattern = swings.toList()
            swings.clear()
            lastShake = ms
            return true
        }

        /** 진행 중인 휘두름 갱신(목록·자동 맞추기·마지막 패턴에도 반영). */
        private fun update(p: Peak) {
            swings[swings.size - 1] = p
            val k = peaks.indexOfLast { it.at == p.at }
            if (k >= 0) peaks[k] = p
            if (lastPattern.lastOrNull()?.at == p.at) lastPattern = lastPattern.dropLast(1) + p
        }

        companion object {
            /** 이만큼 휘둘러야 흔든 것이다(왕복 한 번 반 = 휙 휘힉). */
            const val PEAKS = 3
            /** 첫 휘두름부터 [PEAKS]번째까지 걸릴 수 있는 가장 긴 시간. */
            const val WINDOW_MS = 900L
            /** 휘두름 사이 가장 짧은 간격(같은 휘두름의 떨림을 둘로 세지 않게). */
            const val MIN_GAP_MS = 70L
            /** 휘두름 사이 가장 긴 간격(이보다 뜸하면 처음부터 다시 센다). */
            const val MAX_GAP_MS = 450L
            const val COOLDOWN_MS = 1200L
            /** 중력 추정 저역 통과 계수(Android 문서의 0.8). */
            private const val GRAVITY_ALPHA = 0.8f
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
