package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.ApiProbe
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.LabSetting
import com.labok.goat.data.lab.ScreenMode
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.appsize.ago
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Springs
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.liquid.goo
import com.labok.goat.ui.text.Copy
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 실험실 기능(염소를 열 번 눌러 연다). 사이드바 '실험실' 묶음에 기능마다 한 메뉴로 둔다:
 * 비행기 모드 무선, 상태 표시줄 아이콘, 화면 모드, 숨은 기능 찾기. 설정을 바꾸는 기능은 처음 값을 남겨 [처음대로]로 되돌린다.
 */

/** 실험실 설정 쓰기: 작업 스레드에서 쓰고 [version]을 올려 화면이 값을 다시 읽게 한다. */
private class LabWriter(private val context: Context, private val launch: (suspend () -> Unit) -> Unit) {
    var version by mutableIntStateOf(0)

    fun write(setting: LabSetting, value: String?) = launch {
        val ok = withContext(Dispatchers.IO) { setting.write(context, value) }
        version++
        if (!ok) Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
    }

    fun restore(setting: LabSetting) = launch {
        val ok = withContext(Dispatchers.IO) { setting.restore(context) }
        version++
        Toast.makeText(context, if (ok) Copy.LAB_RESTORED else Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun rememberLabWriter(): LabWriter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { LabWriter(context) { block -> scope.launch { block() } } }
}

/** 실험실 화면 골격: 카드 하나를 가운데 폭으로 둔다. */
@Composable
private fun LabPage(contentPadding: PaddingValues, card: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "card") { Box(Modifier.widthIn(max = ContentMaxWidth)) { GlassCard(content = card) } }
    }
}

/** 비행기 모드 때 켜 둘 무선. */
@Composable
internal fun LabAirplaneScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    LabPage(contentPadding) {
        val raw = remember(w.version) { AirplaneRadios.setting.read(context) }
        Text(Copy.LAB_AIRPLANE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_AIRPLANE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        // 세 줄에 같은 설명을 되풀이하지 않고 한 번만 적는다
        Text(Copy.LAB_AIRPLANE_ROW, color = c.muted, style = MaterialTheme.typography.bodySmall)
        AirplaneRadios.Radio.entries.forEach { radio ->
            Spacer(Modifier.height(Gap.Item))
            LiquidSwitchRow("${radio.label} 켜 두기", null, AirplaneRadios.kept(raw, radio), true) { keep ->
                w.write(AirplaneRadios.setting, AirplaneRadios.edit(raw, radio, keep))
            }
        }
        Spacer(Modifier.height(Gap.Item))
        val changed = remember(w.version) { AirplaneRadios.setting.changed(context) }
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(AirplaneRadios.setting) })
    }
}

/** 상태 표시줄 아이콘 숨기기. */
@Composable
internal fun LabIconsScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    LabPage(contentPadding) {
        val raw = remember(w.version) { IconBlacklist.setting.read(context) }
        Text(Copy.LAB_ICONS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_ICONS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        IconBlacklist.Slot.entries.forEach { slot ->
            Spacer(Modifier.height(Gap.Item))
            // 줄 앞에 상태 표시줄에서 보이는 모양을 그려 어떤 아이콘인지 바로 알아보게 한다
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBarGlyph(slot, c.ink)
                Spacer(Modifier.width(Gap.Card))
                Box(Modifier.weight(1f)) {
                    LiquidSwitchRow("${slot.label} 숨기기", slot.id, IconBlacklist.hidden(raw, slot), true) { hide ->
                        w.write(IconBlacklist.setting, IconBlacklist.edit(raw, slot, hide))
                    }
                }
            }
        }
        Spacer(Modifier.height(Gap.Item))
        val changed = remember(w.version) { IconBlacklist.setting.changed(context) }
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(IconBlacklist.setting) })
    }
}

