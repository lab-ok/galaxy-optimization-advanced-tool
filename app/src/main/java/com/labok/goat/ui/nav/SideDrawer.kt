package com.labok.goat.ui.nav

import com.labok.goat.ui.components.Gap
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.rememberSoftBlob
import androidx.compose.ui.layout.onSizeChanged
import com.labok.goat.ui.liquid.LocalReduceMotion
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.text.Copy

/**
 * 사이드바 항목 = 앱의 기능(화면) 단위. 기능 안의 세부 설정·도구는 각 화면의 점 세 개 메뉴에 둔다.
 * [icon]은 앱 그림과 같은 선 굵기로 직접 그린다.
 */
enum class NavItem(val label: String, val icon: NavIcon) {
    /** 메인 화면: 모든 기능의 지금 상태를 한눈에 보고 바로 들어간다. */
    HOME(Copy.FEATURE_HOME, NavIcon.HOME),
    /** 접고 펼 때도 두 화면의 표시 크기(DPI)를 일관되게 맞추는 기본 기능. */
    DPI_MATCH(Copy.FEATURE_DPI, NavIcon.FOLD),
    /** 앱마다 화면 크기를 따로 두는 기능(삼성 '앱 화면 크게/작게', Shizuku 필요). */
    APP_SIZE(Copy.FEATURE_APP_SIZE, NavIcon.APPS),
    /** 창·전환·애니메이터 배율(전역 설정). */
    ANIMATION(Copy.FEATURE_ANIMATION, NavIcon.SPEED),
    /** 평소에는 회전을 잠가 두고, 흔들면 폰이 놓인 방향으로 한 번 돌린다. */
    SHAKE_ROTATE(Copy.FEATURE_SHAKE_ROTATE, NavIcon.ROTATE),
    /** 국내 모델의 강제 촬영음을 끈다(Shizuku 셸로 숨은 시스템 설정을 바꾼다). */
    CAMERA_MUTE(Copy.FEATURE_CAMERA_MUTE, NavIcon.CAMERA),
    /** 쓰지 않는 기본 앱 끄기(Shizuku, 되돌릴 수 있음). */
    APP_CLEANUP(Copy.FEATURE_APP_CLEANUP, NavIcon.CLEAN),
    /** 화면 주사율의 위·아래 한계를 고정한다(Shizuku 셸). */
    REFRESH(Copy.FEATURE_REFRESH, NavIcon.REFRESH),
    /** 게임 중 성능을 낮추는 GOS를 끈다(Shizuku, 기본 앱 정리와 같은 끄기). */
    GOS(Copy.FEATURE_GOS, NavIcon.GAME),
    /** 잠자기 모드(Doze)를 꺼 알림이 늦게 오지 않게 한다(Shizuku, 재부팅 뒤 다시 끄기). */
    DOZE(Copy.FEATURE_DOZE, NavIcon.BELL),
    /** 해상도를 낮추고 밀도도 같은 비율로 줄인다(시스템 설정 권한, 폴드 제외). */
    RESOLUTION(Copy.FEATURE_RESOLUTION, NavIcon.PIXELS),
    /** 다크 모드 없는 앱도 시스템이 어둡게 그리게 한다(Shizuku setprop). */
    DARK(Copy.FEATURE_DARK, NavIcon.MOON),
    /** 숨은 실험실: 염소를 열 번 누르면 나타난다(시험 중인 기능을 둔다). */
    LAB(Copy.FEATURE_LAB, NavIcon.FLASK),
    /** 실험실: 기능을 만들 때 쓰는 조사 도구(사이드바에는 lab 빌드에서만 보인다). */
    LAB_TOOLS(Copy.FEATURE_LAB_TOOLS, NavIcon.FLASK),
    /** 앱 설정: 앱 정보·업데이트·기록 파일·UI 탄성(사이드바 맨 아래에서 연다, 홈 카드에는 없다). */
    SETTINGS(Copy.FEATURE_SETTINGS, NavIcon.GEAR),
}

enum class NavIcon { HOME, FOLD, APPS, SPEED, ROTATE, CAMERA, CLEAN, REFRESH, GAME, MOON, BELL, PIXELS, FLASK, INFO, GEAR }

