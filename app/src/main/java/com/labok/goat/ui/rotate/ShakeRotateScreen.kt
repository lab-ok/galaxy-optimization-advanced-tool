package com.labok.goat.ui.rotate

import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import com.labok.goat.data.rotate.ShakeHaptics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import com.labok.goat.ui.liquid.LiquidSlider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.background.ExternalChangeNotifier
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy

/**
 * 흔들어서 화면 회전: 켜기 스위치, 민감도, 그림 설명. 회전 잠금은 고급 화면 배율 설정과 같은 시스템 설정 권한을 쓴다.
 * @param onGuide 권한이 없을 때 권한 설정 방법을 연다.
 */
@Composable
internal fun ShakeRotateScreen(contentPadding: PaddingValues, onGuide: () -> Unit, onCalibrate: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ShakeRotate.canWrite(context)) }
    var on by remember { mutableStateOf(ShakeRotate.enabled(context)) }
    // 자동 맞추기 시트에서 바꾼 값도 바로 보이도록 공용 상태를 따른다
    val version by ShakeRotate.changed.collectAsStateWithLifecycle()
    var level by remember(version) { mutableIntStateOf(ShakeRotate.level(context)) }
    var pace by remember(version) { mutableIntStateOf(ShakeRotate.pace(context)) }
    var turn by remember(version) { mutableIntStateOf(ShakeRotate.turn(context)) }
    var swings by remember(version) { mutableIntStateOf(ShakeRotate.swings(context)) }
    var taps by remember(version) { mutableStateOf(ShakeRotate.rejectTaps(context)) }
    var advanced by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var vibration by remember { mutableIntStateOf(ShakeRotate.vibration(context)) }
    var autoOn by remember { mutableStateOf(ShakeRotate.autoRotateOn(context)) }

    // 빠른 설정에서 자동 회전을 켜고 끄는 것을 바로 반영한다
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            // 흔든 순간 GOAT가 잠깐 켜는 자동 회전에는 안내를 띄우지 않는다
            override fun onChange(selfChange: Boolean) { if (!ShakeRotate.turning) autoOn = ShakeRotate.autoRotateOn(context) }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun turnOn() {
        on = ShakeRotate.setEnabled(context, true)
        message = if (on) "" else "회전을 잠그지 못했습니다. 권한 설정을 확인해 주세요."
    }

    // 권한 설정에서 돌아오거나 빠른 설정에서 자동 회전을 켜고 돌아와도 바로 보이게 한다
    LifecycleResumeEffect(Unit) {
        permitted = ShakeRotate.canWrite(context)
        on = ShakeRotate.enabled(context)
        if (on) ShakeRotate.start(context)
        onPauseOrDispose { }
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
                    Text(Copy.SHAKE_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    ShakeTurnArt()
                }
            }
        }
        if (on && autoOn && permitted) item(key = "auto") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(accent = c.ink) {
                    Text(Copy.SHAKE_AUTO_ON_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_AUTO_ON_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.SHAKE_AUTO_OFF_BUTTON, onClick = { ShakeRotate.turnOffAutoRotate(context); autoOn = ShakeRotate.autoRotateOn(context) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (!permitted) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "switch") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.FEATURE_SHAKE_ROTATE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                if (on) Copy.SHAKE_ON_BODY else Copy.SHAKE_OFF_BODY,
                                color = c.muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        LiquidSwitch(on, { want ->
                            when {
                                !want -> { ShakeRotate.setEnabled(context, false); on = false }
                                else -> turnOn()
                            }
                        }, enabled = permitted)
                    }
                    AnimatedVisibility(message.isNotEmpty()) {
                        Text(message, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(Gap.Tight))
                    Text(Copy.SHAKE_NOTIFY_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(key = "sensitivity") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.SHAKE_SENSITIVITY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_SENSITIVITY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    LiquidSlider(
                        value = level, range = ShakeRotatePolicy.MIN_LEVEL..ShakeRotatePolicy.MAX_LEVEL,
                        onValueChange = { level = it },
                        onValueChangeFinished = { ShakeRotate.setLevel(context, level, "조절바") },
                        valueText = { "$it" },
                        description = Copy.SHAKE_SENSITIVITY_LABEL,
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.SHAKE_CALIBRATE, primary = false, modifier = Modifier.fillMaxWidth(), onClick = onCalibrate)
                    Spacer(Modifier.height(Gap.Section))
                    // 고급 설정(접었다 펴기): 자동 맞추기가 이 항목들도 함께 맞춘다
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { advanced = !advanced },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.SHAKE_ADVANCED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(Copy.SHAKE_ADVANCED_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        val turnArrow by animateFloatAsState(if (advanced) 180f else 0f, label = "chevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turnArrow })
                    }
                    AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(Gap.Section))
                            Text(Copy.SHAKE_SWINGS_TITLE, color = c.ink)
                            Text(Copy.SHAKE_SWINGS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            LiquidSlider(
                                value = swings, range = 2..4, onValueChange = { swings = it },
                                onValueChangeFinished = { ShakeRotate.setAdvanced(context, swings = swings, how = "조절바") },
                                valueText = { "${it}번" }, description = Copy.SHAKE_SWINGS_TITLE,
                            )
                            Text(Copy.SHAKE_PACE_TITLE, color = c.ink)
                            Text(Copy.SHAKE_PACE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            LiquidSlider(
                                value = pace, range = 1..ShakeRotatePolicy.MAX_PACE, onValueChange = { pace = it },
                                onValueChangeFinished = { ShakeRotate.setAdvanced(context, pace = pace, how = "조절바") },
                                valueText = { "$it" }, description = Copy.SHAKE_PACE_TITLE,
                            )
                            Text(Copy.SHAKE_TURN_TITLE, color = c.ink)
                            Text(Copy.SHAKE_TURN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            LiquidSlider(
                                value = turn, range = 0..ShakeRotatePolicy.MAX_TURN, onValueChange = { turn = it },
                                onValueChangeFinished = { ShakeRotate.setAdvanced(context, turn = turn, how = "조절바") },
                                valueText = { if (it == 0) "끔" else "$it" }, description = Copy.SHAKE_TURN_TITLE,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Copy.SHAKE_TAPS_TITLE, color = c.ink)
                                    Text(Copy.SHAKE_TAPS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.width(12.dp))
                                LiquidSwitch(taps, { on -> taps = on; ShakeRotate.setAdvanced(context, rejectTaps = on, how = "스위치") })
                            }
                            Spacer(Modifier.height(Gap.Item))
                            LiquidButton(Copy.SHAKE_ADVANCED_RESET, primary = false, modifier = Modifier.fillMaxWidth(), onClick = { ShakeRotate.resetAdvanced(context) })
                        }
                    }
                }
            }
        }
        item(key = "vibration") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.SHAKE_VIBRATION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SHAKE_VIBRATION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    // 값이 바뀔 때마다 그 세기로 예시(휙 … 휘힉)를 울린다
                    LiquidSlider(
                        value = vibration, range = 0..ShakeHaptics.MAX_STRENGTH,
                        onValueChange = { v ->
                            if (v != vibration) { vibration = v; ShakeHaptics.play(context, ShakeHaptics.SAMPLE, v) }
                        },
                        onValueChangeFinished = { ShakeRotate.setVibration(context, vibration) },
                        valueText = { if (it == 0) "끔" else "$it" },
                        description = Copy.SHAKE_VIBRATION_TITLE,
                    )
                }
            }
        }
    }
}


