package com.labok.goat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text

/** 이름–값 한 줄(고급 정보·앱 정보). 숫자는 고정폭 숫자(tabular). */
@Composable
internal fun InfoRow(key: String, value: String, emphasize: Boolean = false) {
    val c = LocalLiquid.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 이름은 한 줄로 두고(좁은 화면에서 '외부/화면'처럼 접히던 것), 값이 길면 값 쪽이 오른쪽 정렬로 줄바꿈한다
        Text(key, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            // 숫자는 고정폭 숫자(tabular)로 줄을 맞추되 글꼴은 본문과 같게 둔다.
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
            color = c.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
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
            .padding(start = 12.dp, top = 10.dp, end = 4.dp)
    ) {
        val shown = displayCommand(text)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 8.dp)) {
            // 큰 글자·좁은 화면에서도 가장 긴 낱말(공백 없이 이어진 글자)이 한 줄에 들어가게 글자를 줄인다(고정폭 글자 폭 약 0.62em,
            // 글자 사이 간격 0). 낱말 사이는 공백에서 줄을 바꾸면 되므로 줄 길이가 아니라 낱말 길이로 잰다.
            // 1.24.8 점검: 글자 1.3배·365dp에서 촬영음 키 이름이 낱말 가운데서 잘렸고, 줄 길이로 재자 PC 명령이 너무 작아졌다
            val longest = shown.split(' ', '\n').maxOf { it.length }.coerceAtLeast(1)
            val fit = with(LocalDensity.current) { (maxWidth / (longest * 0.62f)).toSp() }.value
            // 8sp보다 작아져야 들어가는 낱말(긴 주소 등)은 8sp로 두고 예전처럼 낱말 안에서 줄을 바꾼다.
            // 들어가면 '/', '-' 뒤에서 줄이 바뀌지 않게 낱말 이음표(U+2060)를 넣는다(보이기만, 복사는 원래 글)
            val size = fit.coerceIn(8f, 11f)
            Text(
                if (fit >= 8f) shown.replace(Regex("([^\\s\\w])(?=\\S)"), "$1\u2060") else shown,
                color = c.ink,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = size.sp, letterSpacing = 0.sp)
            )
        }
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            extra?.invoke()
            TextButton(onClick = onCopy) { Text("복사", color = c.ink) }
        }
    }
}

/** 화면 표시용 줄바꿈: 18자를 넘는 낱말 앞에서 줄을 바꾼다. */
internal fun displayCommand(text: String): String = text.split(' ').fold(StringBuilder()) { sb, word ->
    if (sb.isNotEmpty()) sb.append(if (word.length > 18) '\n' else ' ')
    sb.append(word)
}.toString()
