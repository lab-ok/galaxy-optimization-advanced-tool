package com.labok.goat.ui.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.FingerTouchProbe
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Inset
import com.labok.goat.ui.components.PillShape
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 실험실: 센서 동작 테스트(11-13 소장님). 켜 두는 동안 항목별 값을 실시간으로 보이고, 값이 바뀔 때마다 그 줄을 깜빡이며
 * 진동하고 아래 기록에 남긴다. 항목은 앱이 직접 읽는 손 쥠(grip)·근접 센서와, Shizuku로 읽는 grip 입력 장치(getevent)다.
 * 손 쥠 센서는 1.58.3 Fold8 기록에서 1=닿음·쥠, 2=뗌으로 확인했다. 화면을 벗어나면 모두 멈춘다.
 */
@Composable
internal fun SensorLiveCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var vibrate by remember { mutableStateOf(true) }
    val rows = remember { mutableStateListOf<LiveRow>() }
    val log = remember { mutableStateListOf<String>() }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    fun changed(key: String, label: String, value: String) {
        val i = rows.indexOfFirst { it.key == key }
        val old = rows.getOrNull(i)
        if (old != null && old.value == value) return
        val row = LiveRow(key, label, value, (old?.count ?: 0) + if (old == null) 0 else 1, System.currentTimeMillis())
        if (i >= 0) rows[i] = row else rows.add(row)
        if (old == null) return
        log.add(0, "${fmt.format(Date())}  $label  ${old.value} → $value")
        while (log.size > 12) log.removeAt(log.lastIndex)
        if (vibrate) buzzShort(context)
    }

    // 앱이 직접 읽는 센서: 이름에 grip이 들어간 것과 근접 센서
    DisposableEffect(running) {
        if (!running) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensors = sm?.getSensorList(Sensor.TYPE_ALL).orEmpty().filter {
            "grip" in it.name.lowercase() || "grip" in it.stringType.lowercase() || it.type == Sensor.TYPE_PROXIMITY
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                changed("s:${e.sensor.type}:${e.sensor.name}", e.sensor.name, e.values.take(3).joinToString(" ") { v -> if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v) })
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
        }
        sensors.forEach { s ->
            rows.removeAll { it.key.startsWith("s:${s.type}:") }
            rows.add(LiveRow("s:${s.type}:${s.name}", s.name, "-", 0, 0))
            sm?.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (sensors.isEmpty()) log.add(0, "앱에서 읽을 수 있는 손 쥠·근접 센서가 없습니다.")
        onDispose { sm?.unregisterListener(listener) }
    }

    // Shizuku로 읽는 grip 입력 장치(값 1=닿음·쥠, 2=뗌)
    DisposableEffect(running) {
        if (!running) return@DisposableEffect onDispose { }
        val procs = mutableListOf<Process>()
        val alive = AtomicBoolean(true)
        thread(name = "GOAT-SensorLive") {
            val names = ShizukuAccess.shell("getevent -pl")?.let { FingerTouchProbe.deviceNames(it.out) }?.filterValues { "grip" in it.lowercase() }
            if (names == null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { log.add(0, "Shizuku 연결이 없어 입력 장치는 읽지 않습니다.") }
                return@thread
            }
            names.forEach { (path, name) ->
                val p = ShizukuAccess.stream("exec getevent -lt $path") ?: return@forEach
                synchronized(procs) { if (alive.get()) procs.add(p) else p.destroy() }
                thread(name = "GOAT-SensorLive-$name") {
                    runCatching {
                        p.inputStream.bufferedReader().forEachLine { line ->
                            val m = FingerTouchProbe.EVENT.find(line) ?: return@forEachLine
                            if (m.groupValues[2] == "EV_SYN") return@forEachLine
                            val value = m.groupValues[4].trimStart('0').ifEmpty { "0" }
                            android.os.Handler(android.os.Looper.getMainLooper()).post { changed("d:$path", "입력 장치 $name", value) }
                        }
                    }
                }
            }
        }
        onDispose {
            alive.set(false)
            synchronized(procs) { procs.forEach { it.destroy() } }
        }
    }

    GlassCard(Modifier.animateContentSize()) {
        Text("센서 동작 테스트", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(
            "켜 두면 손 쥠(grip)·근접 센서 값을 실시간으로 보여 줍니다. 값이 바뀔 때마다 그 줄이 깜빡이고 진동하며 아래에 기록됩니다. 손 쥠 센서는 1이 닿음·쥠, 2가 뗌입니다. 입력 장치 줄은 Shizuku가 필요합니다.",
            color = c.muted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow("테스트 켜기", "이 화면을 벗어나면 멈춥니다.", running, true) {
            running = it
            if (it) { rows.clear(); log.clear() }
        }
        Spacer(Modifier.height(Gap.Section))
        LiquidSwitchRow("바뀔 때 진동", null, vibrate, true) { vibrate = it }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Item))
            rows.forEach { r -> LiveRowView(r) }
        }
        if (log.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Item))
            log.forEach { Text(it, color = c.ink, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)) }
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("기록 지우기", primary = false, modifier = Modifier.fillMaxWidth(), onClick = { log.clear() })
        }
    }
}

private data class LiveRow(val key: String, val label: String, val value: String, val count: Int, val at: Long)

/** 한 항목: 이름 · 지금 값 · 바뀐 횟수. 값이 바뀌면 잠깐 잉크색으로 물들었다 돌아온다. */
@Composable
private fun LiveRowView(r: LiveRow) {
    val c = LocalLiquid.current
    val flash = remember(r.key) { Animatable(0f) }
    LaunchedEffect(r.at) {
        if (r.at == 0L || r.count == 0) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, androidx.compose.animation.core.tween(700))
    }
    val bg: Color = c.ink.copy(alpha = 0.18f * flash.value)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(PillShape)
            .background(bg)
            .padding(Inset.Row)
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label, color = c.ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("바뀐 횟수 ${r.count}", color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
        Text(r.value, color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

private fun buzzShort(context: Context) {
    runCatching { context.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }
}
