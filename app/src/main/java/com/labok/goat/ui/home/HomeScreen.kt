package com.labok.goat.ui.home

import android.Manifest
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.labok.goat.BuildConfig
import com.labok.goat.background.ExternalChangeNotifier
import com.labok.goat.data.display.PermissionReset
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.domain.DpiPolicy
import com.labok.goat.domain.PanelSpec
import com.labok.goat.domain.ScreenGeometry
import com.labok.goat.domain.ScreenPolicy
import com.labok.goat.ui.about.AboutSheet
import com.labok.goat.ui.about.ChangelogSheet
import com.labok.goat.ui.about.PermissionsSheet
import com.labok.goat.ui.about.ReportMail
import com.labok.goat.ui.about.SettingsScreen
import com.labok.goat.ui.advanced.AdvancedSheet
import com.labok.goat.ui.appsize.AppSizeGuideSheet
import com.labok.goat.ui.appsize.AppSizeHelpSheet
import com.labok.goat.ui.appsize.AppSizeResetSheet
import com.labok.goat.ui.appsize.AppSizeScreen
import com.labok.goat.ui.art.DeviceShape
import com.labok.goat.ui.art.FoldDeviceArt
import com.labok.goat.ui.art.FoldGeometry
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.ReportFlightArt
import com.labok.goat.ui.art.ScreensPreview
import com.labok.goat.ui.camera.CameraMuteHelpSheet
import com.labok.goat.ui.camera.CameraMuteScreen
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.DangerConfirmBox
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.copyText
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.help.GrantGuideSheet
import com.labok.goat.ui.help.HelpSheet
import com.labok.goat.ui.lab.LabAirplaneScreen
import com.labok.goat.ui.lab.LabFlexScreen
import com.labok.goat.ui.lab.LabIconsScreen
import com.labok.goat.ui.lab.LabScreen
import com.labok.goat.ui.lab.LabScreenModeScreen
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidDots
import com.labok.goat.ui.liquid.LiquidMenu
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LiquidToast
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.liquidReveal
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.nav.GLASS_ALPHA
import com.labok.goat.ui.nav.NavGlyph
import com.labok.goat.ui.nav.NavItem
import com.labok.goat.ui.nav.NavSection
import com.labok.goat.ui.nav.SideDrawerContent
import com.labok.goat.ui.rotate.ShakeCalibrationSheet
import com.labok.goat.ui.rotate.ShakeRotateHelpSheet
import com.labok.goat.ui.rotate.ShakeRotateScreen
import com.labok.goat.ui.sizetest.RowCountOverlay
import com.labok.goat.ui.sizetest.SizeTestSheet
import com.labok.goat.ui.text.Copy
import com.labok.goat.ui.tweaks.AnimationHelpSheet
import com.labok.goat.ui.tweaks.AnimationScreen
import com.labok.goat.ui.tweaks.BloatHelpSheet
import com.labok.goat.ui.tweaks.BloatScreen
import com.labok.goat.ui.tweaks.DarkHelpSheet
import com.labok.goat.ui.tweaks.DarkScreen
import com.labok.goat.ui.tweaks.DozeHelpSheet
import com.labok.goat.ui.tweaks.DozeScreen
import com.labok.goat.ui.tweaks.GosHelpSheet
import com.labok.goat.ui.tweaks.GosScreen
import com.labok.goat.ui.tweaks.RefreshHelpSheet
import com.labok.goat.ui.tweaks.RefreshScreen
import com.labok.goat.ui.tweaks.ResolutionHelpSheet
import com.labok.goat.ui.tweaks.ResolutionScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 하단 시트 종류. 한 번에 하나만 연다. */
/** 이 너비(dp) 이상이면 사이드바를 늘 펼쳐 둔다(폴드 내부 화면·태블릿). */
private const val WIDE_DP = 600

