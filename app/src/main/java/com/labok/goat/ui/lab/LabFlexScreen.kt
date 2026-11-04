package com.labok.goat.ui.lab

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.R
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.GoatFlexPanel
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.appsize.ago
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.Inset
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.components.Tile
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 자동 플렉스 모드(폴드8 전용). 폴드8은 반쯤 접어도 시스템이 '반쯤 접힘'을 내지 않아 플렉스 모드가 뜨지 않는다.
 * 켜 두면 GOAT 상주 서비스가 힌지 각도를 보고 정한 범위로 접었을 때 반쯤 접힘을 걸고, 벗어나면 푼다([AutoFlex]).
 * 화면: 켜기(폴드8·Shizuku 아니면 막음), 지금 각도·상태, 각도 범위, 패널을 띄울 앱(앱별 화면 배율 설정과 같은 목록).
 * 이 화면도 플렉스 모드를 지원한다: 앱이 '반쯤 접힘'을 받으면 접는 선 위는 각도·상태, 아래는 조작으로 나뉜다.
 */
@Composable
internal fun LabFlexScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val engineAngle by AutoFlex.angle.collectAsStateWithLifecycle()
    val active by AutoFlex.active.collectAsStateWithLifecycle()
    val latched by AutoFlex.latched.collectAsStateWithLifecycle()
    var click by remember { mutableStateOf(AutoFlex.click(context)) }
    var dim by remember { mutableStateOf(AutoFlex.dimLock(context)) }
    val supported = remember { AutoFlex.supported() && AutoFlex.hasHingeSensor(context) }
    // 자동 플렉스가 꺼져 있어도 게이지에 지금 각도가 보이게 화면에서 직접 읽는다(켜져 있으면 서비스가 읽은 값을 쓴다)
    val localAngle = rememberHingeAngle(supported && engineAngle == null)
    val angle = engineAngle ?: localAngle
    var enabled by remember { mutableStateOf(AutoFlex.enabled(context)) }
    var min by remember { mutableIntStateOf(AutoFlex.min(context)) }
    var max by remember { mutableIntStateOf(AutoFlex.max(context)) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    val horizontal = rememberScreenPortrait()
    // 앱별 모드(기본값과 다르게 고른 앱만, 기본은 유튜브·카메라·갤러리만 플렉스 모드)
    var modes by remember { mutableStateOf(AutoFlex.modes(context)) }
    var appsOpen by remember { mutableStateOf(false) }
    // 앱 목록에서 보고 있는 모드 탭과, 모드 고르기를 열어 둔 줄
    var filter by remember { mutableIntStateOf(AutoFlex.MODE_FLEX) }
    var openRow by remember { mutableStateOf<String?>(null) }
    // 이 설정 화면을 보는 동안에는 자동 플렉스를 멈춰 화면이 바뀌지 않게 한다. 시험은 [플렉스 모드 테스트하기]가 여는
    // 시험 화면(FlexTestActivity)에서만 하므로, 이 화면이 앞에 있을 때(RESUMED)만 멈춘다
    var resumed by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            AutoFlex.setPaused("screen", resumed)
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            AutoFlex.setPaused("screen", false)
        }
    }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    // 설정 화면에서는 자동 플렉스를 멈춰 두지만, 각도가 범위에 들어오고 나가는 것은 진동으로 느끼게 한다(서비스와 같은 진동).
    // 범위를 끌어 바꿀 때가 아니라 폰을 접고 펼 때만 울리도록 각도가 바뀔 때만 본다. 시험 화면에서는 서비스가 울린다
    val rangeNow by rememberUpdatedState(min to max)
    val resumedNow by rememberUpdatedState(resumed)
    val horizontalNow by rememberUpdatedState(horizontal)
    val angleNow by rememberUpdatedState(angle)
    LaunchedEffect(supported) {
        if (!supported) return@LaunchedEffect
        var was: Boolean? = null
        snapshotFlow { angleNow }.collect { a ->
            if (a == null) return@collect
            val inside = AutoFlex.inRange(a, rangeNow.first, rangeNow.second)
            if (was != null && inside != was && resumedNow && horizontalNow) AutoFlex.rangeBuzz(context, inside)
            was = inside
        }
    }
    LaunchedEffect(supported) {
        if (!supported) return@LaunchedEffect
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }

    // 칩은 누르자마자 바뀌고(물방울이 먼저 옮겨 간다), 줄은 방울이 자리 잡은 뒤 물처럼 흘러 다른 묶음으로 옮겨 간다
    val pending = remember { mutableStateMapOf<String, Int>() }
    fun choose(app: AppEntry, mode: Int) = scope.launch {
        pending[app.pkg] = mode
        AutoFlex.setMode(context, app.pkg, mode)
        delay(360)
        modes = AutoFlex.modes(context)
        pending.remove(app.pkg)
    }

    // 각도 범위(플렉스일 때 위 칸): 지금 각도 바늘과 범위 띠가 한 게이지에 있고, 띠 양 끝 손잡이를 끌어 범위를 정한다.
    // 폰을 접은 채로는 끌기 어려우니 아래 두 버튼으로 지금 각도를 범위 시작·끝에 바로 찍는다
    val rangeCard: @Composable () -> Unit = {
        GlassCard {
            Text(Copy.LAB_FLEX_RANGE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.LAB_FLEX_RANGE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            FlexAngleGauge(
                angle,
                min,
                max,
                active,
                onRange = { l, h ->
                    min = l
                    max = h
                },
                onRangeFinished = { AutoFlex.setRange(context, min, max) }
            )
            Spacer(Modifier.height(Gap.Item))
            Text(Copy.LAB_FLEX_RANGE_HOW, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.LAB_FLEX_RANGE_RESET, modifier = Modifier.fillMaxWidth(), onClick = {
                min = AutoFlex.DEFAULT_MIN
                max = AutoFlex.DEFAULT_MAX
                AutoFlex.setRange(context, min, max)
            })
        }
    }

    val list: LazyListScope.() -> Unit = list@{
        if (!supported) {
            item(key = "unsupported") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.LAB_FLEX_UNSUPPORTED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.LAB_FLEX_UNSUPPORTED_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            return@list
        }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                // 삼성 공식 '플렉스 모드 패널' 화면과 같은 순서: 사용 스위치 → 그림 → 설명
                GlassCard {
                    LiquidSwitchRow(if (enabled) stringResource(R.string.lab_flex_switch_on) else stringResource(R.string.lab_flex_switch_off), if (shizuku == ShizukuAccess.State.READY) Copy.LAB_FLEX_SWITCH_BODY else Copy.NEED_SHIZUKU_TITLE, enabled, shizuku == ShizukuAccess.State.READY || enabled) { v ->
                        enabled = v
                        AutoFlex.setEnabled(context, v)
                        GoatService.sync(context)
                        // 켜면 모든 앱(끈 앱 제외)에 패널을 켜고, 끄면 GOAT가 켠 패널을 모두 푼다
                        scope.launch(Dispatchers.IO) { AutoFlex.syncApps(context) }
                    }
                    Spacer(Modifier.height(Gap.Section))
                    FlexPanelArt()
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.LAB_FLEX_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    if (!enabled) Text(Copy.LAB_FLEX_TEST_OFF, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.LAB_FLEX_TEST, modifier = Modifier.fillMaxWidth(), enabled = enabled, disabledHint = Copy.LAB_FLEX_TEST_OFF, onClick = { context.startActivity(Intent(context, FlexTestActivity::class.java)) })
                }
            }
        }
        item(key = "click") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.LAB_FLEX_CLICK, Copy.LAB_FLEX_CLICK_BODY, click, enabled) { v ->
                        click = v
                        AutoFlex.setClick(context, v)
                    }
                    Spacer(Modifier.height(Gap.Item))
                    FlexPanelArt(kkaddak = true)
                }
            }
        }
        // 가까이 접으면 밝기 고정: 자동 플렉스와 따로 켜고 끈다(둘 중 하나라도 켜지면 상주 서비스가 힌지를 듣는다)
        item(key = "dim") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.LAB_FLEX_DIM, if (shizuku == ShizukuAccess.State.READY) Copy.LAB_FLEX_DIM_BODY else Copy.NEED_SHIZUKU_TITLE, dim, shizuku == ShizukuAccess.State.READY || dim) { v ->
                        dim = v
                        AutoFlex.setDimLock(context, v)
                        GoatService.sync(context)
                    }
                }
            }
        }
        item(key = "range") { Box(Modifier.widthIn(max = ContentMaxWidth)) { rangeCard() } }
        // GOAT 패널 옵션은 점 세 개 메뉴 → 「패널 설정」 시트로 옮겼다(11-02 소장님)
        // 앱별로 접었을 때 동작: 요약 줄(모드별 앱 수)을 누르면 펼쳐진다. 펼치면 위쪽 탭으로 모드를 골라 그 모드의 앱만 보고,
        // 줄을 누르면 그 줄 아래에 모드 고르기가 열린다(한 번에 한 줄만)
        val q = query.trim()
        fun modeOf(app: AppEntry) = modes[app.pkg] ?: AutoFlex.defaultMode(app.pkg)
        val counts = AutoFlex.MODES.associateWith { m -> apps.orEmpty().count { modeOf(it) == m } }
        item(key = "apps") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = Gap.Flush) {
                    Row(Modifier.fillMaxWidth().clickable { appsOpen = !appsOpen }.padding(Gap.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NewTag(NewBadge.AUTO_FLEX, Modifier.padding(bottom = Gap.Item))
                            Text(Copy.LAB_FLEX_APPS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                if (apps == null) stringResource(R.string.lab_flex_apps_loading) else AutoFlex.MODES.filter { counts[it]!! > 0 }.joinToString(" · ") { "${AutoFlex.modeLabel(it)} ${counts[it]}" },
                                color = c.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val turn by animateFloatAsState(if (appsOpen) 180f else 0f, Springs.pop(), label = "appsChevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
                    }
                }
            }
        }
        if (appsOpen) {
            item(key = "filter") {
                Column(Modifier.widthIn(max = ContentMaxWidth).animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) {
                    Text(Copy.LAB_FLEX_APPS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Gap.CardPadding))
                    Spacer(Modifier.height(Gap.Item))
                    LiquidChips(
                        options = AutoFlex.MODES,
                        selected = filter,
                        label = { "${AutoFlex.modeLabel(it)} ${counts[it]}" },
                        onSelect = {
                            filter = it
                            openRow = null
                        }
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidTextField(
                        query,
                        { query = it },
                        stringResource(R.string.lab_flex_apps_search),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        leading = { Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
            if (apps == null) item(key = "loading") { LiquidLoader(stringResource(R.string.lab_flex_apps_loading), Modifier.padding(vertical = Gap.Item)) }
            // 고른 모드의 앱만, 최근 사용 순. 모드를 바꾸면 그 줄은 물처럼 빠져나가 다른 탭으로 옮겨 간다
            val shown = apps.orEmpty().filter { (pending[it.pkg] ?: modeOf(it)) == filter }
                .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
            if (apps != null && shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (q.isEmpty()) stringResource(R.string.lab_flex_apps_empty_mode, AutoFlex.modeLabel(filter)) else stringResource(R.string.lab_flex_apps_not_found),
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = ContentMaxWidth).padding(horizontal = Gap.CardPadding).animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)
                    )
                }
            }
            items(shown, key = { it.pkg }) { app ->
                FlexAppRow(
                    app,
                    pending[app.pkg] ?: modeOf(app),
                    open = openRow == app.pkg,
                    onToggle = { openRow = if (openRow == app.pkg) null else app.pkg },
                    modifier = Modifier.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)
                ) {
                    openRow = null
                    choose(app, it)
                }
            }
        }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
        content = list
    )
}