data class NavSection(val title: String, val items: List<NavItem>)

/**
 * 사이드바 내용(리퀴드): 열릴 때 로고가 방울처럼 톡 튀어나오고 항목이 차례로 미끄러져 들어온다.
 * 고른 항목 아래의 잉크 방울은 항목을 바꾸면 머리(빠른 스프링)와 꼬리(느린 스프링)가 goo로 이어져
 * 늘어났다가 합쳐지며 옮겨 간다(LiquidChips와 같은 원리, 세로 방향).
 * @param visible 사이드바가 열려 있는지. 열릴 때마다 등장 애니메이션을 다시 한다.
 */
@Composable
fun SideDrawerContent(
    sections: List<NavSection>,
    current: NavItem?,
    visible: Boolean,
    onSelect: (NavItem) -> Unit,
    onAbout: () -> Unit,
    /** 위에 덮이는 서랍일 때: 살짝 비치는 유리처럼 뒤(흐려진 본문)가 은은하게 보인다. */
    glass: Boolean = false,
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val items = sections.flatMap { it.items }

    // 등장: 항목이 차례로(머리는 DrawerHeader가 물방울로 맺힌다)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) { enter.snapTo(0f); return@LaunchedEffect }
        if (reduce) { enter.snapTo(items.size + sections.size + 2f); return@LaunchedEffect }
        enter.animateTo(items.size + sections.size + 2f, tween(120 + 55 * (items.size + 2), easing = LinearEasing))
    }

    // 고른 항목 방울: 각 항목의 위치를 재고, 머리·꼬리 스프링으로 옮긴다
    val tops = remember { mutableStateMapOf<NavItem, Float>() }
    var rowH by remember { mutableFloatStateOf(0f) }
    val target = current?.let { tops[it] }
    // 고른 항목 아래 물방울(연체 물리). 항목을 바꾸면 그 자리로 끌려가며 휘었다가 표면장력으로 둥글게 돌아온다.
    val blob = rememberSoftBlob()
    var boxW by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target, rowH, boxW) {
        val y = target ?: return@LaunchedEffect
        if (rowH > 0f && boxW > 0f) blob.moveTo(boxW / 2, y + rowH / 2, boxW, rowH, animate = !reduce)
    }

    Box(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(if (glass) c.bg.copy(alpha = GLASS_ALPHA) else c.bg),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            DrawerHeader(version, visible)
            Box(Modifier.onSizeChanged { boxW = it.width.toFloat() }) {
                // 방울 층(항목 뒤): 고른 항목을 따라다니는 연체 물방울
                val path = remember { Path() }
                Canvas(Modifier.matchParentSize()) {
                    blob.frame // 프레임마다 다시 그리기
                    if (blob.blob.ready) drawPath(blob.blob.path(path), c.ink)
                }
                Column {
                    var index = 0
                    sections.forEach { section ->
                        Spacer(Modifier.height(Gap.Section))
                        val titleOrder = index
                        // 제목 없는 묶음(맨 위 홈)은 제목 줄을 그리지 않는다
                        if (section.title.isNotEmpty()) Text(
                            section.title, color = c.muted, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 22.dp, bottom = 6.dp).graphicsLayer { alpha = (enter.value - titleOrder).coerceIn(0f, 1f) },
                        )
                        section.items.forEach { item ->
                            val order = ++index
                            DrawerRow(
                                item, item == current,
                                cover = cover@{
                                    val top = tops[item] ?: return@cover if (item == current) 1f else 0f
                                    blob.frame
                                    if (!blob.blob.ready || rowH <= 0f) (if (item == current) 1f else 0f)
                                    else (1f - kotlin.math.abs(blob.blob.cy - rowH / 2 - top) / rowH).coerceIn(0f, 1f)
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
                    // 맨 아래: 앱 설정. 기능 묶음과 선으로 나누되, 고르면 다른 항목처럼 잉크 방울이 옮겨 와 덮는다.
                    Spacer(Modifier.height(Gap.Section))
                    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 22.dp).background(c.line))
                    Spacer(Modifier.height(Gap.Item))
                    val settingsOrder = ++index
                    DrawerRow(
                        NavItem.SETTINGS, current == NavItem.SETTINGS,
                        cover = cover@{
                            val top = tops[NavItem.SETTINGS] ?: return@cover if (current == NavItem.SETTINGS) 1f else 0f
                            blob.frame
                            if (!blob.blob.ready || rowH <= 0f) (if (current == NavItem.SETTINGS) 1f else 0f)
                            else (1f - kotlin.math.abs(blob.blob.cy - rowH / 2 - top) / rowH).coerceIn(0f, 1f)
                        },
                        appear = { (enter.value - settingsOrder).coerceIn(0f, 1f) },
                        modifier = Modifier.onGloballyPositioned { tops[NavItem.SETTINGS] = it.positionInParent().y },
                        onClick = { onSelect(NavItem.SETTINGS) },
                    )
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
            // 선택 방울의 둥근 끝(반지름 24dp) 안쪽 깊숙이 아이콘을 둬 검은 경계와 붙어 보이지 않게 한다.
            .padding(start = 22.dp, end = 16.dp),
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
            NavIcon.HOME -> home(color, s)
            NavIcon.SPEED -> speed(color, s)
            NavIcon.CLEAN -> clean(color, s)
            NavIcon.REFRESH -> refresh(color, s)
            NavIcon.GAME -> game(color, s)
            NavIcon.MOON -> moon(color, s)
            NavIcon.BELL -> bell(color, s)
            NavIcon.PIXELS -> pixels(color, s)
            NavIcon.FOLD -> fold(color, s)
            NavIcon.APPS -> apps(color, s)
            NavIcon.ROTATE -> rotate(color, s)
            NavIcon.CAMERA -> camera(color, s)
            NavIcon.FLASK -> flask(color, s)
            NavIcon.INFO -> info(color, s)
            NavIcon.GEAR -> gear(color, s)
        }
    }
}

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x), u(y))