/** 화면 모드(디스플레이 색 모드) 값 바꿔 보기. */
@Composable
internal fun LabScreenModeScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    LabPage(contentPadding) {
        val setting = remember(w.version) { ScreenMode.active(context) }
        val now = remember(w.version) { setting?.read(context) }
        Text(Copy.LAB_SCREEN_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_SCREEN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        if (setting == null) {
            Text(Copy.LAB_SCREEN_NONE, color = c.muted, style = MaterialTheme.typography.bodySmall)
            return@LabPage
        }
        Text("${setting.key} = ${now ?: "-"}", color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        val values = if (setting === ScreenMode.samsung) ScreenMode.SAMSUNG_VALUES.map { it to it } else ScreenMode.AOSP_VALUES
        LiquidChips(
            options = values.map { it.first },
            selected = now ?: "",
            label = { v -> values.first { it.first == v }.second },
            onSelect = { w.write(setting, it) }
        )
        Spacer(Modifier.height(Gap.Item))
        val changed = remember(w.version) { setting.changed(context) }
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(setting) })
    }
}

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
    // 플렉스 화면 지원 앱(설치 파일을 읽어 추정, 처음 한 번은 시간이 걸린다). null이면 찾는 중
    var aware by remember { mutableStateOf<Set<String>?>(null) }
    val horizontal = rememberHingeHorizontal(supported)
    // 패널을 끈 앱(예외). 나머지는 모두 켜져 있다
    var off by remember { mutableStateOf(AutoFlex.excluded(context)) }
    var appsOpen by remember { mutableStateOf(false) }
    var gesturesOpen by remember { mutableStateOf(false) }
    var diagRunning by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf(AutoFlex.wheel.read(context)) }
    // 이 설정 화면을 보는 동안에는 자동 플렉스를 멈춰 화면이 바뀌지 않게 한다. 시험은 [플렉스 모드 테스트하기]가 여는
    // 시험 화면(FlexTestActivity)에서만 하므로, 이 화면이 앞에 있을 때(RESUMED)만 멈춘다
    var resumed by remember { mutableStateOf(true) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
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
    val rangeNow by androidx.compose.runtime.rememberUpdatedState(min to max)
    val resumedNow by androidx.compose.runtime.rememberUpdatedState(resumed)
    val horizontalNow by androidx.compose.runtime.rememberUpdatedState(horizontal)
    val angleNow by androidx.compose.runtime.rememberUpdatedState(angle)
    LaunchedEffect(supported) {
        if (!supported) return@LaunchedEffect
        var was: Boolean? = null
        androidx.compose.runtime.snapshotFlow { angleNow }.collect { a ->
            if (a == null) return@collect
            val inside = AutoFlex.inRange(a, rangeNow.first, rangeNow.second)
            if (was != null && inside != was && resumedNow && horizontalNow) AutoFlex.rangeBuzz(context, inside)
            was = inside
        }
    }
    LaunchedEffect(supported) {
        if (!supported) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { loadApps(context) }
        apps = list
        aware = withContext(Dispatchers.IO) { list.filter { com.labok.goat.data.lab.FlexAppScan.supports(context, it.pkg) }.map { it.pkg }.toSet() }
    }

    // 스위치는 누르자마자 바뀌고(물방울 손잡이가 먼저 출렁인다), 줄은 손잡이가 자리 잡은 뒤 물처럼 흘러 다른 묶음으로 옮겨 간다
    val pending = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    fun toggle(app: AppEntry, want: Boolean) = scope.launch {
        pending[app.pkg] = want
        val err = withContext(Dispatchers.IO) { AutoFlex.setAppPanel(context, app.pkg, want) }
        if (err == null) {
            kotlinx.coroutines.delay(360)
            off = AutoFlex.excluded(context)
        } else {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
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
                    LiquidButton(Copy.LAB_FLEX_TEST, modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = { context.startActivity(Intent(context, FlexTestActivity::class.java)) })
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
        // 접으면 패널 자동 표시(공식 화면과 같은 이름): 요약 줄을 누르면 앱 목록이 펼쳐진다
        val q = query.trim()
        val all = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
        val shownApps = apps.orEmpty().filter { it.pkg !in off }
        item(key = "apps") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = Gap.Flush) {
                    Row(Modifier.fillMaxWidth().clickable { appsOpen = !appsOpen }.padding(Gap.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.LAB_FLEX_APPS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(appsSummary(shownApps), color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        val turn by androidx.compose.animation.core.animateFloatAsState(if (appsOpen) 180f else 0f, Springs.pop(), label = "appsChevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
                    }
                }
            }
        }
        if (appsOpen) {
            item(key = "search") {
                Column(Modifier.widthIn(max = ContentMaxWidth).animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) {
                    Text(Copy.LAB_FLEX_APPS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Gap.CardPadding))
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
            val excluded = all.filter { it.pkg in off }
            val known = aware
            // 켜 둔 앱은 플렉스 화면 지원 앱과 그 밖의 앱으로 나눠 모은다(지원 앱을 찾는 동안은 한데 둔다)
            val awareApps = if (known == null) emptyList() else all.filter { it.pkg !in off && it.pkg in known }
            val rest = all.filter { it.pkg !in off && (known == null || it.pkg !in known) }
            if (apps == null) item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
            // 같은 키를 두 묶음에서 그대로 써서, 켜고 끌 때 줄이 사라졌다 생기지 않고 새 자리로 흘러간다
            val anim = Modifier
            if (excluded.isNotEmpty()) {
                item(key = "h-off") { Section("패널을 끈 앱 ${excluded.size}", anim.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) }
                items(excluded, key = { it.pkg }) { app ->
                    FlexAppRow(app, pending[app.pkg] ?: false, Modifier.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade), aware = aware?.contains(app.pkg) == true) { toggle(app, it) }
                }
            }
            if (apps != null && known == null) item(key = "scanning") { LiquidLoader(Copy.LAB_FLEX_APPS_SCANNING, Modifier.padding(vertical = 12.dp)) }
            if (awareApps.isNotEmpty()) {
                item(key = "h-aware") { Section("${Copy.LAB_FLEX_APPS_AWARE} ${awareApps.size}", anim.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) }
                items(awareApps, key = { it.pkg }) { app ->
                    FlexAppRow(app, pending[app.pkg] ?: true, Modifier.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade), aware = true) { toggle(app, it) }
                }
            }
            item(key = "h-rest") { Section(if (known == null) Copy.LAB_FLEX_APPS_REST else Copy.LAB_FLEX_APPS_OTHER, anim.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) }
            items(rest, key = { it.pkg }) { app ->
                FlexAppRow(app, pending[app.pkg] ?: true, Modifier.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) { toggle(app, it) }
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
        // 터치패널 진단: 일반 앱에 시스템 터치패널을 띄울 수 있는지 가릴 정보를 모아 파일로 보낸다
        item(key = "diag") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_FLEX_DIAG_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FLEX_DIAG_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(if (diagRunning) Copy.LAB_FLEX_DIAG_RUNNING else Copy.LAB_FLEX_DIAG_RUN, primary = false, enabled = !diagRunning && shizuku == ShizukuAccess.State.READY, modifier = Modifier.fillMaxWidth(), onClick = {
                        diagRunning = true
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { com.labok.goat.data.lab.FlexPanel.diagnose(context) }
                            val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("flex-panel"), text) }
                            diagRunning = false
                            uri?.let { share(context, it) }
                        }
                    })
                }
            }
        }
        // 터치패드 제스처 도움말(공식 화면과 같은 내용)
        item(key = "gestures") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(padding = Gap.Flush) {
                    Row(Modifier.fillMaxWidth().clickable { gesturesOpen = !gesturesOpen }.padding(Gap.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        Text(Copy.LAB_FLEX_GESTURES_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                        val turn by androidx.compose.animation.core.animateFloatAsState(if (gesturesOpen) 180f else 0f, Springs.pop(), label = "gestureChevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        gesturesOpen,
                        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(UiMotion.ratio(0.75f), UiMotion.stiffness(300f, 0.75f))) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(UiMotion.ratio(0.9f), UiMotion.stiffness(420f, 0.9f))) + androidx.compose.animation.fadeOut()
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
private fun appsSummary(apps: List<AppEntry>): String = when {
    apps.isEmpty() -> "없음"
    apps.size <= 2 -> apps.joinToString(", ") { it.label }
    else -> apps.take(2).joinToString(", ") { it.label } + " 외 ${apps.size - 2}개"
}

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

/**
 * 플렉스 모드 그림: 두께 있는 폴드를 비스듬히 내려다본 모습. 두 화면을 얇은 판(위·아래·옆면)으로 그려 두께감을 내고,
 * 모서리는 모두 둥글린다. 힌지가 천천히 접혔다 펴지고, 플렉스 범위(115° 아래)로 들어오면 아래 화면에 조작 패널이,
 * 위 화면에 영상이 스며들듯 켜진다.
 */
@Composable
internal fun FlexPanelArt() {
    val c = LocalLiquid.current
    val t = com.labok.goat.ui.art.artLoop(3200)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val p = (1 - kotlin.math.cos(t * 2 * Math.PI).toFloat()) / 2
        val k = p * p * (3 - 2 * p) // 0..1 접힘 정도(부드럽게)
        val theta = 180f - 80f * k // 180° 펼침 → 100°
        val sc = size.height / 220f * 170f
        val ox = size.width / 2
        val oy = size.height * 150f / 220f
        val yaw = Math.toRadians(-35.0)
        val pitch = Math.toRadians(28.0)
        val cy = kotlin.math.cos(yaw).toFloat()
        val sy = kotlin.math.sin(yaw).toFloat()
        val cp = kotlin.math.cos(pitch).toFloat()
        val sp = kotlin.math.sin(pitch).toFloat()

        // 3차원 점(x 가로, y 위, z 앞쪽) → 화면 좌표
        fun pt(v: FloatArray) = androidx.compose.ui.geometry.Offset(
            ox + (v[0] * cy + v[2] * sy) * sc,
            oy - (v[1] * cp - (-v[0] * sy + v[2] * cy) * sp) * sc
        )
        fun add(a: FloatArray, b: FloatArray, m: Float = 1f) = floatArrayOf(a[0] + b[0] * m, a[1] + b[1] * m, a[2] + b[2] * m)

        // 모서리를 둥글린 다각형: 각 꼭짓점 앞뒤로 r만큼 물러난 곳을 곡선으로 잇는다(변이 짧으면 반만)
        fun round(q: List<androidx.compose.ui.geometry.Offset>, r: Float) = androidx.compose.ui.graphics.Path().apply {
            val n = q.size
            for (i in 0 until n) {
                val prev = q[(i + n - 1) % n]
                val cur = q[i]
                val next = q[(i + 1) % n]
                val d1 = (prev - cur).getDistance()
                val d2 = (next - cur).getDistance()
                val a = cur + (prev - cur) * (minOf(r, d1 / 2) / d1.coerceAtLeast(0.01f))
                val b = cur + (next - cur) * (minOf(r, d2 / 2) / d2.coerceAtLeast(0.01f))
                if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                quadraticTo(cur.x, cur.y, b.x, b.y)
            }
            close()
        }

        // q = [힌지 왼쪽, 힌지 오른쪽, 먼 오른쪽, 먼 왼쪽] 안의 (u: 가로, v: 힌지→먼 쪽) 지점
        fun at(q: List<androidx.compose.ui.geometry.Offset>, u: Float, v: Float): androidx.compose.ui.geometry.Offset {
            val a = q[0] + (q[1] - q[0]) * u
            val b = q[3] + (q[2] - q[3]) * u
            return a + (b - a) * v
        }

        val w = 0.5f
        val h = 0.62f
        val d = 0.06f // 판 두께
        val r = Math.toRadians(180.0 - theta)
        val up = floatArrayOf(0f, kotlin.math.sin(r).toFloat(), -kotlin.math.cos(r).toFloat()) // 위 화면이 힌지에서 뻗는 쪽
        val front = floatArrayOf(0f, kotlin.math.cos(r).toFloat(), kotlin.math.sin(r).toFloat()) // 위 화면 앞면(화면)이 보는 쪽
        val radius = 5.dp.toPx()
        val edge = androidx.compose.ui.graphics.lerp(c.ink, c.surface, 0.55f) // 옆면: 앞면과 색이 달라 두께가 보인다
        val edgeTop = androidx.compose.ui.graphics.lerp(c.ink, c.surface, 0.55f) // 위 화면 옆면: 검은 앞면과 또렷이 갈린다
        val outline = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)

        // 볼록 껍질(시계 방향 무관): 판의 앞뒤 여덟 점을 감싸는 윤곽. 이걸 옆면 색으로 칠하면 두께가 한 덩어리로 보인다
        fun hull(ps: List<androidx.compose.ui.geometry.Offset>): List<androidx.compose.ui.geometry.Offset> {
            val sorted = ps.sortedWith(compareBy({ it.x }, { it.y }))
            fun cross(o: androidx.compose.ui.geometry.Offset, a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
            val lower = mutableListOf<androidx.compose.ui.geometry.Offset>()
            for (p in sorted) {
                while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
                lower.add(p)
            }
            val upper = mutableListOf<androidx.compose.ui.geometry.Offset>()
            for (p in sorted.asReversed()) {
                while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
                upper.add(p)
            }
            return lower.dropLast(1) + upper.dropLast(1)
        }

        // 판 하나: 앞면과 두께만큼 물러난 뒷면을 감싼 몸통을 옆면 색으로 칠하고 윤곽을 그린 뒤, 앞면(화면)을 얹는다
        fun slab(face: List<FloatArray>, normal: FloatArray, side: androidx.compose.ui.graphics.Color, top: androidx.compose.ui.graphics.Color): List<androidx.compose.ui.geometry.Offset> {
            val q = face.map(::pt)
            val body = round(hull(q + face.map { pt(add(it, normal, -d)) }), radius)
            drawPath(body, side)
            drawPath(body, c.ink, style = outline)
            drawPath(round(q, radius), top)
            drawPath(round(q, radius), c.ink, style = outline)
            return q
        }

        val hingeL = floatArrayOf(-w, 0f, 0f)
        val hingeR = floatArrayOf(w, 0f, 0f)
        val topFace = listOf(hingeL, hingeR, add(hingeR, up, h), add(hingeL, up, h))
        val bottomFace = listOf(hingeL, hingeR, floatArrayOf(w, 0f, h), floatArrayOf(-w, 0f, h))
        // 위 화면(뒤쪽)을 먼저, 앞으로 나온 아래 화면을 나중에 그린다
        val top = slab(topFace, front, edgeTop, c.ink)
        val bottom = slab(bottomFace, floatArrayOf(0f, 1f, 0f), edge, c.surface)
        // 둥근 힌지: 두 판이 만나는 곳을 굵고 둥근 선으로 감싼다
        drawLine(c.ink, bottom[0], bottom[1], 3.6.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)

        // 플렉스 범위에 들어오면 패널과 영상이 서서히 켜진다(125°→110°)
        val on = ((125f - theta) / 15f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        if (on > 0f) {
            val pad = listOf(at(bottom, 0.2f, 0.25f), at(bottom, 0.8f, 0.25f), at(bottom, 0.8f, 0.8f), at(bottom, 0.2f, 0.8f))
            drawPath(round(pad, radius * 0.8f), c.line.copy(alpha = on))
            // 재생 삼각형도 위 화면 면 위에 놓아 화면과 함께 기운다(가로 u, 힌지→먼 쪽 v 좌표로 찍는다)
            val rr = 0.085f * (0.7f + 0.3f * on)
            val tri = listOf(
                at(top, 0.5f - rr * 0.7f, 0.55f + rr / h),
                at(top, 0.5f + rr, 0.55f),
                at(top, 0.5f - rr * 0.7f, 0.55f - rr / h)
            )
            drawPath(round(tri, 2.dp.toPx()), c.onInk.copy(alpha = on))
        }
    }
}

/**
 * 플렉스 모드 테스트([FlexTestActivity]가 띄우는 화면 전체). 펼쳐 있을 때는 범위와 지금 각도, "천천히 접어 보세요" 안내만 보여 주고,
 * 자동 플렉스가 플렉스 모드를 걸면 화면이 위(앱 자리)·아래(조작 패널 자리)로 갈라져 처음 쓰는 사람도 무엇이 바뀌는지 바로 본다.
 * 설정 화면이 뒤로 가 있는 동안이라 자동 플렉스가 실제로 걸린다.
 */
@Composable
internal fun FlexTestScreen(onClose: () -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val engineAngle by AutoFlex.angle.collectAsStateWithLifecycle()
    val active by AutoFlex.active.collectAsStateWithLifecycle()
    val latched by AutoFlex.latched.collectAsStateWithLifecycle()
    val localAngle = rememberHingeAngle(engineAngle == null)
    val angle = engineAngle ?: localAngle
    val min = remember { AutoFlex.min(context) }
    val max = remember { AutoFlex.max(context) }
    val horizontal = rememberHingeHorizontal(true)
    // 갈라지는 정도: 0 한 화면, 1 위아래 두 칸
    val split by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, Springs.water(reduce), label = "flexTestSplit")
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(16.dp)) {
        // 위 칸: 펼쳐 있을 때는 게이지와 안내, 플렉스 모드면 진한 '앱 화면'
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(androidx.compose.ui.graphics.lerp(c.bg, c.ink, split)),
            contentAlignment = Alignment.Center
        ) {
            if (split < 0.5f) {
                Column(Modifier.padding(Gap.CardPadding).graphicsLayer { alpha = 1 - split * 2 }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Copy.LAB_FLEX_TEST_TITLE, color = c.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Gap.Item))
                    // 세로로 접힌 채(책처럼)면 플렉스 모드가 걸리지 않으니 먼저 돌려 달라고 안내한다
                    Text(if (horizontal) Copy.LAB_FLEX_TEST_HOW else Copy.LAB_FLEX_TEST_TURN, color = if (horizontal) c.muted else c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = if (horizontal) FontWeight.Normal else FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("$min~$max°", color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Gap.Section))
                    // 시험만 한다: 조절바 없이 지금 각도만 크게
                    Text(angle?.let { "${it.roundToInt()}°" } ?: "—", color = c.ink, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(Modifier.padding(Gap.CardPadding).graphicsLayer { alpha = (split - 0.5f) * 2 }, horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
                        drawPath(
                            androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width * 0.28f, size.height * 0.18f)
                                lineTo(size.width * 0.82f, size.height / 2)
                                lineTo(size.width * 0.28f, size.height * 0.82f)
                                close()
                            },
                            c.onInk
                        )
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_ON_TITLE, color = c.onInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Text(if (latched) Copy.LAB_FLEX_ON_BODY_CLICK else Copy.LAB_FLEX_ON_BODY, color = c.onInk.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        // 아래 칸: 플렉스 모드면 물방울처럼 부풀어 '조작 패널' 자리가 된다
        if (split > 0.01f) {
            Spacer(Modifier.height(Gap.Card * split))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(split.coerceAtLeast(0.01f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surface)
                    .graphicsLayer { alpha = split },
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.padding(Gap.CardPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 뒤로·재생·앞으로(삼각형으로 직접 그린다)
                        for (i in 0..2) {
                            val tint = if (i == 1) c.onInk else c.ink
                            Box(Modifier.size(if (i == 1) 56.dp else 44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (i == 1) c.ink else c.line), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                                    fun tri(x0: Float, dir: Float) = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(x0, size.height * 0.15f)
                                        lineTo(x0 + dir * size.width * 0.45f, size.height / 2)
                                        lineTo(x0, size.height * 0.85f)
                                        close()
                                    }
                                    when (i) {
                                        0 -> {
                                            drawPath(tri(size.width * 0.5f, -1f), tint)
                                            drawPath(tri(size.width * 0.95f, -1f), tint)
                                        }
                                        1 -> drawPath(tri(size.width * 0.25f, 1.6f), tint)
                                        else -> {
                                            drawPath(tri(size.width * 0.05f, 1f), tint)
                                            drawPath(tri(size.width * 0.5f, 1f), tint)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_TEST_BOTTOM, color = c.ink, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(Copy.LAB_FLEX_TEST_UNFOLD, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Gap.Card))
        LiquidButton(Copy.LAB_FLEX_TEST_CLOSE, primary = false, modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

/**
 * 지금 접는 선이 가로로 놓였는지(화면에서 직접 중력을 읽는다, [on]일 때만). 판정은 서비스와 같다([AutoFlex.isHorizontal]).
 * 처음 한 번 창이 알려 주는 접는 선 방향과 화면 회전으로 힌지 축(기본 방향에서 세로인지 가로인지)을 확인해 저장해 둔다.
 */
@Composable
internal fun rememberHingeHorizontal(on: Boolean): Boolean {
    val context = LocalContext.current
    var horizontal by remember { mutableStateOf(true) }
    val activity = remember(context) { generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull() }
    LaunchedEffect(activity, on) {
        if (!on || activity == null) return@LaunchedEffect
        val info = androidx.window.layout.WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).first()
        val fold = info.displayFeatures.filterIsInstance<androidx.window.layout.FoldingFeature>().firstOrNull() ?: return@LaunchedEffect

        @Suppress("DEPRECATION")
        val rot = activity.windowManager.defaultDisplay.rotation
        val upright = rot == android.view.Surface.ROTATION_0 || rot == android.view.Surface.ROTATION_180
        AutoFlex.setHingeAlongY(context, (fold.orientation == androidx.window.layout.FoldingFeature.Orientation.VERTICAL) == upright)
    }
    androidx.compose.runtime.DisposableEffect(on) {
        if (!on) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                horizontal = AutoFlex.isHorizontal(e.values[0], e.values[1], e.values[2], AutoFlex.hingeAlongY(context))
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(l) }
    }
    return horizontal
}

/** 자동 플렉스 모드 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun FlexHelpSheet() {
    com.labok.goat.ui.components.SheetColumn(title = "사용 방법") {
        com.labok.goat.ui.components.HelpTopic(
            "1. 무엇을 하나요",
            "폴드8은 반쯤 접어 세워도 플렉스 모드로 바뀌지 않습니다. 자동 플렉스 모드를 켜 두면 가로로 접어 정한 각도에 들어올 때 " +
                "GOAT가 대신 플렉스 모드로 바꿔 줍니다. 위 화면에는 쓰던 앱이, 아래 화면에는 조작 화면이 나옵니다."
        ) { FlexPanelArt() }
        com.labok.goat.ui.components.HelpTopic(
            "2. 먼저 Shizuku 연결",
            "플렉스 모드로 바꾸는 명령은 셸 권한이 있어야 해서 Shizuku가 필요합니다. 점 세 개 메뉴의 '권한 설정'에서 연결 방법을 볼 수 있습니다. " +
                "Shizuku가 연결되면 이 화면의 스위치를 켤 수 있습니다."
        )
        com.labok.goat.ui.components.HelpTopic(
            "3. 켜고 쓰기",
            "'사용 중'을 켠 뒤 폰을 옆으로 돌려 접는 선이 가로로 놓이게 하고, 노트북처럼 천천히 접습니다. 각도가 범위에 들어오면 진동과 함께 " +
                "플렉스 모드가 켜지고, 펴거나 닫아 범위를 벗어나면 원래대로 돌아옵니다. 책처럼 세로로 접을 때는 켜지지 않습니다."
        )
        com.labok.goat.ui.components.HelpTopic(
            "4. 각도 범위 맞추기",
            "폴드8 힌지는 원하는 각도에서 저절로 멈추지 않아 손이나 거치대로 각도를 잡습니다. 게이지의 두 손잡이를 끌어 내 습관에 맞는 범위를 정하고, " +
                "손잡이를 꾹 누르고 있으면 1도씩 세밀하게 맞출 수 있습니다. [플렉스 모드 테스트하기]로 바로 시험해 보세요."
        )
        com.labok.goat.ui.components.HelpTopic(
            "5. 까딱으로 플렉스 모드",
            "켜 두면 완전히 편 채 130~160°까지 살짝 접었다 1초 안에 다시 펴는 것(까딱)으로 플렉스 모드를 켜고 끕니다. " +
                "켜 둔 동안은 펼친 채로도 플렉스 모드처럼 씁니다. 닫거나 화면을 끄면 꺼집니다."
        )
        com.labok.goat.ui.components.HelpTopic(
            "6. 앱마다 다른 점",
            "'플렉스 화면 지원 앱'은 유튜브·카메라처럼 접으면 스스로 위아래로 나뉘는 앱입니다(설치 파일을 읽어 추정). " +
                "그 밖의 앱은 다른 폴드처럼 아래 화면에 터치패널이 뜨지 않을 수 있습니다. 왜 그런지 '터치패널 진단'으로 확인할 수 있습니다."
        )
        com.labok.goat.ui.components.HelpTopic(
            "7. 끄기·되돌리기",
            "'사용 중'을 끄면 GOAT가 켠 패널 설정을 모두 풀고 접힘 상태도 원래대로 돌립니다. 재부팅해도 원래대로 돌아옵니다."
        )
    }
}

/**
 * 화면에서 직접 읽는 힌지 각도([on]일 때만): 기본 센서(0·90·180)와 Shizuku 세밀한 각도([com.labok.goat.data.lab.FineHinge]).
 * 세밀한 값이 오면 그걸 쓴다.
 */
@Composable
internal fun rememberHingeAngle(on: Boolean): Float? {
    val context = LocalContext.current
    var coarse by remember { mutableStateOf<Float?>(null) }
    var fineAngle by remember { mutableStateOf<Float?>(null) }
    var fineAt by remember { mutableStateOf(0L) }
    // 세밀한 값이 끊겼는지 다시 보려고 0.5초마다 한 번 깨운다
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(on) {
        while (on) {
            kotlinx.coroutines.delay(500)
            tick++
        }
    }
    androidx.compose.runtime.DisposableEffect(on) {
        if (!on) {
            coarse = null
            fineAngle = null
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val hinge = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE)
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                coarse = e.values[0]
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        if (hinge != null) sm.registerListener(l, hinge, android.hardware.SensorManager.SENSOR_DELAY_UI)
        val fine = com.labok.goat.data.lab.FineHinge { v ->
            fineAngle = v
            fineAt = android.os.SystemClock.elapsedRealtime()
        }
        fine.start()
        onDispose {
            sm?.unregisterListener(l)
            fine.stop()
        }
    }
    // 빨리 펴거나 닫으면 세밀한 로그가 중간 값에서 멈출 수 있다. 1초 넘게 끊겼고 기본 센서가 끝값(0°·180°)이면 기본 센서를 믿는다
    tick
    val c = coarse
    val stale = android.os.SystemClock.elapsedRealtime() - fineAt >= 1000
    return if (c != null && stale && (c <= 0f || c >= 180f)) c else fineAngle ?: c
}

/** 패널 앱 한 줄: 앱별 화면 배율 설정 목록과 같은 모양(아이콘·이름·마지막 사용)에 오른쪽 스위치. 켜고 끌 때 테두리가 물들고 줄이 살짝 출렁인다. */
@Composable
private fun FlexAppRow(app: AppEntry, on: Boolean, modifier: Modifier = Modifier, aware: Boolean = false, onChange: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val shape = RoundedCornerShape(20.dp)
    val border by androidx.compose.animation.animateColorAsState(if (on) c.ink.copy(alpha = 0.5f) else c.line, Springs.calm(reduce), label = "flexRowBorder")
    val squash = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(on) {
        if (reduce || squash.value != 1f) return@LaunchedEffect
        squash.snapTo(0.97f)
        squash.animateTo(1f, Springs.pop(reduce))
    }
    Row(
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(Copy.LAB_FLEX_AWARE_TAG.takeIf { aware }, app.lastUsed.takeIf { it > 0 }?.let { ago(it) }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        LiquidSwitch(on, onChange, label = app.label)
    }
}

/** 목록 줄이 자리를 옮길 때(물처럼 출렁이며)·나타나고 사라질 때 쓰는 스프링. UI 유연성·속도 설정을 따른다. */
private val liquidPlace: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>
    get() = androidx.compose.animation.core.spring(UiMotion.ratio(0.62f), UiMotion.stiffness(260f, 0.62f))
private val liquidFade: androidx.compose.animation.core.FiniteAnimationSpec<Float>
    get() = androidx.compose.animation.core.spring(1f, UiMotion.stiffness(400f, 1f))

/**
 * 힌지 각도 게이지이자 범위 조절: 0°(닫힘)~180°(펼침) 반원 위에 플렉스 범위([min]~[max])를 잉크 띠로 칠하고, 띠 양 끝에 값이 적힌
 * 물방울 손잡이를 둔다. [editable]이면 손잡이를 반원을 따라 끌어 범위를 바꾼다([onRange], 손을 떼면 [onRangeFinished]).
 * 기본 조절바처럼 손잡이를 붙든 채 가만히 있으면 세밀 조절이 펼쳐져 반원 전체가 앞뒤 [GAUGE_FINE]°가 된다.
 * 지금 각도는 작은 물방울 바늘이 스프링으로 따라가며, goo로 띠·손잡이와 엉겨 붙는다. 끄는 동안에는 자동 플렉스를 잠시 멈춘다.
 */
@Composable
private fun FlexAngleGauge(angle: Float?, min: Int, max: Int, active: Boolean, editable: Boolean = true, onRange: (Int, Int) -> Unit = { _, _ -> }, onRangeFinished: () -> Unit = {}) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val haptics = com.labok.goat.ui.liquid.rememberLiquidHaptics()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val lo by androidx.compose.runtime.rememberUpdatedState(min)
    val hi by androidx.compose.runtime.rememberUpdatedState(max)
    val change by androidx.compose.runtime.rememberUpdatedState(onRange)
    val finish by androidx.compose.runtime.rememberUpdatedState(onRangeFinished)
    // 센서 값은 쉬지 않고 들어오므로 스프링을 매번 새로 걸지 않고(걸면 시작하기도 전에 취소돼 바늘이 멈춰 있었다),
    // 프레임마다 목표 쪽으로 물처럼 따라간다(감쇠 있는 용수철: 살짝 지나쳤다 돌아온다)
    val target by androidx.compose.runtime.rememberUpdatedState(angle)
    var pos by remember { mutableStateOf(angle ?: 180f) }
    LaunchedEffect(reduce) {
        var vel = 0f
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val t = target ?: continue
            if (reduce) {
                pos = t
                continue
            }
            val k = 120f * UiMotion.speed * UiMotion.speed
            val damp = 2f * kotlin.math.sqrt(k) * UiMotion.ratio(0.55f)
            vel += (k * (t - pos) - damp * vel) * dt
            pos += vel * dt
        }
    }
    // 잡은 손잡이: 0 없음, 1 범위 끝(min), 2 범위 시작(max). 잡으면 손잡이가 부풀어 오른다
    var held by remember { mutableStateOf(0) }
    // 세밀 조절: 펼쳐진 동안의 가운데 값(반원 꼭대기)
    var fineBase by remember { mutableStateOf<Int?>(null) }
    val fineK by androidx.compose.animation.core.animateFloatAsState(if (fineBase != null) 1f else 0f, Springs.water(reduce), label = "gaugeFine")
    val grow = listOf(
        androidx.compose.animation.core.animateFloatAsState(if (held == 1) 1f else 0f, Springs.water(reduce), label = "gaugeLo"),
        androidx.compose.animation.core.animateFloatAsState(if (held == 2) 1f else 0f, Springs.water(reduce), label = "gaugeHi")
    )
    val glow by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, Springs.pop(reduce), label = "flexGlow")
    val handleStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.labok.goat.ui.liquid.GalaxyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    fun geo(w: Float, h: Float, density: Float): Pair<androidx.compose.ui.geometry.Offset, Float> {
        // 잡으면 값 방울이 반원 바깥으로 떠오르므로(GAUGE_LIFT) 위·양옆에 그만큼 자리를 둔다
        val r = minOf(w / 2 - 58 * density, h - 72 * density)
        return androidx.compose.ui.geometry.Offset(w / 2, h - 12 * density) to r
    }

    // 각도 → 화면 위치: 180°(펼침)는 오른쪽 끝, 0°(닫힘)는 왼쪽 끝
    fun at(center: androidx.compose.ui.geometry.Offset, r: Float, deg: Float) = center + androidx.compose.ui.geometry.Offset(-kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat() * r, -kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat() * r)

    // 화면 위치 → 각도(반원 아래쪽은 가까운 끝으로)
    fun degAt(center: androidx.compose.ui.geometry.Offset, p: androidx.compose.ui.geometry.Offset): Float {
        val dx = p.x - center.x
        val dy = center.y - p.y
        if (dy < 0) return if (dx < 0) 0f else 180f
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), -dx.toDouble())).toFloat().coerceIn(0f, 180f)
    }

    // 세밀 조절 중 값 [v]가 놓이는 반원 위 각도: 가운데 값이 꼭대기(90°), 앞뒤 GAUGE_FINE°가 양 끝
    fun fineDeg(base: Int, v: Int) = 90f + (v - base) * 90f / GAUGE_FINE

    // 손잡이가 그려지는 반원 위 각도: 세밀 조절 중인 손잡이는 확대 눈금 위 자리로 물 흐르듯 옮겨 간다
    fun shownDeg(i: Int, v: Int): Float {
        val base = fineBase ?: return v.toFloat()
        return if (held == i + 1) v + (fineDeg(base, v) - v) * fineK else v.toFloat()
    }

    // 물방울 손잡이: 손가락(값)을 용수철로 늦게 따라가고, 빨리 움직일수록 반원을 따라 길게 늘어났다가 멈추면 출렁이며 돌아온다
    val springs = remember { listOf(androidx.compose.animation.core.Animatable(min.toFloat()), androidx.compose.animation.core.Animatable(max.toFloat())) }
    listOf(min, max).forEachIndexed { i, v ->
        val goal = shownDeg(i, v)
        LaunchedEffect(goal, reduce) {
            if (reduce) springs[i].snapTo(goal) else springs[i].animateTo(goal, androidx.compose.animation.core.spring(UiMotion.ratio(0.5f), UiMotion.stiffness(700f, 0.5f)))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .pointerInput(editable) {
                if (!editable) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val (center, r) = geo(size.width.toFloat(), size.height.toFloat(), density)
                    val grab = 30.dp.toPx()
                    val dl = (down.position - at(center, r, lo.toFloat())).getDistance()
                    val dh = (down.position - at(center, r, hi.toFloat())).getDistance()
                    // 손잡이 근처를 눌렀을 때만 잡는다(그 밖은 화면 스크롤로 넘긴다)
                    val which = when {
                        dl > grab && dh > grab -> return@awaitEachGesture
                        dl <= dh -> 1
                        else -> 2
                    }
                    down.consume()
                    held = which
                    haptics.press()
                    AutoFlex.setPaused("drag", true)
                    val slop = 5.dp.toPx()
                    var still = down.position
                    var stillSince = down.uptimeMillis
                    var anchor = 0f
                    fun openFine(p: androidx.compose.ui.geometry.Offset) {
                        if (fineBase != null) return
                        fineBase = if (which == 1) lo else hi
                        anchor = degAt(center, p)
                        haptics.press()
                    }
                    fun set(v: Int) {
                        val (nl, nh) = if (which == 1) {
                            v.coerceIn(AutoFlex.ANGLE_LOW, hi - 10) to hi
                        } else {
                            lo to v.coerceIn(lo + 10, AutoFlex.ANGLE_HIGH)
                        }
                        if (nl != lo || nh != hi) {
                            change(nl, nh)
                            haptics.tick()
                        }
                    }
                    try {
                        while (true) {
                            // 가만히 붙들고 있으면 이벤트가 오지 않으므로 시간으로도 확인한다
                            val ev = withTimeoutOrNull(GAUGE_FINE_HOLD_MS) { awaitPointerEvent() }
                            if (ev == null) {
                                openFine(still)
                                continue
                            }
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            if ((ch.position - still).getDistance() > slop) {
                                still = ch.position
                                stillSince = ch.uptimeMillis
                            } else if (ch.uptimeMillis - stillSince >= GAUGE_FINE_HOLD_MS) {
                                openFine(ch.position)
                            }
                            val base = fineBase
                            if (base == null) {
                                set(degAt(center, ch.position).roundToInt())
                            } else {
                                // 세밀 조절: 반원 전체가 앞뒤 GAUGE_FINE°라 같은 손 움직임에 훨씬 조금씩 바뀐다
                                val d = (degAt(center, ch.position) - anchor) * (2f * GAUGE_FINE) / 180f
                                set((base + d.roundToInt()).coerceIn(base - GAUGE_FINE, base + GAUGE_FINE))
                            }
                        }
                    } finally {
                        held = 0
                        fineBase = null
                        AutoFlex.setPaused("drag", false)
                        finish()
                    }
                }
            }
    ) {
        // 바탕 반원(선)
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val (center, r) = geo(size.width, size.height, density)
            drawArc(c.line, 180f, 180f, false, androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        // 범위 띠·손잡이·바늘(goo로 이어 붙는다). 세밀 조절 중에는 띠와 다른 손잡이가 잦아든다
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().goo(c.ink, 5.dp)) {
            val (center, r) = geo(size.width, size.height, density)
            val band = (8 + 4 * glow).dp.toPx() * (1 - fineK)
            if (band > 0.5f) {
                drawArc(c.ink, 180f + springs[0].value, springs[1].value - springs[0].value, false, androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(band, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            listOf(min, max).forEachIndexed { i, v ->
                val shrink = if (fineBase != null && held != i + 1) 1 - fineK else 1f
                if (shrink < 0.05f) return@forEachIndexed
                val d = springs[i].value
                val p = at(center, r, d)
                val g = grow[i].value
                val tw = measurer.measure("$v°", handleStyle).size.width
                // 늘어남: 각속도(°/초)에 비례해 반원 방향으로 길어지고 두께는 조금 얇아진다
                val stretch = (kotlin.math.abs(springs[i].velocity) / 400f).coerceAtMost(0.6f)
                // 기본 조절바와 같다: 잡으면 손잡이가 납작하고 넓어지고(30→24dp), 값 방울이 반원 바깥으로 떠올라 큰 글자로 값을 보여 준다
                val w = (tw + (22 + 8 * g).dp.toPx()) * shrink * (1 + stretch)
                val h = (28 - 4 * g).dp.toPx() * shrink * (1 - stretch * 0.3f)
                // 손잡이는 반원을 따라 눕는다(꼭대기 90°에서 수평, 양 끝으로 갈수록 세워진다)
                rotate(d - 90f, p) {
                    drawRoundRect(c.ink, androidx.compose.ui.geometry.Offset(p.x - w / 2, p.y - h / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                }
                if (g > 0.01f) {
                    val bs = (10 + 36 * g).dp.toPx()
                    val lift = measurer.measure("$v°", handleStyle.copy(fontSize = 17.sp)).size.width
                    val bw = maxOf(bs, (lift + 20.dp.toPx()) * (bs / 46.dp.toPx()))
                    val q = at(center, r + g * GAUGE_LIFT.dp.toPx(), d)
                    rotate(d - 90f, q) {
                        drawRoundRect(c.ink, androidx.compose.ui.geometry.Offset(q.x - bw / 2, q.y - bs / 2), androidx.compose.ui.geometry.Size(bw, bs), androidx.compose.ui.geometry.CornerRadius(bs / 2))
                    }
                }
            }
            // 세밀 조절 패널: 기본 조절바처럼 손잡이 자리에서 반원을 따라 양옆으로 퍼져 반원 전체를 덮는 잉크 띠(goo라 손잡이와 이어진다)
            if (fineK > 0.01f) {
                val fromDeg = listOf(min, max).getOrNull(held - 1)?.toFloat() ?: 90f
                val mid = fromDeg + (90f - fromDeg) * fineK
                val sweep = 12f + (180f - 12f) * fineK
                drawArc(c.ink, 180f + mid - sweep / 2, sweep, false, androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(22.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            if (angle != null && fineBase == null) drawCircle(c.ink, (8 + 3 * glow).dp.toPx(), at(center, r, pos))
        }
        // 세밀 조절 눈금(패널 위, 반전색): 앞뒤 GAUGE_FINE칸, 5°마다 길게. 그 위에 손잡이 값 글자(반전)
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val (center, r) = geo(size.width, size.height, density)
            val base = fineBase
            if (base != null && fineK > 0.01f) {
                for (i in -GAUGE_FINE..GAUGE_FINE) {
                    val v = base + i
                    val long = v % 5 == 0
                    val len = (if (long) 10 else 5).dp.toPx() * fineK
                    val d = fineDeg(base, v)
                    drawLine(c.onInk.copy(alpha = 0.75f * fineK), at(center, r - len / 2, d), at(center, r + len / 2, d), 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            listOf(min, max).forEachIndexed { i, v ->
                if (fineBase != null && held != i + 1 && fineK > 0.5f) return@forEachIndexed
                val d = springs[i].value
                // 글자는 떠오른 값 방울을 따라 올라가며 커진다
                val g = grow[i].value
                val p = at(center, r + g * GAUGE_LIFT.dp.toPx(), d)
                val layout = measurer.measure("$v°", handleStyle.copy(color = c.onInk, fontSize = (12 + 5 * g).sp))
                rotate(d - 90f, p) {
                    drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                }
            }
        }
        // 가운데 큰 각도 숫자와 지금 상태
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(angle?.let { "${pos.roundToInt()}°" } ?: "—", color = c.ink, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
            Text(if (active) "플렉스 모드" else "보통", color = if (active) c.ink else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** 잡았을 때 값 방울이 반원 바깥으로 떠오르는 거리(dp, 기본 조절바와 같다). */
private const val GAUGE_LIFT = 26

/** 게이지 세밀 조절이 보여 주는 앞뒤 칸(°). */
private const val GAUGE_FINE = 10

/** 이 시간(ms) 동안 거의 움직이지 않고 붙들고 있으면 게이지 세밀 조절을 연다(기본 조절바와 같다). */
private const val GAUGE_FINE_HOLD_MS = 750L

/** 숨은 기능 찾기: 앱별 글자 크기와 관련 있어 보이는 시스템 함수·설정 키를 찾아 파일로 보낸다(읽기만 한다). */
@Composable
internal fun LabProbeScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    LabPage(contentPadding) {
        Text(Copy.LAB_PROBE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_PROBE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(if (running) Copy.LAB_PROBE_RUNNING else Copy.LAB_PROBE_RUN, enabled = !running, modifier = Modifier.fillMaxWidth(), onClick = {
            running = true
            scope.launch {
                val text = withContext(Dispatchers.IO) { ApiProbe.findFontScale(context) }
                val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("lab-font"), text) }
                summary = text.lineSequence().first()
                running = false
                uri?.let { share(context, it) }
            }
        })
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(Gap.Item))
            Text(summary, color = c.ink, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, Copy.LAB_PROBE_SHARE))
}
