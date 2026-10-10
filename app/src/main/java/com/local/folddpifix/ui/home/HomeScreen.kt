package com.local.folddpifix.ui.home

import com.local.folddpifix.ui.components.copyText
import com.local.folddpifix.ui.components.DangerConfirmBox
import com.local.folddpifix.ui.components.featurePadding
import com.local.folddpifix.ui.components.ContentMaxWidth
import com.local.folddpifix.ui.components.SheetColumn
import com.local.folddpifix.ui.appsize.AppSizeResetSheet
import com.local.folddpifix.ui.appsize.AppSizeGuideSheet
import com.local.folddpifix.ui.appsize.AppSizeHelpSheet
import com.local.folddpifix.ui.appsize.AppSizeScreen
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeState
import com.local.folddpifix.ui.liquid.liquidReveal
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import com.local.folddpifix.ui.liquid.LiquidDots
import com.local.folddpifix.data.display.PermissionReset
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import android.Manifest
import android.os.Build
import android.hardware.display.DisplayManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.folddpifix.AppInfo
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.PanelSpec
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.ui.about.AboutSheet
import com.local.folddpifix.ui.advanced.AdvancedSheet
import com.local.folddpifix.ui.art.FoldDeviceArt
import com.local.folddpifix.ui.art.FoldGeometry
import com.local.folddpifix.ui.art.ReportFlightArt
import com.local.folddpifix.ui.art.ScreensPreview
import com.local.folddpifix.ui.help.GrantGuideSheet
import com.local.folddpifix.ui.help.HelpSheet
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LiquidSlider
import com.local.folddpifix.ui.liquid.LiquidSwitch
import com.local.folddpifix.ui.liquid.LiquidToast
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.text.Copy
import androidx.compose.runtime.saveable.rememberSaveable
import com.local.folddpifix.ui.liquid.LiquidMenu
import androidx.compose.material.icons.outlined.MoreVert
import com.local.folddpifix.ui.nav.SideDrawerContent
import com.local.folddpifix.ui.nav.NavSection
import com.local.folddpifix.ui.nav.NavItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import com.local.folddpifix.BuildConfig
import com.local.folddpifix.ui.lab.LabScreen
import com.local.folddpifix.ui.art.LocalDeviceShape
import com.local.folddpifix.ui.art.DeviceShape
import androidx.compose.runtime.CompositionLocalProvider
import com.local.folddpifix.ui.about.ReportMail
import com.local.folddpifix.domain.ScreenGeometry
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.ui.components.CommandBox
import com.local.folddpifix.ui.sizetest.SizeTestSheet
import com.local.folddpifix.ui.sizetest.RowCountOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 하단 시트 종류. 한 번에 하나만 연다. */
private enum class Sheet { HELP, GUIDE, TEST, ADVANCED, RESET, ABOUT, APP_HELP, APP_GUIDE, APP_RESET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val toast = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalLiquid.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // 줄 세기 화면(크기 테스트에서 연다). 닫으면 크기 테스트로 돌아간다.
    var counting by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var menu by remember { mutableStateOf(false) }
    // 사이드바에서 고른 기능(화면). 기능 안의 세부 설정은 점 세 개 메뉴에 있다.
    var feature by rememberSaveable { mutableStateOf(NavItem.DPI_MATCH) }
    // 화면에 보이는 기능. 사이드바에서 고르면 방울 이동을 보여 준 뒤 사이드바가 닫히면서 바뀐다(물방울 번짐 전환).
    var shown by rememberSaveable { mutableStateOf(NavItem.DPI_MATCH) }
    var flying by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // 문의·문제 신고 공통: 진단 파일을 저장하고, 방울이 날아가는 장면 뒤 개발자에게 보내는 메일을 연다(로그 첨부).
    val mail: (ReportMail.Kind) -> Unit = { kind ->
        scope.launch {
            val uris = vm.exportDiagnostics()
            if (uris.isEmpty()) vm.say(Copy.TOAST_REPORT_FAILED)
            flying = {
                flying = null
                if (uris.isNotEmpty()) vm.say(Copy.TOAST_REPORT_SAVED)
                ReportMail.send(context, kind, uris)
            }
        }
    }
    val report: () -> Unit = { mail(ReportMail.Kind.REPORT) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    // 앱을 연 채로 접거나 펴면 바로 반영한다.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp, configuration.screenHeightDp, configuration.densityDpi) { vm.refresh() }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = vm.onDisplayChanged()
            override fun onDisplayRemoved(displayId: Int) = vm.onDisplayChanged()
            override fun onDisplayChanged(displayId: Int) = vm.onDisplayChanged()
        }
        dm.registerDisplayListener(listener, null)
        onDispose { dm.unregisterDisplayListener(listener) }
    }
    // 준비(권한) 전에는 PC에서 명령을 실행하는 즉시 알아차리도록 화면이 보이는 동안 확인한다.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.hasPermission, state.loading) {
        if (!state.loading && !state.hasPermission) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(2_500); vm.refresh()
                }
            }
        }
    }
    // 권한 초기화 뒤 PC에서 회수되면 시트를 닫고 알린다(이후 권한 설정 안내가 맨 위에 뜬다).
    var hadPermission by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.hasPermission, state.loading) {
        if (state.loading) return@LaunchedEffect
        if (hadPermission == true && !state.hasPermission) {
            if (sheet == Sheet.RESET) sheet = null
            vm.say(Copy.TOAST_PERMISSION_REMOVED)
        }
        hadPermission = state.hasPermission
    }
    LaunchedEffect(message) {
        message?.let { toast.showSnackbar(it); vm.messageShown() }
    }

    // 그림이 이 기기의 실제 화면비를 따르도록, 학습한 두 화면 비율을 모든 그림(시트 포함)에 내려 준다.
    val deviceShape = DeviceShape(
        cover = state.aspectOf(ScreenPolicy.Role.OUTER) ?: FoldGeometry.COVER_ASPECT,
        inner = state.aspectOf(ScreenPolicy.Role.INNER) ?: FoldGeometry.INNER_ASPECT,
    )
    CompositionLocalProvider(LocalDeviceShape provides deviceShape) {
        // 사이드바는 기능 단위. 두 기능 모두 화면 크기(DPI)를 다루므로 '화면 크기'로 묶는다. 조사 도구는 lab 빌드에서만.
        val sections = buildList {
            add(NavSection(Copy.MENU_SECTION_FEATURES, listOf(NavItem.DPI_MATCH, NavItem.APP_SIZE)))
            if (BuildConfig.LAB) add(NavSection(Copy.MENU_SECTION_LAB, listOf(NavItem.LAB_TOOLS)))
        }
        // 점 세 개 메뉴: 지금 기능만의 도구를 먼저, 공통 항목(사용 방법·권한 설정·문제 신고·초기화)은 같은 순서로 뒤에 둔다.
        val common = { help: Sheet, guide: Sheet, reset: Sheet ->
            listOf(
                Copy.MENU_HELP to { sheet = help },
                Copy.MENU_GUIDE to { sheet = guide },
                Copy.MENU_REPORT to report,
                Copy.MENU_RESET to { sheet = reset },
            )
        }
        val menuItems = when (shown) {
            NavItem.DPI_MATCH -> listOf(
                Copy.MENU_TEST to { sheet = Sheet.TEST },
                Copy.MENU_ADVANCED to { sheet = Sheet.ADVANCED },
            ) + common(Sheet.HELP, Sheet.GUIDE, Sheet.RESET)
            NavItem.APP_SIZE -> common(Sheet.APP_HELP, Sheet.APP_GUIDE, Sheet.APP_RESET)
            NavItem.LAB_TOOLS -> listOf(Copy.MENU_REPORT to report)
        }
        // 기본 기능(화면 크기 맞추기) 화면. 기능 전환 애니메이션 안에서 그린다.
        val dpiMatchList: @Composable (PaddingValues) -> Unit = { inner ->
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = featurePadding(inner),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 고정 카드에도 키를 둬, 위에 할 일 카드가 생기거나 없어져도 카드 상태(스위치·슬라이더 방울)가 유지되게 한다.
                item(key = "card:status") { Box(Modifier.widthIn(max = ContentMaxWidth)) { StatusCard(state) } }
                items(todosOf(state), key = { it.key }) { todo ->
                    Box(Modifier.widthIn(max = ContentMaxWidth)) { TodoCard(todo) { action ->
                        when (action) {
                            TodoAction.SETUP -> sheet = Sheet.GUIDE
                            TodoAction.RELEARN -> vm.resetLearned()
                            TodoAction.APPLY -> vm.applyNow()
                            TodoAction.AUTO -> vm.setAuto(true)
                            TodoAction.REPORT -> report()
                            TodoAction.ADOPT -> vm.resolveExternal(adopt = true)
                            TodoAction.RESTORE -> vm.resolveExternal(adopt = false)
                        }
                    } }
                }
                item(key = "card:size") { Box(Modifier.widthIn(max = ContentMaxWidth)) { SizeCard(state, vm, onTest = { sheet = Sheet.TEST }) } }
                item(key = "card:auto") { Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    AutoCard(state.auto) { on ->
                        if (on && !ExternalChangeNotifier.canNotify(context)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.setAuto(on)
                    }
                } }
            }
        }

        // 위쪽 제목 줄: 아래 내용이 비치는 반투명 블러(내용은 haze 원본, 제목 줄은 haze 자식)
        val haze = remember { HazeState() }
        ModalNavigationDrawer(
            drawerState = drawer,
            scrimColor = c.ink.copy(alpha = 0.32f),
            drawerContent = {
                SideDrawerContent(sections, current = feature, visible = drawer.targetValue == DrawerValue.Open, onAbout = {
                    scope.launch { drawer.close() }
                    sheet = Sheet.ABOUT
                }, onSelect = { item ->
                    // 방울이 고른 항목으로 옮겨 가는 것을 잠깐 보여 준 뒤 닫는다.
                    val moved = item != feature
                    feature = item
                    scope.launch {
                        if (moved) { delay(440); shown = item }
                        drawer.close()
                    }
                })
            },
        ) {
            Scaffold(
                containerColor = c.bg,
                topBar = {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = shown,
                                transitionSpec = {
                                    (fadeIn(tween(260, delayMillis = 120)) + slideInVertically(tween(320, delayMillis = 120)) { it / 2 }) togetherWith
                                        (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 })
                                },
                                label = "title",
                            ) { f ->
                                Text(
                                    if (f == NavItem.DPI_MATCH) AppInfo.NAME else f.label,
                                    fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                        modifier = Modifier.hazeChild(
                            haze,
                            HazeStyle(backgroundColor = c.bg, tint = HazeTint(c.bg.copy(alpha = 0.7f)), blurRadius = 24.dp, noiseFactor = 0f),
                        ),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "기능 메뉴 열기") }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "설정 메뉴") }
                                LiquidMenu(expanded = menu, onDismiss = { menu = false }, items = menuItems)
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(toast) { LiquidToast(it.visuals.message) } },
            ) { inner ->
                // 기능을 바꾸면 새 화면이 왼쪽(사이드바 쪽)에서 물방울처럼 번지며 덮는다. 이전 화면은 다 덮인 뒤 사라진다.
                AnimatedContent(
                    targetState = shown,
                    modifier = Modifier.haze(haze),
                    transitionSpec = {
                        (EnterTransition.None togetherWith fadeOut(tween(1, delayMillis = 600))).apply { targetContentZIndex = 1f }
                    },
                    label = "feature",
                ) { f ->
                    // 처음 그릴 때가 들어오는 중(PreEnter)이면 번짐 전환, 앱을 처음 열 때(Visible)는 그대로 그린다
                    val entering = remember { transition.currentState == EnterExitState.PreEnter }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .liquidReveal(entering) { Offset(0f, it.height * 0.3f) }
                            .background(c.bg),
                    ) {
                        when (f) {
                            NavItem.DPI_MATCH -> dpiMatchList(inner)
                            NavItem.APP_SIZE -> AppSizeScreen(inner, onGuide = { sheet = Sheet.APP_GUIDE }, onReset = { sheet = Sheet.APP_RESET })
                            NavItem.LAB_TOOLS -> LabScreen(inner)
                        }
                    }
                }
            }
        }

        if (counting) RowCountOverlay(onClose = { counting = false; sheet = Sheet.TEST })

        flying?.let { done ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ReportFlightArt(onDone = done) }
        }

        sheet?.let { current ->
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.bg,
            ) {
                when (current) {
                    Sheet.HELP -> HelpSheet(state, onOpenGuide = { sheet = Sheet.GUIDE })
                    Sheet.GUIDE -> GrantGuideSheet(state.hasPermission, onCopied = { vm.say(Copy.TOAST_COPIED) })
                    Sheet.TEST -> SizeTestSheet(state, onCount = { sheet = null; counting = true }, onCalibrate = vm::setRulerPpi)
                    Sheet.ADVANCED -> AdvancedSheet(state, onClearLogs = vm::clearDiagnostics)
                    Sheet.RESET -> ResetSheet(
                        canReset = state.hasPermission,
                        onRelearn = { sheet = null; vm.resetLearned() },
                        onDefault = { sheet = null; vm.resetToDefault() },
                        onResetPermission = vm::resetPermissionState,
                        onPoll = { vm.refresh() },
                        onCopy = { text -> if (copyText(context, text, "adb")) vm.say(Copy.TOAST_COPIED) },
                    )
                    Sheet.ABOUT -> AboutSheet(onMail = { ReportMail.inquiry(context) })
                    Sheet.APP_HELP -> AppSizeHelpSheet(onConnect = { sheet = Sheet.APP_GUIDE })
                    Sheet.APP_GUIDE -> AppSizeGuideSheet()
                    Sheet.APP_RESET -> AppSizeResetSheet(onDone = { msg -> sheet = null; vm.say(msg) })
                }
            }
        }
    }
}

