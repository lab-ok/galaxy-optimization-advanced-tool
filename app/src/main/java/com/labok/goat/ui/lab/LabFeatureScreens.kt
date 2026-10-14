package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.ApiProbe
import com.labok.goat.data.lab.FlexPanel
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.LabSetting
import com.labok.goat.data.lab.ScreenMode
import com.labok.goat.data.log.PublicLogFile
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
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
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
 * 플렉스 모드 패널 시험(폴드8처럼 설정 항목이 숨겨진 기기에서 직접 시험).
 * - 접힘 상태: 시스템 상태(Shizuku로 0.7초마다)와 앱이 받는 상태(Jetpack)를 함께 보여 주고, 반쯤 접힘을 강제·해제한다.
 * - 이 화면 자체가 플렉스 모드를 지원한다: 앱이 '반쯤 접힘'을 받으면 접는 선 위는 상태, 아래는 조작으로 나뉜다.
 * - 패널 스위치, 패널을 띄울 앱(앱별 화면 배율 설정과 같은 목록: 검색·켠 앱·최근 사용 순·아이콘), 진단 파일.
 */
@Composable
internal fun LabFlexScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val w = rememberLabWriter()
    val flex by rememberFlexInfo()
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var on by remember { mutableStateOf(emptySet<String>()) }
    var posture by remember { mutableStateOf<String?>(null) }
    var forced by remember { mutableStateOf(FlexPanel.forced) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        // 시스템이 아는 등록 상태를 읽고, 물어볼 수 없으면 GOAT가 기억한 목록을 쓴다
        on = withContext(Dispatchers.IO) {
            val mine = FlexPanel.registered(context)
            apps.orEmpty().mapNotNull { a -> a.pkg.takeIf { FlexPanel.isRegistered(it) ?: (it in mine) } }.toSet()
        }
    }
    // 화면이 보이는 동안 시스템 접힘 상태를 따라 읽는다
    LaunchedEffect(Unit) {
        while (true) {
            posture = withContext(Dispatchers.IO) { FlexPanel.postureNow() }
            kotlinx.coroutines.delay(700)
        }
    }

    fun toggle(app: AppEntry, want: Boolean) = scope.launch {
        val err = withContext(Dispatchers.IO) { FlexPanel.setApp(context, app.pkg, want) }
        if (err == null) on = if (want) on + app.pkg else on - app.pkg else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
    }

    // 접힘 상태 카드(플렉스일 때 위 칸)
    val postureCard: @Composable () -> Unit = {
        GlassCard {
            Text(Copy.LAB_FLEX_POSTURE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.LAB_FLEX_POSTURE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            InfoRow(Copy.LAB_FLEX_POSTURE_SYSTEM, posture?.let { postureLabel(it) } ?: "Shizuku 연결 필요", emphasize = posture == "HALF_OPENED")
            InfoRow(Copy.LAB_FLEX_POSTURE_APP, flex.state + if (flex.tabletop) " · 위아래로 나눔" else "", emphasize = flex.tabletop)
            Spacer(Modifier.height(Gap.Item))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton(Copy.LAB_FLEX_FORCE, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { FlexPanel.forceHalfOpened(context) }
                        forced = FlexPanel.forced
                        if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                })
                LiquidButton(Copy.LAB_FLEX_RESET, modifier = Modifier.weight(1f), primary = false, enabled = forced, onClick = {
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { FlexPanel.resetPosture(context) }
                        forced = FlexPanel.forced
                        if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
    }

    val list: LazyListScope.() -> Unit = {
        if (!flex.tabletop) item(key = "posture") { Box(Modifier.widthIn(max = ContentMaxWidth)) { postureCard() } }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    val raw = remember(w.version) { FlexPanel.setting.read(context) }
                    Text(Copy.LAB_FLEX_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FLEX_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(Copy.LAB_FLEX_SWITCH, "${FlexPanel.setting.key} = ${raw ?: "(없음)"}", raw == "1", true) { v ->
                        w.write(FlexPanel.setting, if (v) "1" else "0")
                    }
                    Spacer(Modifier.height(Gap.Section))
                    val changed = remember(w.version) { FlexPanel.setting.changed(context) }
                    LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(FlexPanel.setting) })
                }
            }
        }
        item(key = "diagnose") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_FLEX_DIAG_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FLEX_DIAG_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(if (running) Copy.LAB_PROBE_RUNNING else Copy.LAB_PROBE_RUN, enabled = !running, modifier = Modifier.fillMaxWidth(), onClick = {
                        running = true
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { FlexPanel.diagnose(context) }
                            val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("lab-flex"), text) }
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
        }
        // 패널을 띄울 앱: 앱별 화면 배율 설정과 같은 목록
        item(key = "search") {
            Column(Modifier.widthIn(max = ContentMaxWidth)) {
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
        val q = query.trim()
        val all = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
        val chosen = all.filter { it.pkg in on }
        val rest = all.filter { it.pkg !in on }
        if (apps == null) item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
        if (chosen.isNotEmpty()) {
            item(key = "h-on") { Section("패널을 켠 앱 ${chosen.size}") }
            items(chosen, key = { "on-" + it.pkg }) { app -> FlexAppRow(app, true) { toggle(app, it) } }
        }
        item(key = "h-rest") { Section(Copy.LAB_FLEX_APPS_REST) }
        items(rest, key = { it.pkg }) { app -> FlexAppRow(app, false) { toggle(app, it) } }
    }

    if (flex.tabletop) {
        // 플렉스 모드: 접는 선 위는 접힘 상태, 아래는 스크롤되는 조작
        val hingeDp = with(androidx.compose.ui.platform.LocalDensity.current) { flex.hingeTop.toDp() }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(hingeDp).padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = ContentMaxWidth)) { postureCard() }
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

private fun postureLabel(name: String) = when (name) {
    "HALF_OPENED" -> "반쯤 접힘"
    "OPENED" -> "펼침"
    "CLOSED" -> "접힘"
    "TENT" -> "텐트"
    else -> name
} + " ($name)"

/** 패널 앱 한 줄: 앱별 화면 배율 설정 목록과 같은 모양(아이콘·이름·마지막 사용)에 오른쪽 스위치. */
@Composable
private fun FlexAppRow(app: AppEntry, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (on) c.ink.copy(alpha = 0.5f) else c.line, shape)
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
