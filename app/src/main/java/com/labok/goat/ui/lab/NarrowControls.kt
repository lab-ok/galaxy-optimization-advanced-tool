package com.labok.goat.ui.lab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.liquid.rememberSoftBlob
import kotlinx.coroutines.launch

/**
 * 좁힌 앱 옆 여백 컨트롤러(실험실)를 GOAT 리퀴드 부품으로 그린다: 테마 잉크색 방울([rememberSoftBlob], 연체 물리),
 * 누르면 옆으로 퍼졌다 튕기는 눌림([liquidPress]). 버튼은 여백 폭을 다 쓰고 손가락으로 누르기 좋게 크게 둔다.
 * 아래(엄지 영역)부터 같은 크기의 원: ▼ 다음 · ▲ 이전 · ▶ · ◀(좌우 넘기기). 위쪽에는 같은 크기의 최근 앱 아이콘.
 * 붙일 쪽은 상단바 꾹 누르기로 정한다(컨트롤러의 옮기기 버튼은 뺐다, 소장님 지정).
 * [compact]면(키보드가 떠 있을 때) 넘기기 네 개만 남긴다.
 */
internal enum class NarrowKey { NEXT, PREV, RIGHT, LEFT, SWAP, EXIT }

@Composable
internal fun NarrowControls(
    appLeft: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** 멀티태스킹 바: 최근에 연 앱 아이콘(최근 순, 첫째가 지금 앱). 비면 그리지 않는다. */
    recents: List<ImageBitmap> = emptyList(),
    onRecent: (Int) -> Unit = {},
    onKey: (NarrowKey) -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxHeight().padding(horizontal = 4.dp), contentAlignment = Alignment.BottomCenter) {
        // 모든 버튼은 같은 크기의 원: 여백 폭을 다 쓴다(최대 72dp). 최근 앱 아이콘도 같은 크기
        val d = maxWidth.coerceAtMost(72.dp)
        val keys = 4
        val buttons = d * keys + GAP * (keys - 1) + BOTTOM
        val fit = ((maxHeight - buttons - DOCK_TOP - 16.dp) / (d + GAP)).toInt().coerceIn(0, 5)
        if (recents.isNotEmpty() && !compact && fit > 0) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = DOCK_TOP)) { RecentDock(recents.take(fit), d, onRecent) }
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = if (compact) 24.dp else BOTTOM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            InkKey(NarrowKey.LEFT, d, onKey = onKey)
            InkKey(NarrowKey.RIGHT, d, onKey = onKey)
            InkKey(NarrowKey.PREV, d, onKey = onKey)
            InkKey(NarrowKey.NEXT, d, onKey = onKey)
        }
    }
}

private val GAP = 10.dp
private val DOCK_TOP = 52.dp

/** 방향키 묶음 아래 여백: 화면 맨 아래보다 조금 위, 엄지가 편한 높이(소장님 지정 10-24: 더 올리기). '원래대로' 버튼은 뺐다(꾹 누르기 가운데로 끈다). */
private val BOTTOM = 120.dp

/**
 * 멀티태스킹 바: 최근에 연 앱 아이콘을 세로로(최근 순, 첫째가 지금 앱). 지금 앱 뒤에는 잉크 방울이 깔리고, 다른 앱을 누르면
 * 방울이 그 아이콘으로 흘러가 늘어났다 출렁이며 자리 잡은 뒤 그 앱으로 바뀐다.
 */