/*
 * 메뉴 아이콘: 24 격자, 선 굵기 1.8dp, 둥근 끝·둥근 이음. 바깥 여백 2~2.5를 지켜 다섯 아이콘의 크기감을 맞춘다.
 */

/** 고급 화면 배율 설정: 펼친 폴드(가운데 접는 선) 양쪽 화면에 같은 길이의 글줄 두 개(두 화면 글자 크기가 같다). */
private fun DrawScope.fold(c: Color, s: Stroke) {
    drawRoundRect(c, p(2.5f, 5f), Size(u(19f), u(14f)), CornerRadius(u(2.4f)), style = s)
    drawLine(c, p(12f, 5f), p(12f, 19f), s.width * 0.75f, StrokeCap.Round)
    for (x in listOf(5.5f, 15f)) {
        drawLine(c, p(x, 10f), p(x + 3.5f, 10f), s.width, StrokeCap.Round)
        drawLine(c, p(x, 14f), p(x + 2.2f, 14f), s.width, StrokeCap.Round)
    }
}

/** 앱별 화면 배율: 앱 네 개 중 오른쪽 아래 하나가 커지는 중(바깥으로 뻗는 화살표). */
private fun DrawScope.apps(c: Color, s: Stroke) {
    val r = CornerRadius(u(2f))
    drawRoundRect(c, p(3f, 3f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(14f, 3f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(3f, 14f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(14f, 14f), Size(u(7f), u(7f)), r, style = s)
    // 커지는 앱: 안쪽 대각선 화살표
    drawLine(c, p(16.2f, 18.8f), p(18.8f, 16.2f), s.width * 0.85f, StrokeCap.Round)
    drawLine(c, p(18.8f, 16.2f), p(16.6f, 16.2f), s.width * 0.85f, StrokeCap.Round)
    drawLine(c, p(18.8f, 16.2f), p(18.8f, 18.4f), s.width * 0.85f, StrokeCap.Round)
}

/**
 * 흔들어서 화면 회전: 비스듬히 든 폰과 그 둘레를 도는 두 호(화살촉은 호 끝의 진행 방향으로), 양옆에 흔들림 표시.
 */
private fun DrawScope.rotate(c: Color, s: Stroke) {
    val center = p(12f, 12f)
    // 폰: 7×12, -30도 기울임, 위쪽에 스피커 점
    withTransform({ rotate(-30f, center) }) {
        drawRoundRect(c, p(8.5f, 6f), Size(u(7f), u(12f)), CornerRadius(u(1.8f)), style = s)
        drawLine(c, p(11f, 8f), p(13f, 8f), s.width * 0.8f, StrokeCap.Round)
    }
    // 두 호: 반지름 9.5, 오른쪽 위와 왼쪽 아래(시계 방향으로 돈다)
    val rad = u(9.5f)
    fun arcWithHead(start: Float, sweep: Float) {
        drawArc(c, start, sweep, false, Offset(center.x - rad, center.y - rad), Size(rad * 2, rad * 2), style = s)
        val e = Math.toRadians((start + sweep).toDouble())
        val tip = Offset(center.x + rad * kotlin.math.cos(e).toFloat(), center.y + rad * kotlin.math.sin(e).toFloat())
        // 시계 방향 진행 방향 = (-sin, cos). 화살촉은 진행 방향 뒤로 ±40도
        val dir = Math.atan2(kotlin.math.cos(e), -kotlin.math.sin(e))
        val len = u(2.8f)
        for (a in listOf(dir + Math.PI - 0.7, dir + Math.PI + 0.7)) {
            drawLine(c, tip, Offset(tip.x + len * kotlin.math.cos(a).toFloat(), tip.y + len * kotlin.math.sin(a).toFloat()), s.width, StrokeCap.Round)
        }
    }
    arcWithHead(-75f, 55f)
    arcWithHead(105f, 55f)
}

/** 카메라 촬영음 끄기: 카메라와 오른쪽 위로 그은 사선(소리 끔). */
private fun DrawScope.camera(c: Color, s: Stroke) {
    val body = Path().apply {
        moveTo(u(4.5f), u(8f)); lineTo(u(8f), u(8f)); lineTo(u(9.5f), u(5.5f)); lineTo(u(14.5f), u(5.5f)); lineTo(u(16f), u(8f))
        lineTo(u(19.5f), u(8f)); quadraticBezierTo(u(21f), u(8f), u(21f), u(9.5f)); lineTo(u(21f), u(17.5f))
        quadraticBezierTo(u(21f), u(19f), u(19.5f), u(19f)); lineTo(u(4.5f), u(19f))
        quadraticBezierTo(u(3f), u(19f), u(3f), u(17.5f)); lineTo(u(3f), u(9.5f)); quadraticBezierTo(u(3f), u(8f), u(4.5f), u(8f)); close()
    }
    drawPath(body, c, style = s)
    drawCircle(c, u(3.4f), p(12f, 13.2f), style = s)
    drawLine(c, p(3f, 21.5f), p(21f, 3.5f), s.width, StrokeCap.Round)
}

/** 홈: 지붕과 문이 있는 집. */
private fun DrawScope.home(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(3.5f), u(11f)); lineTo(u(12f), u(4f)); lineTo(u(20.5f), u(11f))
        moveTo(u(5.5f), u(9.5f)); lineTo(u(5.5f), u(18.5f)); quadraticBezierTo(u(5.5f), u(20f), u(7f), u(20f)); lineTo(u(17f), u(20f))
        quadraticBezierTo(u(18.5f), u(20f), u(18.5f), u(18.5f)); lineTo(u(18.5f), u(9.5f))
    }
    drawPath(path, c, style = s)
    drawLine(c, p(10f, 20f), p(10f, 15f), s.width, StrokeCap.Round)
    drawLine(c, p(10f, 15f), p(14f, 15f), s.width, StrokeCap.Round)
    drawLine(c, p(14f, 15f), p(14f, 20f), s.width, StrokeCap.Round)
}

/** 애니메이션 속도: 계기판 반원과 오른쪽으로 기운 바늘. */
private fun DrawScope.speed(c: Color, s: Stroke) {
    drawArc(c, 180f, 180f, false, p(3f, 6f), Size(u(18f), u(18f)), style = s)
    drawLine(c, p(12f, 15f), p(16.8f, 9.6f), s.width, StrokeCap.Round)
    drawCircle(c, s.width * 0.9f, p(12f, 15f))
    drawLine(c, p(3f, 19f), p(21f, 19f), s.width * 0.8f, StrokeCap.Round)
}

/** 기본 앱 정리: 앱 칸 하나와 그 위에 겹친 빼기 동그라미. */
private fun DrawScope.clean(c: Color, s: Stroke) {
    drawRoundRect(c, p(3.5f, 3.5f), Size(u(11f), u(11f)), CornerRadius(u(2.6f)), style = s)
    drawCircle(c, u(5.2f), p(16f, 16f), style = s)
    drawLine(c, p(13.6f, 16f), p(18.4f, 16f), s.width, StrokeCap.Round)
}

/** 주사율 고정: 화면 틀 안에서 오른쪽으로 달리는 공과 그 뒤의 촘촘한 잔상 세 줄(빠르게 새로 그림). */
private fun DrawScope.refresh(c: Color, s: Stroke) {
    drawRoundRect(c, p(2.5f, 4.5f), Size(u(19f), u(15f)), CornerRadius(u(2.4f)), style = s)
    drawCircle(c, u(2.4f), p(15.5f, 12f))
    for ((i, x) in listOf(7f, 9.6f, 12.2f).withIndex()) {
        val h = 1.6f + i * 0.9f
        drawLine(c.copy(alpha = 0.45f + i * 0.2f), p(x, 12f - h), p(x, 12f + h), s.width * 0.8f, StrokeCap.Round)
    }
}

/** GOS 끄기: 게임 패드(왼쪽 십자 키, 오른쪽 버튼 둘). */
private fun DrawScope.game(c: Color, s: Stroke) {
    val pad = Path().apply {
        moveTo(u(7f), u(7f)); lineTo(u(17f), u(7f))
        cubicTo(u(20.5f), u(7f), u(22f), u(10f), u(22f), u(14f)); cubicTo(u(22f), u(17.5f), u(20f), u(19f), u(18.4f), u(19f))
        cubicTo(u(16.6f), u(19f), u(15.6f), u(16.5f), u(14.5f), u(16.5f)); lineTo(u(9.5f), u(16.5f))
        cubicTo(u(8.4f), u(16.5f), u(7.4f), u(19f), u(5.6f), u(19f)); cubicTo(u(4f), u(19f), u(2f), u(17.5f), u(2f), u(14f))
        cubicTo(u(2f), u(10f), u(3.5f), u(7f), u(7f), u(7f)); close()
    }
    drawPath(pad, c, style = s)
    drawLine(c, p(5.4f, 12f), p(9.4f, 12f), s.width, StrokeCap.Round)
    drawLine(c, p(7.4f, 10f), p(7.4f, 14f), s.width, StrokeCap.Round)
    drawCircle(c, u(1.1f), p(16f, 12.8f))
    drawCircle(c, u(1.1f), p(18.6f, 10.6f))
}

/** 잠자기 모드 끄기: 알림 종과 오른쪽 위의 작은 z(잠). */
private fun DrawScope.bell(c: Color, s: Stroke) {
    val body = Path().apply {
        moveTo(u(4.5f), u(17f))
        cubicTo(u(6f), u(15.5f), u(6f), u(13.5f), u(6f), u(11f))
        cubicTo(u(6f), u(7.4f), u(8.2f), u(5f), u(11.5f), u(5f))
        cubicTo(u(14.8f), u(5f), u(17f), u(7.4f), u(17f), u(11f))
        cubicTo(u(17f), u(13.5f), u(17f), u(15.5f), u(18.5f), u(17f)); close()
    }
    drawPath(body, c, style = s)
    drawLine(c, p(9.8f, 20f), p(13.2f, 20f), s.width, StrokeCap.Round)
    // z
    val z = Path().apply { moveTo(u(17.6f), u(2.6f)); lineTo(u(21.4f), u(2.6f)); lineTo(u(17.6f), u(6.6f)); lineTo(u(21.4f), u(6.6f)) }
    drawPath(z, c, style = Stroke(s.width * 0.8f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** 앱 다크 모드 강제: 초승달과 그 옆 작은 별 둘. */
private fun DrawScope.moon(c: Color, s: Stroke) {
    val crescent = Path().apply {
        moveTo(u(14.5f), u(3.6f))
        cubicTo(u(8.2f), u(3.4f), u(4.2f), u(8.6f), u(5.4f), u(14f))
        cubicTo(u(6.6f), u(19.4f), u(13.4f), u(22f), u(18.4f), u(18.2f))
        cubicTo(u(12.4f), u(18.4f), u(9.4f), u(10.6f), u(14.5f), u(3.6f)); close()
    }
    drawPath(crescent, c, style = s)
    drawCircle(c, u(1.1f), p(18.6f, 7f))
    drawCircle(c, u(0.8f), p(20.4f, 11.6f))
}

/** 해상도 낮추기: 화면 틀 안의 굵은 픽셀 네 칸(점이 줄어 굵어짐)과 옆의 작은 픽셀 두 칸. */
private fun DrawScope.pixels(c: Color, s: Stroke) {
    drawRoundRect(c, p(3f, 3f), Size(u(18f), u(18f)), CornerRadius(u(2.6f)), style = s)
    val r = CornerRadius(u(0.8f))
    drawRoundRect(c, p(6.5f, 6.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c, p(6.5f, 12.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c, p(12.5f, 12.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c.copy(alpha = 0.5f), p(13f, 7f), Size(u(1.8f), u(1.8f)), r)
    drawRoundRect(c.copy(alpha = 0.5f), p(15.4f, 9.4f), Size(u(1.8f), u(1.8f)), r)
}

/** 정보: 원 안의 i. */
private fun DrawScope.info(c: Color, s: Stroke) {
    drawCircle(c, u(9f), p(12f, 12f), style = s)
    drawLine(c, p(12f, 11f), p(12f, 16.5f), s.width, StrokeCap.Round)
    drawCircle(c, s.width * 0.75f, p(12f, 7.8f))
}

/** 앱 설정: 톱니바퀴(바깥 이 8개, 가운데 구멍). */
private fun DrawScope.gear(c: Color, s: Stroke) {
    val o = p(12f, 12f)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val x = kotlin.math.cos(a).toFloat(); val y = kotlin.math.sin(a).toFloat()
        drawLine(c, Offset(o.x + x * u(6.6f), o.y + y * u(6.6f)), Offset(o.x + x * u(9.2f), o.y + y * u(9.2f)), s.width * 1.6f, StrokeCap.Round)
    }
    drawCircle(c, u(6.4f), o, style = s)
    drawCircle(c, u(2.6f), o, style = s)
}

/** 조사 도구: 플라스크, 안에 액체 선과 기포 둘. */
private fun DrawScope.flask(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(9.5f), u(3.5f)); lineTo(u(9.5f), u(9.2f)); lineTo(u(4.6f), u(17.8f))
        quadraticBezierTo(u(3.9f), u(20.5f), u(6.6f), u(20.5f)); lineTo(u(17.4f), u(20.5f))
        quadraticBezierTo(u(20.1f), u(20.5f), u(19.4f), u(17.8f)); lineTo(u(14.5f), u(9.2f)); lineTo(u(14.5f), u(3.5f))
    }
    drawPath(path, c, style = s)
    drawLine(c, p(8f, 3.5f), p(16f, 3.5f), s.width, StrokeCap.Round)
    drawLine(c, p(6.8f, 14.5f), p(17.2f, 14.5f), s.width * 0.8f, StrokeCap.Round)
    drawCircle(c, u(0.9f), p(10.5f, 17.4f))
    drawCircle(c, u(0.65f), p(13.8f, 16.8f))
}

/** 덮이는 서랍·시트의 불투명도: 흐려진 뒤 화면이 살짝 비친다. */
internal const val GLASS_ALPHA = 0.84f
