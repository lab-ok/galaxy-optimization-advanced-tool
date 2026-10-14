package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.labok.goat.ui.components.InfoRow
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidSlider
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
import kotlinx.coroutines.Dispatchers
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
    val flex by rememberFlexInfo()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val angle by AutoFlex.angle.collectAsStateWithLifecycle()
    val active by AutoFlex.active.collectAsStateWithLifecycle()
    val supported = remember { AutoFlex.supported() && AutoFlex.hasHingeSensor(context) }
    var enabled by remember { mutableStateOf(AutoFlex.enabled(context)) }
    var min by remember { mutableIntStateOf(AutoFlex.min(context)) }
    var max by remember { mutableIntStateOf(AutoFlex.max(context)) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    // 패널을 끈 앱(예외). 나머지는 모두 켜져 있다
    var off by remember { mutableStateOf(AutoFlex.excluded(context)) }
    var calibrating by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    var gesturesOpen by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf(AutoFlex.wheel.read(context)) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LaunchedEffect(supported) {
        if (supported) apps = withContext(Dispatchers.IO) { loadApps(context) }
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

    // 지금 각도·상태(플렉스일 때 위 칸)
    val statusCard: @Composable () -> Unit = {
        GlassCard {
            Text(Copy.LAB_FLEX_NOW_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(Gap.Item))
            // 물방울 바늘이 힌지 각도를 따라 반원 위를 흐르고, 플렉스 범위 안에 들어오면 범위 띠와 엉겨 붙는다
            FlexAngleGauge(angle, min, max, active)
            Spacer(Modifier.height(Gap.Item))
            InfoRow(Copy.LAB_FLEX_ANGLE, angle?.let { "${it.toInt()}°" } ?: if (enabled) "화면을 켜면 표시" else "자동 플렉스를 켜면 표시")
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
                }
            }
        }
        if (!flex.tabletop) item(key = "now") { Box(Modifier.widthIn(max = ContentMaxWidth)) { statusCard() } }
        item(key = "range") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_FLEX_RANGE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FLEX_RANGE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.LAB_FLEX_RANGE_HINGE, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.LAB_FLEX_RANGE_HINGE2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FLEX_MIN, color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = min,
                        range = AutoFlex.ANGLE_LOW..AutoFlex.ANGLE_HIGH,
                        onValueChange = { min = it.coerceAtMost(max - 10) },
                        onValueChangeFinished = { AutoFlex.setRange(context, min, max) },
                        valueText = { "$it°" },
                        description = Copy.LAB_FLEX_MIN
                    )
                    Spacer(Modifier.height(Gap.Section))
                    Text(Copy.LAB_FLEX_MAX, color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = max,
                        range = AutoFlex.ANGLE_LOW..AutoFlex.ANGLE_HIGH,
                        onValueChange = { max = it.coerceAtLeast(min + 10) },
                        onValueChangeFinished = { AutoFlex.setRange(context, min, max) },
                        valueText = { "$it°" },
                        description = Copy.LAB_FLEX_MAX
                    )
                    Spacer(Modifier.height(Gap.Item))
                    // 접으면서 조절하기: 실시간 각도를 보며 시작·끝 지점을 그 자리에서 찍는다
                    LiquidButton(if (calibrating) Copy.LAB_FLEX_CALIBRATE_DONE else Copy.LAB_FLEX_CALIBRATE, modifier = Modifier.fillMaxWidth(), onClick = { calibrating = !calibrating })
                    androidx.compose.animation.AnimatedVisibility(
                        calibrating,
                        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(UiMotion.ratio(0.75f), UiMotion.stiffness(300f, 0.75f))) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(UiMotion.ratio(0.9f), UiMotion.stiffness(420f, 0.9f))) + androidx.compose.animation.fadeOut()
                    ) {
                        FlexCalibrator(
                            onStart = { a ->
                                max = a.coerceAtLeast(min + 10).coerceAtMost(AutoFlex.ANGLE_HIGH)
                                AutoFlex.setRange(context, min, max)
                            },
                            onEnd = { a ->
                                min = a.coerceAtMost(max - 10).coerceAtLeast(AutoFlex.ANGLE_LOW)
                                AutoFlex.setRange(context, min, max)
                            },
                            min = min,
                            max = max
                        )
                    }
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.LAB_FLEX_RANGE_RESET, primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                        min = AutoFlex.DEFAULT_MIN
                        max = AutoFlex.DEFAULT_MAX
                        AutoFlex.setRange(context, min, max)
                    })
                }
            }
        }
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
            val rest = all.filter { it.pkg !in off }
            if (apps == null) item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
            // 같은 키를 두 묶음에서 그대로 써서, 켜고 끌 때 줄이 사라졌다 생기지 않고 새 자리로 흘러간다
            val anim = Modifier
            if (excluded.isNotEmpty()) {
                item(key = "h-off") { Section("패널을 끈 앱 ${excluded.size}", anim.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) }
                items(excluded, key = { it.pkg }) { app ->
                    FlexAppRow(app, pending[app.pkg] ?: false, Modifier.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) { toggle(app, it) }
                }
            }
            item(key = "h-rest") { Section(Copy.LAB_FLEX_APPS_REST, anim.animateItem(fadeInSpec = liquidFade, placementSpec = liquidPlace, fadeOutSpec = liquidFade)) }
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

    if (flex.tabletop) {
        // 플렉스 모드: 접는 선 위는 각도·상태, 아래는 스크롤되는 조작
        val hingeDp = with(androidx.compose.ui.platform.LocalDensity.current) { flex.hingeTop.toDp() }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(hingeDp).padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = ContentMaxWidth)) { statusCard() }
            }
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = Gap.Card, bottom = contentPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(Gap.Card),
                modifier = Modifier.fillMaxWidth().weight(1f),
                content = list
            )
        }
    } else {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = featurePadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Gap.Card),
            modifier = Modifier.fillMaxWidth(),
            content = list
        )
    }
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

