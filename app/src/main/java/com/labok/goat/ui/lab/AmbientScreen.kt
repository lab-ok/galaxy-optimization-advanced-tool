package com.labok.goat.ui.lab

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.R
import com.labok.goat.data.lab.AmbientOpen
import com.labok.goat.data.lab.TentDuo
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * 앰비언트 모드(실험실 메뉴, 11-02 소장님: 텐트 모드와 합쳐 따로 메뉴로, 정식 기능처럼).
 * - 맨 위: 텐트로 세운 폴드 아래로 빛이 번지는 움직이는 그림(색이 천천히 바뀐다).
 * - 켜기: 닫힌 폴드를 펼치기 시작하면 안쪽이 빛나고, 반쯤 펴 세워 두면 유튜브 등 어떤 앱에서도 빛이 계속 켜져 있다([AmbientOpen]·[TentDuo]).
 * - 시계: 켤 때 바깥 화면에 스탠바이 시계도 띄울지, 어떤 시계인지 썸네일(실제 시계가 움직이는 작은 화면)에서 고른다.
 * - 빛 방향: 오른쪽 절반으로·양 옆 끝 뒤집기, 방향 확인. [지금 켜기]로 바로 시작할 수도 있다.
 */
@Composable
internal fun AmbientScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var on by remember { mutableStateOf(AmbientOpen.enabled(context)) }
    var clock by remember { mutableStateOf(AmbientOpen.clock(context)) }
    var style by remember { mutableIntStateOf(StandbyActivity.style(context)) }
    var swap by remember { mutableStateOf(TentDuo.swap(context)) }
    var mirror by remember { mutableStateOf(TentDuo.mirror(context)) }
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "hero") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    AmbientHero(on)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(stringResource(R.string.lab_ambient_mode_title), stringResource(R.string.lab_ambient_mode_desc), on, enabled = true, onChange = {
                        on = it
                        AmbientOpen.setEnabled(context, it)
                    })
                    Spacer(Modifier.height(Gap.Item))
                    Text(
                        stringResource(R.string.lab_ambient_mode_body),
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(stringResource(R.string.lab_ambient_start_now), modifier = Modifier.fillMaxWidth(), onClick = { TentDuo.start(context) })
                }
            }
        }
        item(key = "clock") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(Modifier.animateContentSize()) {
                    LiquidSwitchRow(stringResource(R.string.lab_ambient_clock_title), stringResource(R.string.lab_ambient_clock_desc), clock, enabled = true, onChange = {
                        clock = it
                        AmbientOpen.setClock(context, it)
                    })
                    if (clock) {
                        Spacer(Modifier.height(Gap.Item))
                        ClockPicker(style) {
                            style = it
                            StandbyActivity.setStyle(context, it)
                        }
                    }
                }
            }
        }
        item(key = "direction") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(stringResource(R.string.lab_ambient_direction_title), fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(stringResource(R.string.lab_ambient_direction_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(stringResource(R.string.lab_ambient_direction_check), primary = false, modifier = Modifier.fillMaxWidth(), onClick = { TentDuo.start(context, check = true) })
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(stringResource(R.string.lab_ambient_swap_title), stringResource(R.string.lab_ambient_swap_desc), swap, enabled = true, onChange = {
                        swap = it
                        TentDuo.setSwap(context, it)
                    })
                    Spacer(Modifier.height(Gap.Section))
                    LiquidSwitchRow(stringResource(R.string.lab_ambient_mirror_title), stringResource(R.string.lab_ambient_mirror_desc), mirror, enabled = true, onChange = {
                        mirror = it
                        TentDuo.setMirror(context, it)
                    })
                }
            }
        }
    }
}

/**
 * 맨 위 그림: 텐트(ㅅ)로 세운 폴드. 바깥 면(오른쪽)은 영상처럼 색이 천천히 바뀌고, 안쪽 면 아래 책상에 같은 색 빛이 숨 쉬듯 번진다.
 * 끄면 빛이 잦아들고 회색으로 가라앉는다.
 */
@Composable
private fun AmbientHero(on: Boolean) {
    val c = LocalLiquid.current
    val t = rememberInfiniteTransition(label = "ambientHero")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "hue")
    val breath by t.animateFloat(0.82f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breath")
    val power by animateFloatAsState(if (on) 1f else 0.15f, spring(dampingRatio = 0.9f, stiffness = 120f), label = "power")
    Canvas(Modifier.fillMaxWidth().height(170.dp).clipToBounds()) {
        val w = size.width
        val h = size.height
        val apex = Offset(w * 0.5f, h * 0.16f)
        val base = h * 0.84f
        val left = Offset(w * 0.3f, base)
        val right = Offset(w * 0.7f, base)
        val col = Color.hsv(hue, 0.75f, 1f)
        // 책상에 번지는 빛(안쪽 면 아래)
        val glowC = Offset(w * 0.42f, base)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.75f * power * breath), col.copy(alpha = 0f)), glowC, w * 0.36f * breath), w * 0.36f * breath, glowC)
        // 책상 선
        drawLine(c.line, Offset(w * 0.08f, base), Offset(w * 0.92f, base), 1.5.dp.toPx(), StrokeCap.Round)
        // 안쪽 면(왼쪽, 빛나는 쪽)
        val inner = Path().apply {
            moveTo(apex.x, apex.y)
            lineTo(left.x, left.y)
            lineTo(left.x + w * 0.035f, left.y)
            lineTo(apex.x + w * 0.012f, apex.y + h * 0.04f)
            close()
        }
        drawPath(inner, Brush.verticalGradient(listOf(col.copy(alpha = 0.5f * power), col.copy(alpha = 0.95f * power)), apex.y, base))
        // 바깥 면(오른쪽, 영상)
        drawLine(Brush.verticalGradient(listOf(Color.hsv((hue + 40f) % 360f, 0.6f, 0.9f), col), apex.y, base), apex, right, 7.dp.toPx(), StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.85f), apex, left, 2.dp.toPx(), StrokeCap.Round)
        drawCircle(c.ink, 3.dp.toPx(), apex, style = Stroke(1.5.dp.toPx()))
    }
}

/** 시계 썸네일: 실제 시계를 작게 움직여 보여 주고, 누르면 고른다(고른 것은 테두리와 함께 살짝 커진다). */
@Composable
private fun ClockPicker(selected: Int, onPick: (Int) -> Unit) {
    val c = LocalLiquid.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(StandbyClocks.NAMES) { i, name ->
            val pick by animateFloatAsState(if (i == selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "pick")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(i) }) {
                Box(
                    Modifier.size(168.dp, 76.dp)
                        .graphicsLayer {
                            scaleX = 0.94f + 0.06f * pick
                            scaleY = 0.94f + 0.06f * pick
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .border(2.dp, c.ink.copy(alpha = pick), RoundedCornerShape(14.dp))
                ) {
                    StandbyClock(i, now, 80, false)
                }
                Spacer(Modifier.height(Gap.Item))
                Text(name, color = if (i == selected) c.ink else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
