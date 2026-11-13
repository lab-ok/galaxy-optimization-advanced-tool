package com.labok.goat.data.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 실험실: 손 쥠 센서(grip_sensor, 안테나 옆 사람 손 감지)로 측면에 손이 닿은 것·폰을 쥔 것을 구분할 수 있는지 조사한다
 * (11-13 소장님: 닿아 있을 때만 자동 회전. 지문 센서는 인증 중에만 켜져 안 됨, 1.58.2 조사에서 grip_sensor 값이 측면 톡톡과 함께 바뀜).
 * 단계마다 [PHASE_SECONDS]초씩 getevent로 grip 장치 이벤트를 받아 값별로 센다. 앱이 직접 읽을 수 있는 grip 센서가 있는지도 적는다.
 */
object GripProbe {
    const val PHASE_SECONDS = 8

    /** 단계 이름과 할 일(화면에 그대로 보인다). */
    val PHASES = listOf(
        "1단계 · 책상에 내려놓고 손 떼기",
        "2단계 · 책상에 둔 채 측면(전원 버튼)만 톡톡",
        "3단계 · 평소처럼 손에 쥐고 있기"
    )

    /** 작업 스레드에서 부른다. [onPhase]는 단계가 바뀔 때(0부터) 불린다. */
    fun run(context: Context, onPhase: (Int) -> Unit): String = buildString {
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        // 앱이 Shizuku 없이 읽을 수 있는 센서 중 이름에 grip이 들어간 것
        val sm = context.getSystemService(SensorManager::class.java)
        val grips = sm?.getSensorList(Sensor.TYPE_ALL).orEmpty().filter { "grip" in it.name.lowercase() || "grip" in it.stringType.lowercase() }
        appendLine("앱에서 보이는 grip 센서: " + if (grips.isEmpty()) "없음" else grips.joinToString(" · ") { "${it.name}(${it.stringType}, type ${it.type}, ${it.vendor})" })
        val devices = ShizukuAccess.shell("getevent -pl") ?: return append("Shizuku 연결이 없어 조사하지 못했습니다.").toString()
        val names = FingerTouchProbe.deviceNames(devices.out).filterValues { "grip" in it.lowercase() }
        appendLine("grip 입력 장치: " + names.entries.joinToString(" · ") { "${it.key.removePrefix("/dev/input/")}=${it.value}" }.ifEmpty { "없음" })
        PHASES.forEachIndexed { i, phase ->
            onPhase(i)
            val out = ShizukuAccess.shell("timeout $PHASE_SECONDS getevent -lt 2>&1")?.out.orEmpty()
            val events = out.lineSequence().mapNotNull { FingerTouchProbe.EVENT.find(it) }.filter { it.groupValues[1] in names }.toList()
            appendLine()
            appendLine("== $phase (${PHASE_SECONDS}초) ==")
            if (events.none { it.groupValues[2] != "EV_SYN" }) appendLine("(grip 이벤트 없음)")
            events.filter { it.groupValues[2] != "EV_SYN" }.groupBy { it.groupValues[1] }.forEach { (path, list) ->
                val counts = list.groupingBy { "${it.groupValues[3]} ${it.groupValues[4]}" }.eachCount().entries.joinToString(" · ") { "${it.key} ${it.value}번" }
                appendLine("${names[path]}: ${list.size}개 · $counts · 마지막 ${list.last().groupValues[4]}")
                appendLine("    순서: " + list.takeLast(40).joinToString(" ") { it.groupValues[4].trimStart('0').ifEmpty { "0" } })
            }
        }
    }.trimEnd()
}
