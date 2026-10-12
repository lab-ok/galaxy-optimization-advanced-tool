package com.labok.goat.ui.home

import android.os.SystemClock

import com.labok.goat.ui.liquid.rememberLiquidHaptics
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.labok.goat.ui.about.AboutHeader
import com.labok.goat.AppInfo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.hypot
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.data.tweaks.Bloat
import com.labok.goat.data.tweaks.ForceDark
import com.labok.goat.data.tweaks.Gos
import com.labok.goat.data.tweaks.Resolution
import com.labok.goat.data.display.ResolutionState
import com.labok.goat.data.tweaks.RefreshRate
import com.labok.goat.data.appsize.AppDensityNotifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidShape
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.nav.NavGlyph
import com.labok.goat.ui.nav.NavItem
import com.labok.goat.ui.nav.NavSection
import com.labok.goat.ui.text.Copy

/**
 * 메인 화면: 권한·Shizuku 상태 한 줄과, 기능마다 아이콘·이름·지금 상태를 보여 주는 카드. 카드를 누르면 그 기능으로 간다.
 * 카드는 사이드바와 같은 묶음 순서로 놓고, 넓은 화면에서는 한 줄에 더 많이 놓는다.
 */
@Composable
internal fun Dashboard(contentPadding: PaddingValues, state: UiState, sections: List<NavSection>, onOpen: (NavItem, Offset) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val camera by CameraMute.status.collectAsStateWithLifecycle()
    val gos by Gos.status.collectAsStateWithLifecycle()
    val dark by ForceDark.status.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

    // 기본 앱 정리: 꺼 둔 목록 앱 수(Shizuku로 읽음, 못 읽으면 null)
    var cleaned by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tick, shizuku) {
        if (shizuku == ShizukuAccess.State.READY) cleaned = withContext(Dispatchers.IO) { Gos.read(); ForceDark.read(context); Bloat.scan()?.count { it.value } }
    }
    // 기능마다 지금 상태 한 줄(돌아올 때마다 다시 읽는다)
    val status: (NavItem) -> String = { item ->
        tick
        when (item) {
            NavItem.DPI_MATCH -> state.target?.let { (if (state.foldable) "외부 화면 $it dpi" else "$it dpi") + if (state.auto) " · 자동 적용" else "" }
                ?: if (state.foldable) "외부 화면 DPI를 정해 주세요" else "DPI를 정해 주세요"
            NavItem.APP_SIZE -> DensityShell.savedCount(context).let { if (it == 0) "바꾼 앱 없음" else "바꾼 앱 ${it}개" } +
                if (AppDensityNotifier.enabled(context)) " · 알림창 조절" else ""
            NavItem.ANIMATION -> AnimationSpeed.label(AnimationSpeed.read(context, AnimationSpeed.Kind.WINDOW))
            NavItem.SHAKE_ROTATE -> if (ShakeRotate.enabled(context)) "켜짐 · 민감도 ${ShakeRotate.level(context)}" + (if (ShakeRotate.largeOnly(context)) " · 큰 화면만" else "") else "꺼짐"
            NavItem.CAMERA_MUTE -> when (camera) {
                CameraMute.State.MUTED -> "촬영음 꺼 둠"
                CameraMute.State.FORCED -> "촬영음 강제로 남"
                CameraMute.State.NONE -> "강제 설정 없는 기기"
                CameraMute.State.UNKNOWN -> if (CameraMute.wanted(context)) "꺼 두기로 함" else "Shizuku로 끕니다"
            }
            NavItem.APP_CLEANUP -> cleaned?.let { if (it == 0) "꺼 둔 앱 없음" else "꺼 둔 앱 ${it}개" } ?: "쓰지 않는 기본 앱 끄기"
            NavItem.GOS -> when (gos) {
                Gos.State.OFF -> Copy.GOS_DASH_OFF
                Gos.State.ON -> Copy.GOS_DASH_ON
                Gos.State.NONE -> Copy.GOS_DASH_NONE
                Gos.State.UNKNOWN -> if (Gos.wanted(context)) Copy.GOS_DASH_WANTED else Copy.GOS_DASH_UNKNOWN
            }
            NavItem.DARK -> when (dark.forced) {
                true -> if (dark.night) Copy.DARK_DASH_ON else Copy.DARK_DASH_WAITING
                false -> Copy.DARK_DASH_OFF
                null -> Copy.DARK_DASH_UNKNOWN
            }
            NavItem.RESOLUTION -> when {
                !Resolution.supported(context) -> Copy.RES_DASH_FOLD
                ResolutionState.base(context) == null -> Copy.RES_DASH_ORIGINAL
                else -> Resolution.current(context).first.let { Copy.resDash(it, it * 100 / Resolution.base(context).first) }
            }
            NavItem.REFRESH -> if (!RefreshRate.supported(context)) Copy.REFRESH_UNSUPPORTED_SHORT else RefreshRate.read(context).let {
                if (it == RefreshRate.Choice.SMOOTH) Copy.refreshAlways(RefreshRate.max(context)) else it.label
            }
            else -> ""
        }
    }
    // 기능마다 켜져(쓰고) 있는지
    val active: (NavItem) -> Boolean = { item ->
        tick
        when (item) {
            NavItem.DPI_MATCH -> state.target != null && state.auto
            NavItem.APP_SIZE -> DensityShell.savedCount(context) > 0 || AppDensityNotifier.enabled(context)
            NavItem.ANIMATION -> AnimationSpeed.read(context, AnimationSpeed.Kind.WINDOW) != 1f
            NavItem.SHAKE_ROTATE -> ShakeRotate.enabled(context)
            NavItem.CAMERA_MUTE -> camera == CameraMute.State.MUTED || (camera == CameraMute.State.UNKNOWN && CameraMute.wanted(context))
            NavItem.APP_CLEANUP -> (cleaned ?: 0) > 0
            NavItem.GOS -> gos == Gos.State.OFF || (gos == Gos.State.UNKNOWN && Gos.wanted(context))
            NavItem.DARK -> dark.forced == true
            NavItem.RESOLUTION -> ResolutionState.base(context) != null
            NavItem.REFRESH -> RefreshRate.read(context) != RefreshRate.Choice.SYSTEM
            else -> false
        }
    }
    val features = sections.flatMap { it.items }.filter { it != NavItem.HOME && it != NavItem.LAB_TOOLS }
    // 홈이 처음 보인 시각: 이 시간 안에 그려진 카드만 등장 애니메이션을 한다
    val shownAt = remember { SystemClock.uptimeMillis() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        horizontalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "hello", span = { GridItemSpan(maxLineSpan) }) {
            GlassCard {
                // 앱 정보와 같은 리퀴드 로고: 맺히며 나타나고, 누르고 끌면 출렁인다
                AboutHeader(AppInfo.NAME, AppInfo.FULL_NAME)
                Spacer(Modifier.height(Gap.Item))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Copy.HOME_SUBTITLE, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge(Copy.HOME_PERMISSION, state.hasPermission)
                        Badge(Copy.HOME_SHIZUKU, shizuku == ShizukuAccess.State.READY)
                    }
                    Spacer(Modifier.height(Gap.Tight))
                    Text(
                        "켜진 기능 ${features.count(active)}개 / ${features.size}개",
                        color = c.muted, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        itemsIndexed(features, key = { _, it -> it.name }) { i, item ->
            // 처음 화면에 보인 카드만 차례로 맺히고, 나중에 스크롤로 들어오는 카드는 동그라미에서 터지듯 커지지 않게 바로 그린다
            BlobCard(item, status(item), active(item), order = i + 1, intro = SystemClock.uptimeMillis() - shownAt < INTRO_MS) { at -> onOpen(item, at) }
        }
    }
}

