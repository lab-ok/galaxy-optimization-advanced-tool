package com.labok.goat.ui.home

import com.labok.goat.data.appsize.DensityShell
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.AppInfo
import com.labok.goat.R
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.display.ResolutionState
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.VolumeRotate
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.data.tweaks.Bloat
import com.labok.goat.data.tweaks.Doze
import com.labok.goat.data.tweaks.ForceDark
import com.labok.goat.data.tweaks.Gos
import com.labok.goat.data.tweaks.RefreshRate
import com.labok.goat.data.tweaks.Resolution
import com.labok.goat.data.update.Updater
import com.labok.goat.ui.about.LiquidGoatLogo
import com.labok.goat.ui.about.UpdateCard
import com.labok.goat.ui.about.worthShowing
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Inset
import com.labok.goat.ui.components.Radius
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.components.Tile
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidShape
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.poke
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.nav.FeatureSupport
import com.labok.goat.ui.nav.NavGlyph
import com.labok.goat.ui.nav.NavItem
import com.labok.goat.ui.nav.NavSection
import com.labok.goat.ui.text.Copy
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 메인 화면: 권한·Shizuku 상태 한 줄과, 기능마다 아이콘·이름·지금 상태를 보여 주는 카드. 카드를 누르면 그 기능으로 간다.
 * 카드는 사이드바와 같은 묶음 순서로 놓고, 넓은 화면에서는 한 줄에 더 많이 놓는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Dashboard(
    contentPadding: PaddingValues,
    state: UiState,
    sections: List<NavSection>,
    onOpen: (NavItem, Offset) -> Unit,
    /** 시스템 설정 권한 안내(시작하기)를 연다. */
    onSetup: () -> Unit = {},
    /** Shizuku 연결 안내를 연다. */
    onConnect: () -> Unit = {}
) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val powerInput by PowerRotate.status.collectAsStateWithLifecycle()
    val camera by CameraMute.status.collectAsStateWithLifecycle()
    val gos by Gos.status.collectAsStateWithLifecycle()
    val dark by ForceDark.status.collectAsStateWithLifecycle()
    val doze by Doze.status.collectAsStateWithLifecycle()
    val update by Updater.status.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    // 홈을 열거나 홈으로 돌아올 때 새 버전을 조용히 확인한다(앱을 새로 켜면 바로, 그 뒤로는 30분에 한 번).
    // 새 버전이 있으면 홈 머리 바로 아래에 업데이트 카드가 뜬다.
    LaunchedEffect(tick) { withContext(Dispatchers.IO) { Updater.autoCheck(context) } }

    // 기본 앱 정리: 꺼 둔 목록 앱 수(Shizuku로 읽음, 못 읽으면 null)
    var cleaned by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tick, shizuku) {
        if (shizuku == ShizukuAccess.State.READY) {
            cleaned = withContext(Dispatchers.IO) {
                // GOS·촬영음 강제 설정이 없는 기기는 기억해 두고 메뉴에서 숨긴다(실험실이면 보임, FeatureSupport)
                Gos.read().takeIf { it != Gos.State.UNKNOWN }?.let { FeatureSupport.note(context, NavItem.GOS, it != Gos.State.NONE) }
                Doze.read()
                // 앱별 화면 배율 함수가 없는 기기(삼성이 아니거나 폴드 기능이 없는 기기)도 미리 알아 둔다
                runCatching { DensityShell.supported() }.getOrNull()?.let { if (!it) FeatureSupport.note(context, NavItem.APP_SIZE, false) }
                // 촬영음도 실제 값을 읽는다(안 읽으면 '읽지 못함'에 머물러 설정 없는 기기에서도 '꺼 두기로 함'이 떴다)
                CameraMute.read().takeIf { it != CameraMute.State.UNKNOWN }?.let { FeatureSupport.note(context, NavItem.CAMERA_MUTE, it != CameraMute.State.NONE) }
                ForceDark.read(context)
                Bloat.scan()?.count { it.value }
            }
        }
    }
    // 기능마다 지금 상태 한 줄(돌아올 때마다 다시 읽는다)
    val status: (NavItem) -> String = { item ->
        tick
        when (item) {
            NavItem.DPI_MATCH -> state.target?.let { (if (state.foldable) Copy.text(R.string.home_dash_dpi_outer_fmt, it) else "$it dpi") + if (state.auto) Copy.text(R.string.home_dash_suffix_auto) else "" }
                ?: if (state.foldable) Copy.text(R.string.home_dash_dpi_outer_needed) else Copy.text(R.string.home_dash_dpi_needed)
            NavItem.APP_SIZE -> if (DensityShell.rejected(context)) {
                Copy.text(R.string.home_dash_unsupported)
            } else {
                DensityShell.savedCount(context).let { if (it == 0) Copy.text(R.string.home_dash_sized_none) else Copy.text(R.string.home_dash_sized_count_fmt, it) } +
                    if (AppDensityNotifier.enabled(context)) Copy.text(R.string.home_dash_suffix_notify) else ""
            }
            NavItem.ANIMATION -> AnimationSpeed.label(AnimationSpeed.read(context, AnimationSpeed.Kind.WINDOW))
            NavItem.SHAKE_ROTATE -> if (ShakeRotate.enabled(context)) Copy.text(R.string.home_dash_shake_on_fmt, ShakeRotate.level(context)) + (if (ShakeRotate.largeOnly(context)) Copy.text(R.string.home_dash_suffix_large_only) else "") else Copy.DASH_OFF
            NavItem.POWER_ROTATE -> when {
                !PowerRotate.enabled(context) -> Copy.DASH_OFF
                !PowerRotate.canWriteRotation(context) || !PowerRotate.canDraw(context) -> Copy.text(R.string.power_rotate_permission_needed)
                powerInput != PowerRotate.Status.LISTENING -> Copy.text(R.string.power_rotate_waiting)
                else -> Copy.text(if (ShakeRotate.autoRotateOn(context)) R.string.power_rotate_auto_on else R.string.power_rotate_locked)
            }
            NavItem.CAMERA_MUTE -> when (camera) {
                CameraMute.State.MUTED -> Copy.text(R.string.home_dash_camera_muted)
                CameraMute.State.FORCED -> Copy.text(R.string.home_dash_camera_forced)
                CameraMute.State.NONE -> Copy.text(R.string.home_dash_camera_none)
                CameraMute.State.UNKNOWN -> if (CameraMute.wanted(context)) Copy.DASH_WANTED_OFF else Copy.DASH_NEED_SHIZUKU
            }
            // 사용 통계: 권한이 없을 때만 알린다(있으면 기록을 세는 중이라 따로 적을 상태가 없다)
            NavItem.LAB_STATS -> if (AppUsage.hasAccess(context)) "" else Copy.text(R.string.home_dash_stats_need_access)
            NavItem.APP_CLEANUP -> cleaned?.let { if (it == 0) Copy.text(R.string.home_dash_cleanup_none) else Copy.text(R.string.home_dash_cleanup_count_fmt, it) } ?: Copy.BLOAT_INTRO_TITLE
            NavItem.GOS -> when (gos) {
                Gos.State.OFF -> Copy.GOS_DASH_OFF
                Gos.State.ON -> Copy.GOS_DASH_ON
                Gos.State.NONE -> Copy.GOS_DASH_NONE
                Gos.State.UNKNOWN -> if (Gos.wanted(context)) Copy.DASH_WANTED_OFF else Copy.DASH_NEED_SHIZUKU
            }
            NavItem.DOZE -> when (doze) {
                Doze.State.OFF -> Copy.DOZE_DASH_OFF
                Doze.State.ON -> Copy.DOZE_DASH_ON
                Doze.State.UNKNOWN -> if (Doze.wanted(context)) Copy.DASH_WANTED_OFF else Copy.DASH_NEED_SHIZUKU
            }
            NavItem.DARK -> when (dark.forced) {
                true -> if (dark.night) Copy.DARK_DASH_ON else Copy.DARK_DASH_WAITING
                false -> Copy.DASH_OFF
                null -> Copy.DASH_NEED_SHIZUKU
            }
            NavItem.RESOLUTION -> when {
                !Resolution.supported(context) -> Copy.RES_DASH_FOLD
                // 저장된 원래 해상도가 남아 있어도 지금 해상도가 원래와 같으면(밖에서 되돌린 경우) 원래 해상도다
                ResolutionState.base(context) == null || Resolution.current(context) == Resolution.base(context) -> Copy.RES_DASH_ORIGINAL
                else -> Resolution.current(context).first.let { w -> Resolution.base(context).first.let { b -> Copy.resDash(w, w * 100 / b, Resolution.points(w, b)) } }
            }
            NavItem.REFRESH -> if (!RefreshRate.supported(context)) {
                Copy.REFRESH_UNSUPPORTED_SHORT
            } else if (!RefreshRate.changedByGoat(context)) {
                Copy.REFRESH_SYSTEM
            } else {
                RefreshRate.read(context).let {
                    if (it == RefreshRate.Choice.SMOOTH) Copy.refreshAlways(RefreshRate.max(context)) else it.label
                }
            }
            NavItem.STATUS_ICONS -> IconBlacklist.setting.read(context).let { raw ->
                IconBlacklist.Slot.entries.count { IconBlacklist.hidden(raw, it) }
            }.let { if (it == 0) Copy.ICONS_DASH_NONE else Copy.iconsDash(it) }
            // 기기마다 기본값이 달라(NFC가 원래 빠진 기기 등) GOAT로 바꾼 적이 없으면 '기본 설정'으로 둔다
            NavItem.AIRPLANE -> if (!AirplaneRadios.setting.changed(context)) {
                Copy.AIRPLANE_DASH_DEFAULT
            } else {
                AirplaneRadios.setting.read(context).let { raw ->
                    AirplaneRadios.Radio.entries.filter { AirplaneRadios.kept(raw, it) }.map { it.label }
                }.let { if (it.isEmpty()) Copy.AIRPLANE_DASH_NONE else Copy.airplaneDash(it) }
            }
            NavItem.VOLUME_ROTATE -> if (VolumeRotate.enabled(context)) Copy.VOLUME_DASH_ON + if (VolumeRotate.invert(context)) Copy.text(R.string.home_dash_suffix_inverted) else "" else Copy.DASH_OFF
            NavItem.AUTO_FLEX -> if (AutoFlex.enabled(context)) Copy.text(R.string.home_dash_flex_on_fmt, AutoFlex.min(context), AutoFlex.max(context)) + (if (AutoFlex.click(context)) Copy.text(R.string.home_dash_suffix_flex_click) else "") else Copy.DASH_OFF
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
            NavItem.POWER_ROTATE -> PowerRotate.enabled(context)
            NavItem.CAMERA_MUTE -> camera == CameraMute.State.MUTED || (camera == CameraMute.State.UNKNOWN && CameraMute.wanted(context))
            NavItem.APP_CLEANUP -> (cleaned ?: 0) > 0
            NavItem.GOS -> gos == Gos.State.OFF || (gos == Gos.State.UNKNOWN && Gos.wanted(context))
            NavItem.DARK -> dark.forced == true
            NavItem.DOZE -> doze == Doze.State.OFF || (doze == Doze.State.UNKNOWN && Doze.wanted(context))
            NavItem.RESOLUTION -> ResolutionState.base(context) != null && Resolution.current(context) != Resolution.base(context)
            NavItem.REFRESH -> RefreshRate.changedByGoat(context) && RefreshRate.read(context) != RefreshRate.Choice.SYSTEM
            NavItem.STATUS_ICONS -> IconBlacklist.setting.read(context).let { raw ->
                IconBlacklist.Slot.entries.any { IconBlacklist.hidden(raw, it) }
            }
            NavItem.AIRPLANE -> AirplaneRadios.setting.changed(context) &&
                AirplaneRadios.setting.read(context).let { raw ->
                    AirplaneRadios.Radio.entries.any { AirplaneRadios.kept(raw, it) }
                }
            NavItem.VOLUME_ROTATE -> VolumeRotate.enabled(context)
            NavItem.AUTO_FLEX -> AutoFlex.enabled(context)
            else -> false
        }
    }
    val features = sections.flatMap { it.items }.filter { it != NavItem.HOME && it !in NavItem.LAB_ITEMS }
    // 보기 방식: 전체(기존 순서)·활성화(켜진 기능만)·비활성화(꺼진 기능만). 고른 방식은 저장해 다음에도 그대로 연다.
    val homePrefs = remember { context.getSharedPreferences("home", Context.MODE_PRIVATE) }
    var view by remember {
        // 1.4.4의 '켜진 기능만'(only_on)을 활성화로 이어받는다
        mutableStateOf(
            homePrefs.getString("view", null)?.let { runCatching { HomeView.valueOf(it) }.getOrNull() }
                ?: if (homePrefs.getBoolean("only_on", false)) HomeView.ON else HomeView.ALL
        )
    }
    // 보기를 마지막으로 바꾼 시각: 이 시간 안에 새로 그려지는 카드만 방울처럼 맺힌다(스크롤로 들어오는 카드는 그대로)
    var viewChangedAt by remember { mutableLongStateOf(0L) }
    val reduce = LocalReduceMotion.current
    val shown = when (view) {
        HomeView.ALL -> features
        HomeView.ON -> features.filter(active)
        HomeView.OFF -> features.filterNot(active)
    }
    // 자주 쓰는 기능: 홈에서 기능을 연 횟수(기기 안에만 저장). 펴 둔 칸: 처음에는 첫 칸만 펴 둔다
    var openCounts by remember { mutableStateOf(features.associate { it.name to homePrefs.getInt("open_" + it.name, 0) }) }
    var openSections by remember { mutableStateOf(homePrefs.getStringSet("open_sections", null)?.toSet() ?: setOfNotNull(sections.firstOrNull { it.title.isNotEmpty() }?.title)) }
    val open: (NavItem, Offset) -> Unit = { item, at ->
        val n = (openCounts[item.name] ?: 0) + 1
        homePrefs.edit().putInt("open_" + item.name, n).apply()
        openCounts = openCounts + (item.name to n)
        onOpen(item, at)
    }
    val favs = run {
        val used = shown.filter { (openCounts[it.name] ?: 0) > 0 }.sortedByDescending { openCounts[it.name] ?: 0 }
        (used + shown.filter(active)).distinct().take(4)
    }
    // 홈이 처음 보인 시각: 이 시간 안에 그려진 카드만 등장 애니메이션을 한다
    val shownAt = remember { SystemClock.uptimeMillis() }

    // 타일 칸 폭: 가장 긴 기능 이름이 한 줄에 다 보이는 폭(아이콘·여백 포함)보다 좁아지지 않게, 화면 폭에 맞춰 칸 수를 정한다.
    // 그보다 좁은 화면에서는 한 칸으로 두고, 상태 글처럼 긴 글만 흐르게 한다.
    val titleMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val minTile = with(LocalDensity.current) {
        val widest = features.maxOfOrNull { titleMeasurer.measure(it.label, titleStyle).size.width } ?: 0
        widest.toDp() + 40.dp + 12.dp + Gap.CardPadding * 2 + 8.dp
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minTile),
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        horizontalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "hello", span = { GridItemSpan(maxLineSpan) }) {
            // 홈 머리: 카드 없이 바탕 위에 바로 놓는 요약(갤럭시 '디바이스 케어'식). 왼쪽 염소, 가운데 상태 한 문장, 오른쪽 켜진 비율 고리,
            // 아래 권한·Shizuku 상태 알약.
            val on = features.count(active)
            val headline = if (on == 0) Copy.HOME_VIEW_EMPTY_TITLE else stringResource(R.string.home_headline_on_count_fmt, on)
            val pills: @Composable () -> Unit = {
                // 폭이 모자라면 다음 줄로 넘긴다
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap.Tight * 2), verticalArrangement = Arrangement.spacedBy(Gap.Tight * 2)) {
                    // 아직 안 된 알약을 누르면 그 안내로 바로 간다
                    StatusPill(Copy.HOME_PERMISSION, state.hasPermission, onSetup)
                    StatusPill(Copy.HOME_SHIZUKU, shizuku == ShizukuAccess.State.READY, onConnect)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Gap.Tight, vertical = Gap.Item)) {
                if (maxWidth >= HERO_WIDE) {
                    // 넓은 화면: 왼쪽 로고, 가운데 이름·상태·안내·알약, 오른쪽 고리
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 앱 정보 로고와 같은 리퀴드 상자(누르면 출렁이고 표정이 바뀐다, 열 번이면 실험실)
                        LiquidGoatLogo(68.dp, idle = true)
                        // 아이콘과 글은 다른 덩어리라 글 사이 간격보다 넓게 띄운다
                        Spacer(Modifier.width(Space.Group))
                        Column(Modifier.weight(1f)) {
                            Text(AppInfo.FULL_NAME, color = c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(headline, color = c.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(Gap.Item))
                            // 알약은 글 줄 시작에 맞춘다(염소 아래로 튀어나오지 않게)
                            pills()
                        }
                        Spacer(Modifier.width(Space.IconText))
                        ActiveRing(on, features.size, 64.dp)
                    }
                } else {
                    // 좁은 화면: 위 줄은 로고·이름·상태·고리만 두고, 안내와 알약은 아래에 폭 전체로 펼친다
                    // (세 덩어리를 한 줄에 두면 가운데 글이 너무 좁아져 줄마다 잘게 접혔다)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiquidGoatLogo(54.dp, idle = true)
                            Spacer(Modifier.width(Space.IconText))
                            Column(Modifier.weight(1f)) {
                                Text(AppInfo.NAME, color = c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(headline, color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(Space.IconText))
                            ActiveRing(on, features.size, 54.dp)
                        }
                        Spacer(Modifier.height(Gap.Item))
                        pills()
                    }
                }
            }
        }
        // 새 버전이 있으면 홈 머리 바로 아래에 업데이트 카드(바로 내려받아 설치)
        if (update.worthShowing()) item(key = "update", span = { GridItemSpan(maxLineSpan) }) { UpdateCard(manual = false) }
        // 권한이 없으면(처음 쓰는 사람) 머리 바로 아래에 시작하기 카드: 첫 안내 창을 닫아도 여기서 다시 연다
        if (!state.loading && !state.hasPermission) {
            item(key = "setup", span = { GridItemSpan(maxLineSpan) }) {
                GlassCard(accent = c.ink) {
                    Text(Copy.HOME_SETUP_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HOME_SETUP_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.HOME_SETUP_ACTION, modifier = Modifier.fillMaxWidth(), onClick = onSetup)
                }
            }
        }
        item(key = "view", span = { GridItemSpan(maxLineSpan) }) {
            // 머리와 전환 사이, 전환과 타일 사이 여백을 같게 둔다(머리 아래 여백 Gap.Item + 칸 간격 Gap.Card)
            Box(Modifier.fillMaxWidth().padding(bottom = Gap.Item)) {
                LiquidChips(
                    options = HomeView.entries,
                    selected = view,
                    label = { it.label },
                    onSelect = {
                        if (it != view) viewChangedAt = SystemClock.uptimeMillis()
                        view = it
                        homePrefs.edit().putString("view", it.name).apply()
                    }
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                GlassCard {
                    Text(if (view == HomeView.OFF) Copy.HOME_VIEW_EMPTY_OFF_TITLE else Copy.HOME_VIEW_EMPTY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(if (view == HomeView.OFF) Copy.HOME_VIEW_EMPTY_OFF_BODY else Copy.HOME_VIEW_EMPTY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // 11-04 소장님 홈 시안 5번: 위에 '자주 쓰는 기능'(많이 연 기능 4개, 아직 없으면 켜 둔 기능)을 타일로 고정하고, 그 아래 칸(카테고리)은
        // 접고 펴는 카드로 둔다. 칸을 펴면 기능이 한 줄씩(아이콘·이름·지금 상태) 보이고, 누르면 그 기능으로 간다. 펴 둔 칸은 기억한다.
        // 보기(전체·활성화·비활성화)는 칸 안의 줄과 자주 쓰는 기능에 함께 적용하고, 비는 칸은 숨긴다
        // 11-04 소장님: 자주 쓰는 기능과 카테고리 아코디언은 '전체' 보기에서만. 활성화·비활성화 보기는 예전처럼 기능 타일만 늘어놓는다
        if (view == HomeView.ALL) {
            if (favs.isNotEmpty()) {
                item(key = "fav_title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(Copy.HOME_FAVORITES, color = c.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = Gap.Tight, top = Gap.Item))
                }
                itemsIndexed(favs, key = { _, it -> "fav_" + it.name }) { i, item ->
                    Box(Modifier.animateItem(fadeInSpec = if (reduce) snap() else tween(220, delayMillis = i * 28), placementSpec = if (reduce) snap() else spring(UiMotion.ratio(0.62f), UiMotion.stiffness(380f, 0.62f)), fadeOutSpec = if (reduce) snap() else tween(160))) {
                        BlobCard(item, status(item), active(item), order = i + 1, intro = SystemClock.uptimeMillis() - shownAt < INTRO_MS) { at -> open(item, at) }
                    }
                }
            }
            sections.filter { it.title.isNotEmpty() }.forEach { section ->
                val inSection = section.items.filter { it in shown }
                if (inSection.isEmpty()) return@forEach
                item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                    val expanded = section.title in openSections
                    CategoryCard(
                        title = section.title,
                        count = "(${inSection.count(active)}/${inSection.size})",
                        features = inSection.map { it.label to active(it) },
                        expanded = expanded,
                        modifier = Modifier.animateItem(fadeInSpec = if (reduce) snap() else tween(220), placementSpec = if (reduce) snap() else spring(UiMotion.ratio(0.62f), UiMotion.stiffness(380f, 0.62f)), fadeOutSpec = if (reduce) snap() else tween(160)),
                        onToggle = {
                            openSections = if (expanded) openSections - section.title else openSections + section.title
                            homePrefs.edit().putStringSet("open_sections", openSections).apply()
                        }
                    ) {
                        inSection.forEach { item ->
                            CategoryRow(item, status(item), active(item)) { at -> open(item, at) }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(shown, key = { _, it -> it.name }) { i, item ->
                Box(Modifier.animateItem(fadeInSpec = if (reduce) snap() else tween(220, delayMillis = i * 28), placementSpec = if (reduce) snap() else spring(UiMotion.ratio(0.62f), UiMotion.stiffness(380f, 0.62f)), fadeOutSpec = if (reduce) snap() else tween(160))) {
                    BlobCard(item, status(item), active(item), order = i + 1, intro = SystemClock.uptimeMillis() - shownAt < INTRO_MS) { at -> open(item, at) }
                }
            }
        }
    }
}

/**
 * 홈 칸(카테고리) 카드: 변경 기록(앱 설정 → 정보)의 아코디언과 같은 모양(11-04 소장님 "변경사항의 그것처럼").
 * 머리 줄 전체(칸 이름 · 그 칸 기능 이름 요약 · 아래 화살표)를 누르면 접고 펴고, 펼침이 끝날 무렵 카드 전체가 보이게 목록을 굴린다.
 * 펼친 안쪽 [content]는 기능 줄([CategoryRow])이다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    title: String,
    /** 켜진 기능 수/전체 기능 수, 예: (1/3). */
    count: String,
    /** 둘째 줄 기능 이름 요약: 켜진 기능은 굵게·진하게, 꺼진 기능은 옅게(11-04 소장님 "구분감 있게"). */
    features: List<Pair<String, Boolean>>,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = LocalLiquid.current
    val bring = remember { BringIntoViewRequester() }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded && touched) {
            delay(320)
            bring.bringIntoView()
        }
    }
    GlassCard(modifier = modifier.fillMaxWidth().bringIntoViewRequester(bring), padding = Gap.Flush) {
        Row(
            Modifier.fillMaxWidth().clickable {
                touched = true
                onToggle()
            }.semantics { stateDescription = if (expanded) Copy.HOME_SECTION_OPEN else Copy.HOME_SECTION_CLOSED }
                .padding(Gap.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.width(Space.Inline))
                    Text(count, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
                val summary = remember(features, c.ink, c.muted) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        features.forEachIndexed { i, (name, on) ->
                            if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = c.muted.copy(alpha = 0.5f))) { append(" · ") }
                            withStyle(
                                if (on) {
                                    androidx.compose.ui.text.SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)
                                } else {
                                    androidx.compose.ui.text.SpanStyle(color = c.muted.copy(alpha = 0.55f))
                                }
                            ) { append(com.labok.goat.ui.liquid.keepAll(name)) }
                        }
                    }
                }
                androidx.compose.material3.Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(expanded, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = Gap.CardPadding, end = Gap.CardPadding, bottom = Gap.Item)) { content() }
        }
    }
}

