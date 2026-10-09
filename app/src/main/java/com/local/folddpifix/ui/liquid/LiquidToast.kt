package com.local.folddpifix.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 액체 토스트(원본 toast): 아래에서 방울처럼 솟아 출렁이며 나타나는 잉크색 알약. */
@Composable
fun LiquidToast(text: String) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val sx = remember { Animatable(0.6f) }
    val sy = remember { Animatable(0.6f) }
    LaunchedEffect(text) {
        coroutineScope {
            launch { sx.animateTo(1f, Springs.pop(reduce)) }
            launch { sy.animateTo(1f, Springs.water(reduce)) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .wrapContentWidth()
                .graphicsLayer { scaleX = sx.value; scaleY = sy.value }
                .background(c.ink, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(text, color = c.onInk, fontWeight = FontWeight.SemiBold)
        }
    }
}
