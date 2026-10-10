package com.local.folddpifix.ui.nav

import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import com.local.folddpifix.ui.liquid.goo
import com.local.folddpifix.ui.liquid.Springs
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.folddpifix.AppInfo
import com.local.folddpifix.R
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.liquidPress
import com.local.folddpifix.ui.text.Copy

/**
 * 사이드바 항목 = 앱의 기능(화면) 단위. 기능 안의 세부 설정·도구는 각 화면의 점 세 개 메뉴에 둔다.
 * [icon]은 앱 그림과 같은 선 굵기로 직접 그린다.
 */
enum class NavItem(val label: String, val icon: NavIcon) {
    /** 접고 펼 때도 두 화면의 표시 크기(DPI)를 일관되게 맞추는 기본 기능. */
    DPI_MATCH(Copy.FEATURE_DPI, NavIcon.FOLD),
    /** 실험실: 앱마다 화면 크기를 다르게 두는 기능(조사 단계, lab 빌드 전용). */
    APP_SIZE(Copy.FEATURE_APP_SIZE, NavIcon.FLASK),
}

enum class NavIcon { FOLD, FLASK }

data class NavSection(val title: String, val items: List<NavItem>)

/**
 * 사이드바 내용(리퀴드): 열릴 때 로고가 방울처럼 톡 튀어나오고 항목이 차례로 미끄러져 들어온다.
 * 고른 항목 아래의 잉크 방울은 항목을 바꾸면 머리(빠른 스프링)와 꼬리(느린 스프링)가 goo로 이어져
 * 늘어났다가 합쳐지며 옮겨 간다(LiquidChips와 같은 원리, 세로 방향).
 * @param visible 사이드바가 열려 있는지. 열릴 때마다 등장 애니메이션을 다시 한다.
 */
@Composable
fun SideDrawerContent(sections: List<NavSection>, current: NavItem?, visible: Boolean, onSelect: (NavItem) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val items = sections.flatMap { it.items }

    // 등장: 로고 방울, 항목 차례로
    val logo = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) { logo.snapTo(0f); enter.snapTo(0f); return@LaunchedEffect }
        if (reduce) { logo.snapTo(1f); enter.snapTo(items.size + 2f); return@LaunchedEffect }
        launch { logo.animateTo(1f, Springs.pop()) }
        enter.animateTo(items.size + 2f, tween(120 + 55 * (items.size + 2), easing = LinearEasing))
    }

    // 고른 항목 방울: 각 항목의 위치를 재고, 머리·꼬리 스프링으로 옮긴다
    val tops = remember { mutableStateMapOf<NavItem, Float>() }
    var rowH by remember { mutableFloatStateOf(0f) }
    val target = current?.let { tops[it] }
    val pill = rememberLiquidPill(target, reduce)

    Box(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(c.bg),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        val k = logo.value
                        scaleX = 0.6f + 0.4f * k; scaleY = 0.6f + 0.4f * k; alpha = k.coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(AppInfo.NAME, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text("버전 $version", color = c.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                // 방울 층(항목 뒤): 고른 항목을 따라다니는 잉크 알약
                Canvas(Modifier.matchParentSize().goo(c.ink, 7.dp)) {
                    drawLiquidPill(pill, c.ink, size.width, rowH)
                }
                Column {
                    var index = 0
                    sections.forEach { section ->
                        Spacer(Modifier.height(18.dp))
                        val titleOrder = index
                        Text(
                            section.title, color = c.muted, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp).graphicsLayer { alpha = (enter.value - titleOrder).coerceIn(0f, 1f) },
                        )
                        section.items.forEach { item ->
                            val order = ++index
                            DrawerRow(
                                item, item == current,
                                cover = cover@{
                                    val top = tops[item] ?: return@cover if (item == current) 1f else 0f
                                    if (pill.center < 0f || rowH <= 0f) (if (item == current) 1f else 0f)
                                    else (1f - kotlin.math.abs(pill.center - top) / rowH).coerceIn(0f, 1f)
                                },
                                appear = { (enter.value - order).coerceIn(0f, 1f) },
                                modifier = Modifier.onGloballyPositioned {
                                    tops[item] = it.positionInParent().y
                                    rowH = it.size.height.toFloat()
                                },
                            ) { onSelect(item) }
                        }
                        index++
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(
    item: NavItem,
    selected: Boolean,
    cover: () -> Float,
    appear: () -> Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalLiquid.current
    val interaction = remember { MutableInteractionSource() }
    // 글자·아이콘 색은 잉크 방울이 이 항목을 덮은 만큼 배경색으로 바뀐다(방울이 도착할 때 반전).
    val fg = lerp(c.ink, c.onInk, cover())
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                val k = appear()
                alpha = k; translationX = (1f - k) * -18.dp.toPx()
            }
            .liquidPress(interaction, sx = 1.02f, sy = 0.95f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavGlyph(item.icon, fg)
        Spacer(Modifier.width(14.dp))
        Text(item.label, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** 메뉴 아이콘(24dp). 앱 그림과 같은 둥근 선으로 그린다. */
@Composable
fun NavGlyph(icon: NavIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            NavIcon.FOLD -> fold(color, s)
            NavIcon.FLASK -> flask(color, s)
        }
    }
}

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x), u(y))

/** 펼친 폴드: 가로로 긴 기기와 가운데 접는 선. */
private fun DrawScope.fold(c: Color, s: Stroke) {
    drawRoundRect(c, p(3f, 5.5f), Size(u(18f), u(13f)), CornerRadius(u(2f)), style = s)
    drawLine(c, p(12f, 7.5f), p(12f, 16.5f), s.width * 0.8f, StrokeCap.Round)
}

private fun DrawScope.flask(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(9.5f), u(3.5f)); lineTo(u(9.5f), u(9.5f)); lineTo(u(4.5f), u(18.5f))
        quadraticBezierTo(u(4f), u(20.5f), u(6.2f), u(20.5f)); lineTo(u(17.8f), u(20.5f))
        quadraticBezierTo(u(20f), u(20.5f), u(19.5f), u(18.5f)); lineTo(u(14.5f), u(9.5f)); lineTo(u(14.5f), u(3.5f))
    }
    drawPath(path, c, style = s)
    drawLine(c, p(8f, 3.5f), p(16f, 3.5f), s.width, StrokeCap.Round)
    drawLine(c, p(7f, 15f), p(17f, 15f), s.width * 0.8f, StrokeCap.Round)
}
