package com.local.folddpifix.ui.art

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.goo
import kotlin.math.cos
import kotlin.math.sin

/**
 * 문제 신고 장면: 흩어진 방울들이 모여 문서 모양으로 뭉친 뒤 위로 날아간다(약 0.9초). 끝나면 [onDone].
 */
@Composable
fun ReportFlightArt(onDone: () -> Unit) {
    val c = LocalLiquid.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.size(140.dp).goo(c.ink, 6.dp)) {
        val p = t.value
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