/** 사용 방법: 무엇을 하는지, 자동 회전, 누워서 볼 때, 다시 세로로, 민감도·자동 맞추기, 배터리·알림, 앱이 방향을 정할 때, 끌 때. */
@Composable
internal fun ShakeRotateHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "평소에는 화면 회전을 잠가 둡니다. 화면을 회전하려면 원하는 방향으로 기기를 든 채 흔들어 주세요. " +
                "흔드는 순간에만 자동 회전이 켜져 기기 방향에 맞게 화면이 회전하고, 곧바로 다시 잠깁니다. " +
                "세로로 든 채 흔들면 세로 방향이 유지됩니다. 회전하면 흔든 리듬에 맞춰 진동으로 알려 드립니다.",
        ) { ShakeTurnArt() }
        HelpTopic(
            "2. 자동 회전은 꺼 두세요",
            "흔들어서 화면 회전은 자동 회전을 끈 상태에서 동작합니다. 켜면 GOAT가 자동 회전을 끄고 지금 방향으로 잠급니다. " +
                "빠른 설정에서 자동 회전을 다시 켜면 흔들지 않아도 화면이 저절로 돌아가니, 이 화면에 뜨는 [자동 회전 끄기]를 눌러 주세요.",
        )
        HelpTopic(
            "3. 누워서 볼 때",
            "옆으로 누워 폰을 보면 자동 회전은 폰이 옆으로 누운 것으로 알고 화면을 가로로 돌려 버립니다. " +
                "흔들어서 화면 회전을 켜 두면 흔들기 전까지는 지금 방향 그대로입니다.",
        ) { LyingDownArt() }
        HelpTopic(
            "4. 다시 세로로 돌리기",
            "가로로 본 뒤에는 폰을 세로로 세워 든 채 흔들면 세로로 돌아옵니다. 들기만 해서는 돌아가지 않으니, 누운 자세에서 뒤척여도 화면은 그대로입니다.",
        ) { ShakeTurnArt(toLandscape = false) }
        HelpTopic(
            "5. 민감도와 자동 맞추기",
            "민감도는 1~${ShakeRotatePolicy.MAX_LEVEL}이고, 숫자가 클수록 살짝만 흔들어도 돌아갑니다. " +
                "[흔들어서 자동으로 맞추기]를 누르고 ${CALIBRATE_SECONDS}초 동안 평소 돌릴 때처럼 흔들면, 그 세기보다 조금 약하게 흔들어도 돌아가도록 맞춥니다. " +
                "걷거나 뒤척일 때 저절로 돌아가면 숫자를 낮추세요.",
        )
        HelpTopic(
            "6. 배터리와 알림",
            "흔들기는 가속도 센서로만 감지하고, 화면이 꺼져 있을 때는 센서를 쓰지 않아 배터리를 거의 쓰지 않습니다. " +
                "안드로이드는 알림이 없는 앱의 센서를 멈추므로 GOAT 알림 하나가 떠 있어야 합니다. 알림창 앱별 배율 조절을 켜 두었다면 " +
                "그 알림 하나를 같이 쓰고 따로 띄우지 않습니다. 흔들기만 켰을 때는 소리·진동 없는 조용한 알림 하나만 둡니다.",
        )
        HelpTopic(
            "7. 동영상처럼 앱이 방향을 정할 때",
            "동영상 앱의 전체 화면처럼 앱이 방향을 직접 정하면 앱이 정한 방향이 먼저입니다. 그 앱을 닫으면 잠가 둔 방향으로 돌아옵니다.",
        )
        HelpTopic(
            "8. 끄면",
            "끄면 켜기 전의 자동 회전 설정으로 되돌립니다.",
        )
    }
}