/** 칸 안의 기능 한 줄: 켜짐 점·이름·지금 상태. 누른 자리에서 다음 화면이 번진다. */
@Composable
private fun CategoryRow(item: NavItem, status: String, on: Boolean, onClick: (Offset) -> Unit) {
    val c = LocalLiquid.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Box))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .onSizeChanged { size = it }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    onClick(origin + Offset(size.width / 2f, size.height / 2f))
                    true
                }
            }
            .pointerInput(item) { detectTapGestures { pos -> onClick(origin + pos) } }
            .padding(top = Gap.RowPad, bottom = Gap.RowPad, end = Space.Unit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 기능마다 아이콘(11-04): 켜진 기능은 잉크 바탕에 흰 아이콘, 꺼진 기능은 옅은 바탕
        Box(Modifier.size(Tile.Icon).clip(RoundedCornerShape(Tile.IconRadius)).background(if (on) c.ink else c.ink.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            com.labok.goat.ui.nav.NavGlyph(item.icon, if (on) c.bg else c.ink.copy(alpha = 0.7f), Modifier.size(Tile.IconGlyph))
        }
        Spacer(Modifier.width(Space.IconText))
        Column(Modifier.weight(1f)) {
            Text(item.label, color = c.ink, style = MaterialTheme.typography.bodyLarge)
            Text(status, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) c.ink else c.line))
    }
}