private enum class Sheet { HELP, GUIDE, TEST, ADVANCED, RESET, ABOUT, APP_HELP, APP_GUIDE, APP_RESET, SHAKE_HELP, SHAKE_CALIBRATE, CAMERA_HELP, ANIM_HELP, BLOAT_HELP, REFRESH_HELP, GOS_HELP, DARK_HELP, RES_HELP, DOZE_HELP, CHANGELOG, PERMISSIONS, FLEX_HELP, ICONS_HELP, AIRPLANE_HELP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    vm: HomeViewModel = viewModel(),
    /** 처음 보여 줄 기능 화면(NavItem 이름). 화면 점검 도구(tools/ui-sweep.sh)가 각 화면을 바로 열 때 쓴다. */
    openScreen: String? = null,
    /** 처음 띄울 시트(Sheet 이름). 보여 주기만 하고 아무것도 실행하지 않는다. */
    openSheet: String? = null
) {
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
    var feature by rememberSaveable { mutableStateOf(NavItem.HOME) }
    // 화면에 보이는 기능. 사이드바에서 고르면 방울 이동을 보여 준 뒤 사이드바가 닫히면서 바뀐다(물방울 번짐 전환).
    var shown by rememberSaveable { mutableStateOf(NavItem.HOME) }
    // 점검 도구용 진입: 이름이 맞을 때만 그 화면·시트를 연다(실험실 항목은 열린 경우에만)
    LaunchedEffect(openScreen, openSheet) {
        val item = openScreen?.let { n -> runCatching { NavItem.valueOf(n) }.getOrNull() }
        if (item != null && (item !in NavItem.LAB_ITEMS || LabUnlock.unlocked)) {
            feature = item
            shown = item
        }
        openSheet?.let { n -> runCatching { Sheet.valueOf(n) }.getOrNull() }?.let { sheet = it }
    }
    var flying by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 다음 화면이 번지기 시작할 자리(홈 카드를 누른 곳, 화면 기준). 사이드바에서 고르면 null.
    var revealFrom by remember { mutableStateOf<Offset?>(null) }
    // 기능 화면 영역의 화면 위치(넓은 화면에서는 사이드바만큼 오른쪽). 번짐 시작점을 이 영역 기준으로 바꾼다.
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }

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
                    delay(2_500)
                    vm.refresh()
                }
            }
        }
    }
    // 처음 열었는데 권한이 없으면 시작하기(권한 설정) 안내를 한 번 바로 연다. 닫으면 다시 자동으로 열지 않는다
    // (홈 맨 위 '준비하기' 카드로 언제든 다시 열 수 있다).
    val firstRunPrefs = remember { context.getSharedPreferences("first_run", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(state.hasPermission, state.loading) {
        if (state.loading || state.hasPermission || firstRunPrefs.getBoolean("setup_shown", false)) return@LaunchedEffect
        firstRunPrefs.edit().putBoolean("setup_shown", true).apply()
        sheet = Sheet.GUIDE
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
        message?.let {
            toast.showSnackbar(it)
            vm.messageShown()
        }
    }

    // 그림이 이 기기의 실제 화면비를 따르도록, 학습한 두 화면 비율을 모든 그림(시트 포함)에 내려 준다.
    val screenAspect = LocalConfiguration.current.let { cfg -> minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() / maxOf(cfg.screenWidthDp, cfg.screenHeightDp).coerceAtLeast(1) }
    val deviceShape = DeviceShape(
        cover = state.aspectOf(ScreenPolicy.Role.OUTER) ?: if (state.foldable) FoldGeometry.COVER_ASPECT else screenAspect,
        inner = state.aspectOf(ScreenPolicy.Role.INNER) ?: FoldGeometry.INNER_ASPECT
    )
    CompositionLocalProvider(LocalDeviceShape provides deviceShape) {
        // 사이드바는 기능 단위. DPI를 다루는 두 기능은 '화면 배율'로, 그 밖의 유틸은 '편의 기능'으로 묶는다. 조사 도구는 lab 빌드에서만.
        val sections = buildList {
            add(NavSection("", listOf(NavItem.HOME)))
            add(NavSection(Copy.MENU_SECTION_SCREEN, listOf(NavItem.DPI_MATCH, NavItem.APP_SIZE)))
            // 화면 표시: 크기(해상도) → 움직임(주사율·애니메이션) → 색(다크) 순. 해상도는 화면 배율 바로 뒤라 이어서 읽힌다.
            add(NavSection(Copy.MENU_SECTION_DISPLAY, listOf(NavItem.RESOLUTION, NavItem.REFRESH, NavItem.ANIMATION, NavItem.DARK, NavItem.STATUS_ICONS)))
            add(NavSection(Copy.MENU_SECTION_TOOLS, listOf(NavItem.SHAKE_ROTATE, NavItem.CAMERA_MUTE, NavItem.AIRPLANE)))
            add(NavSection(Copy.MENU_SECTION_SYSTEM, listOf(NavItem.APP_CLEANUP, NavItem.GOS, NavItem.DOZE)))
            // 실험실: 염소를 열 번 눌러 연 경우에만 보인다. 조사 도구는 lab 빌드에서만 함께 둔다.
            val lab = (if (LabUnlock.unlocked) listOfNotNull(NavItem.LAB_SCREEN, NavItem.LAB_FLEX.takeIf { com.labok.goat.data.lab.AutoFlex.supported() }) else emptyList()) +
                listOfNotNull(NavItem.LAB_TOOLS.takeIf { BuildConfig.LAB })
            if (lab.isNotEmpty()) add(NavSection(Copy.MENU_SECTION_LAB, lab))
        }
        // 점 세 개 메뉴: 지금 기능만의 도구를 먼저, 공통 항목(사용 방법·권한 설정·문제 신고·초기화)은 같은 순서로 뒤에 둔다.
        val common = { help: Sheet, guide: Sheet, reset: Sheet ->
            listOf(
                Copy.MENU_HELP to { sheet = help },
                Copy.MENU_GUIDE to { sheet = guide },
                Copy.MENU_REPORT to report,
                Copy.MENU_RESET to { sheet = reset }
            )
        }
        val menuItems = when (shown) {
            NavItem.DPI_MATCH -> listOfNotNull(
                // 크기 테스트는 두 화면을 비교하는 도구라 폴드에서만
                (Copy.MENU_TEST to { sheet = Sheet.TEST }).takeIf { state.foldable },
                Copy.MENU_ADVANCED to { sheet = Sheet.ADVANCED }
            ) + common(Sheet.HELP, Sheet.GUIDE, Sheet.RESET)
            NavItem.APP_SIZE -> common(Sheet.APP_HELP, Sheet.APP_GUIDE, Sheet.APP_RESET)
            // 권한은 고급 화면 배율 설정과 같은 시스템 설정 권한이라 같은 권한 설정 안내를 연다. 초기화할 것은 없다.
            NavItem.SHAKE_ROTATE -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.SHAKE_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.GUIDE },
                Copy.MENU_REPORT to report
            )
            // Shizuku 연결 안내는 앱별 화면 배율 설정과 같은 시트를 쓴다
            NavItem.CAMERA_MUTE -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.CAMERA_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.ANIMATION -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.ANIM_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.APP_CLEANUP -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.BLOAT_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.REFRESH -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.REFRESH_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.GOS -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.GOS_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.DOZE -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.DOZE_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.RESOLUTION -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.RES_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.DARK -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.DARK_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.HOME -> listOf(Copy.MENU_REPORT to report)
            // 자동 플렉스는 Shizuku가 필요해 Shizuku 연결 안내(앱별 화면 배율 설정과 같은 시트)를 연다
            NavItem.LAB_FLEX -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.FLEX_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.APP_GUIDE },
                Copy.MENU_REPORT to report
            )
            // 상태 표시줄 아이콘·비행기 모드 무선은 시스템 설정 권한(보안 설정 쓰기)으로 바꿔 같은 권한 설정 안내를 연다
            NavItem.STATUS_ICONS -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.ICONS_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.AIRPLANE -> listOf(
                Copy.MENU_HELP to { sheet = Sheet.AIRPLANE_HELP },
                Copy.MENU_GUIDE to { sheet = Sheet.GUIDE },
                Copy.MENU_REPORT to report
            )
            NavItem.LAB_SCREEN, NavItem.LAB_TOOLS -> listOf(Copy.MENU_REPORT to report)
            NavItem.SETTINGS -> listOf(Copy.MENU_REPORT to report)
        }
        // 기본 기능(화면 크기 맞추기) 화면. 기능 전환 애니메이션 안에서 그린다.
        val dpiMatchList: @Composable (PaddingValues) -> Unit = { inner ->
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = featurePadding(inner),
                verticalArrangement = Arrangement.spacedBy(Gap.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 고정 카드에도 키를 둬, 위에 할 일 카드가 생기거나 없어져도 카드 상태(스위치·슬라이더 방울)가 유지되게 한다.
                item(key = "card:status") { Box(Modifier.widthIn(max = ContentMaxWidth)) { StatusCard(state) } }
                items(todosOf(state), key = { it.key }) { todo ->
                    Box(Modifier.widthIn(max = ContentMaxWidth)) {
                        TodoCard(todo) { action ->
                            when (action) {
                                TodoAction.SETUP -> sheet = Sheet.GUIDE
                                TodoAction.RELEARN -> vm.resetLearned()
                                TodoAction.APPLY -> vm.applyNow()
                                TodoAction.AUTO -> vm.setAuto(true)
                                TodoAction.REPORT -> report()
                                TodoAction.ADOPT -> vm.resolveExternal(adopt = true)
                                TodoAction.RESTORE -> vm.resolveExternal(adopt = false)
                            }
                        }
                    }
                }
                item(key = "card:size") { Box(Modifier.widthIn(max = ContentMaxWidth)) { SizeCard(state, vm, onTest = { sheet = Sheet.TEST }) } }
                item(key = "card:auto") {
                    Box(Modifier.widthIn(max = ContentMaxWidth)) {
                        AutoCard(state.auto) { on ->
                            if (on && !ExternalChangeNotifier.canNotify(context)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.setAuto(on)
                        }
                    }
                }
            }
        }

        // 위쪽 제목 줄: 아래 내용이 비치는 반투명 블러(내용은 haze 원본, 제목 줄은 haze 자식)
        val haze = remember { HazeState() }
        // 넓은 화면(폴드 내부 화면·태블릿)에서는 사이드바를 왼쪽에 늘 펼쳐 두고, 좁은 화면에서는 ☰로 여닫는다.
        val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
        val haptics = rememberLiquidHaptics()
        // 사이드바가 열리고 닫힐 때(넓은 화면은 늘 펼쳐 있어 울리지 않는다)
        var drawerWasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(drawer.targetValue) {
            val open = drawer.targetValue == DrawerValue.Open
            if (!wide && open != drawerWasOpen) {
                if (open) haptics.open() else haptics.close()
            }
            drawerWasOpen = open
        }
        // 뒤로 가기: 열린 사이드바를 먼저 닫고, 다른 기능 화면이면 홈으로 돌아간다. 홈에서만 앱을 닫는다.
        BackHandler(enabled = drawer.isOpen || shown != NavItem.HOME) {
            if (drawer.isOpen) {
                scope.launch { drawer.close() }
            } else {
                revealFrom = null
                feature = NavItem.HOME
                shown = NavItem.HOME
            }
        }
        val drawerItems: @Composable () -> Unit = {
            SideDrawerContent(sections, current = feature, visible = wide || drawer.targetValue == DrawerValue.Open, glass = !wide, animateSelection = wide, onAbout = {
                // 사이드바 맨 아래 '앱 설정': 앱 정보·업데이트·기록 파일·UI 탄성을 모은 화면으로 간다
                revealFrom = null
                feature = NavItem.SETTINGS
                shown = NavItem.SETTINGS
                scope.launch { drawer.close() }
            }, onSelect = { item, at ->
                // 방울이 고른 항목으로 옮겨 가는 동안, 새 화면이 누른 줄에서 번져 나오고 사이드바도 함께 닫힌다(한 동작으로 이어지게).
                val moved = item != feature
                if (moved) haptics.segment()
                revealFrom = at
                feature = item
                shown = item
                scope.launch { drawer.close() }
            })
        }
        val main: @Composable () -> Unit = {
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
                                label = "title"
                            ) { f ->
                                // 메뉴 아이콘(사이드바·홈 타일과 같은 그림)과 제목
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.ink), contentAlignment = Alignment.Center) {
                                        NavGlyph(f.icon, c.onInk, Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        f.label,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                        modifier = Modifier.hazeChild(
                            haze,
                            HazeStyle(backgroundColor = c.bg, tint = HazeTint(c.bg.copy(alpha = 0.7f)), blurRadius = 24.dp, noiseFactor = 0f)
                        ),
                        navigationIcon = {
                            if (!wide) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "기능 메뉴 열기") }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "설정 메뉴") }
                                LiquidMenu(expanded = menu, onDismiss = { menu = false }, items = menuItems)
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(toast) { LiquidToast(it.visuals.message) } }
            ) { inner ->
                // 기능을 바꾸면 새 화면이 왼쪽(사이드바 쪽)에서 물방울처럼 번지며 덮는다. 이전 화면은 다 덮인 뒤 사라진다.
                AnimatedContent(
                    targetState = shown,
                    modifier = Modifier.haze(haze).onGloballyPositioned { contentOrigin = it.positionInRoot() },
                    transitionSpec = {
                        (EnterTransition.None togetherWith fadeOut(tween(1, delayMillis = 600))).apply { targetContentZIndex = 1f }
                    },
                    label = "feature"
                ) { f ->
                    // 처음 그릴 때가 들어오는 중(PreEnter)이면 번짐 전환, 앱을 처음 열 때(Visible)는 그대로 그린다
                    val entering = remember { transition.currentState == EnterExitState.PreEnter }
                    Box(
                        Modifier
                            .fillMaxSize()
                            // 홈 카드를 눌러 왔으면 누른 자리에서, 사이드바에서 왔으면 왼쪽(사이드바 쪽)에서 번진다
                            .liquidReveal(entering) { size -> revealFrom?.minus(contentOrigin) ?: Offset(0f, size.height * 0.3f) }
                            .background(c.bg)
                    ) {
                        when (f) {
                            NavItem.DPI_MATCH -> dpiMatchList(inner)
                            NavItem.APP_SIZE -> AppSizeScreen(inner, onGuide = { sheet = Sheet.APP_GUIDE }, onReset = { sheet = Sheet.APP_RESET })
                            NavItem.SHAKE_ROTATE -> ShakeRotateScreen(inner, onGuide = { sheet = Sheet.GUIDE }, onCalibrate = { sheet = Sheet.SHAKE_CALIBRATE })
                            NavItem.CAMERA_MUTE -> CameraMuteScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.HOME -> Dashboard(inner, state, sections, onOpen = { item, at ->
                                revealFrom = at
                                feature = item
                                shown = item
                            }, onSetup = { sheet = Sheet.GUIDE }, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.ANIMATION -> AnimationScreen(inner, permitted = state.hasPermission, onGuide = { sheet = Sheet.GUIDE })
                            NavItem.APP_CLEANUP -> BloatScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.REFRESH -> RefreshScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.GOS -> GosScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.DOZE -> DozeScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.RESOLUTION -> ResolutionScreen(inner, permitted = state.hasPermission, onGuide = { sheet = Sheet.GUIDE })
                            NavItem.DARK -> DarkScreen(inner, onConnect = { sheet = Sheet.APP_GUIDE })
                            NavItem.AIRPLANE -> LabAirplaneScreen(inner)
                            NavItem.STATUS_ICONS -> LabIconsScreen(inner)
                            NavItem.LAB_SCREEN -> LabScreenModeScreen(inner)
                            NavItem.LAB_FLEX -> LabFlexScreen(inner)
                            NavItem.LAB_TOOLS -> LabScreen(inner)
                            NavItem.SETTINGS -> SettingsScreen(inner, onAbout = { sheet = Sheet.ABOUT }, onChangelog = { sheet = Sheet.CHANGELOG }, onPermissions = { sheet = Sheet.PERMISSIONS })
                        }
                    }
                }
            }
        }
        // 서랍·시트가 덮으면 뒤 화면을 흐린다(덮개는 반투명이라 흐려진 화면이 살짝 비친다). 동작 줄이기면 바로 바꾼다.
        val reduce = LocalReduceMotion.current
        val covered = sheet != null || (!wide && drawer.targetValue == DrawerValue.Open)
        val behind by animateDpAsState(if (covered) BEHIND_BLUR else 0.dp, if (reduce) snap() else tween(280), label = "behindBlur")
        if (wide) {
            Row(Modifier.fillMaxSize().background(c.bg).blur(behind)) {
                drawerItems()
                Box(Modifier.weight(1f)) { main() }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                scrimColor = c.ink.copy(alpha = 0.16f),
                drawerContent = drawerItems
            ) { Box(Modifier.blur(behind)) { main() } }
        }

        if (counting) {
            RowCountOverlay(onClose = {
                counting = false
                sheet = Sheet.TEST
            })
        }

        flying?.let { done ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ReportFlightArt(onDone = done) }
        }

        sheet?.let { current ->
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.bg.copy(alpha = GLASS_ALPHA),
                scrimColor = c.ink.copy(alpha = 0.16f)
            ) {
                when (current) {
                    Sheet.HELP -> HelpSheet(state, onOpenGuide = { sheet = Sheet.GUIDE })
                    Sheet.GUIDE -> GrantGuideSheet(state.hasPermission, onCopied = { vm.say(Copy.TOAST_COPIED) })
                    Sheet.TEST -> SizeTestSheet(state, onCount = {
                        sheet = null
                        counting = true
                    }, onCalibrate = vm::setRulerPpi)
                    Sheet.ADVANCED -> AdvancedSheet(state, onClearLogs = vm::clearDiagnostics)
                    Sheet.RESET -> ResetSheet(
                        canReset = state.hasPermission,
                        foldable = state.foldable,
                        onRelearn = {
                            sheet = null
                            vm.resetLearned()
                        },
                        onDefault = {
                            sheet = null
                            vm.resetToDefault()
                        },
                        onResetPermission = vm::resetPermissionState,
                        onPoll = { vm.refresh() },
                        onCopy = { text -> if (copyText(context, text, "adb")) vm.say(Copy.TOAST_COPIED) }
                    )
                    Sheet.ABOUT -> AboutSheet(onMail = { ReportMail.inquiry(context) })
                    Sheet.APP_HELP -> AppSizeHelpSheet(foldable = state.foldable, onConnect = { sheet = Sheet.APP_GUIDE })
                    Sheet.APP_GUIDE -> AppSizeGuideSheet()
                    Sheet.APP_RESET -> AppSizeResetSheet(onDone = { msg ->
                        sheet = null
                        vm.say(msg)
                    })
                    Sheet.SHAKE_HELP -> ShakeRotateHelpSheet()
                    Sheet.SHAKE_CALIBRATE -> ShakeCalibrationSheet(onClose = { sheet = null })
                    Sheet.CAMERA_HELP -> CameraMuteHelpSheet()
                    Sheet.FLEX_HELP -> com.labok.goat.ui.lab.FlexHelpSheet()
                    Sheet.ANIM_HELP -> AnimationHelpSheet()
                    Sheet.BLOAT_HELP -> BloatHelpSheet()
                    Sheet.REFRESH_HELP -> RefreshHelpSheet()
                    Sheet.GOS_HELP -> GosHelpSheet()
                    Sheet.DOZE_HELP -> DozeHelpSheet()
                    Sheet.ICONS_HELP -> com.labok.goat.ui.lab.StatusIconsHelpSheet()
                    Sheet.AIRPLANE_HELP -> com.labok.goat.ui.lab.AirplaneHelpSheet()
                    Sheet.CHANGELOG -> ChangelogSheet()
                    Sheet.PERMISSIONS -> PermissionsSheet()
                    Sheet.DARK_HELP -> DarkHelpSheet()
                    Sheet.RES_HELP -> ResolutionHelpSheet()
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
        state.target == null -> if (state.foldable) Copy.STATUS_NO_TARGET else Copy.STATUS_NO_TARGET_SINGLE
        state.external != null -> Copy.STATUS_EXTERNAL
        state.status == UiState.Status.BLOCKED -> Copy.STATUS_HOLD
        state.foldable && state.rows.size < 2 -> Copy.STATUS_NEED_OTHER
        state.status == UiState.Status.OK -> if (state.foldable) Copy.STATUS_OK else Copy.STATUS_OK_SINGLE
        else -> Copy.STATUS_FIXING
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoldDeviceArt(
                unfolded = unfolded,
                height = 118.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    // 폴드가 아니면 화면이 하나라 외부·내부를 말하지 않는다
                    if (!state.foldable) {
                        Copy.CURRENT
                    } else {
                        when (role) {
                            ScreenPolicy.Role.INNER -> Copy.INNER
                            ScreenPolicy.Role.OUTER -> Copy.OUTER
                            null -> Copy.CURRENT
                        }
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.dpi?.current?.toString() ?: "—",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        color = c.ink
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
    val key: String,
    val title: String,
    val body: String?,
    val action: TodoAction?,
    val actionLabel: String?,
    val blocking: Boolean = false,
    /** 두 번째 선택지(외부 변경의 복원 등). 있으면 버튼을 본문 아래에 나란히 둔다. */
    val second: TodoAction? = null,
    val secondLabel: String? = null
)

/** 사용자가 할 일만. 앱을 못 쓰게 하는 것(준비)을 맨 위에. 할 일이 없으면 아무것도 보여 주지 않는다. */
private fun todosOf(state: UiState): List<Todo> = buildList {
    if (state.loading) return@buildList
    if (!state.hasPermission) add(Todo("setup", Copy.TODO_SETUP_TITLE, Copy.TODO_SETUP_BODY, TodoAction.SETUP, Copy.TODO_SETUP_ACTION, blocking = true))
    state.external?.let { e ->
        add(
            Todo(
                "external",
                Copy.EXTERNAL_TITLE,
                "다른 설정에서 ${if (state.foldable) "${e.role.label} 화면 " else ""}DPI가 ${e.from} → ${e.to}(으)로 변경되었습니다. ${Copy.EXTERNAL_BODY}",
                TodoAction.ADOPT,
                "${e.to} 기준으로 설정",
                blocking = true,
                second = TodoAction.RESTORE,
                secondLabel = "${e.from}(으)로 복원"
            )
        )
    }
    if (state.hasPermission && state.status == UiState.Status.BLOCKED) {
        add(Todo("hold", Copy.TODO_HOLD_TITLE, Copy.TODO_HOLD_BODY, TodoAction.RELEARN, Copy.TODO_HOLD_ACTION))
    }
    if (state.foldable && state.target != null && state.rows.size < 2) add(Todo("other", Copy.TODO_OTHER_TITLE, Copy.TODO_OTHER_BODY, null, null))
    if (state.hasPermission && state.status == UiState.Status.NEEDS_FIX && state.external == null) {
        add(Todo("apply", Copy.TODO_APPLY_TITLE, null, TodoAction.APPLY, Copy.TODO_APPLY_ACTION))
    }
    if (state.hasPermission && !state.auto && state.target != null) add(Todo("auto", Copy.TODO_AUTO_TITLE, Copy.TODO_AUTO_BODY, TodoAction.AUTO, Copy.TODO_AUTO_ACTION))
    if (state.hasCrash) add(Todo("crash", Copy.TODO_CRASH_TITLE, Copy.TODO_CRASH_BODY, TodoAction.REPORT, Copy.TODO_CRASH_ACTION))
}

/** 할 일 카드 안쪽 폭이 이보다 넓을 때만 버튼을 글 옆에 둔다. */
private val TODO_SIDE_BUTTON = 360.dp

@Composable
private fun TodoCard(todo: Todo, onAction: (TodoAction) -> Unit) {
    val c = LocalLiquid.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + shrinkVertically()
    ) {
        GlassCard(accent = if (todo.blocking) c.ink else null, padding = 16.dp) {
            if (todo.second != null) {
                Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(Gap.Card))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (todo.action != null && todo.actionLabel != null) LiquidButton(todo.actionLabel, modifier = Modifier.weight(1f), onClick = { onAction(todo.action) })
                    LiquidButton(todo.secondLabel ?: "", modifier = Modifier.weight(1f), onClick = { onAction(todo.second) }, primary = false)
                }
            } else {
                // 넓으면 글 옆에 버튼, 좁으면(제목이 버튼에 밀려 접히는 폭) 버튼을 글 아래 폭 전체로 둔다
                BoxWithConstraints {
                    if (maxWidth >= TODO_SIDE_BUTTON) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                                todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (todo.action != null && todo.actionLabel != null) {
                                Spacer(Modifier.width(10.dp))
                                LiquidButton(todo.actionLabel, onClick = { onAction(todo.action) })
                            }
                        }
                    } else {
                        Column {
                            Text(todo.title, fontWeight = FontWeight.SemiBold, color = c.ink)
                            todo.body?.let { Text(it, color = c.muted, style = MaterialTheme.typography.bodySmall) }
                            if (todo.action != null && todo.actionLabel != null) {
                                Spacer(Modifier.height(Gap.Card))
                                LiquidButton(todo.actionLabel, modifier = Modifier.fillMaxWidth(), onClick = { onAction(todo.action) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 외부 화면 크기(바로 조절) → 내부 화면은 기기 정보로 계산. 미세조정과 미리보기. */
@Composable
private fun SizeCard(state: UiState, vm: HomeViewModel, onTest: () -> Unit) {
    val c = LocalLiquid.current
    // 조절 범위: 최소 120(ldpi) ~ 이 기기 화면의 짧은 변으로 정한 최대(앱이 깨지지 않는 범위). 폴드는 외부 화면 기준.
    val shortSide = remember(state.plan, state.activeScreen) {
        val key = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.OUTER)?.key } ?: state.activeScreen?.key
        key?.split('x')?.mapNotNull { it.toIntOrNull() }?.minOrNull()
    }
    val sliderRange = DpiPolicy.MIN_DPI..(shortSide?.let { DpiPolicy.maxForShortSide(it) } ?: DpiPolicy.MAX_DPI)
    var outer by remember(state.target) { mutableIntStateOf(state.target ?: 360) }
    var adjust by remember(state.adjust) { mutableIntStateOf(state.adjust) }
    val outerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.OUTER)?.let { s -> ps.first { it.screen.key == s.key } } }
    val innerPanel = state.plan?.panels?.let { ps -> ScreenPolicy.screenFor(ps.map { it.screen }, ScreenPolicy.Role.INNER)?.let { s -> ps.first { it.screen.key == s.key } } }
    // 끄는 동안의 미리보기 값으로 내부 화면 크기를 계산한다.
    val previewPlan = state.plan?.copy(referenceDpi = outer, adjust = adjust)
    val innerDpi = innerPanel?.let { previewPlan?.targetFor(it.screen)?.dpi }

    GlassCard {
        Text(Copy.SIZE_TITLE, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Gap.Item))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.foldable) Copy.SIZE_OUTER else Copy.SIZE_SINGLE, color = c.ink, modifier = Modifier.weight(1f))
            Text("$outer dpi", color = c.ink, fontWeight = FontWeight.SemiBold)
        }
        Text(Copy.SIZE_OUTER_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(
            value = outer,
            range = sliderRange,
            onValueChange = { outer = it },
            onValueChangeFinished = {},
            valueText = { "$it" },
            description = if (state.foldable) Copy.SIZE_OUTER else Copy.SIZE_SINGLE,
            enabled = state.hasPermission
        )
        // 내부 화면 보정은 폴드에서만(화면이 하나면 맞출 다른 화면이 없다)
        if (state.foldable) {
            Spacer(Modifier.height(Gap.Section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Copy.SIZE_INNER_TUNE, color = c.ink, modifier = Modifier.weight(1f))
                Text(innerDpi?.let { "$it dpi" } ?: "—", color = c.ink, fontWeight = FontWeight.SemiBold)
            }
            Text(Copy.SIZE_INNER_TUNE_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidSlider(
                value = adjust,
                range = -SettingsRepository.MAX_ADJUST..SettingsRepository.MAX_ADJUST,
                onValueChange = { adjust = it },
                onValueChangeFinished = {},
                valueText = { if (it > 0) "+$it" else "$it" },
                centered = true,
                description = Copy.SIZE_INNER_TUNE,
                enabled = state.hasPermission && innerPanel != null
            )
        }
        // 조절바는 값만 바꾸고, [적용]을 눌러야 화면에 반영한다(끄는 도중 화면이 계속 바뀌지 않게).
        // 처음 설치해 저장된 값이 없을 때도 지금 값을 바로 적용할 수 있게 버튼을 보인다.
        val pending = state.hasPermission && (state.target == null || outer != state.target || adjust != state.adjust)
        AnimatedVisibility(pending, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(Gap.Item))
                Text(Copy.SIZE_PENDING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.target != null) {
                        LiquidButton(Copy.SIZE_REVERT, modifier = Modifier.weight(1f), primary = false, onClick = {
                            outer = state.target
                            adjust = state.adjust
                        })
                    }
                    LiquidButton(Copy.SIZE_APPLY, modifier = Modifier.weight(1f), onClick = { vm.commitSize(outer, adjust) })
                }
            }
        }
        if (state.foldable && outerPanel != null && innerPanel != null && innerDpi != null) {
            Spacer(Modifier.height(Gap.Item))
            ScreensPreview(
                outerInch = outerPanel.screen.diagonalInch, outerAspect = aspect(outerPanel, outerPanel, false), outerPpi = outerPanel.screen.ppi, outerDpi = outer,
                innerInch = innerPanel.screen.diagonalInch, innerAspect = aspect(innerPanel, outerPanel, true), innerPpi = innerPanel.screen.ppi, innerDpi = innerDpi,
                outerLabel = "${Copy.SIZE_PREVIEW_OUTER} $outer", innerLabel = "${Copy.SIZE_PREVIEW_INNER} $innerDpi",
                innerActive = state.effective?.role?.let { it == ScreenPolicy.Role.INNER }
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.SIZE_TEST, modifier = Modifier.fillMaxWidth(), onClick = onTest, primary = false)
        }
    }
}