/** 상태: 폴드 기기 그림 + 지금 화면의 크기 + 한 줄 설명. */
@Composable
private fun StatusCard(state: UiState) {
    val c = LocalLiquid.current
    val role = state.effective?.role
    val unfolded = role == ScreenPolicy.Role.INNER
    val line = when {
        state.loading -> ""
        !state.hasPermission -> Copy.STATUS_NEED_SETUP
        state.target == null -> Copy.STATUS_NO_TARGET
        state.external != null -> Copy.STATUS_EXTERNAL
        state.status == UiState.Status.BLOCKED -> Copy.STATUS_HOLD
        state.rows.size < 2 -> Copy.STATUS_NEED_OTHER
        state.status == UiState.Status.OK -> Copy.STATUS_OK
        else -> Copy.STATUS_FIXING
    }
    GlassCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoldDeviceArt(
                unfolded = unfolded, height = 118.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (role) { ScreenPolicy.Role.INNER -> Copy.INNER; ScreenPolicy.Role.OUTER -> Copy.OUTER; null -> Copy.CURRENT },
                    color = c.muted, style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.dpi?.current?.toString() ?: "—",
                        fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = c.ink,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("dpi", color = c.muted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(line, color = c.ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class TodoAction { SETUP, RELEARN, APPLY, AUTO, REPORT, ADOPT, RESTORE }

private data class Todo(
    val key: String, val title: String, val body: String?, val action: TodoAction?, val actionLabel: String?,
    val blocking: Boolean = false,
    /** 두 번째 선택지(외부 변경의 복원 등). 있으면 버튼을 본문 아래에 나란히 둔다. */
    val second: TodoAction? = null, val secondLabel: String? = null,
)

/** 사용자가 할 일만. 앱을 못 쓰게 하는 것(준비)을 맨 위에. 할 일이 없으면 아무것도 보여 주지 않는다. */
private fun todosOf(state: UiState): List<Todo> = buildList {
    if (state.loading) return@buildList
    if (!state.hasPermission) add(Todo("setup", Copy.TODO_SETUP_TITLE, Copy.TODO_SETUP_BODY, TodoAction.SETUP, Copy.TODO_SETUP_ACTION, blocking = true))
    state.external?.let { e ->
        add(Todo(
            "external", Copy.EXTERNAL_TITLE,
            "다른 설정에서 ${e.role.label} 화면 DPI가 ${e.from} → ${e.to}(으)로 변경되었습니다. ${Copy.EXTERNAL_BODY}",
            TodoAction.ADOPT, "${e.to} 기준으로 설정", blocking = true,
            second = TodoAction.RESTORE, secondLabel = "${e.from}(으)로 복원",
        ))
    }
    if (state.hasPermission && state.status == UiState.Status.BLOCKED) {
        add(Todo("hold", Copy.TODO_HOLD_TITLE, Copy.TODO_HOLD_BODY, TodoAction.RELEARN, Copy.TODO_HOLD_ACTION))
    }
    if (state.target != null && state.rows.size < 2) add(Todo("other", Copy.TODO_OTHER_TITLE, Copy.TODO_OTHER_BODY, null, null))
    if (state.hasPermission && state.status == UiState.Status.NEEDS_FIX && state.external == null) {
        add(Todo("apply", Copy.TODO_APPLY_TITLE, null, TodoAction.APPLY, Copy.TODO_APPLY_ACTION))
    }
    if (state.hasPermission && !state.auto && state.target != null) add(Todo("auto", Copy.TODO_AUTO_TITLE, Copy.TODO_AUTO_BODY, TodoAction.AUTO, Copy.TODO_AUTO_ACTION))
    if (state.hasCrash) add(Todo("crash", Copy.TODO_CRASH_TITLE, Copy.TODO_CRASH_BODY, TodoAction.REPORT, Copy.TODO_CRASH_ACTION))
}

@Composable
private fun TodoCard(todo: Todo, onAction: (TodoAction) -> Unit) {
    val c = LocalLiquid.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + shrinkVertically(),
    ) {
        GlassCard(accent = if (todo.blocking) c.ink else null, padding = 16.dp) {
            if (todo.second != null) {
                Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (todo.action != null && todo.actionLabel != null) LiquidButton(todo.actionLabel, modifier = Modifier.weight(1f), onClick = { onAction(todo.action) })
                    LiquidButton(todo.secondLabel ?: "", modifier = Modifier.weight(1f), onClick = { onAction(todo.second) }, primary = false)
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                    todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                }
                if (todo.action != null && todo.actionLabel != null) {
                    Spacer(Modifier.width(10.dp))
                    LiquidButton(todo.actionLabel, onClick = { onAction(todo.action) })
                }
            }
        }
    }
}

/** 외부 화면 크기(바로 조절) → 내부 화면은 기기 정보로 계산. 미세조정과 미리보기. */
@Composable
private fun SizeCard(state: UiState, vm: HomeViewModel, onTest: () -> Unit) {
    val c = LocalLiquid.current
    var outer by remember(state.target) { mutableIntStateOf(state.target ?: 360) }
    var adjust by remember(state.adjust) { mutableIntStateOf(state.adjust) }
    val outerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.OUTER)?.let { s -> ps.first { it.screen.key == s.key } } }
    val innerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.INNER)?.let { s -> ps.first { it.screen.key == s.key } } }
    // 끄는 동안의 미리보기 값으로 내부 화면 크기를 계산한다.
    val previewPlan = state.plan?.copy(referenceDpi = outer, adjust = adjust)
    val innerDpi = innerPanel?.let { previewPlan?.targetFor(it.screen)?.dpi }

    GlassCard(padding = 18.dp) {
        Text(Copy.SIZE_TITLE, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Copy.SIZE_OUTER, color = c.ink, modifier = Modifier.weight(1f))
            Text("$outer dpi", color = c.ink, fontWeight = FontWeight.SemiBold)
        }
        Text(Copy.SIZE_OUTER_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
        LiquidSlider(
            value = outer, range = SLIDER_RANGE,
            onValueChange = { outer = it },
            onValueChangeFinished = {},
            valueText = { "$it" },
            description = Copy.SIZE_OUTER,
            enabled = state.hasPermission,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Copy.SIZE_INNER_TUNE, color = c.ink, modifier = Modifier.weight(1f))
            Text(innerDpi?.let { "$it dpi" } ?: "—", color = c.ink, fontWeight = FontWeight.SemiBold)
        }
        Text(Copy.SIZE_INNER_TUNE_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
        LiquidSlider(
            value = adjust, range = -SettingsRepository.MAX_ADJUST..SettingsRepository.MAX_ADJUST,
            onValueChange = { adjust = it },
            onValueChangeFinished = {},
            valueText = { if (it > 0) "+$it" else "$it" },
            centered = true,
            description = Copy.SIZE_INNER_TUNE,
            enabled = state.hasPermission && innerPanel != null,
        )
        // 조절바는 값만 바꾸고, [적용]을 눌러야 화면에 반영한다(끄는 도중 화면이 계속 바뀌지 않게).
        val pending = state.target != null && (outer != state.target || adjust != state.adjust)
        AnimatedVisibility(pending, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(Copy.SIZE_PENDING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidButton(Copy.SIZE_REVERT, modifier = Modifier.weight(1f), primary = false, onClick = {
                        outer = state.target ?: outer; adjust = state.adjust
                    })
                    LiquidButton(Copy.SIZE_APPLY, modifier = Modifier.weight(1f), onClick = { vm.commitSize(outer, adjust) })
                }
            }
        }
        if (outerPanel != null && innerPanel != null && innerDpi != null) {
            Spacer(Modifier.height(6.dp))
            ScreensPreview(
                outerInch = outerPanel.screen.diagonalInch, outerAspect = aspect(outerPanel, outerPanel, false), outerPpi = outerPanel.screen.ppi, outerDpi = outer,
                innerInch = innerPanel.screen.diagonalInch, innerAspect = aspect(innerPanel, outerPanel, true), innerPpi = innerPanel.screen.ppi, innerDpi = innerDpi,
                outerLabel = "${Copy.SIZE_PREVIEW_OUTER} $outer", innerLabel = "${Copy.SIZE_PREVIEW_INNER} $innerDpi",
                innerActive = state.effective?.role?.let { it == ScreenPolicy.Role.INNER },
            )
            Spacer(Modifier.height(10.dp))
            LiquidButton(Copy.SIZE_TEST, modifier = Modifier.fillMaxWidth(), onClick = onTest, primary = false)
        }
    }
}

