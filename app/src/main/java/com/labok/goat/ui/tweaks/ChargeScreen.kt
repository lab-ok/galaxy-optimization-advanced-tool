package com.labok.goat.ui.tweaks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.BuildConfig
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.ChargeLimit
import com.labok.goat.data.tweaks.ChargeLimit.Choice
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 충전 상한: 지금 배터리, 보호 방식(끔·기본·최대)과 최대일 때 상한(80~95%), 삼성 설정 열기, 주의.
 * 이 기기에 삼성 배터리 보호 설정이 있을 때만 바꾸고, 없으면 설정 화면으로 안내한다(Shizuku로 찾고 쓴다).
 * lab 빌드는 에뮬레이터에서도 화면을 볼 수 있게 설정이 있는 것처럼 보여 준다(쓰기는 하지 않는다).
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun ChargeScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var version by remember { mutableIntStateOf(0) }
    var lookup by remember { mutableStateOf<ChargeLimit.Lookup?>(null) }
    var battery by remember { mutableStateOf(ChargeLimit.battery(context)) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) { version++; battery = ChargeLimit.battery(context); onPauseOrDispose { } }
    // 화면을 보는 동안 충전기를 꽂고 빼거나 잔량이 바뀌면 바로 다시 읽는다
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) { battery = ChargeLimit.battery(context) }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(version, ready) { lookup = if (ready) withContext(Dispatchers.IO) { ChargeLimit.find() } else ChargeLimit.Lookup.NoShizuku }

    val simulated = BuildConfig.LAB && lookup == ChargeLimit.Lookup.Missing
    val found = (lookup as? ChargeLimit.Lookup.Present)?.found ?: if (simulated) ChargeLimit.Found("global", 1, 85) else null
    // 알 수 없는 값이면 아무것도 고르지 않은 상태(null)로 둔다
    var choice by remember(found) { mutableStateOf(found?.let { ChargeLimit.choiceOf(it.mode) }) }
    var limit by remember(found) { mutableIntStateOf(found?.limit ?: 85) }

    fun apply(next: Choice, nextLimit: Int) {
        haptics.segment()
        choice = next; limit = nextLimit
        if (simulated) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ChargeLimit.set(context, next, nextLimit, "고르기") }
            busy = false
            version++
            if (ok) haptics.snap() else { haptics.reject(); Toast.makeText(context, Copy.CHARGE_TOAST_FAILED, Toast.LENGTH_SHORT).show() }
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
                    Text(Copy.CHARGE_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.CHARGE_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    // 설정이 없거나 값을 모르면 어느 방식도 강조하지 않는 그림
                    ChargeArt(choice, limit)
                    Spacer(Modifier.height(Gap.Tight))
                    Text(
                        if (battery.present) Copy.chargeBattery(
                            battery.level,
                            when {
                                battery.charging -> Copy.CHARGE_STATE_CHARGING
                                // 충전하지 않는 까닭은 온도 보호·약한 충전기 등도 있어, 잔량이 상한 근처일 때만 상한으로 보인다고 쓴다
                                battery.held && choice == Choice.MAXIMUM && kotlin.math.abs(battery.level - limit) <= 1 -> Copy.CHARGE_STATE_AT_LIMIT
                                battery.held -> Copy.CHARGE_STATE_HELD
                                else -> null
                            },
                            battery.tempC,
                        ) else Copy.CHARGE_NO_BATTERY,
                        color = c.ink, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        when {
            !ready -> item(key = "connect") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.CHARGE_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.CHARGE_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.CHARGE_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            found == null && lookup != null -> item(key = "missing") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.CHARGE_MISSING_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.CHARGE_MISSING_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (found != null) item(key = "choice") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CHARGE_PICK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(
                        when {
                            simulated -> Copy.CHARGE_SIMULATED
                            choice == null -> Copy.chargeUnknown(found.mode)
                            else -> Copy.CHARGE_PICK_BODY
                        },
                        color = c.muted, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Gap.Tight))
                    Column(Modifier.selectableGroup()) {
                        ChargeLimit.choicesFor(found).forEach { ch ->
                            LiquidRadioRow(ch.label, ch.desc, choice == ch, enabled = !busy) { if (ch != choice) apply(ch, limit) }
                        }
                    }
                    // 상한: 최대일 때만, 이 기기에 상한 키가 있을 때만(이전 One UI는 85% 하나)
                    if (choice == Choice.MAXIMUM && found.limit != null) {
                        Spacer(Modifier.height(Gap.Item))
                        Text(Copy.CHARGE_LIMIT_TITLE, color = c.ink, modifier = Modifier.padding(start = 36.dp))
                        Spacer(Modifier.height(Gap.Tight))
                        LiquidChips(
                            ChargeLimit.LIMITS, ChargeLimit.nearest(limit), { "$it%" }, { if (it != limit) apply(Choice.MAXIMUM, it) },
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    }
                }
            }
        }
        item(key = "settings") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CHARGE_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.CHARGE_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.CHARGE_OPEN_SETTINGS, primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                        val intent = ChargeLimit.settingsIntent(context)
                        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) {
                            Toast.makeText(context, Copy.CHARGE_TOAST_NO_SETTINGS, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun ChargeHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 왜 충전을 멈추나요",
            "리튬 배터리는 100%로 가득 찬 채 오래 있거나 뜨거울 때 부담이 큽니다. 80~95%에서 충전을 멈추면 하루에 쓸 양은 조금 줄지만 " +
                "배터리 부담을 덜 수 있습니다. 충전기에 꽂힌 채 잔량이 상한 근처에서 '충전하지 않음'으로 바뀌면 상한에서 멈춘 것으로 볼 수 있습니다. " +
                "충전하지 않는 까닭은 온도 보호나 약한 충전기일 수도 있습니다.",
        ) { ChargeArt(Choice.MAXIMUM, 85) }
        HelpTopic(
            "2. 세 가지 방식",
            "'끔'은 100%까지 충전합니다. '기본'은 100%까지 채운 뒤 95% 근처로 내려왔다가 다시 채워 가득 찬 채 머무는 시간을 줄입니다. " +
                "'최대'는 정한 상한(80·85·90·95%)에서 충전을 멈춥니다. 이전 One UI는 '끔'과 '최대'(85%)만 있습니다.",
        )
        HelpTopic(
            "3. 이 기기에서만 바꾸는 이유",
            "배터리 보호는 삼성이 공개하지 않은 설정이라 기기와 One UI 버전마다 위치가 다릅니다. GOAT는 이 기기에 설정이 이미 있을 때만 바꾸고, " +
                "없으면 새로 만들지 않습니다. 바꾼 뒤 '배터리 설정 열기'로 삼성 설정에 같은 값이 보이는지 확인해 주세요.",
        )
        HelpTopic(
            "4. 권한과 되돌리기",
            "Shizuku 셸 권한으로 읽고 바꿉니다. '끔'을 고르거나 삼성 설정 > 배터리 > 배터리 보호에서 언제든 되돌릴 수 있습니다.",
        )
    }
}
