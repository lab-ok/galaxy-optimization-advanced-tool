package com.labok.goat.data.lab

import com.labok.goat.data.shizuku.ShizukuAccess
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * 폴드8의 세밀한 힌지 각도. 일반 앱이 받는 힌지 센서는 0°·90°·180° 세 단계뿐이지만, 삼성 센서 부품은 실제 각도를
 * 시스템 로그(`sensors-hal` 태그의 `lid_angle_fusion` 줄, 약 8° 이상에서 1초에 8번쯤)에 남긴다. Shizuku 셸은 로그를 읽을 수 있어
 * 그 줄을 따라 읽어 각도를 꺼낸다(공개 저장소 rmb707/Duos-Public이 SM-F971U1에서 확인한 방법을 참고해 따로 구현).
 * 로그가 안 나오는 기기면 아무 값도 오지 않고, 호출하는 쪽은 기본 힌지 센서 값을 그대로 쓴다.
 */
class FineHinge(private val onAngle: (Float) -> Unit) {
    @Volatile private var process: Process? = null

    @Volatile private var running = false

    @Volatile private var gen = 0

    fun start() {
        if (running) return
        running = true
        val my = ++gen
        thread(name = "fine-hinge", isDaemon = true) {
            // 11-03 사용자 신고(1.45.0, "처음엔 각도가 잘 나오다가 이제 90°·180°만"): 예전에는 시작할 때 Shizuku가 아직 안 붙었거나
            // (업데이트·재부팅 직후) 로그 스트림이 중간에 끊기면 그대로 멈추고 다시 시도하지 않아, 기본 센서(0·90·180)만 남았다.
            // 이제 원하는 동안(running)은 붙을 때까지 3초마다 다시 열고, 끊기면 2초 뒤 다시 연다
            var first = true
            // 멈췄다 곧바로 다시 시작하면 새 줄이 뜨므로, 옛 줄은 세대(gen)가 바뀌면 끝난다
            while (running && gen == my) {
                // -T 1: 지난 로그는 건너뛰고 지금부터. -s: sensors-hal 정보 줄만
                val p = ShizukuAccess.stream("logcat -v brief -T 1 -s sensors-hal:I")
                if (p == null) {
                    if (first) android.util.Log.i("GOAT", "세밀한 힌지 각도: Shizuku 연결을 기다림(3초마다 다시 시도)")
                    first = false
                    sleepWhileRunning(3000)
                    continue
                }
                if (!first) android.util.Log.i("GOAT", "세밀한 힌지 각도: 다시 연결됨")
                first = false
                process = p
                val smooth = Smoother()
                runCatching {
                    p.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (!running || gen != my) break
                            parse(line)?.let { smooth.feed(it) }?.let(onAngle)
                        }
                    }
                }
                runCatching { p.destroy() }
                process = null
                if (running && gen == my) sleepWhileRunning(2000)
            }
        }
    }

    private fun sleepWhileRunning(ms: Long) {
        var left = ms
        while (running && left > 0) {
            Thread.sleep(200)
            left -= 200
        }
    }

    fun stop() {
        running = false
        // Shizuku 연결이 이미 끊겼으면 원격 프로세스의 destroy가 NullPointerException을 던져, 화면이 꺼질 때 앱이 죽었다(10-15 실기기 크래시)
        runCatching { process?.destroy() }
        process = null
    }

    /**
     * 센서 부품이 합쳐 낸 각도는 펼쳐 둬도 176~179°처럼 몇 도씩 흔들린다. 끝에 가까우면 끝으로 붙이고(175° 이상은 180°, 5° 이하는 0°),
     * 1.5° 넘게 바뀔 때만 새 값을 낸다(시험할 수 있게 뗐다). 최근 값들의 가운데 값을 쓰면 빨리 펼친 뒤 로그가 멈출 때
     * 마지막 값이 버려져 163°처럼 중간에 멈춰 보였다. 그래서 들어온 값을 늦추지 않고 그대로 쓴다.
     */
    internal class Smoother {
        private var shown = Float.NaN

        /** 새로 보여 줄 각도, 바뀌지 않았으면 null. */
        fun feed(raw: Float): Float? {
            val v = when {
                raw >= 175f -> 180f
                raw <= 5f -> 0f
                else -> raw
            }
            if (v == shown) return null
            if (!shown.isNaN() && abs(v - shown) < 1.5f && v != 180f && v != 0f) return null
            shown = v
            return v
        }
    }

    companion object {
        private val ANGLE = Regex("""lid_angle_fusion ts=\d+ ns value \[\s*\d+/\s*(-?\d+)/""")

        /** 로그 한 줄에서 각도를 꺼낸다(시험할 수 있게 뗐다). 0~180 밖이면 버린다. */
        fun parse(line: String): Float? = ANGLE.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it in 0f..180f }
    }
}
