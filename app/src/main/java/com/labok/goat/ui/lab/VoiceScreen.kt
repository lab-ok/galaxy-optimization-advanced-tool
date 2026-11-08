package com.labok.goat.ui.lab

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.VoiceRedirect
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioGroup
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 실험실 '위스퍼 음성 입력'(11-08 소장님 "실험실 메뉴로 따로 만들고 조사 도구까지 녹여서 되는지 안 되는지 확인할 수 있게").
 * 위: 기능 스위치와 넘겨 줄 입력기 고르기. 아래: 조사(삼성 음성 입력이 별도 입력기인지, 마이크를 누를 때 입력기가 바뀌는지 기록·판정,
 * 고른 입력기로 바꾸기 시험). 실험실 화면이라 문구는 코드에 둔다(조사 도구와 같이 문구 검사에서 뺌).
 */
@Composable
internal fun VoiceScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    // 키보드 목록에서 켜고 돌아오면 다시 읽도록 1초마다 갱신값을 올린다
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    var on by remember { mutableStateOf(VoiceRedirect.enabled(context)) }
    var target by remember { mutableStateOf(VoiceRedirect.target(context)) }
    val candidates = remember(tick / 5) { VoiceRedirect.candidates(context) }
    val enabledIds = remember(tick) { VoiceRedirect.enabledImes(context).map { it.id }.toSet() }
    val current = remember(tick) { VoiceRedirect.current(context) }
    val samsungIme = remember(tick / 5) { VoiceRedirect.allImes(context).firstOrNull { it.packageName == VoiceRedirect.SAMSUNG_VOICE } }
    val probing by VoiceRedirect.probeOn.collectAsStateWithLifecycle()
    val log by VoiceRedirect.probeLog.collectAsStateWithLifecycle()
    val saw by VoiceRedirect.sawSamsungVoice.collectAsStateWithLifecycle()
    var trial by remember { mutableStateOf<String?>(null) }
    val targetOn = target != null && target in enabledIds
    val openKeyboards = { runCatching { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    // Play 스토어 앱이 있으면 그 화면으로, 없으면 웹으로 연다
    val openFutoStore = {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${VoiceRedirect.FUTO}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${VoiceRedirect.FUTO}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    // 한 번에 준비(11-08 소장님 "설치까지 딸깍으로"): 설치 → 키보드 목록에 켜기(자동) → 마이크 허용 → 기능 켜기
    val futoInstalled = remember(tick) { VoiceRedirect.futoInstalled(context) }
    val futoIme = remember(tick) { VoiceRedirect.futoIme(context) }
    val futoMic = remember(tick) { VoiceRedirect.futoMicAllowed(context) }
    LaunchedEffect(futoIme) {
        val id = futoIme ?: return@LaunchedEffect
        // 설치되면 GOAT가 키보드 목록에서 켜고 넘겨 줄 입력기로 고른다(설정 화면에 갈 필요 없음)
        withContext(Dispatchers.IO) { runCatching { VoiceRedirect.enableIme(context, id) } }
        // 아직 고르지 않았거나, 고른 것이 음성 모드가 없는 일반 키보드면 FUTO로 바꾼다
        val chosenIsVoice = VoiceRedirect.allImes(context).firstOrNull { it.id == target }?.let { VoiceRedirect.hasVoice(it) } ?: false
        if (target == null || !chosenIsVoice) {
            target = id
            VoiceRedirect.setTarget(context, id)
        }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "setup") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text("한 번에 준비", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text("아래 버튼을 차례로 누르면 됩니다. 키보드 목록에서 켜기와 입력기 고르기는 GOAT가 알아서 합니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    val steps = listOf(
                        "1. FUTO Voice Input(위스퍼) 설치" to futoInstalled,
                        "2. 키보드 목록에서 켜고 고르기(자동)" to (futoIme != null && futoIme in enabledIds && target == futoIme),
                        "3. FUTO Voice Input에서 마이크 허용·음성 모델 받기" to futoMic,
                        "4. 기능 켜기" to on
                    )
                    steps.forEach { (name, ok) -> Text((if (ok) "✓ " else "· ") + name, color = if (ok) c.ink else c.muted, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(Gap.Item))
                    when {
                        !futoInstalled -> LiquidButton("FUTO Voice Input 설치하기", onClick = { openFutoStore() }, modifier = Modifier.fillMaxWidth())
                        !futoMic -> LiquidButton("FUTO Voice Input 열어 마이크 허용하기", onClick = {
                            context.packageManager.getLaunchIntentForPackage(VoiceRedirect.FUTO)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        }, modifier = Modifier.fillMaxWidth())
                        !on -> LiquidButton("기능 켜기", onClick = {
                            on = true
                            VoiceRedirect.setEnabled(context, true)
                            GoatService.sync(context)
                        }, modifier = Modifier.fillMaxWidth(), enabled = targetOn)
                        else -> Text("준비가 끝났습니다. 아무 입력 칸에서 삼성 키보드의 마이크를 눌러 보세요.", color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(
                        "삼성 키보드 마이크를 위스퍼로",
                        "삼성 키보드에서 마이크를 누르면 삼성 음성 입력 대신 아래에서 고른 음성 입력기로 넘깁니다. 말하기가 끝나면 삼성 키보드로 돌아옵니다.",
                        on,
                        enabled = on || targetOn,
                        onChange = {
                            on = it
                            VoiceRedirect.setEnabled(context, it)
                            GoatService.sync(context)
                        }
                    )
                    if (!targetOn) {
                        Spacer(Modifier.height(Gap.Item))
                        Text(
                            if (target == null) "먼저 아래에서 넘겨 줄 음성 입력기를 고르세요." else "고른 입력기가 키보드 목록에서 꺼져 있습니다. 키보드 목록에서 켜 주세요.",
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item(key = "target") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text("넘겨 줄 음성 입력기", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text("위스퍼 음성 입력기(FUTO Voice Input, WhisperInput 등)를 설치한 뒤 고르세요. 음성 모드가 있는 입력기를 위에 둡니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    if (candidates.isEmpty()) {
                        Text("설치된 다른 입력기가 없습니다.", color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LiquidRadioGroup {
                            candidates.forEach { info ->
                                val id = info.id
                                val state = listOfNotNull(
                                    "음성 모드".takeIf { VoiceRedirect.hasVoice(info) },
                                    if (id in enabledIds) "키보드 목록에서 켜짐" else "키보드 목록에서 꺼짐"
                                ).joinToString(" · ")
                                LiquidRadioRow(info.loadLabel(context.packageManager).toString(), state, selected = id == target, enabled = true, onClick = {
                                    target = id
                                    VoiceRedirect.setTarget(context, id)
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                        LiquidButton("키보드 목록 열기", onClick = { openKeyboards() }, modifier = Modifier.weight(1f), primary = false)
                        LiquidButton("FUTO Voice Input 받기", onClick = { openFutoStore() }, modifier = Modifier.weight(1f), primary = false)
                    }
                }
            }
        }
        item(key = "probe") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text("되는지 확인", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.height(Gap.Tight))
                    val checks = listOf(
                        "삼성 음성 입력이 별도 입력기로 등록됨" to (samsungIme != null),
                        "보안 설정 쓰기 권한" to (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED),
                        "넘겨 줄 입력기를 고르고 켜 둠" to targetOn,
                        "마이크를 누를 때 삼성 음성 입력으로 바뀜(아래 기록)" to saw
                    )
                    checks.forEach { (name, ok) ->
                        Text((if (ok) "✓ " else "✗ ") + name, color = if (ok) c.ink else c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("지금 입력기: ${VoiceRedirect.label(context, current)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    Text(
                        "1) [기록 시작]을 누르고 2) 아무 앱의 글 입력 칸에서 삼성 키보드 마이크를 누른 뒤 3) 여기로 돌아와 [기록 끝]을 누르세요. 마이크를 누를 때 입력기가 바뀌는지 기록해 판정합니다.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(if (probing) "기록 끝" else "기록 시작", onClick = {
                        if (probing) VoiceRedirect.stopProbe(context) else VoiceRedirect.startProbe(context)
                    }, modifier = Modifier.fillMaxWidth())
                    if (log.isNotEmpty()) {
                        Spacer(Modifier.height(Gap.Item))
                        log.forEach { Text(it, color = c.ink, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text("입력기 바꾸기 시험: GOAT가 지금 입력기를 고른 입력기로 바꿀 수 있는지 봅니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                        LiquidButton("고른 입력기로 바꾸기", onClick = {
                            val t = target
                            trial = if (t == null) {
                                "넘겨 줄 입력기를 먼저 고르세요."
                            } else if (VoiceRedirect.switchTo(context, t)) {
                                "바뀜: ${VoiceRedirect.label(context, t)}"
                            } else {
                                "바뀌지 않음(키보드 목록에서 켜져 있는지 확인)"
                            }
                        }, modifier = Modifier.weight(1f), primary = false, enabled = target != null)
                        LiquidButton("삼성 키보드로 되돌리기", onClick = {
                            val samsung = VoiceRedirect.enabledImes(context).firstOrNull { it.packageName.startsWith("com.samsung.android.honeyboard") }?.id
                            trial = if (samsung != null && VoiceRedirect.switchTo(context, samsung)) "삼성 키보드로 되돌림" else "삼성 키보드를 찾지 못함"
                        }, modifier = Modifier.weight(1f), primary = false)
                    }
                    trial?.let {
                        Spacer(Modifier.height(Gap.Tight))
                        Text(it, color = c.ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