/** 상태 방울과 이름: 되어 있으면 잉크 방울이 통 차오르고, 아니면 빈 고리. */
@Composable
private fun Badge(label: String, on: Boolean) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val k by animateFloatAsState(if (on) 1f else 0f, Springs.pop(reduce), label = "badge")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            drawCircle(c.line, size.minDimension / 2, style = Stroke(1.5.dp.toPx()))
            if (k > 0.01f) drawCircle(c.ink, size.minDimension / 2 * k.coerceAtMost(1.25f))
        }
        Spacer(Modifier.width(6.dp))
        Text(label + if (on) "" else " 필요", color = if (on) c.ink else c.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 기능 카드(리퀴드): 카드 몸체가 연체 물방울이다. 처음엔 작은 방울에서 [order] 순서대로 맺히고, 누르면 그 자리가 움푹 들어갔다가
 * 표면장력으로 돌아온다. 켜진 기능은 테두리가 잉크색으로 굵어지고(바탕은 그대로), 오른쪽 위 상태 알약이 점에서 '켜짐' 알약으로 출렁이며 늘어난다.
 */
@Composable
private fun BlobCard(item: NavItem, status: String, on: Boolean, order: Int, intro: Boolean, onClick: (Offset) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val d = LocalDensity.current
    val body = rememberSoftBlob()
    val pill = rememberSoftBlob()
    val bodyPath = remember { Path() }
    val pillPath = remember { Path() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    // 카드의 화면 위치(누른 자리를 화면 기준으로 바꿔 다음 화면이 거기서 번지게)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val fill by animateFloatAsState(if (on) 1f else 0f, Springs.calm(reduce), label = "fill")
    val animateIn = intro && !reduce
    val text = remember { Animatable(if (animateIn) 0f else 1f) }
    val corner = with(d) { LiquidShape.card.toPx() }
    val pillW = with(d) { 52.dp.toPx() }
    val pillH = with(d) { 24.dp.toPx() }
    val dot = with(d) { 10.dp.toPx() }
    val inset = with(d) { 16.dp.toPx() }

    fun pillCenter() = Offset(box.width - inset - pillW / 2, inset + pillH / 2)

    // 등장: 작은 방울 → 카드 모양(차례로)
    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val w = box.width.toFloat(); val h = box.height.toFloat()
        if (animateIn) {
            body.moveTo(w / 2, h / 2, w * 0.3f, w * 0.3f, animate = false)
            kotlinx.coroutines.delay(60L * order)
        }
        // 큰 카드라 출렁임이 커 보인다: 처음 값(1.4·1.0, 너무 낭창)과 단단한 값(2.2·2.4) 사이로 살짝만 출렁이게 한다
        body.blob.gain = 1.7f
        body.blob.damping = 1.6f
        pill.blob.gain = 1.6f
        pill.blob.damping = 1.8f
        body.moveTo(w / 2, h / 2, w, h, animate = animateIn, corner = corner)
        if (animateIn) text.animateTo(1f, Springs.calm(reduce)) else text.snapTo(1f)
    }
    // 상태 알약: 꺼짐은 작은 점, 켜짐은 알약(출렁이며 늘어난다)
    LaunchedEffect(box, on) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val p = pillCenter()
        val (w, h) = if (on) pillW to pillH else dot to dot
        pill.moveTo(if (on) p.x else p.x + pillW / 2 - dot / 2, p.y, w, h, animate = !reduce && pill.blob.ready)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .onSizeChanged { box = it }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(reduce) {
                detectTapGestures(onPress = { pos ->
                    if (!reduce && body.blob.ready) {
                        haptics.press()
                        // 누른 자리를 안쪽으로 밀어 움푹 들어가게 한다
                        val cx = box.width / 2f; val cy = box.height / 2f
                        val dx = cx - pos.x; val dy = cy - pos.y; val l = hypot(dx, dy).coerceAtLeast(1f)
                        body.poke(pos.x, pos.y, dx / l * 720f, dy / l * 720f, box.height * 0.42f)
                    }
                    val released = tryAwaitRelease()
                    if (!reduce) haptics.release()
                    if (released) onClick(origin + pos)
                })
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            body.frame; pill.frame
            if (!body.blob.ready) return@Canvas
            body.blob.path(bodyPath)
            // 꺼짐: 유리판(표면 + 얇은 테두리) / 켜짐: 같은 유리판에 잉크색 굵은 테두리(바탕은 채우지 않는다)
            drawPath(bodyPath, c.surface)
            drawPath(bodyPath, lerp(c.line, c.ink, fill), style = Stroke((1f + 2f * fill).dp.toPx()))
            if (pill.blob.ready) {
                pill.blob.path(pillPath)
                if (on) drawPath(pillPath, c.ink) else drawPath(pillPath, c.muted, style = Stroke(1.5.dp.toPx()))
            }
        }
        val fg = c.ink
        val sub = c.muted
        Column(
            Modifier
                .padding(Gap.CardPadding)
                .graphicsLayer { alpha = text.value; translationY = (1f - text.value) * 8.dp.toPx() },
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.ink), contentAlignment = Alignment.Center) {
                NavGlyph(item.icon, c.onInk)
            }
            Spacer(Modifier.height(Gap.Item))
            // 모든 카드 높이를 같게: 제목 두 줄·상태 두 줄 자리를 늘 잡아 두고, 실제 글은 그 위쪽에 붙여 쓴다
            Box {
                Column(Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}) {
                    Text("", fontWeight = FontWeight.SemiBold, minLines = 2)
                    Text("", style = MaterialTheme.typography.bodySmall, minLines = 2)
                }
                Column {
                    Text(item.label, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(status, color = sub, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 알약 글자(켜짐일 때만, 알약이 다 늘어난 뒤 나타난다)
        val label by animateFloatAsState(if (on) 1f else 0f, Springs.calm(reduce), label = "pillText")
        if (label > 0.05f) Text(
            "켜짐", color = c.onInk, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp + 3.dp, end = 16.dp + 13.dp).graphicsLayer { alpha = label * text.value },
        )
    }
}

/** 홈이 처음 보인 뒤 이 시간 안에 나타난 카드만 등장 애니메이션을 한다(ms). */
private const val INTRO_MS = 1200L