/** 자동 플렉스 모드 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun FlexHelpSheet() {
    SheetColumn(title = stringResource(R.string.lab_help_title)) {
        HelpTopic(
            stringResource(R.string.lab_help_what_title),
            stringResource(R.string.lab_flex_help_what_body)
        ) { FlexPanelArt() }
        HelpTopic(
            stringResource(R.string.lab_flex_help_shizuku_title),
            stringResource(R.string.lab_flex_help_shizuku_body)
        )
        HelpTopic(
            stringResource(R.string.lab_flex_help_use_title),
            stringResource(R.string.lab_flex_help_use_body)
        )
        HelpTopic(
            stringResource(R.string.lab_flex_help_range_title),
            stringResource(R.string.lab_flex_help_range_body, Copy.LAB_FLEX_TEST)
        )
        HelpTopic(
            stringResource(R.string.lab_flex_help_nod_title),
            stringResource(R.string.lab_flex_help_nod_body)
        )
        HelpTopic(
            stringResource(R.string.lab_flex_help_apps_title),
            stringResource(R.string.lab_flex_help_apps_body, Copy.LAB_FLEX_APPS_TITLE)
        )
        HelpTopic(
            stringResource(R.string.lab_flex_help_off_title),
            stringResource(R.string.lab_flex_help_off_body)
        )
    }
}

/** 패널 앱 한 줄: 앱별 화면 배율 설정 목록과 같은 모양(아이콘·이름·마지막 사용)에 오른쪽 스위치. 켜고 끌 때 테두리가 물들고 줄이 살짝 출렁인다. */
@Composable
private fun FlexAppRow(app: AppEntry, mode: Int, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (open) c.ink.copy(alpha = 0.5f) else c.line, Springs.calm(reduce), label = "flexRowBorder")
    val squash = remember { Animatable(1f) }
    LaunchedEffect(open) {
        if (reduce || squash.value != 1f) return@LaunchedEffect
        squash.snapTo(0.97f)
        squash.animateTo(1f, Springs.pop(reduce))
    }
    Column(
        modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = squash.value
                scaleY = 2f - squash.value
            }
            .clip(shape)
            .background(c.surface)
            .border(1.dp, border, shape)
            .clickable(onClick = onToggle)
            .padding(Inset.Row)
            .animateContentSize(spring(UiMotion.ratio(0.62f), UiMotion.stiffness(260f, 0.62f)))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tile.App).clip(RoundedCornerShape(Tile.AppRadius))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
            Spacer(Modifier.width(Space.IconText))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(Space.Inline))
            Text(AutoFlex.modeLabel(mode), color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
        if (open) {
            Spacer(Modifier.height(Gap.Item))
            LiquidChips(options = AutoFlex.MODES, selected = mode, label = { AutoFlex.modeLabel(it) }, onSelect = onChange)
        }
    }
}