private val SLIDER_RANGE = 280..520


/** 세로 ÷ 가로(미리보기용). 내부 화면은 실제 방향(Fold8은 가로로 긴 화면)으로 계산한다. */
private fun aspect(p: PanelSpec.Resolved, cover: PanelSpec.Resolved, isInner: Boolean): Float =
    ScreenGeometry.orientedSize(p.screen.key, cover.screen.key, isInner)?.let { (w, h) -> h / w } ?: 2f

@Composable
private fun AutoCard(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    GlassCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Copy.AUTO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.AUTO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            LiquidSwitch(on, onChange)
        }
    }
}

@Composable
private fun ResetSheet(
    canReset: Boolean,
    onRelearn: () -> Unit,
    onDefault: () -> Unit,
    onResetPermission: () -> Unit,
    onPoll: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    // 권한 초기화 단계: 0 = 닫힘, 1 = 경고, 2 = 재확인, 3 = 앱 쪽 초기화 완료(PC 회수 대기)
    var step by remember { mutableIntStateOf(0) }
    // PC에서 회수하는 즉시 알아채도록 대기 중에는 권한 상태를 확인한다.
    LaunchedEffect(step) {
        if (step == 3) while (true) { delay(2_000); onPoll() }
    }
    SheetColumn {
        Text(Copy.RESET_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(16.dp))
        GlassCard(padding = 16.dp) {
            Text(Copy.RESET_RELEARN, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.RESET_RELEARN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            LiquidButton(Copy.RESET_RELEARN, modifier = Modifier.fillMaxWidth(), onClick = onRelearn)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 16.dp) {
            Text(Copy.RESET_DEFAULT, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.RESET_DEFAULT_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            LiquidButton(Copy.RESET_DEFAULT, modifier = Modifier.fillMaxWidth(), onClick = onDefault, primary = false, enabled = canReset)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 16.dp, accent = if (step > 0) c.danger else null) {
            Text(Copy.RESET_PERMISSION, fontWeight = FontWeight.SemiBold, color = c.danger)
            Text(Copy.RESET_PERMISSION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text(Copy.RESET_PERMISSION_LIMIT, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(step, label = "revoke") { s ->
                when (s) {
                    0 -> LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.fillMaxWidth(), onClick = { step = 1 }, primary = false, enabled = canReset, danger = true)
                    3 -> Column(Modifier.fillMaxWidth()) {
                        Text(Copy.RESET_PERMISSION_STEP, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        val cmd = PermissionReset.command(context.packageName)
                        CommandBox(cmd, onCopy = { onCopy(cmd) })
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 대기 표시: 방울이 세 칸을 차례로 흘러 다닌다.
                            var dot by remember { mutableIntStateOf(0) }
                            LaunchedEffect(Unit) { while (true) { delay(600); dot = (dot + 1) % 3 } }
                            LiquidDots(count = 3, index = dot)
                            Spacer(Modifier.width(8.dp))
                            Text(Copy.RESET_PERMISSION_WAITING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> DangerConfirmBox(
                        if (s == 1) Copy.RESET_PERMISSION_WARN else Copy.RESET_PERMISSION_CONFIRM,
                        note = Copy.RESET_PERMISSION_KEEP,
                    ) {
                        LiquidButton(Copy.CANCEL, modifier = Modifier.weight(1f), onClick = { step = 0 }, primary = false)
                        if (s == 1) LiquidButton(Copy.CONTINUE, modifier = Modifier.weight(1f), onClick = { step = 2 }, primary = false, danger = true)
                        else LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.weight(1f), onClick = { onResetPermission(); step = 3 }, danger = true, burst = false)
                    }
                }
            }
        }
    }
}
