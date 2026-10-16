package com.labok.goat.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import kotlin.math.hypot
import kotlinx.coroutines.launch

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
    val bg = LocalLiquid.current.bg
    var far by remember { mutableStateOf(1f) }
    LaunchedEffect(size) {
        if (size == Size.Zero) return@LaunchedEffect
        val o = origin(size)
        // 가장 먼 모서리까지 덮어야 한다
        far = maxOf(hypot(o.x, o.y), hypot(size.width - o.x, o.y), hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y))
        blob.blob.gain = 4f
        blob.blob.damping = 3f
        blob.moveTo(o.x, o.y, start * 2, start * 2, animate = false)
        val follow = launch {
            snapshotFlow { r.value }.collect {
                val d = 2 * (start + it)
                blob.moveTo(o.x, o.y, d, d, animate = true, carry = 0.85f)
            }
        }
        // 누른 자리에서 바로 번져 나와(초반이 빠른 곡선) 사이드바가 물러나는 동안 화면을 덮는다. 앱 설정의 UI 애니메이션 속도를 따른다.
        r.animateTo(far * 1.08f, tween((640 / UiMotion.speed).toInt(), easing = LinearOutSlowInEasing))
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
                blob.blob.ready -> clipPath(blob.blob.path(path)) {
                    this@drawWithContent.drawContent()
                    // 방울이 작을 때는 새 화면 글자가 조각으로 비쳐 깨져 보인다. 처음엔 바탕색으로 채웠다가 방울이 화면의 40%쯤 커지는 동안 걷는다(1/4로는 한 프레임 비쳤다, QA 2차)
                    val veil = 1f - (r.value / (far * REVEAL_VEIL)).coerceIn(0f, 1f)
                    if (veil > 0f) drawRect(bg.copy(alpha = veil))
                }
            }
        }
}

/** 새 화면을 바탕색으로 가리는 구간: 가장 먼 모서리까지 거리에 대한 방울 반지름 비율. */
private const val REVEAL_VEIL = 0.4f
