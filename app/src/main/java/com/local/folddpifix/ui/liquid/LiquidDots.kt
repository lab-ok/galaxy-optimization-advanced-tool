package com.local.folddpifix.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 액체 페이지 점: 현재 점 자리의 물방울([SoftBlob])이 다음 점으로 흘러가며 늘어났다가 다시 동그랗게 모인다. */
@Composable
fun LiquidDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val d = LocalDensity.current
    val step = with(d) { 18.dp.toPx() }
    val cy = with(d) { 10.dp.toPx() }
    val dot = with(d) { 10.dp.toPx() }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    LaunchedEffect(index) { blob.moveTo(step * index + step / 2, cy, dot, dot, animate = !reduce) }
    Canvas(modifier.width((count * 18).dp).height(20.dp)) {
        blob.frame
        repeat(count) { i -> drawCircle(c.line, 3.dp.toPx(), Offset(step * i + step / 2, size.height / 2)) }
        drawPath(blob.blob.path(path), c.ink)
    }
}
