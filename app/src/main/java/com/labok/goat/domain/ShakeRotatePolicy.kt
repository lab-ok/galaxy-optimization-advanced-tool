package com.labok.goat.domain

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 흔들어서 화면 회전의 판정 규칙: 민감도 단계, 흔들기 판정, 자동 맞추기, 흔든 리듬을 진동으로 바꾸기.
 * 흔든 순간 어느 방향으로 돌릴지는 시스템 자동 회전이 정한다([com.labok.goat.data.rotate.ShakeEngine]).
 */
object ShakeRotatePolicy {
    /** 민감도 단계: 1(세게 흔들어야 함, 둔하게) ~ [MAX_LEVEL](살짝만 흔들어도 됨, 예민하게). */
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 30
    /** 처음 값: 손목으로 가볍게 휙휙 흔들면 돌아가는 정도. */
    const val DEFAULT_LEVEL = 17
    private const val STRONGEST = 18f
    private const val SOFTEST = 1.2f

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
    private const val GYRO_SOFT = 0.3f

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

    /**
     * 자동 맞추기: 사용자가 흔든 휘두름 사이 간격의 가운데 값(ms)으로 그 사람의 흔들기 리듬을 구한다. 휘두름이 세 번보다 적으면 null.
     * 판정은 이 리듬의 [RHYTHM_SLACK]배까지만 기다려(최소 [MIN_RHYTHM_GAP_MS]), 다른 움직임이 흔들기로 잡히는 일을 줄인다.
     */
    fun rhythmOf(peaks: List<Peak>): Long? {
        val gaps = peaks.zipWithNext { a, b -> b.at - a.at }.filter { it in Detector.MIN_GAP_MS..Detector.MAX_GAP_MS }.sorted()
        if (gaps.size < 2) return null
        return gaps[gaps.size / 2]
    }

    /** 리듬 → 휘두름 사이 가장 긴 간격. */
    fun maxGapFor(rhythm: Long?): Long =
        rhythm?.let { (it * RHYTHM_SLACK).toLong().coerceIn(MIN_RHYTHM_GAP_MS, Detector.MAX_GAP_MS) } ?: Detector.MAX_GAP_MS

    private const val RHYTHM_SLACK = 2.2f
    private const val MIN_RHYTHM_GAP_MS = 300L

    /** 손목 회전 기준 단계(0=보지 않음 ~ [MAX_TURN]) ↔ 최소 회전 속도(rad/s). */
    const val MAX_TURN = 10
    fun gyroOfTurn(turn: Int): Float = turn.coerceIn(0, MAX_TURN) * 0.3f

    /** 자동 맞추기: 휘두를 때 손목이 돈 빠르기의 가운데 값의 절반을 기준으로 삼는다. 자이로가 없거나 모르면 null. */
    fun calibrateTurn(peaks: List<Peak>): Int? {
        val g = peaks.map { it.gyro }.filter { it > 0f }.sorted()
        if (g.size < 2) return null
        return Math.round(g[g.size / 2] * 0.5f / 0.3f).coerceIn(1, MAX_TURN)
    }

    /** 빠르기 단계(1=천천히 ~ [MAX_PACE]=빠르게) ↔ 휘두름 사이 가장 긴 간격(ms). */
    const val MAX_PACE = 10
    fun gapOfPace(pace: Int): Long = Detector.MAX_GAP_MS - (pace.coerceIn(1, MAX_PACE) - 1) * (Detector.MAX_GAP_MS - 250L) / (MAX_PACE - 1)
    fun paceOfGap(gap: Long): Int =
        (1 + Math.round((Detector.MAX_GAP_MS - gap).toFloat() * (MAX_PACE - 1) / (Detector.MAX_GAP_MS - 250L))).coerceIn(1, MAX_PACE)

    /** 자동 맞추기에서 흔들림으로 셀 최소 세기. */
    const val CALIBRATE_MIN = 1f

    /** 큰 화면으로 볼 짧은 변(dp). 안드로이드의 태블릿 기준(sw600dp)과 같다. 폴드 외부 화면은 약 400dp, 내부 화면은 약 700dp. */
    const val LARGE_DP = 600

    /** 짧은 변 [shortPx]·밀도 [densityDpi]인 화면이 큰 화면인지. */
    fun isLarge(shortPx: Int, densityDpi: Int): Boolean = densityDpi > 0 && shortPx * 160 / densityDpi >= LARGE_DP