@Composable
private fun RecentDock(icons: List<ImageBitmap>, diameter: Dp, onPick: (Int) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    var picked by remember { mutableIntStateOf(0) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val cell = diameter + GAP
    // 앱 목록이 바뀌면(다른 앱으로 옮겨 감) 방울을 다시 첫째(지금 앱)로
    LaunchedEffect(icons) { picked = 0 }
    LaunchedEffect(picked, box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val px = box.width.toFloat()
        val ch = box.height.toFloat() / icons.size
        blob.moveTo(px / 2, ch * picked + ch / 2, px, px, animate = !reduce, corner = px / 2)
    }
    Box(
        Modifier.fillMaxWidth().height(cell * icons.size).onSizeChanged { box = it }.pointerInput(icons.size) {
            detectTapGestures { o ->
                val i = (o.y / (size.height.toFloat() / icons.size)).toInt().coerceIn(0, icons.size - 1)
                if (i != picked) {
                    picked = i
                    haptics.toggle(true)
                    onPick(i)
                }
            }
        }
    ) {
        Canvas(Modifier.matchParentSize()) {
            blob.frame
            drawPath(blob.blob.path(path), c.ink.copy(alpha = 0.24f))
        }
        Column {
            icons.forEach { bmp ->
                Box(Modifier.fillMaxWidth().height(cell), contentAlignment = Alignment.Center) {
                    Image(bmp, null, Modifier.size(diameter * 0.78f).clip(RoundedCornerShape(diameter * 0.24f)))
                }
            }
        }
    }
}

/**
 * 큰 잉크 버튼: 누르면 옆으로 퍼졌다 튕기고(LiquidButton과 같은 스프링), 안의 잉크 방울이 누른 방향으로 한 번 밀렸다
 * 돌아온다(연체 방울). 화살표도 그 방향으로 살짝 미끄러졌다 돌아온다. [primary]가 아니면 테두리만 있는 버튼(옮기기·끄기).
 */
@Composable
private fun InkKey(key: NarrowKey, height: Dp, primary: Boolean = true, appLeft: Boolean = true, onKey: (NarrowKey) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val interaction = remember { MutableInteractionSource() }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(0f) }
    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        blob.moveTo(box.width / 2f, box.height / 2f, box.width.toFloat(), box.height.toFloat(), animate = false, corner = box.width / 2f)
    }
    val dir = when (key) {
        NarrowKey.NEXT -> Offset(0f, 1f)
        NarrowKey.PREV -> Offset(0f, -1f)
        NarrowKey.RIGHT -> Offset(1f, 0f)
        NarrowKey.LEFT -> Offset(-1f, 0f)
        NarrowKey.SWAP -> Offset(if (appLeft) 1f else -1f, 0f)
        NarrowKey.EXIT -> Offset.Zero
    }
    Canvas(
        Modifier.size(height).onSizeChanged { box = it }.liquidPress(interaction, sx = 1.08f, sy = 0.92f)
            .clickable(interaction, null) {
                haptics.press()
                if (primary && !reduce) blob.poke(box.width / 2f, box.height / 2f, dir.x * 900f, dir.y * 900f, box.width.toFloat())
                if (!reduce) {
                    scope.launch {
                        slide.snapTo(0f)
                        slide.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                        slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                }
                onKey(key)
            }
    ) {
        blob.frame
        val r = size.width / 2
        if (primary) {
            drawPath(blob.blob.path(path), c.ink)
        } else {
            drawRoundRect(c.surface, cornerRadius = CornerRadius(r))
            drawRoundRect(c.line, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
        }
        val s = size.width * 0.24f
        glyph(key, center + dir * (slide.value * 6.dp.toPx()), s, if (primary) c.onInk else c.ink, appLeft)
    }
}

/** 버튼 그림(메뉴 아이콘과 같은 둥근 선). [s]는 반지름 정도의 크기. */
internal fun DrawScope.glyph(key: NarrowKey, at: Offset, s: Float, color: Color, appLeft: Boolean = true) {
    val st = Stroke(width = s * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val (x, y) = at
    fun path(vararg p: Float) = Path().apply {
        moveTo(x + p[0], y + p[1])
        var i = 2
        while (i < p.size) {
            lineTo(x + p[i], y + p[i + 1])
            i += 2
        }
    }
    when (key) {
        NarrowKey.PREV -> drawPath(path(-s, s * 0.45f, 0f, -s * 0.45f, s, s * 0.45f), color, style = st)
        NarrowKey.NEXT -> drawPath(path(-s, -s * 0.45f, 0f, s * 0.45f, s, -s * 0.45f), color, style = st)
        NarrowKey.LEFT -> drawPath(path(s * 0.45f, -s, -s * 0.45f, 0f, s * 0.45f, s), color, style = st)
        NarrowKey.RIGHT -> drawPath(path(-s * 0.45f, -s, s * 0.45f, 0f, -s * 0.45f, s), color, style = st)
        // 반대쪽으로 옮기기: 작은 화면 틀과, 앱이 갈 쪽을 가리키는 화살표
        NarrowKey.SWAP -> {
            val sign = if (appLeft) 1f else -1f
            drawRoundRect(color, Offset(x - s * 0.9f, y - s * 1.1f), Size(s * 1.8f, s * 2.2f), CornerRadius(s * 0.3f), style = st)
            drawPath(path(-sign * s * 0.35f, 0f, sign * s * 0.45f, 0f), color, style = st)
            drawPath(path(sign * s * 0.15f, -s * 0.32f, sign * s * 0.47f, 0f, sign * s * 0.15f, s * 0.32f), color, style = st)
        }
        // 좁은 화면 끄기: 양옆으로 펼치는 화살표와 양쪽 벽
        NarrowKey.EXIT -> {
            drawPath(path(-s, 0f, s, 0f), color, style = st)
            drawPath(path(-s * 0.6f, -s * 0.4f, -s, 0f, -s * 0.6f, s * 0.4f), color, style = st)
            drawPath(path(s * 0.6f, -s * 0.4f, s, 0f, s * 0.6f, s * 0.4f), color, style = st)
            drawLine(color, Offset(x - s * 1.25f, y - s * 0.8f), Offset(x - s * 1.25f, y + s * 0.8f), st.width, StrokeCap.Round)
            drawLine(color, Offset(x + s * 1.25f, y - s * 0.8f), Offset(x + s * 1.25f, y + s * 0.8f), st.width, StrokeCap.Round)
        }
    }
}
