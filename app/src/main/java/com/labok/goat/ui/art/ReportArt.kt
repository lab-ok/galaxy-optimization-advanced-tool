package com.labok.goat.ui.art

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.goo
import kotlin.math.cos
import kotlin.math.sin

/**
 * 문제 신고 장면: 흩어진 방울들이 모여 문서 모양으로 뭉친 뒤 위로 날아간다(약 0.9초). 끝나면 [onDone].
 * 방울이 화면 글자·조작 위에 바로 겹치지 않게, 장면 동안 화면 전체를 바탕색 막으로 옅게 덮었다가 걷는다.
 */
@Composable
fun ReportFlightArt(onDone: () -> Unit) {
    val c = LocalLiquid.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val p = t.value
                val veil = minOf(p / VEIL_EDGE, (1f - p) / VEIL_EDGE, 1f).coerceIn(0f, 1f)
                drawRect(c.bg.copy(alpha = VEIL_ALPHA * veil))
            },
        contentAlignment = Alignment.Center
    ) {
        Flight { t.value }
    }
}

/** 방울 모임·날아감 그림(진행 0→1). 진행은 그리기 단계에서만 읽어 매 프레임 다시 구성하지 않는다. */
@Composable
private fun Flight(progress: () -> Float) {
    val c = LocalLiquid.current
    Canvas(Modifier.size(140.dp).goo(c.ink, 6.dp)) {
        val p = progress()
        val gather = (p / 0.55f).coerceIn(0f, 1f)
        val fly = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val center = Offset(size.width / 2, size.height / 2 - fly * size.height * 0.6f)
        repeat(8) { i ->
            val a = i * Math.PI / 4
            val dist = size.minDimension * 0.42f * (1f - gather)
            drawCircle(c.ink, 7.dp.toPx(), Offset(center.x + cos(a).toFloat() * dist, center.y + sin(a).toFloat() * dist))
        }
        val w = 34.dp.toPx() * gather * (1f - 0.4f * fly)
        val h = 44.dp.toPx() * gather * (1f - 0.4f * fly)
        if (w > 0f) drawRoundRect(c.ink, Offset(center.x - w / 2, center.y - h / 2), Size(w, h), CornerRadius(6.dp.toPx()))
    }
}

/** 막이 짙어지고(처음) 걷히는(끝) 구간: 장면 길이에 대한 비율. */
private const val VEIL_EDGE = 0.15f

/** 막의 가장 짙은 불투명도. 뒤 화면이 살짝 비쳐 어디서 신고했는지는 보인다. */
private const val VEIL_ALPHA = 0.85f
