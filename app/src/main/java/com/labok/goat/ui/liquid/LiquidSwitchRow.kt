package com.labok.goat.ui.liquid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.Text

/**
 * 제목·설명과 오른쪽 [LiquidSwitch] 한 줄. 화면 읽기가 제목·설명·켜짐 상태를 한 항목으로 읽도록 줄 전체를 묶는다.
 */
@Composable
fun LiquidSwitchRow(title: String, desc: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // 쓸 수 없는 줄은 제목도 흐리게 한다(스위치만 흐려져 쓸 수 있는 것처럼 보였다, 조합 점검)
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) c.ink else c.muted)
            desc?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(12.dp))
        LiquidSwitch(checked, onChange, enabled = enabled, label = title)
    }
}
