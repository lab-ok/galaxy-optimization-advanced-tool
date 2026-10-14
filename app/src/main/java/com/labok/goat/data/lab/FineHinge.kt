package com.labok.goat.data.lab

import com.labok.goat.data.shizuku.ShizukuAccess
import kotlin.concurrent.thread

/**
 * 폴드8의 세밀한 힌지 각도. 일반 앱이 받는 힌지 센서는 0°·90°·180° 세 단계뿐이지만, 삼성 센서 부품은 실제 각도를
 * 시스템 로그(`sensors-hal` 태그의 `lid_angle_fusion` 줄, 약 8° 이상에서 1초에 8번쯤)에 남긴다. Shizuku 셸은 로그를 읽을 수 있어
 * 그 줄을 따라 읽어 각도를 꺼낸다(공개 저장소 rmb707/Duos-Public이 SM-F971U1에서 확인한 방법을 참고해 따로 구현).
 * 로그가 안 나오는 기기면 아무 값도 오지 않고, 호출하는 쪽은 기본 힌지 센서 값을 그대로 쓴다.
 */
class FineHinge(private val onAngle: (Float) -> Unit) {
    @Volatile private var process: Process? = null

    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        thread(name = "fine-hinge", isDaemon = true) {
            // -T 1: 지난 로그는 건너뛰고 지금부터. -s: sensors-hal 정보 줄만
            val p = ShizukuAccess.stream("logcat -v brief -T 1 -s sensors-hal:I") ?: run {
                running = false
                return@thread
            }
            process = p
            val smooth = Smoother()
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        parse(line)?.let { smooth.feed(it) }?.let(onAngle)
                    }
                }
            }
            running = false
        }
    }

    fun stop() {
        running = false
        process?.destroy()
        process = null
    }

    /**
     * 센서 부품이 합쳐 낸 각도는 펼쳐 둬도 176~179°처럼 몇 도씩 흔들린다. 최근 세 값의 가운데 값으로 튀는 값을 버리고,
     * 끝에 가까우면 끝으로 붙이고(175° 이상은 180°, 5° 이하는 0°), 1° 넘게 바뀔 때만 새 값을 낸다(시험할 수 있게 뗐다).
     */
    internal class Smoother {
        private val recent = ArrayDeque<Float>()
        private var shown = Float.NaN

        /** 새로 보여 줄 각도, 바뀌지 않았으면 null. */
        fun feed(raw: Float): Float? {
            recent.addLast(raw)
            if (recent.size > 3) recent.removeFirst()
            val mid = recent.sorted()[recent.size / 2]
            val v = when {
                mid >= 175f -> 180f
                mid <= 5f -> 0f
                else -> mid
            }
            if (!shown.isNaN() && kotlin.math.abs(v - shown) < 1.5f && v != 180f && v != 0f) return null
            if (v == shown) return null
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
