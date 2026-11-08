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
import com.labok.goat.R
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
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy
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
    // 시험용 입력칸(11-08 소장님 "입력창이라도 넣어 편의를"): 다른 앱에 가지 않고 이 화면에서 바로 마이크를 눌러 본다
    var testText by remember { mutableStateOf("") }
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
        Step(Copy.text(R.string.voice_01), futoInstalled),
        Step(Copy.text(R.string.voice_02), autoDone, auto = true),
        Step(Copy.text(R.string.voice_03), futoMic),
        Step(Copy.text(R.string.voice_04), modelDone),
        Step(Copy.text(R.string.voice_05), triggerSeen),
        Step(Copy.text(R.string.voice_06), triggerSeen)
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
                        Copy.text(R.string.voice_07),
                        Copy.text(R.string.voice_08),
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
                        if (currentStep < 0) Copy.text(R.string.voice_09) else Copy.text(R.string.voice_10, steps.count { it.done }, steps.size),
                        color = if (currentStep < 0) c.ink else c.muted,
                        fontWeight = if (currentStep < 0) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (currentStep < 0) {
                        Spacer(Modifier.height(Gap.Item))
                        LiquidTextField(testText, { testText = it }, Copy.text(R.string.voice_11), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item(key = "steps") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.text(R.string.voice_12), fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.text(R.string.voice_13), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    steps.forEachIndexed { i, step ->
                        Spacer(Modifier.height(Gap.Item))
                        val now = i == currentStep
                        Text(
                            (if (step.done) "✓ " else "${i + 1}. ") + step.title + if (step.auto) Copy.text(R.string.voice_14) else "",
                            color = if (step.done || now) c.ink else c.muted,
                            fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (now) {
                            val guide = when (i) {
                                0 -> Copy.text(R.string.voice_15)
                                1 -> if (VoiceRedirect.userOff(context)) Copy.text(R.string.voice_16) else Copy.text(R.string.voice_17)
                                2 -> Copy.text(R.string.voice_18)
                                3 -> Copy.text(R.string.voice_19)
                                4 -> Copy.text(R.string.voice_20)
                                else -> Copy.text(R.string.voice_21)
                            }
                            Text(guide, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            val openFuto = {
                                context.packageManager.getLaunchIntentForPackage(VoiceRedirect.FUTO)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                                Unit
                            }
                            when (i) {
                                0 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton(Copy.text(R.string.voice_22), onClick = { openFutoStore() }, modifier = Modifier.fillMaxWidth())
                                }
                                2 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton(Copy.text(R.string.voice_23), onClick = openFuto, modifier = Modifier.fillMaxWidth())
                                }
                                3 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                                        LiquidButton(Copy.text(R.string.voice_24), onClick = openFuto, modifier = Modifier.weight(1f), primary = false)
                                        LiquidButton(Copy.text(R.string.voice_25), onClick = { VoiceRedirect.setModelDone(context, true) }, modifier = Modifier.weight(1f))
                                    }
                                }
                                4 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidButton(Copy.text(R.string.voice_26), onClick = { openKeyboards() }, modifier = Modifier.fillMaxWidth())
                                }
                                5 -> {
                                    Spacer(Modifier.height(Gap.Item))
                                    LiquidTextField(testText, { testText = it }, Copy.text(R.string.voice_27), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "advanced") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                LiquidButton(if (advanced) Copy.text(R.string.voice_28) else Copy.text(R.string.voice_29), onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth(), primary = false)
            }
        }
        if (!advanced) return@LazyColumn
        item(key = "target") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.text(R.string.voice_30), fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.text(R.string.voice_31), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    if (candidates.isEmpty()) {
                        Text(Copy.text(R.string.voice_32), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LiquidRadioGroup {
                            candidates.forEach { info ->
                                val id = info.id
                                val state = listOfNotNull(
                                    Copy.text(R.string.voice_33).takeIf { VoiceRedirect.hasVoice(info) },
                                    if (id in enabledIds) Copy.text(R.string.voice_34) else Copy.text(R.string.voice_35)
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
                        LiquidButton(Copy.text(R.string.voice_36), onClick = { openKeyboards() }, modifier = Modifier.weight(1f), primary = false)
                        LiquidButton(Copy.text(R.string.voice_37), onClick = { openFutoStore() }, modifier = Modifier.weight(1f), primary = false)
                    }
                }
            }
        }
        item(key = "probe") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.text(R.string.voice_38), fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.height(Gap.Tight))
                    val checks = listOf(
                        Copy.text(R.string.voice_39) to (samsungIme != null),
                        Copy.text(R.string.voice_40) to (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED),
                        Copy.text(R.string.voice_41) to targetOn,
                        Copy.text(R.string.voice_42) to (sawDirect || saw)
                    )
                    checks.forEach { (name, ok) ->
                        Text((if (ok) "✓ " else "✗ ") + name, color = if (ok) c.ink else c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(Copy.text(R.string.voice_43, VoiceRedirect.label(context, current)), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    Text(
                        Copy.text(R.string.voice_44),
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(if (probing) Copy.text(R.string.voice_45) else Copy.text(R.string.voice_46), onClick = {
                        if (probing) VoiceRedirect.stopProbe(context) else VoiceRedirect.startProbe(context)
                    }, modifier = Modifier.fillMaxWidth())
                    if (log.isNotEmpty()) {
                        Spacer(Modifier.height(Gap.Item))
                        log.forEach { Text(it, color = c.ink, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.text(R.string.voice_47), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.Tight)) {
                        LiquidButton(Copy.text(R.string.voice_48), onClick = {
                            val t = target
                            trial = if (t == null) {
                                Copy.text(R.string.voice_49)
                            } else if (VoiceRedirect.switchTo(context, t)) {
                                Copy.text(R.string.voice_50, VoiceRedirect.label(context, t))
                            } else {
                                Copy.text(R.string.voice_51)
                            }
                        }, modifier = Modifier.weight(1f), primary = false, enabled = target != null)
                        LiquidButton(Copy.text(R.string.voice_52), onClick = {
                            val samsung = VoiceRedirect.enabledImes(context).firstOrNull { it.packageName.startsWith("com.samsung.android.honeyboard") }?.id
                            trial = if (samsung != null && VoiceRedirect.switchTo(context, samsung)) Copy.text(R.string.voice_53) else Copy.text(R.string.voice_54)
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
