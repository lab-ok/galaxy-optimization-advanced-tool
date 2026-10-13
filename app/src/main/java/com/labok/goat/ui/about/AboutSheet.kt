package com.labok.goat.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.InfoRow
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidShape
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text

/** 앱 정보: 사이드바처럼 리퀴드로 반응하는 로고·제목([AboutHeader]), 버전과 문의 메일, 필요 권한. */
@Composable
internal fun AboutSheet(onMail: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
    SheetColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        AboutHeader(DeveloperProfile.PRODUCT, DeveloperProfile.TAGLINE)
        Spacer(Modifier.height(Gap.Section))
        Text(DeveloperProfile.HEADLINE, color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Gap.Item))
        DeveloperProfile.HIGHLIGHTS.forEach {
            Text(it, color = c.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = Gap.Tight / 2))
        }
        Spacer(Modifier.height(Gap.Section))
        GlassCard {
            InfoRow("버전", pkg.versionName ?: "-")
            InfoRow("라이선스", DeveloperProfile.LICENSE)
            InfoRow("문의", DeveloperProfile.CONTACT_EMAIL)
            Text(DeveloperProfile.LICENSE_NOTE, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            Row(Modifier.fillMaxWidth()) {
                LiquidButton("메일 보내기", modifier = Modifier.fillMaxWidth(), onClick = onMail, primary = false)
            }
        }
        Spacer(Modifier.height(Gap.Section))
        Text(DeveloperProfile.COPYRIGHT, color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** 필요 권한: 권한마다 상태 방울(허용되면 잉크 방울 + 체크, 아니면 빈 고리)이 차례로 맺히고, 받는 방법을 알약으로 보여 준다. */
@Composable
internal fun PermissionsCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    GlassCard {
        Text("필요 권한", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text("이 앱이 쓰는 권한 전체입니다. 저장소·위치 권한은 쓰지 않고, 인터넷은 업데이트 확인에만 씁니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        AppPermissions.all.forEachIndexed { i, item ->
            Spacer(Modifier.height(Gap.Card))
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
                Spacer(Modifier.height(Gap.Card))
            }
            PermissionRow(item, AppPermissions.granted(context, item), index = i)
        }
    }
}

@Composable
private fun PermissionRow(item: AppPermissions.Item, granted: Boolean, index: Int) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80L * index)
        pop.animateTo(1f, Springs.pop(reduce))
    }
    Row(verticalAlignment = Alignment.Top) {
        Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        ) {
            val r = size.minDimension / 2
            if (granted) {
                drawCircle(c.ink, r)
                val k = r * 0.5f
                drawLine(c.onInk, Offset(center.x - k, center.y), Offset(center.x - k * 0.2f, center.y + k * 0.7f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.onInk, Offset(center.x - k * 0.2f, center.y + k * 0.7f), Offset(center.x + k, center.y - k * 0.6f), 2.dp.toPx(), StrokeCap.Round)
            } else {
                drawCircle(c.danger, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Pill(
                    when {
                        !item.applies -> "해당 없음"
                        granted -> "허용됨"
                        else -> "필요함"
                    },
                    strong = item.applies && !granted
                )
            }
            Text(
                item.permission.substringAfterLast('.'),
                color = c.muted,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(Gap.Tight))
            Text(item.why, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Tight))
            Pill(item.how.label, strong = false, outlined = true)
        }
    }
}

/** 작은 알약 표시. [strong]이면 빨간색, [outlined]면 테두리만. */
@Composable
private fun Pill(text: String, strong: Boolean, outlined: Boolean = false) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(LiquidShape.control)
    val color = if (strong) c.danger else c.ink
    Box(
        Modifier
            .clip(shape)
            .then(if (outlined) Modifier.border(1.dp, c.line, shape) else Modifier.background(color.copy(alpha = 0.08f)))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = if (outlined) c.muted else color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

/** 필요 권한(앱 설정 → 정보). */
@Composable
internal fun PermissionsSheet() {
    SheetColumn { PermissionsCard() }
}