/** 목록 줄이 자리를 옮길 때(물처럼 출렁이며)·나타나고 사라질 때 쓰는 스프링. UI 유연성·속도 설정을 따른다. */
private val liquidPlace: FiniteAnimationSpec<IntOffset>
    get() = spring(UiMotion.ratio(0.62f), UiMotion.stiffness(260f, 0.62f))
private val liquidFade: FiniteAnimationSpec<Float>
    get() = spring(1f, UiMotion.stiffness(400f, 1f))

/** GOAT 패널 설정 시트(플렉스 모드 화면의 점 세 개 메뉴 → 「패널 설정」, 11-02 소장님). */
@Composable
internal fun GoatPanelSheet() {
    SheetColumn(title = Copy.MENU_GOAT_PANEL) { GoatPanelCard() }
}

/** GOAT 패널 옵션: 이동 시간·이동 방식·보일 타일, 앰비언트·블러, 미리 보기. */
@Composable
private fun GoatPanelCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var sec by remember { mutableIntStateOf(GoatFlexPanel.seekSec(context)) }
    var how by remember { mutableIntStateOf(GoatFlexPanel.seekMode(context)) }
    GlassCard {
        Text(stringResource(R.string.lab_flex_panel_title), fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(
            stringResource(R.string.lab_flex_panel_body),
            color = c.muted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.lab_flex_panel_seek_time), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidChips(options = GoatFlexPanel.SEEK_SECONDS, selected = sec, label = { Copy.text(R.string.lab_flex_panel_seek_seconds, it) }, onSelect = {
            sec = it
            GoatFlexPanel.setSeekSec(context, it)
        })
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_seek_mode), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidChips(options = GoatFlexPanel.SEEK_MODES, selected = how, label = { GoatFlexPanel.seekModeLabel(it) }, onSelect = {
            how = it
            GoatFlexPanel.setSeekMode(context, it)
        })
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.lab_flex_panel_seek_mode_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        // 품질·앰비언트 프리셋(11-02 고도화). 화면 분석은 보호 콘텐츠(넷플릭스 등 DRM)에서는 검게 찍혀 포스터·앱 색으로 대신한다
        var quality by remember { mutableIntStateOf(GoatFlexPanel.quality(context)) }
        var ambient by remember { mutableIntStateOf(GoatFlexPanel.ambientMode(context)) }
        var intensity by remember { mutableIntStateOf(GoatFlexPanel.intensity(context)) }
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_quality), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidChips(options = GoatFlexPanel.QUALITIES, selected = quality, label = { GoatFlexPanel.qualityLabel(it) }, onSelect = {
            quality = it
            GoatFlexPanel.setQuality(context, it)
        })
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_ambient), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidChips(options = GoatFlexPanel.AMBIENT_MODES, selected = ambient, label = { GoatFlexPanel.ambientLabel(it) }, onSelect = {
            ambient = it
            GoatFlexPanel.setAmbientMode(context, it)
        })
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.lab_flex_panel_ambient_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_intensity), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidChips(options = GoatFlexPanel.INTENSITIES, selected = intensity, label = { GoatFlexPanel.intensityLabel(it) }, onSelect = {
            intensity = it
            GoatFlexPanel.setIntensity(context, it)
        })
        // 블러 설정(11-02 소장님): 바닥 반사 블러는 거리 블러 곡선 전체 배율, 버튼 유리 블러는 카드 안에서만 더 흐리는 반지름
        var floorBlur by remember { mutableIntStateOf(GoatFlexPanel.floorBlur(context)) }
        var buttonBlur by remember { mutableIntStateOf(GoatFlexPanel.buttonBlur(context)) }
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_floor_blur), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.lab_flex_panel_floor_blur_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(
            value = floorBlur,
            range = 10..200,
            onValueChange = { floorBlur = it },
            onValueChangeFinished = { GoatFlexPanel.setFloorBlur(context, floorBlur) },
            valueText = { "$it%" },
            description = stringResource(R.string.lab_flex_panel_floor_blur)
        )
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.lab_flex_panel_button_blur), color = c.ink, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.lab_flex_panel_button_blur_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(
            value = buttonBlur,
            range = 0..100,
            onValueChange = { buttonBlur = it },
            onValueChangeFinished = { GoatFlexPanel.setButtonBlur(context, buttonBlur) },
            valueText = { "${it}dp" },
            description = stringResource(R.string.lab_flex_panel_button_blur)
        )
        var haptic by remember { mutableStateOf(GoatFlexPanel.haptics(context)) }
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow(stringResource(R.string.lab_flex_panel_haptic_title), stringResource(R.string.lab_flex_panel_haptic_desc), haptic, enabled = true, onChange = {
            haptic = it
            GoatFlexPanel.setHaptics(context, it)
        })
        var motionOn by remember { mutableStateOf(GoatFlexPanel.motion(context)) }
        Spacer(Modifier.height(Gap.Section))
        LiquidSwitchRow(stringResource(R.string.lab_flex_panel_motion_title), stringResource(R.string.lab_flex_panel_motion_desc), motionOn, enabled = true, onChange = {
            motionOn = it
            GoatFlexPanel.setMotion(context, it)
        })
        listOf("progress" to stringResource(R.string.lab_flex_panel_tile_progress), "brightness" to stringResource(R.string.lab_flex_panel_tile_brightness), "volume" to stringResource(R.string.lab_flex_panel_tile_volume)).forEach { (key, name) ->
            var on by remember { mutableStateOf(GoatFlexPanel.shown(context, key)) }
            Spacer(Modifier.height(Gap.Section))
            LiquidSwitchRow(stringResource(R.string.lab_flex_panel_tile_switch, name), null, on, enabled = true, onChange = {
                on = it
                GoatFlexPanel.setShown(context, key, it)
            })
        }
        Spacer(Modifier.height(Gap.Section))
        LiquidButton(stringResource(R.string.lab_flex_panel_preview), primary = false, modifier = Modifier.fillMaxWidth(), onClick = { GoatFlexPanel.preview(context) })
    }
}