    /**
     * 휘두름 하나. [at] 시작 시각(ms), [force] 최고 세기(m/s²), [x]·[y]·[z] 그때 움직인 방향(중력 뺀 가속도),
     * [dur] 기준을 넘은 채 이어진 시간(ms, 한 샘플이면 0), [jerk] 가장 가파른 세기 변화(m/s² / 샘플), [gyro] 그동안 가장 빠른 손목 회전(rad/s).
     */
    data class Peak(
        val at: Long, val force: Float,
        val x: Float = 0f, val y: Float = 0f, val z: Float = 0f,
        val dur: Long = 40, val jerk: Float = 0f, val gyro: Float = 0f,
    ) {
        /** 두 휘두름이 반대쪽으로 움직였으면 true(왕복). */
        fun opposes(o: Peak) = x * o.x + y * o.y + z * o.z < 0f
    }

    private const val G = 9.81f

    /**
     * 가속도 센서 값으로 흔들기를 판정한다.
     *
     * 1. 중력 빼기: 가속도를 저역 통과(α=[GRAVITY_ALPHA], 50Hz에서 약 0.9Hz 아래)해 중력으로 보고 빼서, 움직임만 남긴다
     *    (Android 공식 문서의 방식). 중력 반대쪽으로 흔든 것도 같은 세기로 잡힌다.
     * 2. 휘두름 세기: 움직임 크기가 [threshold]를 넘으면 휘두름이다. 같은 방향이 이어지는 동안은 한 휘두름이고
     *    (최고 세기·길이·날카로움·손목 회전을 기록), 방향이 뒤집히면 새 휘두름이다. 계속 흔들어도 방향이 바뀔 때마다 하나씩 센다.
     * 3. 탭·충격 거르기: 기준을 넘은 시간이 한 샘플뿐([TAP_MAX_MS] 이하)이고 그동안 손목 회전도 없으면 톡 친 것·부딪힌 것으로 보고
     *    세지 않는다(RIM 탭 특허의 '짧은 스파이크', Motorola twist의 자이로 확인과 같은 생각). 같은 쪽으로 톡톡 치는 것은 방향이
     *    안 바뀌어 애초에 한 번으로 남고, 바닥에 부딪혀 튕기는 왕복 충격도 이 규칙으로 걸러진다.
     * 4. 떨어뜨림: 가속도 크기가 [FREE_FALL] 아래로 [FREE_FALL_SAMPLES]번 이어지면(자유 낙하) [FALL_QUIET_MS] 동안 판정하지 않는다.
     * 5. 시간: 휘두름 사이가 [MIN_GAP_MS]~[MAX_GAP_MS]이고, 첫 휘두름부터 [PEAKS]번째까지 [WINDOW_MS] 안이어야 한다.
     *    한 번 판정하면 [COOLDOWN_MS] 동안 쉰다.
     * 6. 회전: 자이로가 있으면 그 사이 손목 회전이 [gyroMin]을 넘거나, 팔로 크게 흔들어 세 번 모두 기준의 [STRONG]배를 넘어야 한다.
     *    회전 없는 약한 왕복 진동(차량 등)은 아니다.
     *
     * 판정한 휘두름들은 [lastPattern](흔든 리듬대로 진동), 모든 휘두름은 [peaks](자동 맞추기)로 남긴다.
     */
    class Detector(
        var threshold: Float = thresholdOf(DEFAULT_LEVEL),
        var gyroMin: Float = gyroMinOf(DEFAULT_LEVEL),
        /** 휘두름 사이 가장 긴 간격. 자동 맞추기로 그 사람의 리듬을 알면 거기에 맞춰 줄인다([maxGapFor]). */
        var maxGap: Long = MAX_GAP_MS,
        /** 흔든 것으로 칠 휘두름 수(2~4). */
        var needed: Int = PEAKS,
        /** 한 샘플짜리 탭·충격을 휘두름으로 세지 않는다. */
        var rejectTaps: Boolean = true,
    ) {
        private val swings = ArrayDeque<Peak>()
        private var lastShake = Long.MIN_VALUE / 2
        private var gx = 0f
        private var gy = 0f
        private var gz = 0f
        private var started = false
        private var lastForce = 0f
        private var lastAbove = Long.MIN_VALUE / 2
        private var falling = 0
        private var quietUntil = Long.MIN_VALUE / 2
        private var gyroSeen = false
        private var gyroPeak = 0f
        private var gyroAt = Long.MIN_VALUE / 2
        private var gyroNow = 0f

        /** 최근 휘두름들(자동 맞추기용, 최대 [MAX_KEPT]개). 진행 중인 휘두름도 바로 반영된다. */
        val peaks = mutableListOf<Peak>()

        /** 마지막으로 판정한 흔들기의 휘두름들(진동으로 되돌려 줄 때 쓴다). */
        var lastPattern: List<Peak> = emptyList()
            private set

        /** 마지막 값에서 중력을 뺀 가속도(기기 x·y, m/s²). 화면에서 움직임을 그대로 보여 줄 때 쓴다. */
        var linearX = 0f; private set
        var linearY = 0f; private set

        /** 방금 값에서 놓친 이유(로그용). 다음 값을 넣으면 지워진다. */
        var lastMiss: String? = null; private set

        /** 자이로 값(rad/s). 지금 회전 속도와 최근 [WINDOW_MS] 안의 가장 빠른 회전을 기억한다. */
        fun feedGyro(x: Float, y: Float, z: Float, ms: Long) {
            gyroSeen = true
            val w = sqrt(x * x + y * y + z * z)
            gyroNow = w
            if (w >= gyroPeak || ms - gyroAt > WINDOW_MS) { gyroPeak = w; gyroAt = ms }
        }

        /** 새 가속도 값([ms]는 단조 증가 시각). 흔들기로 판정되면 true. */
        fun feed(x: Float, y: Float, z: Float, ms: Long): Boolean {
            lastMiss = null
            if (!started) { gx = x; gy = y; gz = z; started = true }
            // 떨어뜨림(자유 낙하): 잠시 판정을 멈춘다
            if (sqrt(x * x + y * y + z * z) < FREE_FALL) {
                if (++falling >= FREE_FALL_SAMPLES) { quietUntil = ms + FALL_QUIET_MS; swings.clear() }
            } else falling = 0
            gx = GRAVITY_ALPHA * gx + (1 - GRAVITY_ALPHA) * x
            gy = GRAVITY_ALPHA * gy + (1 - GRAVITY_ALPHA) * y
            gz = GRAVITY_ALPHA * gz + (1 - GRAVITY_ALPHA) * z
            val dx = x - gx; val dy = y - gy; val dz = z - gz
            linearX = dx; linearY = dy
            val force = sqrt(dx * dx + dy * dy + dz * dz)
            val jerk = abs(force - lastForce)
            lastForce = force
            if (ms < quietUntil) { if (force > threshold) lastMiss = "떨어뜨림 직후"; return false }
            if (ms - lastShake < COOLDOWN_MS) return false
            if (force <= threshold) return false

            // 오래 멈췄으면 처음부터 다시 센다
            if (swings.isNotEmpty() && ms - lastAbove > maxGap) swings.clear()
            val open = swings.lastOrNull()
            val sameWay = open != null && open.x * dx + open.y * dy + open.z * dz > 0f
            if (open != null && (sameWay || ms - open.at < MIN_GAP_MS)) {
                // 같은 휘두름이 이어진다: 길이·날카로움·회전·최고 세기를 갱신
                lastAbove = ms
                val grown = open.copy(
                    dur = ms - open.at, jerk = maxOf(open.jerk, jerk), gyro = maxOf(open.gyro, gyroNow),
                ).let { if (force > it.force) it.copy(force = force, x = dx, y = dy, z = dz) else it }
                update(grown)
                return tryShake(ms)
            }
            // 방향이 뒤집혔다: 앞 휘두름이 탭·충격이었으면 빼고 새 휘두름을 연다
            if (open != null && isTap(open)) { swings.removeLast(); lastMiss = "탭·충격으로 봄" }
            // 휘두름 시작 사이가 그 사람의 리듬보다 훨씬 길면(느린 왕복) 처음부터 다시 센다
            swings.lastOrNull()?.let { if (ms - it.at > maxGap) swings.clear() }
            lastAbove = ms
            val p = Peak(ms, force, dx, dy, dz, dur = 0, jerk = jerk, gyro = gyroNow)
            swings.addLast(p)
            peaks += p
            if (peaks.size > MAX_KEPT) peaks.removeAt(0)
            while (swings.size > needed || (swings.size > 1 && ms - swings.first().at > WINDOW_MS)) swings.removeFirst()
            return tryShake(ms)
        }

        /** 기준을 넘은 시간이 한 샘플 정도로 짧고 손목 회전도 없으면 톡 친 것·부딪힌 것이다. */
        private fun isTap(p: Peak) = rejectTaps && p.dur <= TAP_MAX_MS && (!gyroSeen || p.gyro < gyroMin)

        /** 마지막 휘두름까지 [PEAKS]번이 모였고, 마지막 것도 탭이 아님이 확인되면(두 샘플 이상 이어졌거나 손목이 돌았으면) 흔들기다. */
        private fun tryShake(ms: Long): Boolean {
            if (swings.size < needed) return false
            val last = swings.last()
            if (isTap(last)) return false  // 아직 한 샘플뿐: 다음 값까지 기다린다
            // 손목이 돌았거나, 팔로 크게 흔들어 세 번 모두 기준의 [STRONG]배를 넘었으면 흔들기다(차량 진동처럼 약한 왕복은 아니다)
            val turned = !gyroSeen || gyroMin <= 0f || (gyroPeak >= gyroMin && ms - gyroAt <= WINDOW_MS) || swings.all { it.force >= threshold * STRONG }
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
            const val WINDOW_MS = 1400L
            /** 휘두름 사이 가장 짧은 간격(같은 휘두름의 떨림을 둘로 세지 않게). */
            const val MIN_GAP_MS = 70L
            /** 휘두름 사이 가장 긴 간격(이보다 뜸하면 처음부터 다시 센다). 천천히 크게 흔드는 것(약 1Hz)까지 받는다. */
            const val MAX_GAP_MS = 650L
            const val COOLDOWN_MS = 1200L
            /** 손목 회전 없이도 흔들기로 칠 세기 배수. */
            const val STRONG = 1.4f
            /** 기준을 넘은 시간이 이 이하면 탭·충격 후보(50Hz에서 한 샘플). */
            const val TAP_MAX_MS = 15L
            /** 자유 낙하로 볼 가속도 크기(m/s²)와 연속 샘플 수, 그 뒤 쉬는 시간. */
            const val FREE_FALL = 3f
            const val FREE_FALL_SAMPLES = 3
            const val FALL_QUIET_MS = 1500L
            /** 중력 추정 저역 통과 계수. 0.9면 50Hz에서 약 0.9Hz 아래를 중력으로 본다(천천히 흔드는 것도 움직임으로 남는다). */
            private const val GRAVITY_ALPHA = 0.9f
            private const val MAX_KEPT = 32
        }
    }

