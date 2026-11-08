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
    val sawDirect by VoiceRedirect.sawDirectVoice.collectAsStateWithLifecycle()
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
    val triggerSeen = remember(tick) { VoiceRedirect.triggerSeen(context) }
    val modelDone = remember(tick) { VoiceRedirect.modelDone(context) }
    var advanced by remember { mutableStateOf(false) }
    // GOAT가 대신 할 수 있는 준비는 화면을 연 동안 5초마다 알아서 한다(키보드 목록 켜기·고르기·마이크 권한·Google 음성 입력 켜기·기능 켜기)
    LaunchedEffect(tick / 5, futoInstalled) {
        if (!futoInstalled) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { VoiceRedirect.autoSetup(context) } }
        on = VoiceRedirect.enabled(context)
        target = VoiceRedirect.target(context)
    }
    val autoDone = futoIme != null && futoIme in enabledIds && target == futoIme && on

    // 처음 하는 사람을 위한 단계: 제목, 끝났는지, 지금 할 일 설명
    data class Step(val title: String, val done: Boolean, val auto: Boolean = false)
    val steps = listOf(
        Step("위스퍼 입력기 설치", futoInstalled),
        Step("키보드 목록에 켜고 고르기·기능 켜기", autoDone, auto = true),
        Step("마이크 허용", futoMic),
        Step("한국어 모델 받기", modelDone),
        Step("삼성 키보드 음성 입력을 'Google 음성 입력'으로", triggerSeen),
        Step("시험해 보기", triggerSeen)
    )
    val currentStep = steps.indexOfFirst { !it.done }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "hero") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(
                        "삼성 키보드 마이크로 위스퍼 받아쓰기",
                        "삼성 키보드의 마이크를 누르면 위스퍼(OpenAI의 음성 인식, 폰 안에서 동작해 녹음을 밖으로 보내지 않음)가 받아 적고, 말을 마치면 삼성 키보드로 돌아옵니다.",
                        on,
                        enabled = on || targetOn,
                        onChange = {
                            on = it
                            VoiceRedirect.setEnabled(context, it)
                            VoiceRedirect.setUserOff(context, !it)
                            GoatService.sync(context)
                        }
                    )
                    Spacer(Modifier.height(Gap.Item))
                    Text(
                        if (currentStep < 0) "✓ 준비가 끝났습니다. 아무 입력 칸에서 삼성 키보드의 마이크를 눌러 쓰면 됩니다." else "준비 ${steps.count { it.done }}/${steps.size} · 아래 단계를 차례로 따라 하세요.",
                        color = if (currentStep < 0) c.ink else c.muted,
                        fontWeight = if (currentStep < 0) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item(key = "steps") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text("준비하기", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text("'자동'은 GOAT가 알아서 합니다. 나머지는 지금 할 단계만 펼쳐 보여 드립니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    steps.forEachIndexed { i, step ->
                        Spacer(Modifier.height(Gap.Item))
                        val now = i == currentStep
                        Text(
                            (if (step.done) "✓ " else "${i + 1}. ") + step.title + if (step.auto) " (자동)" else "",
                            color = if (step.done || now) c.ink else c.muted,
                            fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (now) {
                            val guide = when (i) {
                                0 -> "아래 버튼을 누르면 Play 스토어가 열립니다. [설치]를 누르고, 설치가 끝나면 GOAT로 돌아오세요. 돌아오면 다음 단계는 GOAT가 이어서 합니다."
                                1 -> if (VoiceRedirect.userOff(context)) "기능을 직접 끄셔서 자동으로 켜지 않았습니다. 맨 위 스위치를 켜 주세요." else "GOAT가 위스퍼 입력기를 키보드 목록에 켜고, 넘겨 줄 입력기로 고르고, 기능을 켜는 중입니다. 몇 초 기다려 주세요."
                                2 -> "아래 버튼을 누르면 위스퍼 입력기 앱(FUTO Voice Input)이 열립니다. 마이크 권한을 묻는 창이 뜨면 [허용]을 누르고 GOAT로 돌아오세요."
                                3 -> "기본 모델은 영어만 받아 적습니다. 아래 버튼으로 위스퍼 입력기 앱을 열고, 언어(Languages) 설정에서 한국어가 들어 있는 모델(Multilingual, 다국어)을 받으세요. Wi-Fi에서 1~2분 걸립니다. 화면 이름은 버전마다 조금 다를 수 있습니다. 다 받았으면 [받았어요]를 누르세요."
                                4 -> "아래 버튼을 누르면 키보드 설정이 열립니다. ① '삼성 키보드'를 누르고 ② '음성 입력'을 누른 뒤 ③ 'Google 음성 입력'을 고르세요. 삼성 음성 입력으로 두면 마이크가 키보드 안에서 처리돼 GOAT가 위스퍼로 넘길 수 없습니다. 고른 뒤 다음 단계로 시험해 보세요."
                                else -> "카톡 같은 아무 입력 칸에서 삼성 키보드의 마이크를 눌러 보세요. 위스퍼 화면이 뜨면 성공이고, 이 단계에 ✓가 생깁니다. 말을 마치면 삼성 키보드로 저절로 돌아옵니다."
                            }
                            Text(guide, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            val openFuto = {
                                context.packageManager.getLaunchIntentForPackage(VoiceRedirect.FUTO)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                                Unit
                            }
                            when (i) {
                                0 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton("Play 스토어에서 설치하기", onClick = { openFutoStore() }, modifier = Modifier.fillMaxWidth())
                                }
                                2 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton("위스퍼 입력기 열기", onClick = openFuto, modifier = Modifier.fillMaxWidth())
                                }
                                3 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                                        LiquidButton("위스퍼 입력기 열기", onClick = openFuto, modifier = Modifier.weight(1f), primary = false)
                                        LiquidButton("받았어요", onClick = { VoiceRedirect.setModelDone(context, true) }, modifier = Modifier.weight(1f))
                                    }
                                }
                                4, 5 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton("키보드 설정 열기", onClick = { openKeyboards() }, modifier = Modifier.fillMaxWidth(), primary = i == 4)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "advanced") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                LiquidButton(if (advanced) "고급 접기" else "고급: 입력기 고르기·되는지 확인", onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth(), primary = false)
            }
        }
        if (!advanced) return@LazyColumn
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
                        LiquidButton("위스퍼 입력기 받기", onClick = { openFutoStore() }, modifier = Modifier.weight(1f), primary = false)
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
                        "삼성 음성 입력이 별도 입력기로 등록됨(없어도 'Google 음성 입력'으로 하면 됨)" to (samsungIme != null),
                        "보안 설정 쓰기 권한" to (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED),
                        "넘겨 줄 입력기를 고르고 켜 둠" to targetOn,
                        "마이크를 누를 때 음성 입력기로 바뀜(아래 기록, GOAT 시험 버튼으로 바꾼 것은 빼고)" to (sawDirect || saw)
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
