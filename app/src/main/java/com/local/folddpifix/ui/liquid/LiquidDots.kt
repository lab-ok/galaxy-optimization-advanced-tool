package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 액체 페이지 점(원본 pager): 현재 점으로 방울이 옮겨 가며 꼬리를 끈다. */
@Composable
fun LiquidDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val head = remember { Animatable(index.toFloat()) }
    val tail = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) {
        coroutineScope {
            launch { head.animateTo(index.toFloat(), Springs.calm(reduce)) }
            launch { tail.animateTo(index.toFloat(), Springs.lag(reduce)) }
        }
    }
    Box(modifier.width((count * 18).dp).height(20.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val step = 18.dp.toPx()
            repeat(count) { i -> drawCircle(c.line, 3.dp.toPx(), Offset(step * i + step / 2, size.height / 2)) }
        }
        Canvas(Modifier.matchParentSize().goo(c.ink, 4.dp)) {
            val step = 18.dp.toPx()
            val cy = size.height / 2
            drawCircle(c.ink, 5.dp.toPx(), Offset(step * head.value + step / 2, cy))
            drawCircle(c.ink, 3.5.dp.toPx(), Offset(step * tail.value + step / 2, cy))
        }
    }
}
