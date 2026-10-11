package com.labok.goat.data.rotate

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.domain.ShakeRotatePolicy.Beat
import com.labok.goat.domain.ShakeRotatePolicy.Peak

/**
 * 흔든 리듬을 진동으로 재생한다(휙 휘힉 → 톡 토독). [strength]는 사용자가 정한 세기(0=끔, 1~[MAX_STRENGTH]).
 *
 * Android 햅틱 지침과 조사를 따른다:
 * - 짧은 펄스만 쓴다(윙윙거리는 긴 진동 없음). 기기가 진동 프리미티브를 내면 틱·클릭을 쓰고, 못 내면 짧은 펄스로 대신한다.
 * - 프리미티브는 하나라도 지원하지 않으면 전체가 재생되지 않으므로 쓸 것을 모두 확인하고 고른다.
 * - 세기가 높을수록 모든 진동을 또렷한 클릭으로 바꾸고 펄스를 길고 세게 한다(가장 높은 단계는 클릭을 두 번 겹쳐 더 단단하게).
 */
object ShakeHaptics {
    const val MAX_STRENGTH = 10
    const val DEFAULT_STRENGTH = 7

    /** 조절바 예시: 휙 … 휘힉. */
    val SAMPLE = listOf(Peak(0, 18f), Peak(260, 9f), Peak(350, 14f))

    fun play(context: Context, pattern: List<Peak>, strength: Int) {
        if (strength <= 0) return
        val beats = ShakeRotatePolicy.vibrationOf(pattern)
        if (beats.isEmpty()) return
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect(v, beats, strength.coerceIn(1, MAX_STRENGTH))) }
    }

    private fun effect(v: Vibrator, beats: List<Beat>, s: Int): VibrationEffect {
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        // 세기 1~10 → 프리미티브 배율 0.35~1.0. 7 이상이면 모두 클릭, 10이면 클릭을 겹쳐 친다.
        val gain = 0.35f + 0.65f * (s - 1) / (MAX_STRENGTH - 1)
        val allClick = s >= 7
        val double = s >= MAX_STRENGTH
        return if (v.areAllPrimitivesSupported(tick, click)) {
            VibrationEffect.startComposition().apply {
                beats.forEach { b ->
                    val p = if (b.accent || allClick) click else tick
                    addPrimitive(p, (b.scale * gain).coerceIn(0.05f, 1f), b.gap)
                    if (double) addPrimitive(click, (b.scale * gain).coerceIn(0.05f, 1f), 12)
                }
            }.compose()
        } else {
            // 프리미티브가 없으면 짧은 펄스: 세기가 높을수록 길고(12~32ms) 세게. 시작과 끝은 0으로 둔다.
            val pulse = 12L + 20L * (s - 1) / (MAX_STRENGTH - 1)
            val timings = mutableListOf<Long>(); val amps = mutableListOf<Int>()
            beats.forEachIndexed { i, b ->
                timings += if (i == 0) 0L else (b.gap - pulse).coerceAtLeast(30L); amps += 0
                timings += pulse
                amps += if (v.hasAmplitudeControl()) (255 * b.scale * gain).toInt().coerceIn(30, 255) else 255
            }
            VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
        }
    }
}
