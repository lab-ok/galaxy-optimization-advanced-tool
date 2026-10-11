package com.labok.goat.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 흔들어 화면 회전: 평소에는 회전을 잠가 두고, 흔든 순간 폰이 놓인 방향으로 돌린 뒤 그 방향에 다시 잠근다.
 * 회전 값은 Android Surface.ROTATION_0..3(0=세로, 1=왼쪽으로 눕힌 가로, 2=거꾸로, 3=오른쪽으로 눕힌 가로).
 */
object ShakeRotatePolicy {
    /** 흔들기 민감도. [threshold]는 중력을 뺀 가속도 크기(m/s²)가 넘어야 하는 값. */
    enum class Sensitivity(val label: String, val threshold: Float) {
        LOW("약하게", 17f),
        NORMAL("보통", 13f),
        HIGH("강하게", 10f),
    }

    private const val G = 9.81f

    /** 이보다 기울어지지 않은(바닥에 눕힌) 상태면 방향을 정하지 않는다. 가로·세로 성분의 크기(m/s²). */
    private const val MIN_TILT = 4f

    /**
     * 중력 방향(기기 좌표 x·y)으로 폰이 놓인 방향을 구한다. 폰을 바닥에 평평하게 눕혀 방향을 알 수 없으면 null.
     * 기기 위쪽이 하늘을 보면 y가 +, 왼쪽으로 눕히면(위쪽이 왼쪽) x가 +.
     */
    fun orientationOf(x: Float, y: Float): Int? {
        if (hypot(x, y) < MIN_TILT) return null
        return if (abs(y) >= abs(x)) (if (y > 0) 0 else 2) else (if (x > 0) 1 else 3)
    }

    /**
     * 흔든 뒤 잠글 방향. 폰이 놓인 방향이 지금 화면과 다르면 그 방향으로 돌린다.
     * 같으면(누워서 보느라 중력과 눈의 방향이 다를 때) 세로와 가로를 맞바꾼다.
     */
    fun next(current: Int, held: Int?): Int = when {
        held != null && held != current -> held
        current == 0 -> held?.takeIf { it != 0 } ?: 1
        else -> 0
    }

    /**
     * 가속도 센서 값으로 흔들기를 판정한다. 중력을 뺀 크기가 [Sensitivity.threshold]를 넘는 순간을 하나의 '흔들림'으로 세고,
     * [WINDOW_MS] 안에 [PEAKS]번 넘으면 흔든 것으로 본다. 한 번 판정하면 [COOLDOWN_MS] 동안 쉰다.
     * 흔들기 직전에 가만히 있던 때의 중력 방향을 [heldBefore]로 남겨, 흔드는 동안의 출렁임이 방향 판단을 흐리지 않게 한다.
     */
    class Detector(var sensitivity: Sensitivity = Sensitivity.NORMAL) {
        private val peaks = ArrayDeque<Long>()
        private var lastPeak = Long.MIN_VALUE / 2
        private var lastShake = Long.MIN_VALUE / 2
        private var gx = 0f
        private var gy = G

        /** 마지막으로 가만히 있을 때 폰이 놓인 방향. */
        val heldBefore: Int? get() = orientationOf(gx, gy)

        /** 새 센서 값([ms]는 단조 증가 시각). 흔들기로 판정되면 true. */
        fun feed(x: Float, y: Float, z: Float, ms: Long): Boolean {
            val force = abs(sqrt(x * x + y * y + z * z) - G)
            if (force < CALM) {
                // 가만히 있을 때만 중력 방향을 천천히 따라간다
                gx += (x - gx) * 0.2f
                gy += (y - gy) * 0.2f
            }
            if (ms - lastShake < COOLDOWN_MS) return false
            if (force > sensitivity.threshold && ms - lastPeak > PEAK_GAP_MS) {
                lastPeak = ms
                peaks.addLast(ms)
            }
            while (peaks.isNotEmpty() && ms - peaks.first() > WINDOW_MS) peaks.removeFirst()
            if (peaks.size >= PEAKS) {
                peaks.clear()
                lastShake = ms
                return true
            }
            return false
        }

        companion object {
            const val PEAKS = 3
            const val WINDOW_MS = 800L
            const val PEAK_GAP_MS = 90L
            const val COOLDOWN_MS = 1500L
            private const val CALM = 1.2f
        }
    }
}
