package com.labok.goat.ui.liquid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * 반폭 버튼 두 개. 좁은 폭·큰 글자에서 라벨이 한 줄에 안 들어가면 세로로 쌓는다.
 * [content]는 받은 Modifier를 두 버튼에 그대로 붙인다.
 */
@Composable
fun LiquidButtonPair(labels: List<String>, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = labels.maxOf { measurer.measure(it, style).size.width }
        val half = with(density) { ((maxWidth - GAP) / 2 - LABEL_PAD).toPx() }
        if (widest <= half) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) { content(Modifier.weight(1f)) }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP)) { content(Modifier.fillMaxWidth()) }
        }
    }
}

private val GAP = 10.dp

// LiquidButton 라벨의 좌우 안쪽 여백(22dp씩)
private val LABEL_PAD = 44.dp
