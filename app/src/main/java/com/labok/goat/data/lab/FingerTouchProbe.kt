package com.labok.goat.data.lab

import android.os.Build
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 실험실: 지문 센서(측면 전원 버튼)에 손가락이 닿기만 해도(인증 없이) 시스템이 알아채는지 조사한다(11-13 소장님:
 * 예전 지문 센서 스와이프처럼, 닿아 있을 때만 자동 회전). [SECONDS]초 동안 모든 입력 장치 이벤트를 받아 장치별로 세고,
 * 같은 동안의 지문 관련 시스템 로그와 센서 정보를 함께 적는다. 화면 터치 장치는 개수만 적는다. Shizuku 셸로만 읽는다.
 */
object FingerTouchProbe {
    const val SECONDS = 12

    private val FP_LOG = Regex("(?i)finger|fpsensor|fp_|egis|goodix|qfp|biometric|sidefps|sfps")
    internal val EVENT = Regex("""^\[\s*[\d.]+]\s+(/dev/input/event\d+):\s+(\S+)\s+(\S+)\s+(\S+)""")
    private val DEVICE = Regex("""add device \d+: (/dev/input/event\d+)""")
    private val NAME = Regex("""name:\s+"([^"]*)"""")

    /** 작업 스레드에서 부른다. 시작하면 바로 [SECONDS]초 동안 기록한다. */
    fun run(): String = buildString {
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        val devices = ShizukuAccess.shell("getevent -pl") ?: return append("Shizuku 연결이 없어 조사하지 못했습니다.").toString()
        val names = deviceNames(devices.out)
        appendLine("입력 장치 ${names.size}개: " + names.entries.joinToString(" · ") { "${it.key.removePrefix("/dev/input/")}=${it.value}" })
        val start = ShizukuAccess.shell("date +%m-%d\\ %H:%M:%S.000")?.out?.trim().orEmpty()
        val events = ShizukuAccess.shell("timeout $SECONDS getevent -lt 2>&1")?.out.orEmpty()
        appendLine()
        appendLine("== ${SECONDS}초 동안 장치별 이벤트 ==")
        val byDevice = events.lineSequence().mapNotNull { EVENT.find(it) }.groupBy { it.groupValues[1] }
        if (byDevice.isEmpty()) appendLine("(이벤트 없음)")
        byDevice.forEach { (path, list) ->
            val name = names[path].orEmpty()
            val touch = name.contains("touch", true)
            appendLine("$path ($name): ${list.size}개" + if (touch) " · 화면 터치라 내용 생략" else "")
            if (!touch) {
                // 같은 종류 이벤트가 몇 번 나왔는지(키 이름·값) — 지문 센서에서 무엇이 나오는지 본다
                list.groupingBy { "${it.groupValues[2]} ${it.groupValues[3]} ${it.groupValues[4]}" }.eachCount().entries
                    .sortedByDescending { it.value }.take(15).forEach { appendLine("    ${it.value}× ${it.key}") }
            }
        }
        appendLine()
        appendLine("== 같은 동안 지문 관련 시스템 로그 ==")
        val log = ShizukuAccess.shell(if (start.isNotEmpty()) "logcat -d -v time -T '$start'" else "logcat -d -v time -t 2000")?.out.orEmpty()
        val fp = log.lineSequence().filter { FP_LOG.containsMatchIn(it) }.toList()
        appendLine(if (fp.isEmpty()) "(없음)" else fp.takeLast(80).joinToString("\n"))
        appendLine()
        appendLine("== 지문 센서 정보(dumpsys fingerprint 앞부분) ==")
        appendLine(ShizukuAccess.shell("dumpsys fingerprint | head -40")?.out?.ifBlank { "(없음)" } ?: "(읽지 못함)")
    }.trimEnd()

    /** getevent -pl 출력에서 장치 경로 → 이름. */
    internal fun deviceNames(out: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var path: String? = null
        out.lineSequence().forEach { line ->
            DEVICE.find(line)?.let { path = it.groupValues[1] }
            NAME.find(line)?.let { m -> path?.let { map[it] = m.groupValues[1] } }
        }
        return map
    }
}
