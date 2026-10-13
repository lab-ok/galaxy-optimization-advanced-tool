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
 * - 1~6은 틱·클릭 프리미티브로 치고(높을수록 모두 클릭), 7~10은 프리미티브(클릭 최대 약 12ms)보다 센 모터 최대 진폭 펄스로 친다.
 *   펄스 길이를 7=16ms → 8=22ms → 9=30ms → 10=45ms로 늘려, 8에서 9로 갑자기 세지던 계단을 없앴다(1.4.5까지는 8이 짧은 클릭이라 약했다).
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
        // 세기 1~6 → 프리미티브 배율 0.35~1.0. 5 이상이면 모두 클릭, 6은 클릭을 겹쳐 친다.
        val gain = 0.35f + 0.65f * (s - 1) / (MAX_POWER_FROM - 2)
        val allClick = s >= 5
        val double = s >= MAX_POWER_FROM - 1
        // 7~10은 모터 최대 출력 펄스로 친다(진폭 최대, 길이로 세기를 나눈다)
        if (s >= MAX_POWER_FROM) return powerPulses(v, beats, POWER_PULSE_MS[s - MAX_POWER_FROM])
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
            val timings = mutableListOf<Long>()
            val amps = mutableListOf<Int>()
            beats.forEachIndexed { i, b ->
                timings += if (i == 0) 0L else (b.gap - pulse).coerceAtLeast(30L)
                amps += 0
                timings += pulse
                amps += if (v.hasAmplitudeControl()) (255 * b.scale * gain.coerceAtMost(1f)).toInt().coerceIn(30, 255) else 255
            }
            VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
        }
    }

    /** 모터를 최대 진폭으로 [pulse]ms씩 흔든 리듬대로 친다(가장 센 진동). 약한 휘두름도 조금만 약하게(세 단계 중 최소 80%). */
    private fun powerPulses(v: Vibrator, beats: List<Beat>, pulse: Long): VibrationEffect {
        val timings = mutableListOf<Long>()
        val amps = mutableListOf<Int>()
        beats.forEachIndexed { i, b ->
            timings += if (i == 0) 0L else (b.gap - pulse).coerceAtLeast(30L)
            amps += 0
            timings += pulse
            amps += if (v.hasAmplitudeControl()) (255 * (0.8f + 0.2f * b.scale)).toInt().coerceAtMost(255) else 255
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
    }

    /** 이 단계부터 모터 최대 출력 펄스를 쓴다. */
    private const val MAX_POWER_FROM = 7

    /** 7·8·9·10단계의 펄스 길이(ms). */
    private val POWER_PULSE_MS = longArrayOf(16L, 22L, 30L, 45L)
}
