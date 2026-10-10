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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/**
 * 액체 팝업 메뉴: 점 세 개 버튼 자리의 동그란 물방울([SoftBlob])이 메뉴판 모양으로 부풀어 오른다.
 * 쉬는 모양만 메뉴판으로 바꿔 주면 구동력·표면장력·압력이 방울을 끌어 늘리므로, 버튼 쪽에서 먼저
 * 번지고 먼 모서리가 늦게 따라오며 한 번 출렁이고 멈춘다. 항목은 위에서부터 차례로 떠오른다.
 * 닫을 때는 버튼 쪽으로 오므라들며 흐려진다. 메뉴판은 잉크색, 글자는 배경색, 모서리 곡률 26dp.
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
    val density = LocalDensity.current
    val items0 = remember { Animatable(0f) }  // 항목 등장 진행 0→items.size
    val fade = remember { Animatable(1f) }    // 닫을 때 메뉴판 투명도
    val menuW = 220.dp
    val rowH = 50.dp
    val menuH = rowH * items.size + 16.dp
    val radius = 26.dp
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    // 캔버스 좌표: 오른쪽 8dp, 위 4dp 안쪽에 버튼(지름 40dp)과 메뉴판의 오른쪽 위 모서리를 맞춘다.
    val geo = with(density) {
        val btn = 40.dp.toPx(); val right = menuW.toPx() + 8.dp.toPx(); val top = 4.dp.toPx()
        floatArrayOf(btn, right, top, menuW.toPx(), menuH.toPx(), radius.toPx())
    }
    // 펼침 정도(가로 pw, 세로 ph, 0=버튼 원 → 1=메뉴판). 이 값으로 매 프레임 방울 크기를 바꾸는데,
    // 바뀐 만큼의 85%는 모양째 옮기고 15%만 힘으로 따라오게 해 둥근 사각형이 유지된 채 가장자리만 살짝 늦는다.
    // 한 번에 큰 모양으로 바꾸거나 힘만으로 따라가게 하면 가장자리가 뒤처져 찌그러진다.
    val pw = remember { Animatable(0f) }
    val ph = remember { Animatable(0f) }
    fun shape(animate: Boolean) {
        val (btn, right, top) = geo
        val w = btn + (geo[3] - btn) * pw.value; val h = btn + (geo[4] - btn) * ph.value
        blob.moveTo(right - w / 2, top + h / 2, w, h, animate, corner = geo[5], carry = 0.85f)
    }
    LaunchedEffect(Unit) {
        blob.blob.gain = 6f
        blob.blob.damping = 3.5f
        if (reduce) { pw.snapTo(1f); ph.snapTo(1f); shape(false); items0.snapTo(items.size.toFloat()); return@LaunchedEffect }
        shape(false)
        launch { snapshotFlow { pw.value to ph.value }.collect { shape(true) } }
        launch { pw.animateTo(1f, spring(dampingRatio = 1f, stiffness = 520f)) }
        launch { ph.animateTo(1f, spring(dampingRatio = 1f, stiffness = 330f)) }
        items0.animateTo(items.size.toFloat(), tween(80 + 45 * items.size, delayMillis = 110))
    }
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch {
            if (!reduce) {
                // 펼칠 때를 거꾸로: 세로가 먼저, 가로가 뒤따라 버튼 원으로 오므라들고, 끝 무렵에 흐려진다.
                launch { items0.animateTo(0f, tween(90)) }
                launch { fade.animateTo(0f, tween(90, delayMillis = 150)) }
                launch { ph.animateTo(0f, tween(220, easing = FastOutLinearInEasing)) }
                pw.animateTo(0f, tween(240, easing = FastOutLinearInEasing))
            }
            onDismiss()
            after()
        }
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 0),
        onDismissRequest = { close {} },
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.width(menuW + 16.dp).height(menuH + 16.dp)) {
            Canvas(Modifier.size(menuW + 16.dp, menuH + 16.dp).graphicsLayer { alpha = fade.value }) {
                blob.frame
                blob.blob.path(path)
                translate(top = 3.dp.toPx()) { drawPath(path, c.ink.copy(alpha = 0.10f)) }
                drawPath(path, c.ink)
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
