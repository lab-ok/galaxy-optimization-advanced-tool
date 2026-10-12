package com.labok.goat.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.realTimeLoop
import kotlin.math.PI
import kotlin.math.cos

/**
 * 리퀴드 로딩 표시: 양쪽 방울이 가운데로 모여 가운데 방울과 하나로 합쳐졌다가 다시 갈라지기를 되풀이한다(goo).
 * 트래픽스 UI의 loader(component/loader.js)를 옮겼다: 한 주기 [PERIOD_MS] 동안 s = (1 − cos 2πt)/2,
 * 양쪽 방울은 가운데에서 48·(1 − s)만큼 떨어지고 반지름 11·(1 − 0.25s), 가운데 방울 반지름 13·(0.85 + 0.45s)(280 너비 기준 단위).
 * 동작 줄이기가 켜져 있으면 합쳐진 모습으로 멈춘다. [label]은 아래 작은 글자.
 */
@Composable
fun LiquidLoader(label: String, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val t = if (reduce) 0.5f else realTimeLoop(PERIOD_MS)
    Column(modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(150.dp).height(56.dp).goo(c.ink, 6.dp)) {
            val u = size.width / 156f
            val s = ((1 - cos(t * 2 * PI)) / 2).toFloat()
            val off = 48f * (1 - s) * u
            val cx = size.width / 2; val cy = size.height / 2
            val side = 11f * (1 - 0.25f * s) * u
            drawCircle(c.ink, side, Offset(cx - off, cy))
            drawCircle(c.ink, side, Offset(cx + off, cy))
            drawCircle(c.ink, 13f * (0.85f + 0.45f * s) * u, Offset(cx, cy))
        }
        Text(label, color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}

private const val PERIOD_MS = 1600
