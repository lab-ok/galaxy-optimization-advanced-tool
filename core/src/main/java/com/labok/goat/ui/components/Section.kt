package com.labok.goat.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text

/** 화면 안 묶음 제목(여러 기능 화면이 함께 쓴다, 11-11 모듈 분리 때 AppSizeScreen.kt에서 옮김). */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalLiquid.current
    // 섹션 제목은 카드 안 글자와 같은 선에서 시작한다
    Row(modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = Gap.CardPadding, top = Gap.Tight), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
