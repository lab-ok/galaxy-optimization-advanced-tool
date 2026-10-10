package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 액체 팝업 메뉴: 점 세 개 버튼 자리의 작은 방울이 오른쪽 위를 축으로 메뉴판으로 부푼다.
 * 가로가 먼저 퍼지고 세로가 살짝 늦게 따라와 물이 번지듯 열리며(감쇠 큰 스프링이라 출렁임은 거의 없다),
 * 항목은 위에서부터 차례로 떠오른다. 닫을 때는 빠르게 버튼 쪽으로 오므라든다.
 * 메뉴판은 잉크색, 글자는 배경색, 모서리 곡률 26dp.
 */
@Composable
fun LiquidMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>,
) {
    if (!expanded) return
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val pw = remember { Animatable(0f) }      // 가로 펼침 0→1
    val ph = remember { Animatable(0f) }      // 세로 펼침 0→1
    val items0 = remember { Animatable(0f) }  // 항목 등장 진행 0→items.size
    LaunchedEffect(Unit) {
        if (reduce) {
            pw.snapTo(1f); ph.snapTo(1f); items0.snapTo(items.size.toFloat()); return@LaunchedEffect
        }
        coroutineScope {
            launch { pw.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 520f)) }
            launch { ph.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 300f)) }
            launch { items0.animateTo(items.size.toFloat(), tween(80 + 45 * items.size, delayMillis = 90)) }
        }
    }
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch {
            if (!reduce) coroutineScope {
                launch { items0.animateTo(0f, tween(90)) }
                launch { ph.animateTo(0f, tween(170, easing = FastOutLinearInEasing)) }
                launch { pw.animateTo(0f, tween(190, easing = FastOutLinearInEasing)) }
            }
            onDismiss()
            after()
        }
    }
    val menuW = 220.dp
    val rowH = 50.dp
    val menuH = rowH * items.size + 16.dp
    val radius = 26.dp

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 0),
        onDismissRequest = { close {} },
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.width(menuW + 16.dp).height(menuH + 16.dp)) {
            Canvas(Modifier.size(menuW + 16.dp, menuH + 16.dp)) {
                val btn = 40.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 4.dp.toPx()
                val w = btn + (menuW.toPx() - btn) * pw.value
                val h = btn + (menuH.toPx() - btn) * ph.value
                val r = minOf(radius.toPx(), minOf(w, h) / 2)
                // 펼쳐지는 동안 가장자리가 살짝 둥글게 부푼 물방울처럼 보이도록 곡률을 크게 시작한다.
                val bulge = (1f - minOf(pw.value, ph.value)).coerceIn(0f, 1f)
                val rr = r + (minOf(w, h) / 2 - r) * bulge * 0.5f
                drawRoundRect(c.ink.copy(alpha = 0.10f * ph.value), Offset(right - w, top + 3.dp.toPx()), Size(w, h), CornerRadius(rr))
                drawRoundRect(c.ink, Offset(right - w, top), Size(w, h), CornerRadius(rr))
            }
            Column(
                Modifier
                    .padding(top = 12.dp, end = 8.dp)
                    .width(menuW)
                    .align(Alignment.TopEnd),
            ) {
                items.forEachIndexed { i, (label, action) ->
                    val interaction = remember { MutableInteractionSource() }
                    val k = (items0.value - i).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .graphicsLayer { alpha = k; translationY = (1f - k) * -6.dp.toPx() }
                            .liquidPress(interaction, sx = 1.03f, sy = 0.94f)
                            .clickable(interaction, null) { close(action) }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(label, color = c.onInk, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
