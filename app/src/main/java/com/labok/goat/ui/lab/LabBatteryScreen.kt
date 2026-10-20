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
    LaunchedEffect(Unit) {
        // 화면을 여는 동안 2초마다 다시 읽는다
        withContext(Dispatchers.IO) { BatteryMonitor.retireLogging(context) }
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
            Spacer(Modifier.height(Gap.Item))
            val s = now
            if (s == null) {
                Text("배터리 상태를 확인하지 못했습니다.", color = c.ink)
            } else {
                // 맨 위 그림(시안 8): 사용 중·충전 중에 따라 다른 물 막대 애니메이션
                BatteryBarsArt(s.level, s.charging)
                Spacer(Modifier.height(Gap.Item))
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
            }
        }
        // 배터리 수명 카드는 뺐다: 삼성 기기의 남은 전하량은 화면 잔량으로 만든 값이라 수명을 추정할 수 없고(2026-10-20 조사),
        // 삼성 수명값은 설정에서도 볼 수 있다. 대신 충전 중 전류를 쌓아 실제 용량을 잰다
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) { ChargeMeterCard() }
        BatterySinceCharge(now?.level, now?.charging == true)
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
