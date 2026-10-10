package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * 물방울 번짐 전환: 화면을 [origin] 자리의 작은 물방울([SoftBlob]) 안에만 그리고, 그 물방울을 화면 전체로 키운다.
 * 크기 변화의 85%는 모양째 옮기고 나머지만 힘으로 따라오게 해, 가장자리가 살짝 출렁이며 번진다.
 * 다 번지면 자르기를 멈춘다. [active]가 false이거나 동작 줄이기가 켜져 있으면 바로 그린다.
 */
@Composable
fun Modifier.liquidReveal(active: Boolean, origin: (Size) -> Offset): Modifier {
    if (!active || LocalReduceMotion.current) return this
    val start = with(LocalDensity.current) { 10.dp.toPx() }
    val blob = rememberSoftBlob()
    val r = remember { Animatable(0f) }
    var size by remember { mutableStateOf(Size.Zero) }
    var done by remember { mutableStateOf(false) }
    val path = remember { Path() }
    LaunchedEffect(size) {
        if (size == Size.Zero) return@LaunchedEffect
        val o = origin(size)
        // 가장 먼 모서리까지 덮어야 한다
        val far = maxOf(hypot(o.x, o.y), hypot(size.width - o.x, o.y), hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y))
        blob.blob.gain = 4f
        blob.blob.damping = 3f
        blob.moveTo(o.x, o.y, start * 2, start * 2, animate = false)
        val follow = launch { snapshotFlow { r.value }.collect { val d = 2 * (start + it); blob.moveTo(o.x, o.y, d, d, animate = true, carry = 0.85f) } }
        // 사이드바가 닫히며 물러나는 동안 그 뒤에서 번져 나오도록 천천히 키운다
        r.animateTo(far * 1.08f, tween(720, easing = FastOutSlowInEasing))
        follow.cancel()
        done = true
    }
    return this
        .onSizeChanged { size = it.toSize() }
        .drawWithContent {
            // 방울 상태(frame)를 항상 읽어야 방울이 움직일 때마다 다시 그려진다
            blob.frame
            when {
                done -> drawContent()
                blob.blob.ready -> clipPath(blob.blob.path(path)) { this@drawWithContent.drawContent() }
            }
        }
}
