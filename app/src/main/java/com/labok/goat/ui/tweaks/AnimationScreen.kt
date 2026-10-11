package com.labok.goat.ui.tweaks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy

/**
 * 애니메이션 속도: 한 번에 고르는 값(끔·0.5배·0.75배·기본·1.5배)과, 펼치면 셋을 따로 0~2배(0.05 단위)로 맞추는 조절바.
 * 다른 곳(개발자 옵션)에서 바꿔도 돌아올 때 다시 읽는다.
 */
@Composable
internal fun AnimationScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val values = remember(version) { AnimationSpeed.Kind.entries.associateWith { AnimationSpeed.read(context, it) } }
    var detail by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }
    // 셋이 같으면 그 값이 고른 값, 다르면 '직접'
    val same = values.values.distinct().singleOrNull()

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.ANIM_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.ANIM_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    SpeedArt(same ?: 1f)
                }
            }
        }
        if (!permitted) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.ANIM_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    com.labok.goat.ui.liquid.LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "presets") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.ANIM_PRESET_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(
                        if (same == null) "지금: 직접 맞춤(${AnimationSpeed.Kind.entries.joinToString(" · ") { AnimationSpeed.label(values.getValue(it)) }})"
                        else "지금: ${AnimationSpeed.label(same)}",
                        color = c.muted, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Gap.Item))
                    LiquidChips(
                        options = AnimationSpeed.PRESETS,
                        selected = same ?: -1f,
                        label = { AnimationSpeed.label(it) },
                        onSelect = { v -> if (permitted) { AnimationSpeed.setAll(context, v, "고르기"); version++ } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Gap.Section))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { detail = !detail },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Copy.ANIM_DETAIL_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(Copy.ANIM_DETAIL_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        val turn by animateFloatAsState(if (detail) 180f else 0f, label = "chevron")
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
                    }
                    AnimatedVisibility(detail, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            AnimationSpeed.Kind.entries.forEach { kind ->
                                Spacer(Modifier.height(Gap.Item))
                                Text(kind.label, color = c.ink)
                                var v by remember(version) { mutableIntStateOf(Math.round(values.getValue(kind) * 20)) }
                                LiquidSlider(
                                    value = v, range = 0..40, onValueChange = { v = it },
                                    onValueChangeFinished = { if (permitted) { AnimationSpeed.set(context, kind, v / 20f, "조절바"); version++ } },
                                    valueText = { AnimationSpeed.label(it / 20f) }, description = kind.label, enabled = permitted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun AnimationHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "앱을 열고 닫거나 화면이 바뀔 때의 움직임 속도를 바꿉니다. 0.5배로 하면 움직임이 절반 시간에 끝나 폰이 더 빠르게 느껴집니다. " +
                "커뮤니티에서 가장 많이 쓰는 값은 0.5배입니다.",
        ) { SpeedArt(0.5f) }
        HelpTopic(
            "2. 세 가지 설정",
            "개발자 옵션의 '창 애니메이션 배율', '전환 애니메이션 배율', 'Animator 길이 배율'과 같습니다. 한 번에 고르면 셋이 같이 바뀌고, " +
                "'하나씩 맞추기'를 펼치면 따로 맞출 수 있습니다.",
        )
        HelpTopic(
            "3. 끔과 주의",
            "'끔'은 움직임 없이 바로 바뀝니다. 너무 빠르게 하면 일부 앱에서 화면이 깜빡이거나 멈춘 것처럼 보일 수 있습니다. 그럴 때는 0.5배나 기본으로 돌리세요. " +
                "GOAT의 물방울 움직임도 이 설정을 따릅니다.",
        )
        HelpTopic(
            "4. 권한과 유지",
            "화면 배율 동기화와 같은 시스템 설정 권한으로 바꾸고, 재부팅해도 유지됩니다.",
        )
    }
}
