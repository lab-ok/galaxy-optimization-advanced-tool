package com.labok.goat.ui.help

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.labok.goat.R
import com.labok.goat.ui.art.ConnectionIllustration
import com.labok.goat.ui.art.DensityIllustration
import com.labok.goat.ui.art.FoldDeviceArt
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.home.UiState
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy

/** 사용 방법: 원리 두 장면 + 준비 안내로 이어지는 버튼. 쉬운 말 한두 문장만. */
@Composable
internal fun HelpSheet(state: UiState, onOpenGuide: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "help").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1600, delayMillis = 1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t"
    )
    val anim = if (reduce) 1f else t
    SheetColumn(title = Copy.MENU_HELP) {
        // 다른 사용 방법과 같은 꼴: 제목 → 그림 → 설명(HelpTopic). 폴드가 아니면 두 화면 이야기 대신 DPI 하나만 설명한다.
        if (!state.foldable) {
            HelpTopic(
                stringResource(R.string.help_single_dpi_title),
                stringResource(R.string.help_single_dpi_body)
            )
        }
        if (state.foldable) {
            HelpTopic(
                stringResource(R.string.help_fold_why_title),
                stringResource(R.string.help_fold_why_body)
            ) {
                DensityIllustration(corrected = anim)
                Text(
                    if (anim < 0.5f) stringResource(R.string.help_fold_before) else stringResource(R.string.help_fold_after),
                    color = c.ink,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (state.foldable) {
            HelpTopic(
                stringResource(R.string.help_fold_outer_title),
                stringResource(R.string.help_fold_outer_body)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    FoldDeviceArt(unfolded = anim > 0.5f, height = 110.dp)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        // 권한을 받는 길(PC·폰 연결) 그림. 권한이 있으면 연결된 모습으로 보여 준다.
        HelpTopic(
            "${if (state.foldable) 3 else 2}. ${stringResource(R.string.help_setup_title)}",
            if (state.hasPermission) {
                stringResource(R.string.help_setup_done)
            } else {
                Copy.TODO_SETUP_BODY
            }
        ) { ConnectionIllustration(wireless = true, done = state.hasPermission) }
        if (!state.hasPermission) {
            LiquidButton(Copy.MENU_GUIDE, modifier = Modifier.fillMaxWidth(), onClick = onOpenGuide)
        }
    }
}
