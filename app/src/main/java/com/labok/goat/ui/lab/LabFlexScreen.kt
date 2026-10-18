package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.FlexPanel
import com.labok.goat.data.lab.SideSplitTest
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.appsize.ago
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidProgressButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
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
    var sideClick by remember { mutableStateOf(AutoFlex.sideClick(context)) }
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
    val horizontal = rememberHingeHorizontal(supported)
    // 앱별 모드(기본값 플렉스 모드가 아닌 앱만)
    var modes by remember { mutableStateOf(AutoFlex.modes(context)) }
    var appsOpen by remember { mutableStateOf(false) }
    // 앱 목록에서 보고 있는 모드 탭과, 모드 고르기를 열어 둔 줄
    var filter by remember { mutableIntStateOf(AutoFlex.MODE_FLEX) }
    var openRow by remember { mutableStateOf<String?>(null) }
    var gesturesOpen by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf(AutoFlex.wheel.read(context)) }
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
                    LiquidSwitchRow(if (enabled) "사용 중" else "사용 안 함", if (shizuku == ShizukuAccess.State.READY) Copy.LAB_FLEX_SWITCH_BODY else Copy.LAB_FLEX_NEED_SHIZUKU, enabled, shizuku == ShizukuAccess.State.READY || enabled) { v ->
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
                    LiquidButton(Copy.LAB_FLEX_TEST, modifier = Modifier.fillMaxWidth(), enabled = enabled, disabledHint = Copy.LAB_FLEX_TEST_NEED_ON, onClick = { context.startActivity(Intent(context, FlexTestActivity::class.java)) })
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
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(Copy.LAB_FLEX_SIDE_CLICK, Copy.LAB_FLEX_SIDE_CLICK_BODY, sideClick, enabled) { v ->
                        sideClick = v
                        AutoFlex.setSideClick(context, v)
                    }
                }
            }
        }
        // 가로 까딱 분할 자동 시험: 까딱 없이 여러 경우를 돌려 결과 파일을 만들고, 끝나면 공유 창을 띄운다
        item(key = "side-split-test") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_SIDE_TEST_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_SIDE_TEST_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    val test by SideSplitTest.state.collectAsStateWithLifecycle()
                    LiquidProgressButton(
                        Copy.LAB_SIDE_TEST_RUN,
                        progress = if (test.running) test.progress ?: 0f else null,
                        status = test.status,
                        enabled = shizuku == ShizukuAccess.State.READY,
                        disabledHint = Copy.DISABLED_SHIZUKU,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (!test.running) SideSplitTest.start(context) }
                    )
                    val last = remember(test.running) { SideSplitTest.lastFile(context) }
                    last?.let { uri ->
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.LAB_SHARE, primary = false, modifier = Modifier.fillMaxWidth(), onClick = { shareResult(context, uri) })
                    }
                }
            }
        }
        // 가까이 접으면 밝기 고정: 자동 플렉스와 따로 켜고 끈다(둘 중 하나라도 켜지면 상주 서비스가 힌지를 듣는다)
        item(key = "dim") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.LAB_FLEX_DIM, if (shizuku == ShizukuAccess.State.READY) Copy.LAB_FLEX_DIM_BODY else Copy.LAB_FLEX_NEED_SHIZUKU, dim, shizuku == ShizukuAccess.State.READY || dim) { v ->
                        dim = v
                        AutoFlex.setDimLock(context, v)
                        GoatService.sync(context)
                    }
                }
            }
        }
        item(key = "range") { Box(Modifier.widthIn(max = ContentMaxWidth)) { rangeCard() } }
        // 앱별로 접었을 때 동작: 요약 줄(모드별 앱 수)을 누르면 펼쳐진다. 펼치면 위쪽 탭으로 모드를 골라 그 모드의 앱만 보고,
        // 줄을 누르면 그 줄 아래에 모드 고르기가 열린다(한 번에 한 줄만)
        val q = query.trim()
        fun modeOf(app: AppEntry) = modes[app.pkg] ?: AutoFlex.MODE_FLEX
        val counts = AutoFlex.MODES.associateWith { m -> apps.orEmpty().count { modeOf(it) == m } }
        item(key = "apps") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = Gap.Flush) {
                    Row(Modifier.fillMaxWidth().clickable { appsOpen = !appsOpen }.padding(Gap.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NewTag(NewBadge.FLEX_APP_MODES, Modifier.padding(bottom = Gap.Item))
                            Text(Copy.LAB_FLEX_APPS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                if (apps == null) "앱 목록을 불러오는 중…" else AutoFlex.MODES.filter { counts[it]!! > 0 }.joinToString(" · ") { "${AutoFlex.modeLabel(it)} ${counts[it]}" },
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
                        "앱 이름 검색",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        leading = { Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
            if (apps == null) item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
            // 고른 모드의 앱만, 최근 사용 순. 모드를 바꾸면 그 줄은 물처럼 빠져나가 다른 탭으로 옮겨 간다
            val shown = apps.orEmpty().filter { (pending[it.pkg] ?: modeOf(it)) == filter }
                .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
            if (apps != null && shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (q.isEmpty()) "${AutoFlex.modeLabel(filter)}인 앱이 없습니다." else "찾는 앱이 없습니다.",
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
        // 스크롤 휠 위치(공식 설정과 같은 값): 패널 옆 스크롤 휠을 왼쪽·오른쪽 중 어디에 둘지
        item(key = "wheel") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_FLEX_WHEEL_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FLEX_WHEEL_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidChips(
                        options = listOf("1", "2"),
                        selected = wheel ?: "2",
                        label = { if (it == "1") "왼쪽" else "오른쪽" },
                        onSelect = { v ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { AutoFlex.wheel.write(context, v) }
                                if (ok) wheel = v else Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
        // 터치패드 제스처 도움말(공식 화면과 같은 내용)
        item(key = "gestures") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = Gap.Flush) {
                    Row(Modifier.fillMaxWidth().clickable { gesturesOpen = !gesturesOpen }.padding(Gap.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Text(Copy.LAB_FLEX_GESTURES_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                        val turn by animateFloatAsState(if (gesturesOpen) 180f else 0f, Springs.pop(), label = "gestureChevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
                    }
                    // 전체 이름으로 부른다: 그냥 부르면 ColumnScope 확장판이 잡혀 지금과 다른 함수가 된다
                    AnimatedVisibility(
                        gesturesOpen,
                        enter = expandVertically(spring(UiMotion.ratio(0.75f), UiMotion.stiffness(300f, 0.75f))) + fadeIn(),
                        exit = shrinkVertically(spring(UiMotion.ratio(0.9f), UiMotion.stiffness(420f, 0.9f))) + fadeOut()
                    ) {
                        Column(Modifier.padding(start = Gap.CardPadding, end = Gap.CardPadding, bottom = Gap.CardPadding)) {
                            FLEX_GESTURES.forEachIndexed { i, (title, body) ->
                                if (i > 0) Spacer(Modifier.height(Gap.Item))
                                Text(title, color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(body, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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

/** "YouTube, 전화 외 6개"처럼 패널을 띄우는 앱을 최근 사용 순으로 두 개까지 적고 나머지는 개수로. */
/** 터치패드 제스처(삼성 공식 '터치패드 제스처 도움말'과 같은 내용). */
private val FLEX_GESTURES = listOf(
    "누르기" to "마우스 왼쪽 클릭처럼 고르기, 앱 열기, 버튼 누르기를 합니다.",
    "두 손가락으로 누르기" to "마우스 오른쪽 클릭처럼 더 보기 메뉴를 엽니다.",
    "두 손가락으로 밀기" to "원하는 방향으로 화면을 스크롤합니다.",
    "두 손가락으로 확대/축소" to "확대하거나 축소합니다.",
    "길게 누르기" to "화면에서 항목을 고릅니다.",
    "길게 누른 채 움직이기" to "항목을 옮기거나 글자를 고릅니다.",
    "세 손가락으로 누르기" to "이전 화면으로 돌아갑니다.",
    "네 손가락으로 누르기" to "최근 앱 화면을 엽니다."
)

/** 자동 플렉스 모드 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun FlexHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 하는 일",
            "폴드8은 반쯤 접어 세워도 플렉스 모드로 바뀌지 않습니다. 자동 플렉스 모드를 켜 두면 가로로 접어 정한 각도에 들어올 때 " +
                "GOAT가 대신 플렉스 모드로 바꿔 줍니다. 위 화면에는 쓰던 앱이, 아래 화면에는 조작 패널이 나옵니다."
        ) { FlexPanelArt() }
        HelpTopic(
            "2. 먼저 Shizuku 연결",
            "플렉스 모드로 바꾸는 명령에는 셸 권한이 필요해 Shizuku를 사용합니다. 점 세 개 메뉴의 '권한 설정'에서 연결 방법을 볼 수 있습니다. " +
                "Shizuku가 연결되면 이 화면의 스위치를 켤 수 있습니다."
        )
        HelpTopic(
            "3. 켜고 사용",
            "맨 위 스위치를 켜 '사용 중'으로 바꾼 뒤 휴대폰을 옆으로 돌려 접는 선이 가로로 놓이게 하고, 노트북처럼 천천히 접습니다. 각도가 범위에 들어오면 진동과 함께 " +
                "플렉스 모드가 켜지고, 펴거나 닫아 범위를 벗어나면 원래대로 돌아옵니다. 책처럼 세로로 접을 때는 켜지지 않습니다."
        )
        HelpTopic(
            "4. 각도 범위 맞추기",
            "폴드8 힌지는 원하는 각도에서 저절로 멈추지 않아 손이나 거치대로 각도를 잡습니다. 게이지의 두 손잡이를 끌어 사용 습관에 맞는 범위를 정하고, " +
                "손잡이를 길게 누르면 1도씩 세밀하게 맞출 수 있습니다. [플렉스 모드 테스트하기]로 바로 시험해 보세요."
        )
        HelpTopic(
            "5. 까딱으로 플렉스 모드",
            "켜 두면 완전히 편 채 130~160°까지 살짝 접었다가 3초 안에 다시 펴는 동작(까딱)으로 플렉스 모드를 켜고 끕니다. " +
                "켜져 있는 동안에는 펼친 상태에서도 플렉스 모드처럼 사용합니다. 닫거나 화면을 끄면 꺼집니다."
        )
        HelpTopic(
            "6. 앱마다 다른 점",
            "갤러리·유튜브·카메라처럼 플렉스 화면을 직접 지원하는 앱은 접으면 자동으로 위아래로 나뉩니다. " +
                "그 밖의 앱은 폴드8에서 삼성 터치패널이 뜨지 않아, '앱별로 접었을 때 동작'에서 그 앱을 '터치패널'로 고르면 아래 화면에 GOAT가 만든 터치패널이 나옵니다."
        )
        HelpTopic(
            "7. 끄기·되돌리기",
            "맨 위 스위치를 끄면 GOAT가 켠 패널 설정을 모두 해제하고 접힘 상태도 원래대로 되돌립니다. 재부팅해도 원래대로 돌아옵니다."
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
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(spring(UiMotion.ratio(0.62f), UiMotion.stiffness(260f, 0.62f)))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
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

/** 시험 결과 파일을 공유 창으로 보낸다. */
internal fun shareResult(context: android.content.Context, uri: android.net.Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, Copy.LAB_SHARE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
