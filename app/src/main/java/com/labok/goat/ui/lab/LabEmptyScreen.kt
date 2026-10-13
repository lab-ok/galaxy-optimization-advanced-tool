package com.labok.goat.ui.lab

import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.data.rotate.ShakeRotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy

/** 실험실(염소를 열 번 눌러 연다). 시험 중인 기능을 여기에 둔다. */
@Composable
internal fun LabEmptyScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "fastTurn") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    var on by remember { mutableStateOf(ShakeRotate.fastTurn(context)) }
                    LiquidSwitchRow(Copy.LAB_FAST_TURN_TITLE, Copy.LAB_FAST_TURN_BODY, on, enabled = true, onChange = { on = it; ShakeRotate.setFastTurn(context, it) })
                }
            }
        }
    }
}