    /** 진동 한 번: 앞 진동과의 간격(ms), 세기(0.5·0.7·1.0 세 단계), 가장 센 휘두름이면 [accent](또렷한 클릭). */
    data class Beat(val gap: Int, val scale: Float, val accent: Boolean)

    /**
     * 흔든 대로 진동(휙 휘힉 → 톡 토독). Android 햅틱 지침과 조사를 따른다:
     * - 간격: 흔든 간격을 그대로 두되, 진동이 하나하나 구분되도록 [MIN_GAP_MS] 이상, 늘어지지 않게 [MAX_GAP_MS] 이하.
     * - 세기: 사람은 진동 세기를 로그로 느끼므로, 가장 센 휘두름 대비 비율을 로그 곡선에 얹고 체감되는 세 단계(0.5·0.7·1.0)로 나눈다.
     * - 종류: 가장 센 휘두름 하나만 또렷한 클릭([Beat.accent]), 나머지는 짧은 틱. 모두 짧은 펄스라 윙윙거리지 않는다.
     */
    fun vibrationOf(pattern: List<Peak>): List<Beat> {
        if (pattern.isEmpty()) return emptyList()
        val top = pattern.maxOf { it.force }.coerceAtLeast(0.01f)
        val strongest = pattern.indexOfFirst { it.force == top }
        return pattern.mapIndexed { i, p ->
            val gap = if (i == 0) 0 else (p.at - pattern[i - 1].at).toInt().coerceIn(MIN_GAP_MS, MAX_GAP_MS)
            val r = (p.force / top).coerceIn(0f, 1f)
            val curve = ln(1f + r * (E - 1f))  // 0..1, 로그 곡선
            val scale = when {
                curve > 0.85f -> 1f
                curve > 0.6f -> 0.7f
                else -> 0.5f
            }
            Beat(gap, scale, accent = i == strongest)
        }
    }

    /** 진동 사이 가장 짧은 간격: 이보다 붙으면 두 진동이 하나로 뭉쳐 느껴진다(Android 햅틱 지침 50ms). */
    const val MIN_GAP_MS = 50
    const val MAX_GAP_MS = 400
    private const val E = 2.7182817f
}
