package com.labok.goat.ui.lab

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

/** 실험실(염소를 열 번 눌러 연다). 시험 중인 기능을 여기에 둔다. 지금은 비어 있다(빠른 회전은 1.4.7에서 정식으로 옮겼다). */
@Composable
internal fun LabEmptyScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "empty") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_EMPTY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_EMPTY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
