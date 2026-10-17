package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.data.lab.BatteryMonitor
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 실험실 '배터리 모니터링'. 위: 지금 상태(잔량·충전·온도·전압·전류·전력, 2초마다 다시 읽음).
 * 가운데: 마지막 충전 이후 쓴 양·화면 켜짐/꺼짐·이 패턴의 예상 시간·앱별 순위(시스템 배터리 통계, Shizuku).
 * 아래: 15분 기록을 켜면 최근 24시간 잔량 그래프와 시간당 소모량·남은 시간 추정.
 */
@Composable
internal fun LabBatteryScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var now by remember { mutableStateOf(BatteryMonitor.read(context)) }
    var logging by remember { mutableStateOf(BatteryMonitor.logging(context)) }
    var samples by remember { mutableStateOf(BatteryMonitor.samples(context)) }
    LaunchedEffect(Unit) {
        // 화면을 여는 동안 2초마다 다시 읽는다. 기록이 켜져 있으면 화면을 열 때 한 줄 남긴다
        BatteryMonitor.record(context)
        samples = BatteryMonitor.samples(context)
        while (true) {
            now = BatteryMonitor.read(context)
            delay(2_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Text(Copy.FEATURE_LAB_BATTERY, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.LAB_BATTERY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            val s = now
            if (s == null) {
                Text("배터리 상태를 확인하지 못했습니다.", color = c.ink)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.level}", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text("%", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
                Text(
                    if (s.charging) "충전 중" + (s.plug?.let { " · $it" } ?: "") else "배터리 사용 중",
                    color = c.ink,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Gap.Item))
                Metric("온도", s.tempC?.let { String.format(Locale.KOREA, "%.1f°C", it) })
                Metric("전압", s.voltageMv?.let { String.format(Locale.KOREA, "%.2fV", it / 1000f) })
                Metric("전류", s.currentMa?.let { "${if (it > 0) "+" else ""}$it mA" })
                Metric("전력", s.watts?.let { String.format(Locale.KOREA, "%+.1fW", it) })
                Metric("상태", s.health)
            }
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            BatterySinceCharge(now?.level, now?.charging == true)
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            LiquidSwitchRow(Copy.LAB_BATTERY_LOG, Copy.LAB_BATTERY_LOG_BODY, logging, true) { want ->
                logging = want
                BatteryMonitor.setLogging(context, want)
                samples = BatteryMonitor.samples(context)
            }
            Spacer(Modifier.height(Gap.Card))
            val t = System.currentTimeMillis()
            val day = samples.filter { t - it.time <= BatteryMonitor.DAY_MS }
            Text("최근 24시간 잔량", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(Gap.Item))
            if (day.size < 2) {
                Text(if (logging) "기록이 쌓이면 그래프가 표시됩니다(15분마다 기록)." else "기록을 켜면 15분마다 배터리 잔량을 기록합니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                LevelGraph(day, t)
                Spacer(Modifier.height(Gap.Item))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("24시간 전", color = c.muted, style = MaterialTheme.typography.labelSmall)
                    Text("지금", color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(Gap.Item))
            val drain = BatteryMonitor.drainPerHour(samples, t)
            Text(
                if (drain == null) {
                    "시간당 소모량: 충전하지 않은 기록이 30분 넘게 쌓이면 계산합니다."
                } else {
                    val left = now?.takeIf { !it.charging && drain > 0.05f }?.let { (it.level / drain).roundToInt() }
                    String.format(Locale.KOREA, "시간당 소모량: 약 %.1f%%", drain) + (left?.let { " · 현재 추세라면 약 ${it}시간 남음" } ?: "")
                },
                color = c.ink,
                style = MaterialTheme.typography.bodySmall
            )
            if (samples.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Card))
                LiquidButton("기록 지우기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
                    BatteryMonitor.clear(context)
                    samples = emptyList()
                })
            }
        }
    }
}

@Composable
private fun Metric(name: String, value: String?) {
    val c = LocalLiquid.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value ?: "알 수 없음", color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 최근 24시간 잔량 꺾은선과 그 아래 물. 가로는 시간(왼쪽 24시간 전 → 오른쪽 지금), 세로는 0~100%. 충전 중 구간은 굵게 칠한다. */
@Composable
private fun LevelGraph(list: List<BatteryMonitor.Sample>, now: Long) {
    val c = LocalLiquid.current
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        fun x(t: Long) = (1f - (now - t).toFloat() / BatteryMonitor.DAY_MS) * w
        fun y(level: Int) = h - level / 100f * h
        // 25·50·75% 눈금
        listOf(25, 50, 75).forEach { drawLine(c.line, Offset(0f, y(it)), Offset(w, y(it)), 1.dp.toPx()) }
        val sorted = list.sortedBy { it.time }
        val path = Path().apply {
            sorted.forEachIndexed { i, s -> if (i == 0) moveTo(x(s.time), y(s.level)) else lineTo(x(s.time), y(s.level)) }
        }
        // 잔량 선 아래를 옅은 잉크 물로 채운다(배터리에 남은 물처럼)
        if (sorted.size >= 2) {
            val water = Path().apply {
                addPath(path)
                lineTo(x(sorted.last().time), h)
                lineTo(x(sorted.first().time), h)
                close()
            }
            drawPath(water, c.ink.copy(alpha = 0.2f))
        }
        drawPath(path, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        sorted.zipWithNext().forEach { (a, b) ->
            if (a.charging && b.charging) drawLine(c.ink.copy(alpha = 0.35f), Offset(x(a.time), y(a.level)), Offset(x(b.time), y(b.level)), 6.dp.toPx(), StrokeCap.Round)
        }
        sorted.lastOrNull()?.let { drawCircle(c.ink, 3.5.dp.toPx(), Offset(x(it.time), y(it.level))) }
    }
}
