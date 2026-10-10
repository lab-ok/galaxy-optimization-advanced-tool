package com.local.folddpifix.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
 * 사이드바 내용: 앱 로고·이름·버전 머리말, 구역별 메뉴. 고른 항목은 잉크색 알약으로 표시하고,
 * 누르면 항목이 살짝 눌리며 색이 부드럽게 바뀐다.
 */
@Composable
fun SideDrawerContent(sections: List<NavSection>, current: NavItem?, onSelect: (NavItem) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(AppInfo.NAME, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
                Text("버전 $version", color = c.muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        sections.forEach { section ->
            Spacer(Modifier.height(18.dp))
            Text(
                section.title, color = c.muted, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
            section.items.forEach { item -> DrawerRow(item, item == current) { onSelect(item) } }
        }
    }
}

@Composable
private fun DrawerRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) c.ink else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(if (selected) c.onInk else c.ink, label = "navFg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .liquidPress(interaction, sx = 1.02f, sy = 0.95f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
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
