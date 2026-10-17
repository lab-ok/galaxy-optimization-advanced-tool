package com.labok.goat.ui.lab

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.LabSetting
import com.labok.goat.data.lab.PrivacyDot
import com.labok.goat.data.lab.ScreenMode
import com.labok.goat.data.lab.StatusBarHider
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 실험실 기능(염소를 열 번 눌러 연다). 사이드바 '실험실' 묶음에 기능마다 한 메뉴로 둔다:
 * 비행기 모드 무선, 상태 표시줄 아이콘, 화면 모드. 설정을 바꾸는 기능은 처음 값을 남겨 [처음대로]로 되돌린다.
 */

/** 실험실 설정 쓰기: 작업 스레드에서 쓰고 [version]을 올려 화면이 값을 다시 읽게 한다. */
private class LabWriter(private val context: Context, private val launch: (suspend () -> Unit) -> Unit) {
    var version by mutableIntStateOf(0)

    /** [처음대로] 결과 한 줄([RestoreNotice]). 다시 바꾸면 지운다. */
    var notice by mutableStateOf<String?>(null)

    fun write(setting: LabSetting, value: String?) = launch {
        notice = null
        val ok = withContext(Dispatchers.IO) { setting.write(context, value) }
        version++
        if (!ok) Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
    }

    // 시스템 토스트는 화면 아래에 떠서 긴 화면 맨 아래의 [처음대로] 버튼을 가리므로, 결과는 버튼 바로 아래 글로 알린다
    fun restore(setting: LabSetting) = launch {
        val ok = withContext(Dispatchers.IO) { setting.restore(context) }
        version++
        notice = if (ok) Copy.LAB_RESTORED else Copy.LAB_WRITE_FAILED
    }
}

/** [처음대로] 버튼 바로 아래의 결과 줄. 화면 읽기 프로그램이 토스트처럼 읽어 주게 알림 영역으로 둔다. */
@Composable
private fun RestoreNotice(w: LabWriter) {
    val notice = w.notice ?: return
    Spacer(Modifier.height(Gap.Item))
    Text(notice, color = LocalLiquid.current.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun rememberLabWriter(): LabWriter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { LabWriter(context) { block -> scope.launch { block() } } }
}

/** 실험실 화면 골격: 카드를 가운데 폭으로 둔다. 기능이 둘 이상이면 [more]로 카드를 아래에 더한다. */
@Composable
private fun LabPage(
    contentPadding: PaddingValues,
    before: List<@Composable ColumnScope.() -> Unit> = emptyList(),
    more: List<@Composable ColumnScope.() -> Unit> = emptyList(),
    card: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        before.forEachIndexed { i, extra -> item(key = "before$i") { Box(Modifier.widthIn(max = ContentMaxWidth)) { GlassCard(content = extra) } } }
        item(key = "card") { Box(Modifier.widthIn(max = ContentMaxWidth)) { GlassCard(content = card) } }
        more.forEachIndexed { i, extra -> item(key = "more$i") { Box(Modifier.widthIn(max = ContentMaxWidth)) { GlassCard(content = extra) } } }
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
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, disabledHint = Copy.DISABLED_NOTHING_CHANGED, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(AirplaneRadios.setting) })
        RestoreNotice(w)
    }
}

