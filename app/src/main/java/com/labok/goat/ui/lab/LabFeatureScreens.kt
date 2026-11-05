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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.R
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.LabSetting
import com.labok.goat.data.lab.PrivacyDot
import com.labok.goat.data.lab.StatusBarHider
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
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

/**
 * 시스템 설정 권한이 없을 때 맨 아래에 붙이는 '권한 설정 필요' 카드(애니메이션·흔들기 화면과 같은 모양).
 * 이 화면들도 시스템 설정을 바꾸므로 권한 없이 스위치만 보이면 눌러도 바뀌지 않는다(1.51.0 QA).
 */
private fun permissionCard(permitted: Boolean, onGuide: () -> Unit): List<@Composable ColumnScope.() -> Unit> = if (permitted) {
    emptyList()
} else {
    listOf {
        Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = LocalLiquid.current.ink)
        Text(Copy.TODO_SETUP_BODY, color = LocalLiquid.current.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
    }
}

/** 비행기 모드 때 켜 둘 무선. */
@Composable
internal fun LabAirplaneScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    LabPage(contentPadding, more = permissionCard(permitted, onGuide)) {
        val raw = remember(w.version) { AirplaneRadios.setting.read(context) }
        Text(Copy.LAB_AIRPLANE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_AIRPLANE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        AirplaneArt(AirplaneRadios.Radio.entries.map { AirplaneRadios.kept(raw, it) }, AirplaneRadios.Radio.entries.map { it.label })
        Spacer(Modifier.height(Gap.Item))
        // 세 줄에 같은 설명을 되풀이하지 않고 한 번만 적는다
        Text(Copy.LAB_AIRPLANE_ROW, color = c.muted, style = MaterialTheme.typography.bodySmall)
        AirplaneRadios.Radio.entries.forEach { radio ->
            Spacer(Modifier.height(Gap.Item))
            LiquidSwitchRow(stringResource(R.string.lab_airplane_keep_radio, radio.label), null, AirplaneRadios.kept(raw, radio), true) { keep ->
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
internal fun LabIconsScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val w = rememberLabWriter()
    // 상단바 숨기기는 아이콘 카드와 따로 둔 카드: 아이콘만이 아니라 상단바 자리까지 숨겨 앱이 그 자리까지 쓰게 한다(포커스 창 방식, [StatusBarHider])
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
    }
    // 마이크·카메라 사용 표시(초록 점): 기기 설정 값이라 Shizuku로 바꾼다. 아이콘 카드 아래에 따로 둔다
    val dotCard: @Composable ColumnScope.() -> Unit = {
        val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var dotHidden by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(shizuku) { dotHidden = withContext(Dispatchers.IO) { PrivacyDot.hidden() } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrivacyDotGlyph(c.ink)
            Spacer(Modifier.width(Space.IconText))
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
    LabPage(contentPadding, before = listOf(hideCard), more = listOf(dotCard) + permissionCard(permitted, onGuide)) {
        val raw = remember(w.version) { IconBlacklist.setting.read(context) }
        Text(Copy.LAB_ICONS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(Copy.LAB_ICONS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        StatusDropArt()
        IconBlacklist.Slot.entries.forEach { slot ->
            Spacer(Modifier.height(Gap.Item))
            // 줄 앞에 상태 표시줄에서 보이는 모양을 그려 어떤 아이콘인지 바로 알아보게 한다
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBarGlyph(slot, c.ink)
                Spacer(Modifier.width(Space.IconText))
                Box(Modifier.weight(1f)) {
                    LiquidSwitchRow(stringResource(R.string.lab_status_icons_hide_slot, slot.label), slot.id, IconBlacklist.hidden(raw, slot), true) { hide ->
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

/** 상태 표시줄 아이콘 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun StatusIconsHelpSheet() {
    SheetColumn(title = stringResource(R.string.lab_help_title)) {
        HelpTopic(
            stringResource(R.string.lab_help_what_title),
            stringResource(R.string.lab_status_icons_help_what_body)
        )
        HelpTopic(
            stringResource(R.string.lab_status_icons_help_permission_title),
            stringResource(R.string.lab_status_icons_help_permission_body)
        )
        HelpTopic(
            stringResource(R.string.lab_status_icons_help_unhidable_title),
            stringResource(R.string.lab_status_icons_help_unhidable_body)
        )
        HelpTopic(stringResource(R.string.lab_help_revert_title), stringResource(R.string.lab_help_revert_body))
        // 같은 화면의 상단바 숨기기 카드도 설명한다(빠져 있었음, QA 1차)
        HelpTopic(
            stringResource(R.string.lab_status_icons_help_statusbar_title),
            stringResource(R.string.lab_status_icons_help_statusbar_body)
        )
        HelpTopic(
            stringResource(R.string.lab_status_icons_help_limits_title),
            stringResource(R.string.lab_status_icons_help_limits_body)
        )
    }
}

/** 비행기 모드 무선 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun AirplaneHelpSheet() {
    SheetColumn(title = stringResource(R.string.lab_help_title)) {
        HelpTopic(
            stringResource(R.string.lab_help_what_title),
            stringResource(R.string.lab_airplane_help_what_body)
        )
        HelpTopic(
            stringResource(R.string.lab_airplane_help_when_title),
            stringResource(R.string.lab_airplane_help_when_body)
        )
        HelpTopic(
            stringResource(R.string.lab_airplane_help_permission_title),
            stringResource(R.string.lab_airplane_help_permission_body)
        )
        HelpTopic(stringResource(R.string.lab_help_revert_title), stringResource(R.string.lab_help_revert_body))
    }
}