/** 이 폭부터 홈 머리를 한 줄(로고·글·고리)로 놓는다. 그보다 좁으면 두 줄로 나눈다. */
private val HERO_WIDE = 420.dp

/** 홈 타일 보기 방식. */
private enum class HomeView(private val labelOf: () -> String) { ALL({ Copy.HOME_VIEW_ALL }), ON({ Copy.HOME_VIEW_ON }), OFF({ Copy.HOME_VIEW_OFF }); val label get() = labelOf() }

/** 켜진 기능 비율 고리: 잉크 호가 출렁이며 차오르고, 가운데에 켜진 수/전체 수. */
@Composable
private fun ActiveRing(on: Int, total: Int, diameter: Dp) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val k by animateFloatAsState(if (total == 0) 0f else on.toFloat() / total, Springs.water(reduce), label = "ring")
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = (diameter / 10.7f).toPx()
            val r = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(if (c.dark) c.muted.copy(alpha = 0.35f) else c.line, 0f, 360f, false, tl, r, style = Stroke(w))
            if (k > 0.005f) drawArc(c.ink, -90f, 360f * k.coerceIn(0f, 1.05f), false, tl, r, style = Stroke(w, cap = StrokeCap.Round))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$on", color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("/$total", color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 상태 알약: 되어 있으면 잉크 점이 통 차오르고, 아니면 빈 고리와 '필요'. */
