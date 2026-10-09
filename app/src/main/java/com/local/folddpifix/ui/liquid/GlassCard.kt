package com.local.folddpifix.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 유리판(원본 sc.panel): 그림자 없이 표면색 + 엷은 틴트 + 1px 테두리. 실시간 블러는 쓰지 않는다(성능).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(LiquidShape.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .background(c.tint)
            .border(1.dp, accent ?: c.line, shape)
            .padding(padding),
        content = content,
    )
}