/** 세로 ÷ 가로(미리보기용). 내부 화면은 실제 방향(Fold8은 가로로 긴 화면)으로 계산한다. */
private fun aspect(p: PanelSpec.Resolved, cover: PanelSpec.Resolved, isInner: Boolean): Float = ScreenGeometry.orientedSize(p.screen.key, cover.screen.key, isInner)?.let { (w, h) -> h / w } ?: 2f

@Composable
private fun AutoCard(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Copy.AUTO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.AUTO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            LiquidSwitch(on, onChange, label = Copy.AUTO_TITLE)
        }
    }
}

@Composable
private fun ResetSheet(
    canReset: Boolean,
    foldable: Boolean = true,
    onRelearn: () -> Unit,
    onDefault: () -> Unit,
    onResetPermission: () -> Unit,
    onPoll: () -> Unit,
    onCopy: (String) -> Unit
) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    // 권한 초기화 단계: 0 = 닫힘, 1 = 경고, 2 = 재확인, 3 = 앱 쪽 초기화 완료(PC 회수 대기)
    var step by remember { mutableIntStateOf(0) }
    // PC에서 회수하는 즉시 알아채도록 대기 중에는 권한 상태를 확인한다.
    LaunchedEffect(step) {
        if (step == 3) {
            while (true) {
                delay(2_000)
                onPoll()
            }
        }
    }
    SheetColumn {
        Text(Copy.RESET_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Spacer(Modifier.height(Gap.Section))
        // 화면 학습 초기화는 두 화면을 배우는 폴드에서만
        if (foldable) {
            GlassCard {
                Text(Copy.RESET_RELEARN, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.RESET_RELEARN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(Copy.RESET_RELEARN, modifier = Modifier.fillMaxWidth(), onClick = onRelearn)
            }
            Spacer(Modifier.height(Gap.Card))
        }
        GlassCard {
            Text(Copy.RESET_DEFAULT, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.RESET_DEFAULT_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.RESET_DEFAULT, modifier = Modifier.fillMaxWidth(), onClick = onDefault, primary = false, enabled = canReset)
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(accent = if (step > 0) c.danger else null) {
            Text(Copy.RESET_PERMISSION, fontWeight = FontWeight.SemiBold, color = c.danger)
            Text(Copy.RESET_PERMISSION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text(Copy.RESET_PERMISSION_LIMIT, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            AnimatedContent(step, label = "revoke") { s ->
                when (s) {
                    0 -> LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.fillMaxWidth(), onClick = { step = 1 }, primary = false, enabled = canReset, danger = true)
                    3 -> Column(Modifier.fillMaxWidth()) {
                        Text(Copy.RESET_PERMISSION_STEP, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Gap.Item))
                        val cmd = PermissionReset.command(context.packageName)
                        CommandBox(cmd, onCopy = { onCopy(cmd) })
                        Spacer(Modifier.height(Gap.Item))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 대기 표시: 방울이 세 칸을 차례로 흘러 다닌다.
                            var dot by remember { mutableIntStateOf(0) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(600)
                                    dot = (dot + 1) % 3
                                }
                            }
                            LiquidDots(count = 3, index = dot)
                            Spacer(Modifier.width(8.dp))
                            Text(Copy.RESET_PERMISSION_WAITING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> DangerConfirmBox(
                        if (s == 1) Copy.RESET_PERMISSION_WARN else Copy.RESET_PERMISSION_CONFIRM,
                        note = Copy.RESET_PERMISSION_KEEP
                    ) {
                        LiquidButton(Copy.CANCEL, modifier = Modifier.weight(1f), onClick = { step = 0 }, primary = false)
                        if (s == 1) {
                            LiquidButton(Copy.CONTINUE, modifier = Modifier.weight(1f), onClick = { step = 2 }, primary = false, danger = true)
                        } else {
                            LiquidButton(Copy.RESET_PERMISSION, modifier = Modifier.weight(1f), onClick = {
                                onResetPermission()
                                step = 3
                            }, danger = true, burst = false)
                        }
                    }
                }
            }
        }
    }
}

/** 서랍·시트가 덮었을 때 뒤 화면 흐림 반지름. */
private val BEHIND_BLUR = 14.dp
