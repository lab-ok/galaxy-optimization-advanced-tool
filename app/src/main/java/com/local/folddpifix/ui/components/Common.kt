package com.local.folddpifix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.liquid.LocalLiquid

/** 이름–값 한 줄(고급 정보·앱 정보). 숫자는 고정폭 숫자(tabular). */
@Composable
internal fun InfoRow(key: String, value: String, emphasize: Boolean = false) {
    val c = LocalLiquid.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
        Text(
            value,
            // 숫자는 고정폭 숫자(tabular)로 줄을 맞추되 글꼴은 본문과 같게 둔다.
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
            color = c.ink,
        )
    }
}

/**
 * 복사할 명령·주소 한 칸. 긴 낱말(패키지 이름, 권한 이름, 주소)은 줄 가운데서 잘리지 않도록 자기 줄로 내려 보여 주고,
 * 복사는 원래 한 줄 그대로 한다. 버튼([extra], 복사)은 아래 오른쪽에 둔다.
 */
@Composable
internal fun CommandBox(text: String, onCopy: () -> Unit, extra: @Composable (() -> Unit)? = null) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(start = 12.dp, top = 10.dp, end = 4.dp),
    ) {
        Text(
            displayCommand(text),
            color = c.ink,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        )
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            extra?.invoke()
            TextButton(onClick = onCopy) { Text("복사", color = c.ink) }
        }
    }
}

/** 화면 표시용 줄바꿈: 18자를 넘는 낱말 앞에서 줄을 바꾼다. */
internal fun displayCommand(text: String): String =
    text.split(' ').fold(StringBuilder()) { sb, word ->
        if (sb.isNotEmpty()) sb.append(if (word.length > 18) '\n' else ' ')
        sb.append(word)
    }.toString()