/** 플렉스 모드 그림: 반쯤 접은 폴드의 위 화면에는 영상, 아래 화면에는 조작 패널. 힌지가 천천히 접혔다 펴진다. */
@Composable
private fun FlexPanelArt() {
    val c = LocalLiquid.current
    val t = com.labok.goat.ui.art.artLoop(3200)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val k = (1 - kotlin.math.cos(t * 2 * Math.PI).toFloat()) / 2 // 0..1 접힘 정도
        val w = size.width * 0.5f
        val h = size.height * 0.36f
        val cx = size.width / 2
        val hingeY = size.height * 0.62f
        // 아래 화면(바닥): 원근감 있게 납작한 사각형
        val bottom = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w / 2, hingeY)
            lineTo(cx + w / 2, hingeY)
            lineTo(cx + w / 2 + w * 0.12f, hingeY + h * 0.55f)
            lineTo(cx - w / 2 - w * 0.12f, hingeY + h * 0.55f)
            close()
        }
        drawPath(bottom, c.surface)
        drawPath(bottom, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        // 조작 패널: 재생 막대와 버튼 세 개
        drawLine(c.ink, androidx.compose.ui.geometry.Offset(cx - w * 0.35f, hingeY + h * 0.18f), androidx.compose.ui.geometry.Offset(cx + w * 0.35f, hingeY + h * 0.18f), 2.dp.toPx())
        for (i in -1..1) drawCircle(c.ink, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(cx + i * w * 0.18f, hingeY + h * 0.38f))
        // 위 화면: 접힌 정도만큼 세워진다(0이면 거의 펼침, 1이면 직각)
        val rise = h * (0.55f + 0.45f * k)
        val lean = w * 0.08f * (1 - k)
        val top = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w / 2, hingeY)
            lineTo(cx + w / 2, hingeY)
            lineTo(cx + w / 2 - lean, hingeY - rise)
            lineTo(cx - w / 2 + lean, hingeY - rise)
            close()
        }
        drawPath(top, c.ink)
        // 위 화면 속 영상(재생 삼각형)
        val pc = androidx.compose.ui.geometry.Offset(cx, hingeY - rise / 2)
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(pc.x - 6.dp.toPx(), pc.y - 8.dp.toPx())
            lineTo(pc.x + 9.dp.toPx(), pc.y)
            lineTo(pc.x - 6.dp.toPx(), pc.y + 8.dp.toPx())
            close()
        }
        drawPath(tri, c.onInk)
    }
}

