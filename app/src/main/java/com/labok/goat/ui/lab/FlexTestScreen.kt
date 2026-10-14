package com.labok.goat.ui.lab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlin.math.roundToInt

/**
 * 플렉스 모드 테스트([FlexTestActivity]가 띄우는 화면 전체). 펼쳐 있을 때는 범위와 지금 각도, "천천히 접어 보세요" 안내만 보여 주고,
 * 자동 플렉스가 플렉스 모드를 걸면 화면이 위(앱 자리)·아래(조작 패널 자리)로 갈라져 처음 쓰는 사람도 무엇이 바뀌는지 바로 본다.
 * 설정 화면이 뒤로 가 있는 동안이라 자동 플렉스가 실제로 걸린다.
 */
@Composable
internal fun FlexTestScreen(onClose: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val engineAngle by AutoFlex.angle.collectAsStateWithLifecycle()
    val active by AutoFlex.active.collectAsStateWithLifecycle()
    val latched by AutoFlex.latched.collectAsStateWithLifecycle()
    val localAngle = rememberHingeAngle(engineAngle == null)
    val angle = engineAngle ?: localAngle
    val min = remember { AutoFlex.min(context) }
    val max = remember { AutoFlex.max(context) }
    val horizontal = rememberHingeHorizontal(true)
    // 갈라지는 정도: 0 한 화면, 1 위아래 두 칸
    val split by animateFloatAsState(if (active) 1f else 0f, Springs.water(reduce), label = "flexTestSplit")
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(16.dp)) {
        // 위 칸: 펼쳐 있을 때는 게이지와 안내, 플렉스 모드면 진한 '앱 화면'
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(lerp(c.bg, c.ink, split)),
            contentAlignment = Alignment.Center
        ) {
            if (split < 0.5f) {
                Column(Modifier.padding(Gap.CardPadding).graphicsLayer { alpha = 1 - split * 2 }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Copy.LAB_FLEX_TEST_TITLE, color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Gap.Item))
                    // 세로로 접힌 채(책처럼)면 플렉스 모드가 걸리지 않으니 먼저 돌려 달라고 안내한다
                    Text(if (horizontal) Copy.LAB_FLEX_TEST_HOW else Copy.LAB_FLEX_TEST_TURN, color = if (horizontal) c.muted else c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = if (horizontal) FontWeight.Normal else FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text("$min~$max°", color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Gap.Section))
                    // 시험만 한다: 조절바 없이 지금 각도만 크게
                    Text(angle?.let { "${it.roundToInt()}°" } ?: "—", color = c.ink, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(Modifier.padding(Gap.CardPadding).graphicsLayer { alpha = (split - 0.5f) * 2 }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(56.dp)) {
                        drawPath(
                            Path().apply {
                                moveTo(size.width * 0.28f, size.height * 0.18f)
                                lineTo(size.width * 0.82f, size.height / 2)
                                lineTo(size.width * 0.28f, size.height * 0.82f)
                                close()
                            },
                            c.onInk
                        )
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_ON_TITLE, color = c.onInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Text(if (latched) Copy.LAB_FLEX_ON_BODY_CLICK else Copy.LAB_FLEX_ON_BODY, color = c.onInk.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
        // 아래 칸: 플렉스 모드면 물방울처럼 부풀어 '조작 패널' 자리가 된다
        if (split > 0.01f) {
            Spacer(Modifier.height(Gap.Card * split))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(split.coerceAtLeast(0.01f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surface)
                    .graphicsLayer { alpha = split },
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.padding(Gap.CardPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 뒤로·재생·앞으로(삼각형으로 직접 그린다)
                        for (i in 0..2) {
                            val tint = if (i == 1) c.onInk else c.ink
                            Box(Modifier.size(if (i == 1) 56.dp else 44.dp).clip(CircleShape).background(if (i == 1) c.ink else c.line), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(20.dp)) {
                                    fun tri(x0: Float, dir: Float) = Path().apply {
                                        moveTo(x0, size.height * 0.15f)
                                        lineTo(x0 + dir * size.width * 0.45f, size.height / 2)
                                        lineTo(x0, size.height * 0.85f)
                                        close()
                                    }
                                    when (i) {
                                        0 -> {
                                            drawPath(tri(size.width * 0.5f, -1f), tint)
                                            drawPath(tri(size.width * 0.95f, -1f), tint)
                                        }
                                        1 -> drawPath(tri(size.width * 0.25f, 1.6f), tint)
                                        else -> {
                                            drawPath(tri(size.width * 0.05f, 1f), tint)
                                            drawPath(tri(size.width * 0.5f, 1f), tint)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_TEST_BOTTOM, color = c.ink, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Text(Copy.LAB_FLEX_TEST_UNFOLD, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Gap.Card))
        LiquidButton(Copy.LAB_FLEX_TEST_CLOSE, primary = false, modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}
