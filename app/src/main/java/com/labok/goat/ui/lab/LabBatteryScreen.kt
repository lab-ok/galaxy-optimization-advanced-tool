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
import com.labok.goat.data.lab.BatteryHealth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    var health by remember { mutableStateOf<BatteryHealth.Info?>(null) }
    LaunchedEffect(Unit) {
        // 화면을 여는 동안 2초마다 다시 읽는다
        health = withContext(Dispatchers.IO) { BatteryHealth.read(context) }
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
            Text(Copy.LAB_BATTERY_NOW, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.LAB_BATTERY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            val s = now
            if (s == null) {
                Text("배터리 상태를 확인하지 못했습니다.", color = c.ink)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.level}", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text("%", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (s.charging) "충전 중" + (s.plug?.let { " · $it" } ?: "") else "배터리 사용 중",
                        color = c.ink,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Spacer(Modifier.height(Gap.Item))
                Metric("온도", s.tempC?.let { String.format(Locale.KOREA, "%.1f°C", it) })
                Metric("전압", s.voltageMv?.let { String.format(Locale.KOREA, "%.2fV", it / 1000f) })
                Metric("전류", s.currentMa?.let { "${if (it > 0) "+" else ""}$it mA" })
                Metric("전력", s.watts?.let { String.format(Locale.KOREA, "%+.1fW", it) })
                Metric("상태", s.health)
            }
        }
        health?.takeIf { !it.empty }?.let { h ->
            Spacer(Modifier.height(Gap.Card))
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) { HealthCard(h) }
        }
        BatterySinceCharge(now?.level, now?.charging == true)
    }
}

/**
 * 배터리 수명(웨어율): 새 배터리 대비 지금 담을 수 있는 용량을 큰 숫자와 물 막대로, 아래에 충전 횟수와 삼성 건강 지수.
 * 삼성은 설정에 보여 주지 않는 값이라 Shizuku로 `dumpsys battery`에서 읽는다([BatteryHealth]).
 */
@Composable
private fun HealthCard(h: BatteryHealth.Info) {
    val c = LocalLiquid.current
    Text("배터리 수명", fontWeight = FontWeight.SemiBold, color = c.ink)
    Text("새 배터리일 때와 비교해 지금 담을 수 있는 용량입니다. 보통 80% 아래로 내려가면 사용 시간이 눈에 띄게 줄어듭니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Item))
    h.asoc?.let { a ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$a", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text("%", color = c.muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Spacer(Modifier.height(Gap.Tight))
        LiquidBar(a / 100f, height = 12.dp, description = "배터리 수명 $a%")
        Spacer(Modifier.height(Gap.Item))
    }
    h.cycles?.let { Metric("충전 횟수(완충 환산)", String.format(Locale.KOREA, "%.0f회", it)) }
    h.bsoh?.let { Metric("삼성 건강 지수(참고)", "$it%") }
}

@Composable
private fun Metric(name: String, value: String?) {
    val c = LocalLiquid.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value ?: "알 수 없음", color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
