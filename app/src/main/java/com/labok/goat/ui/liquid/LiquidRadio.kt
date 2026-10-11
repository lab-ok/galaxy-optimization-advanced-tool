package com.labok.goat.ui.liquid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 여럿 중 하나를 고르는 한 줄(라디오): 왼쪽 동그라미는 고르면 잉크 방울이 통 차오르며 살짝 넘쳤다 자리 잡고,
 * 오른쪽에 이름과 설명을 둔다. 줄 전체를 누를 수 있다. 진동은 부르는 쪽이 [LiquidHaptics.segment]로 낸다.
 */
@Composable
fun LiquidRadioRow(label: String, desc: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        if (reduce) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "radio",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            drawCircle(if (selected) c.ink else c.line, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (fill > 0.01f) drawCircle(c.ink, (r - 5.dp.toPx()) * fill.coerceAtMost(1.15f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled || selected) c.ink else c.muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (desc != null) Text(desc, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
