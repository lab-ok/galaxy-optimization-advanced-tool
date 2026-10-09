package com.local.folddpifix.ui.art

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import com.local.folddpifix.ui.liquid.rememberBreath
import com.local.folddpifix.ui.liquid.Springs

/**
 * 미리보기: 외부·내부 화면을 같은 높이(폴드 실물 비율, 내부는 가로로 긴 화면)로 나란히 그리고, 각 화면에 적용될 밀도로 아이콘을 그린다.
 * 아이콘의 그려지는 크기는 (밀도 ÷ PPI)에 비례하므로, 맞춰진 상태면 두 화면의 아이콘이 같은 크기로 보인다.
 * 값이 바뀌면 스프링으로 부드럽게 커지고 작아진다.
 */
@Composable
fun ScreensPreview(
    @Suppress("UNUSED_PARAMETER") outerInch: Float, outerAspect: Float, outerPpi: Float, outerDpi: Int,
    innerInch: Float, innerAspect: Float, innerPpi: Float, innerDpi: Int,
    outerLabel: String, innerLabel: String,
    modifier: Modifier = Modifier,
    /** 지금 켜진 화면: true면 내부, false면 외부, null이면 모름. 켜진 화면의 테두리가 숨 쉬고, 바뀌면 부드럽게 옮겨 간다. */
    innerActive: Boolean? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val sel by animateFloatAsState(if (innerActive == true) 1f else 0f, Springs.calm(reduceMotion), label = "sel")
    val breathe = rememberBreath(innerActive)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val outerIcon by animateFloatAsState(outerDpi / outerPpi, Springs.water(reduce), label = "o")
    val innerIcon by animateFloatAsState(innerDpi / innerPpi, Springs.water(reduce), label = "i")
    val labelColor = LocalLiquid.current.muted
    Canvas(modifier.fillMaxWidth().height(172.dp)) {
        val labelH = 22.dp.toPx()
        // 폴드는 외부·내부 화면의 실제 높이가 거의 같다(Fold7 기준 5.97" · 5.94"). 그래서 두 화면을 같은 높이로 그리고
        // 가로 폭만 각자의 화면비(세로÷가로)로 정한다. 보고된 PPI가 부정확해도 기기 모양은 실제와 같게 보인다.
        val gap = 28.dp.toPx()
        val area = size.height - labelH
        val h = minOf(area * 0.94f, (size.width - gap) * 0.92f / (1f / outerAspect + 1f / innerAspect))
        val ow = h / outerAspect; val oh = h
        val iw = h / innerAspect; val ih = h
        // 아이콘 배율: 내부 화면의 실제 높이(인치)를 그려진 높이에 맞춘 값을 두 화면에 똑같이 쓴다.
        val innerHeightIn = innerInch / kotlin.math.sqrt(1 + innerAspect * innerAspect) * innerAspect
        val scale = h / innerHeightIn
        val start = (size.width - ow - gap - iw) / 2
        // 두 화면의 아래쪽을 맞춘다(실제로 나란히 둔 모습).
        val bottom = (area + maxOf(oh, ih)) / 2
        val ot = Offset(start, bottom - oh)
        val it = Offset(start + ow + gap, bottom - ih)
        // 기기 틀(공통 스타일). 켜진 화면은 몸체가 진하고 안쪽 테두리가 숨 쉬며, 바뀌면 외부↔내부로 부드럽게 옮겨 간다.
        val screens = listOf(ot to Size(ow, oh), it to Size(iw, ih)).mapIndexed { idx, (p, s) ->
            val w = when (innerActive) {
                null -> 1f
                else -> if (idx == 0) 1f - sel else sel
            }
            val b = if (reduceMotion) 0.5f else breathe
            deviceFrame(c, p, s, emphasis = 0.45f + 0.55f * w, glow = if (innerActive == null) 0f else w * b)
        }
        // 아이콘 한 변 = (dpi/ppi) × 1인치 × 0.32(48dp 아이콘이 화면에서 차지하는 실제 비율에 맞춘 값) × scale
        // 실제 비율(48dp 아이콘)보다 2배 크게 그려 차이를 알아보기 쉽게 한다. 두 화면에 같은 배율이라 비교는 정확하다.
        val unit = scale * 0.60f
        iconRows(screens[0].topLeft, screens[0].size, outerIcon * unit, c.ink.copy(alpha = 0.55f), rows = 3)
        iconRows(screens[1].topLeft, screens[1].size, innerIcon * unit, c.ink.copy(alpha = 0.55f), rows = 3)
        listOf(outerLabel to (ot.x + ow / 2), innerLabel to (it.x + iw / 2)).forEach { (text, cx) ->
            val layout = measurer.measure(text, labelStyle.copy(color = labelColor))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, bottom + 6.dp.toPx()))
        }
    }
}