/**
 * 접으면서 조절하기: 힌지 각도를 직접 읽어 크게 보여 주고, 지금 각도를 플렉스 시작 지점(펼친 쪽에서 접어 들어올 때 켜지는 각도)과
 * 끝 지점(더 접었을 때 꺼지는 각도)으로 찍는다. 폴드8은 힌지가 중간에 멈추지 않아 손으로 각도를 잡은 채 누른다.
 */
@Composable
private fun FlexCalibrator(onStart: (Int) -> Unit, onEnd: (Int) -> Unit, min: Int, max: Int) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var angle by remember { mutableStateOf<Float?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val hinge = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE)
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                angle = e.values[0]
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        if (hinge != null) sm.registerListener(l, hinge, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    val now = angle?.toInt()
    val inside = now != null && now in min..max
    Column(Modifier.fillMaxWidth().padding(top = Gap.Item), horizontalAlignment = Alignment.CenterHorizontally) {
        FlexAngleGauge(angle, min, max, inside)
        Spacer(Modifier.height(Gap.Item))
        Text(if (inside) Copy.LAB_FLEX_CAL_INSIDE else Copy.LAB_FLEX_CAL_OUTSIDE, color = if (inside) c.ink else c.muted, style = MaterialTheme.typography.labelMedium)
        Text(Copy.LAB_FLEX_CAL_HOW, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton(Copy.LAB_FLEX_CAL_START, modifier = Modifier.weight(1f), enabled = now != null, onClick = { now?.let(onStart) })
            LiquidButton(Copy.LAB_FLEX_CAL_END, modifier = Modifier.weight(1f), enabled = now != null, onClick = { now?.let(onEnd) })
        }
    }
}

/** 패널 앱 한 줄: 앱별 화면 배율 설정 목록과 같은 모양(아이콘·이름·마지막 사용)에 오른쪽 스위치. 켜고 끌 때 테두리가 물들고 줄이 살짝 출렁인다. */
@Composable
private fun FlexAppRow(app: AppEntry, on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
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
            if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
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
 * 힌지 각도 게이지: 0°(닫힘)~180°(펼침) 반원 위에 플렉스 범위([min]~[max])를 잉크 띠로 칠하고, 지금 각도에 물방울 바늘을 둔다.
 * 바늘은 스프링으로 따라가며 goo로 띠와 엉겨 붙어, 범위 안이면 띠에 녹아든 것처럼, 밖이면 떨어진 방울처럼 보인다.
 */
@Composable
private fun FlexAngleGauge(angle: Float?, min: Int, max: Int, active: Boolean) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
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
    val a = object {
        val value get() = pos
    }
    val glow by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, Springs.pop(reduce), label = "flexGlow")
    Box(Modifier.fillMaxWidth().height(110.dp)) {
        // 바탕 반원(선)
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val r = minOf(size.width / 2, size.height) - 14.dp.toPx()
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 6.dp.toPx())
            drawArc(c.line, 180f, 180f, false, androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        // 범위 띠와 물방울 바늘(goo로 이어 붙는다)
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().goo(c.ink, 5.dp)) {
            val r = minOf(size.width / 2, size.height) - 14.dp.toPx()
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 6.dp.toPx())

            // 각도 → 화면 위치: 180°(펼침)는 오른쪽 끝, 0°(닫힘)는 왼쪽 끝
            fun at(deg: Float) = center + androidx.compose.ui.geometry.Offset(-kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat() * r, -kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat() * r)
            val start = 180f + min
            drawArc(c.ink, start, (max - min).toFloat(), false, androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke((8 + 4 * glow).dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (angle != null) drawCircle(c.ink, (9 + 3 * glow).dp.toPx(), at(a.value))
        }
        // 가운데 큰 각도 숫자
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(angle?.let { "${a.value.toInt()}°" } ?: "—", color = c.ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (active) "플렉스 모드" else "보통", color = if (active) c.ink else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

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
