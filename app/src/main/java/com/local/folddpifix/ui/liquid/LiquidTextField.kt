package com.local.folddpifix.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 액체 입력칸: 칸 자체가 연체 물방울([SoftBlob])이다.
 * - 누르면(포커스) 방울이 사방으로 살짝 부풀며 테두리가 잉크색으로 굵어지고, 놓으면(포커스 해제) 원래 크기로 오므라든다.
 * - 글자를 칠 때마다 커서 쪽 가장자리를 톡 건드려 물결이 번지듯 출렁인다(지울 때는 안쪽으로).
 * [leading]에는 돋보기 같은 작은 그림을 넣을 수 있다.
 */
@Composable
fun LiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val d = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var box by remember { mutableStateOf(IntSize.Zero) }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    val grow = with(d) { 3.dp.toPx() }
    val corner = with(d) { 22.dp.toPx() }
    var last by remember { mutableStateOf(value) }

    LaunchedEffect(box, focused) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val g = if (focused) grow else 0f
        // 칸 테두리 안쪽에 방울을 두고, 포커스 때 부풀 여유(grow)를 남긴다
        blob.moveTo(box.width / 2f, box.height / 2f, box.width - 2 * grow + 2 * g, box.height - 2 * grow + 2 * g, animate = !reduce, corner = corner)
    }
    LaunchedEffect(value) {
        if (reduce || box == IntSize.Zero || value == last) { last = value; return@LaunchedEffect }
        val sign = if (value.length >= last.length) 1f else -1f
        last = value
        // 커서는 글자 끝 근처(대략 왼쪽에서 글자 수만큼): 그 자리 윗·아랫면을 바깥(지우면 안쪽)으로 톡 친다
        val x = (with(d) { 48.dp.toPx() } + value.length * with(d) { 9.dp.toPx() }).coerceAtMost(box.width - corner)
        blob.poke(x, 0f, 0f, -260f * sign, box.height * 0.8f)
        blob.poke(x, box.height.toFloat(), 0f, 260f * sign, box.height * 0.8f)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .onSizeChanged { box = it },
    ) {
        Canvas(Modifier.matchParentSize()) {
            blob.frame
            if (!blob.blob.ready) return@Canvas
            blob.blob.path(path)
            drawPath(path, c.surface)
            drawPath(path, if (focused) c.ink else c.line, style = Stroke((if (focused) 1.6f else 1f).dp.toPx()))
        }
        Row(Modifier.matchParentSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = c.ink)),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = keyboardOptions,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
