package com.local.folddpifix.ui.help

import com.local.folddpifix.ui.components.SheetColumn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.art.DensityIllustration
import com.local.folddpifix.ui.art.FoldDeviceArt
import com.local.folddpifix.ui.home.UiState
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion

/** 사용 방법: 원리 두 장면 + 준비 안내로 이어지는 버튼. 쉬운 말 한두 문장만. */
@Composable
internal fun HelpSheet(state: UiState, onOpenGuide: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "help").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, delayMillis = 1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t",
    )
    val anim = if (reduce) 1f else t
    SheetColumn {
        Text("사용 방법", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            Text("1. 같은 DPI인데 크기가 다른 이유", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text("두 화면은 PPI(인치당 픽셀 수)가 달라서, 같은 DPI라도 실제 표시 크기가 다릅니다.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            DensityIllustration(corrected = anim)
            Text(
                if (anim < 0.5f) "보정 전: 내부 화면이 더 크게 표시" else "보정 후: 두 화면이 같은 크기로 표시",
                color = c.ink, style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text("2. 외부 화면 DPI만 설정합니다", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text("내부 화면 DPI는 두 화면의 해상도와 PPI로 계산해 같은 크기로 표시되게 합니다. 재부팅 후에도 자동으로 다시 적용합니다.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                FoldDeviceArt(unfolded = anim > 0.5f, height = 110.dp)
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard {
            Text("3. 최초 1회 권한 설정", fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.hasPermission) "권한이 설정되어 있습니다."
                else "DPI는 시스템 설정이라 WRITE_SECURE_SETTINGS 권한이 필요합니다. Shizuku(PC 없이)나 PC의 adb로 한 번 받으면 이후에는 앱이 자동으로 처리합니다.",
                color = c.muted, style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.hasPermission) {
                Spacer(Modifier.height(12.dp))
                LiquidButton("권한 설정 방법", modifier = Modifier.fillMaxWidth(), onClick = onOpenGuide)
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