@Composable
private fun StatusPill(label: String, on: Boolean, onClick: () -> Unit = {}) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val k by animateFloatAsState(if (on) 1f else 0f, Springs.pop(reduce), label = "pill")
    Row(
        Modifier
            .clip(CircleShape)
            .then(if (on) Modifier else Modifier.clickable(onClick = onClick))
            .border(1.dp, if (c.dark) c.muted.copy(alpha = 0.5f) else c.line, CircleShape)
            .padding(Inset.Pill),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(if (c.dark) c.muted else c.line, size.minDimension / 2, style = Stroke(1.2.dp.toPx()))
            if (k > 0.01f) drawCircle(c.ink, size.minDimension / 2 * k.coerceAtMost(1.25f))
        }
        Spacer(Modifier.width(Space.Inline))
        Text(if (on) label else stringResource(R.string.home_pill_needed_fmt, label), color = if (on) c.ink else c.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
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
    val bodyPath = remember { Path() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    // 카드의 화면 위치(누른 자리를 화면 기준으로 바꿔 다음 화면이 거기서 번지게)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val fill by animateFloatAsState(if (on) 1f else 0f, Springs.calm(reduce), label = "fill")
    val animateIn = intro && !reduce
    val text = remember { Animatable(if (animateIn) 0f else 1f) }
    val corner = with(d) { LiquidShape.card.toPx() }
    // 눌러서 다음 화면이 누른 자리에서 물방울로 번지기 시작하면, 처음 몇 프레임 작은 방울이 글자 일부만 덮어 글자가 깨져 보인다.
    // 번지기 시작하는 순간 카드 글자를 먼저 거둬 방울이 빈 카드 위로 번지게 한다. 곧바로 홈으로 돌아와도 글자가 남게 잠시 뒤 되살린다.
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(LEAVE_RESET_MS)
            leaving = false
        }
    }
    val openAt: (Offset) -> Unit = { at ->
        if (!reduce) leaving = true
        onClick(at)
    }

    // 등장: 작은 방울 → 카드 모양(차례로)
    LaunchedEffect(box) {
        if (box == IntSize.Zero) return@LaunchedEffect
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        if (animateIn) {
            body.moveTo(w / 2, h / 2, w * 0.3f, w * 0.3f, animate = false)
            delay(60L * order)
        }
        // 큰 카드라 출렁임이 커 보인다: 처음 값(1.4·1.0, 너무 낭창)과 단단한 값(2.2·2.4) 사이로 살짝만 출렁이게 한다
        body.blob.gain = 1.7f
        body.blob.damping = 1.6f
        body.moveTo(w / 2, h / 2, w, h, animate = animateIn, corner = corner)
        if (animateIn) text.animateTo(1f, Springs.calm(reduce)) else text.snapTo(1f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { box = it }
            .onGloballyPositioned { origin = it.positionInRoot() }
            // 손가락 대신 TalkBack 등 접근성 서비스로도 누를 수 있게 버튼으로 알린다(카드 가운데에서 번진다)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    openAt(origin + Offset(box.width / 2f, box.height / 2f))
                    true
                }
            }
            .pointerInput(reduce) {
                detectTapGestures(onPress = { pos ->
                    if (!reduce && body.blob.ready) {
                        haptics.press()
                        // 누른 자리를 안쪽으로 밀어 움푹 들어가게 한다
                        val cx = box.width / 2f
                        val cy = box.height / 2f
                        val dx = cx - pos.x
                        val dy = cy - pos.y
                        val l = hypot(dx, dy).coerceAtLeast(1f)
                        body.poke(pos.x, pos.y, dx / l * 720f, dy / l * 720f, box.height * 0.42f)
                    }
                    val released = tryAwaitRelease()
                    if (!reduce) haptics.release()
                    if (released) openAt(origin + pos)
                })
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            body.frame
            if (!body.blob.ready) return@Canvas
            body.blob.path(bodyPath)
            // 꺼짐·켜짐 모두 같은 굵기(3dp) 테두리. 켜짐은 잉크색 쪽으로 연하게 물든다(바탕은 채우지 않는다)
            drawPath(bodyPath, c.surface)
            drawPath(bodyPath, lerp(c.line, c.ink, 0.5f * fill), style = Stroke(3.dp.toPx()))
        }
        val fg = c.ink
        val sub = c.muted
        // 아이콘 오른쪽에 제목과 지금 상태를 한 줄씩 둔다. 칸보다 길면 글자가 천천히 흘러 끝까지 보인다.
        // 켜짐은 굵은 테두리로만 보이고, 모든 카드가 같은 높이(두 줄)라 빈칸이 생기지 않는다.
        Row(
            Modifier
                .padding(Gap.CardPadding)
                .graphicsLayer {
                    alpha = if (leaving) 0f else text.value
                    translationY = (1f - text.value) * 8.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 꺼진 기능은 아이콘 칸을 살짝 연하게(켜지면 테두리와 함께 진해진다)
            Box(Modifier.size(Tile.Large).clip(RoundedCornerShape(Tile.LargeRadius)).background(c.ink.copy(alpha = 0.45f + 0.55f * fill)), contentAlignment = Alignment.Center) {
                NavGlyph(item.icon, c.onInk)
            }
            Spacer(Modifier.width(Space.IconText))
            Column(Modifier.weight(1f)) {
                Text(item.label, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500))
                Text(status, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500))
            }
        }
    }
}

/** 카드를 눌러 글자를 거둔 뒤 되살리기까지(ms). 번짐 전환(약 0.64초)이 홈을 다 덮은 뒤다. */
private const val LEAVE_RESET_MS = 1000L

/** 홈이 처음 보인 뒤 이 시간 안에 나타난 카드만 등장 애니메이션을 한다(ms). */
private const val INTRO_MS = 1200L