/** 상태 표시줄 아이콘 숨기기. */
@Composable
internal fun LabIconsScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    // 상단바 숨기기(실험)는 아이콘 카드와 따로 둔 카드: 아이콘만이 아니라 상단바 자리까지 숨겨 앱이 그 자리까지 쓰게 한다(포커스 창 방식, [StatusBarHider])
    val hideCard: @Composable ColumnScope.() -> Unit = {
        NewTag(NewBadge.STATUS_BAR_HIDE, Modifier.padding(bottom = Gap.Item))
        val scope = rememberCoroutineScope()
        val hiding by StatusBarHider.on.collectAsStateWithLifecycle()
        LiquidSwitchRow(Copy.LAB_STATUS_HIDE, Copy.LAB_STATUS_HIDE_BODY, hiding, true) { want ->
            scope.launch {
                val err = withContext(Dispatchers.IO) { if (want) StatusBarHider.start(context) else StatusBarHider.stop(context).let { null } }
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
        // 주의 문구는 설명 바로 아래에 붙인다(다른 카드처럼 설명 줄과 같은 간격)
        Text(Copy.LAB_STATUS_HIDE_NOTE, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 72.dp))
        // 실기에서 다시 숨지 않을 때 원인을 볼 수 있게 진단 글을 공유한다(메신저 등으로 바로 보냄)
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(Copy.LAB_STATUS_HIDE_SEND_LOG, primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                // 메신저(디스코드 등)는 긴 글을 공유로 받지 못해 글 파일로 저장해 첨부한다
                val uri = withContext(Dispatchers.IO) { StatusBarHider.diagnosticsFile(context) }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "GOAT 상단바 숨기기 기록")
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(send, Copy.LAB_STATUS_HIDE_SEND_LOG))
            }
        })
    }
    // 마이크·카메라 사용 표시(초록 점): 기기 설정 값이라 Shizuku로 바꾼다. 아이콘 카드 아래에 따로 둔다
    val dotCard: @Composable ColumnScope.() -> Unit = {
        val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var dotHidden by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(shizuku) { dotHidden = withContext(Dispatchers.IO) { PrivacyDot.hidden() } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrivacyDotGlyph(c.ink)
            Spacer(Modifier.width(Gap.Card))
            Box(Modifier.weight(1f)) {
                // 지원되는 버전(Android 13 이하)에서만 켤 수 있고, 그 밖에서는 잠그고 이유를 적는다
                val dotOk = remember(dotHidden) { PrivacyDot.supported(context) }
                LiquidSwitchRow(Copy.LAB_PRIVACY_DOT_SWITCH, if (dotOk) Copy.LAB_PRIVACY_DOT_BODY else Copy.LAB_PRIVACY_DOT_BLOCKED, dotHidden == true, dotOk && dotHidden != null) { hide ->
                    dotHidden = hide
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { PrivacyDot.set(context, hide) }
                        if (err != null) {
                            dotHidden = withContext(Dispatchers.IO) { PrivacyDot.hidden() }
                            // 왜 안 되는지 보이게 셸 메시지를 함께 띄운다
                            Toast.makeText(context, "${Copy.LAB_WRITE_FAILED} $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    // 순서: 상단바 숨기기(가장 크게 바뀜) → 아이콘 숨기기 → 초록 점
    LabPage(contentPadding, before = listOf(hideCard), more = listOf(dotCard)) {
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
        // [처음대로]는 아이콘 카드 맨 아래: 아이콘 목록을 GOAT가 바꾸기 전으로 되돌린다(초록 점은 따로 둔 카드의 스위치로 되돌린다)
        Spacer(Modifier.height(Gap.Item))
        val changed = remember(w.version) { IconBlacklist.setting.changed(context) }
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, disabledHint = Copy.DISABLED_NOTHING_CHANGED, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(IconBlacklist.setting) })
        RestoreNotice(w)
    }
}

/** 마이크·카메라 사용 표시 아이콘: 상태 표시줄 아이콘과 같은 선 굵기로 마이크를 그리고, 오른쪽 위에 사용 표시 점을 찍는다. */
@Composable
private fun PrivacyDotGlyph(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // 마이크 머리(둥근 캡슐)
        drawRoundRect(color, Offset(7f * u, 3f * u), Size(7f * u, 11f * u), CornerRadius(3.5f * u), style = s)
        // 받침 호와 기둥
        drawArc(color, 0f, 180f, false, Offset(4.5f * u, 7.5f * u), Size(12f * u, 10f * u), style = s)
        drawLine(color, Offset(10.5f * u, 17.5f * u), Offset(10.5f * u, 21f * u), s.width, StrokeCap.Round)
        // 사용 표시 점(초록 점 자리)
        drawCircle(color, 2.6f * u, Offset(19.5f * u, 4.5f * u))
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
        // 쓸 수 없는 기기에서는 [처음대로]를 말하는 안내 대신 쓸 수 없다는 줄만 보인다
        if (setting != null) Text(Copy.LAB_SCREEN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        if (setting == null) {
            Text(Copy.LAB_SCREEN_NONE, color = c.muted, style = MaterialTheme.typography.bodySmall)
            return@LabPage
        }
        Text("${setting.key} = ${now ?: "-"}", color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        val samsung = setting === ScreenMode.samsung
        val values = if (samsung) ScreenMode.SAMSUNG_VALUES.map { it to (SAMSUNG_MODE_NAMES[it] ?: it) } else ScreenMode.AOSP_VALUES
        LiquidChips(
            options = values.map { it.first },
            selected = now ?: "",
            label = { v -> values.first { it.first == v }.second },
            onSelect = { w.write(setting, it) }
        )
        if (samsung) {
            Spacer(Modifier.height(Gap.Item))
            SAMSUNG_MODE_DESCS[now]?.let { Text(it, color = c.ink, style = MaterialTheme.typography.bodySmall) }
            Text(Copy.LAB_SCREEN_TRY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Gap.Item))
        val changed = remember(w.version) { setting.changed(context) }
        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = changed, disabledHint = Copy.DISABLED_NOTHING_CHANGED, modifier = Modifier.fillMaxWidth(), onClick = { w.restore(setting) })
        RestoreNotice(w)
        if (samsung) {
            Spacer(Modifier.height(Gap.Section))
            Text(Copy.LAB_SCREEN_WHY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
            listOf(Copy.LAB_SCREEN_WHY_1, Copy.LAB_SCREEN_WHY_2, Copy.LAB_SCREEN_WHY_3).forEach {
                Text("· $it", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 삼성 화면 모드 값별 이름과 설명([ScreenMode.SAMSUNG_VALUES]). */
private val SAMSUNG_MODE_NAMES = mapOf(
    "0" to Copy.LAB_SCREEN_MODE_0,
    "1" to Copy.LAB_SCREEN_MODE_1,
    "2" to Copy.LAB_SCREEN_MODE_2,
    "3" to Copy.LAB_SCREEN_MODE_3,
    "4" to Copy.LAB_SCREEN_MODE_4,
    "5" to Copy.LAB_SCREEN_MODE_5
)
private val SAMSUNG_MODE_DESCS = mapOf(
    "0" to Copy.LAB_SCREEN_DESC_0,
    "1" to Copy.LAB_SCREEN_DESC_1,
    "2" to Copy.LAB_SCREEN_DESC_2,
    "3" to Copy.LAB_SCREEN_DESC_3,
    "4" to Copy.LAB_SCREEN_DESC_4,
    "5" to Copy.LAB_SCREEN_DESC_5
)

/** 상태 표시줄 아이콘 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun StatusIconsHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 하는 일",
            "화면 맨 위 상태 표시줄에서 알람·블루투스·진동 같은 아이콘 중 선택한 것을 숨겨 상태 표시줄을 깔끔하게 정리합니다. " +
                "아이콘만 숨길 뿐 기능은 그대로 켜져 있습니다."
        )
        HelpTopic(
            "2. 필요한 권한",
            "Android 시스템 UI 튜너가 쓰는 설정을 바꾸므로 고급 화면 배율 설정과 같은 시스템 설정 권한이 필요합니다. " +
                "점 세 개 메뉴의 '권한 설정'에서 받는 방법을 볼 수 있습니다."
        )
        HelpTopic(
            "3. 숨겨지지 않는 아이콘",
            "One UI 버전에 따라 일부 아이콘은 이름이 달라 숨겨지지 않을 수 있습니다. 마이크·카메라 사용 표시(초록 점)는 Android 13 이하에서만 " +
                "바꿀 수 있고, Android 14 이상은 시스템이 막아 두어 스위치가 잠깁니다."
        )
        HelpTopic("4. 되돌리기", "[처음대로]를 누르면 숨긴 아이콘이 GOAT가 바꾸기 전 값으로 돌아갑니다.")
        // 같은 화면의 상단바 숨기기(실험) 카드도 설명한다(빠져 있었음, QA 1차)
        HelpTopic(
            "5. 상단바 숨기기(실험)",
            "아이콘뿐 아니라 상단바 전체를 숨겨 앱이 그 자리까지 화면을 사용합니다. 위 가장자리를 쓸어내리면 잠깐 보입니다. 뒤로 가기를 쓰면 잠깐 보였다가 " +
                "자동으로 다시 숨겨지고, 키보드가 열려 있는 동안에는 보이다가 키보드를 닫으면 다시 숨겨집니다. " +
                "설정 앱처럼 다른 앱 위 표시를 막는 화면에서는 보이고, 그 화면을 벗어나면 다시 숨겨집니다. 그래도 보이면 알림의 [다시 숨기기]를 누르세요. '다른 앱 위에 표시' 권한이 필요하며, Shizuku가 연결되어 있으면 GOAT가 받습니다."
        )
    }
}

/** 비행기 모드 무선 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun AirplaneHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 하는 일",
            "비행기 모드를 켜면 보통 Wi-Fi·블루투스·NFC까지 함께 꺼집니다. 여기서 고른 무선은 비행기 모드를 켜도 끄지 않고 그대로 둡니다. " +
                "모바일 데이터(통신)는 비행기 모드에서 항상 꺼집니다."
        )
        HelpTopic(
            "2. 적용 시점",
            "다음에 비행기 모드를 켤 때부터 적용됩니다. 이미 비행기 모드라면 한 번 껐다 다시 켜 주세요."
        )
        HelpTopic(
            "3. 필요한 권한",
            "비행기 모드 설정을 바꾸므로 시스템 설정 권한이 필요합니다. 점 세 개 메뉴의 '권한 설정'에서 받는 방법을 볼 수 있습니다."
        )
        HelpTopic("4. 되돌리기", "[처음대로]를 누르면 GOAT가 바꾸기 전 값으로 돌아갑니다.")
    }
}
