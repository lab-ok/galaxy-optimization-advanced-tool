package com.labok.goat.ui.tweaks

import com.labok.goat.ui.liquid.LiquidRadioGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.display.ResolutionState
import com.labok.goat.data.tweaks.Resolution
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 해상도 낮추기: 지금·원래 해상도, 크기 고르기(누르면 적용하고 확인 창), 주의. 폴드는 지원하지 않는다고 안내한다.
 * 앱별 화면 배율을 정해 둔 앱이 있으면 그 앱들이 커 보인다고 먼저 묻는다(삼성이 정해진 단계만 받아 함께 줄일 수 없다).
 * 적용 뒤의 확인 창은 화면이 다시 만들어져도 떠야 하므로 [ResolutionConfirm]으로 앱 맨 위에 둔다.
 */
@Composable
internal fun ResolutionScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    val pending by Resolution.pending.collectAsStateWithLifecycle()
    var version by remember { mutableIntStateOf(0) }
    val supported = remember { Resolution.supported(context) }
    // 해상도가 바뀌면 화면 설정(Configuration)이 바뀌므로 그것을 키로 다시 읽는다(반영 전 크기를 붙들지 않게)
    val config = LocalConfiguration.current
    val base = remember(version, pending, config) { Resolution.base(context) }
    val now = remember(version, pending, config) { Resolution.current(context) }
    val options = remember(base) { Resolution.optionsFor(base) }
    val selected = options.minByOrNull { kotlin.math.abs(it.short - now.first) }
    var busy by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Resolution.Option?>(null) }
    LifecycleResumeEffect(Unit) {
        // 다른 곳에서 해상도를 키웠으면 낡은 기록을 지운 뒤 다시 읽는다(부수 효과는 작업 스레드에서)
        scope.launch { withContext(Dispatchers.IO) { Resolution.dropStale(context) }; version++ }
        onPauseOrDispose { }
    }

    fun apply(o: Resolution.Option) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Resolution.apply(context, o, "고르기") }
            busy = false
            version++
            if (ok) haptics.snap() else { haptics.reject(); Toast.makeText(context, Copy.RES_TOAST_FAILED, Toast.LENGTH_SHORT).show() }
        }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.RES_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.RES_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    ResolutionArt(percent = selected?.percent?.takeIf { it < 100 } ?: options.getOrNull(1)?.percent ?: 75)
                }
            }
        }
        when {
            !supported -> item(key = "fold") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.RES_FOLD_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.RES_FOLD_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            !permitted -> item(key = "permission") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.RES_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (supported) item(key = "pick") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.RES_PICK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.resNow(now.first, now.second, base.first, base.second), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidRadioGroup {
                        options.forEach { o ->
                            LiquidRadioRow(
                                Copy.resOption(o.name, o.short, o.long, o.percent),
                                if (o.percent >= 100) Copy.RES_ORIGINAL_DESC else Copy.resOptionDesc(o.percent),
                                selected == o, enabled = permitted && !busy && pending == 0L,
                            ) {
                                if (o == selected) return@LiquidRadioRow
                                haptics.segment()
                                // 앱별 화면 배율을 정해 둔 앱이 있으면 먼저 알린다(해상도를 낮추면 그 앱들은 커 보인다)
                                if (o.percent < 100 && DensityShell.savedCount(context) > 0) ask = o else apply(o)
                            }
                        }
                    }
                }
            }
        }
        if (supported) item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.RES_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.RES_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    ask?.let { o ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(Copy.RES_APPS_TITLE) },
            text = { Text(Copy.resAppsBody(DensityShell.savedCount(context))) },
            confirmButton = { TextButton(onClick = { ask = null; apply(o) }) { Text(Copy.RES_APPS_OK) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(Copy.GOS_CONFIRM_CANCEL) } },
        )
    }
}

/**
 * 해상도를 바꾼 뒤 확인 창: 남은 시간을 세고, '유지'를 누르지 않으면 [Resolution]이 되돌린다. 화면 크기가 바뀌어
 * 액티비티가 다시 만들어져도 다시 뜨도록 앱 맨 위(MainActivity)에 둔다. 남은 시간은 기기 보호 저장소의 마감 시각으로 잰다.
 */
@Composable
fun ResolutionConfirm() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by Resolution.pending.collectAsStateWithLifecycle()
    if (pending == 0L) return
    var left by remember(pending) { mutableLongStateOf(((pending - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(pending) {
        while (true) {
            left = ((pending - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
            if (left == 0L) break
            delay(250)
        }
    }
    AlertDialog(
        onDismissRequest = { },
        title = { Text(Copy.RES_CONFIRM_TITLE) },
        text = { Text(Copy.resConfirmBody(left.toInt())) },
        confirmButton = { TextButton(onClick = { Resolution.keep(context) }) { Text(Copy.RES_CONFIRM_KEEP) } },
        dismissButton = { TextButton(onClick = { scope.launch(Dispatchers.IO) { Resolution.revert(context, "확인 창 되돌리기") } }) { Text(Copy.RES_CONFIRM_REVERT) } },
    )
}

/** 사용 방법. */
@Composable
internal fun ResolutionHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "화면이 그리는 점(픽셀)의 수를 줄입니다. 글자·아이콘 크기는 그대로 두고 선명도만 조금 낮춰, 게임에서 프레임이 오르거나 " +
                "배터리 부담이 줄 수 있습니다. 효과는 기기와 앱마다 다릅니다.",
        ) { ResolutionArt(75) }
        HelpTopic(
            "2. 바꾼 뒤 확인",
            "바꾸면 화면이 잠깐 깜빡이고 ${Resolution.CONFIRM_MS / 1000}초 동안 '이 해상도를 유지할까요?'를 묻습니다. '유지'를 누르지 않으면 " +
                "원래대로 되돌립니다. 확인하기 전에 재부팅하면 다음 부팅 때 원래대로 되돌리도록 해 두었습니다.",
        )
        HelpTopic(
            "3. 함께 바뀌는 것",
            "화면 크기가 그대로 보이도록 표시 밀도(DPI)도 같은 비율로 줄입니다. 고급 화면 배율 설정을 쓰고 있으면 그 값도 같은 비율로 맞춥니다. " +
                "앱별 화면 배율을 정해 둔 앱은 삼성이 정해진 단계만 받아 함께 줄일 수 없어, 해상도를 낮추는 동안 조금 커 보입니다.",
        )
        HelpTopic(
            "4. 폴드와 권한",
            "폴드는 두 화면의 해상도가 따로 저장되는 방식이 확인되지 않아 지원하지 않습니다. 고급 화면 배율 설정과 같은 시스템 설정 권한으로 바꿉니다.",
        )
        HelpTopic(
            "5. 되돌리기",
            "'원래대로'를 고르면 GOAT가 바꾸기 전 해상도(삼성 설정에서 FHD+로 줄여 두었으면 그 크기)로 돌아갑니다. " +
                "화면을 쓸 수 없을 만큼 문제가 생기면 PC에서 '${Resolution.PC_UNDO}'를 실행하세요.",
        )
    }
}
