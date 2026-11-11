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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.core.R
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.Radius
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy

/**
 * 애니메이션 속도: 한 번에 고르는 값(끔·0.5배·0.75배·기본·1.5배)과, 펼치면 셋을 따로 0~2배(0.05 단위)로 맞추는 조절바.
 * 다른 곳(개발자 옵션)에서 바꿔도 돌아올 때 다시 읽는다.
 */
@Composable
fun AnimationScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val values = remember(version) { AnimationSpeed.Kind.entries.associateWith { AnimationSpeed.read(context, it) } }
    var detail by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        version++
        onPauseOrDispose { }
    }
    // 셋이 같으면 그 값이 고른 값, 다르면 '직접'
    val same = values.values.distinct().singleOrNull()

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.ANIM_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.ANIM_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    // 셋을 따로 맞췄으면 기본(1배)으로 그리지 않고, 그림이 보여 주는 앱 열기에 걸리는 화면 전환 배율을 그린다
                    // 이때 그림 아래에 「전환」 이름을 붙여, 창 값만 바꾼 사용자가 반영이 안 된 줄 알지 않게 한다
                    SpeedArt(same ?: values.getValue(AnimationSpeed.Kind.TRANSITION), name = if (same == null) AnimationSpeed.Kind.TRANSITION.short else null)
                }
            }
        }
        if (!permitted) {
            item(key = "permission") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.TODO_SETUP_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item(key = "presets") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.ANIM_PRESET_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(
                        if (same == null) {
                            // 셋을 따로 맞춘 때는 어느 값이 어느 항목인지 짧은 이름을 붙인다(창·전환·움직임)
                            stringResource(R.string.tweaks_anim_now_custom_fmt, AnimationSpeed.Kind.entries.joinToString(" · ") { "${it.short} ${AnimationSpeed.label(values.getValue(it))}" })
                        } else {
                            stringResource(R.string.tweaks_anim_now_fmt, AnimationSpeed.label(same))
                        },
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    // 셋을 함께 0~2배(0.05 단위)로. 셋이 다르면 창 애니메이션 값에서 시작한다.
                    var all by remember(version) { mutableIntStateOf(Math.round((same ?: values.getValue(AnimationSpeed.Kind.WINDOW)) * 20)) }
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = all,
                        range = 0..40,
                        onValueChange = { all = it },
                        onValueChangeFinished = {
                            if (permitted) {
                                AnimationSpeed.setAll(context, all / 20f, "슬라이더")
                                version++
                            }
                        },
                        valueText = { AnimationSpeed.label(it / 20f) },
                        description = Copy.ANIM_PRESET_TITLE,
                        enabled = permitted
                    )
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.ANIM_SLIDER_HINT, color = c.muted, style = MaterialTheme.typography.labelSmall)
                    Text(Copy.ANIM_KEEP_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(Gap.Section))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Row)).clickable { detail = !detail },
                        verticalAlignment = Alignment.CenterVertically
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
                                Spacer(Modifier.height(Gap.Item))
                                LiquidSlider(
                                    value = v,
                                    range = 0..40,
                                    onValueChange = { v = it },
                                    onValueChangeFinished = {
                                        if (permitted) {
                                            AnimationSpeed.set(context, kind, v / 20f, "슬라이더")
                                            version++
                                        }
                                    },
                                    valueText = { AnimationSpeed.label(it / 20f) },
                                    description = kind.label,
                                    enabled = permitted
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
fun AnimationHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(
            stringResource(R.string.tweaks_help_what_title),
            stringResource(R.string.tweaks_anim_help_what_body)
        ) { SpeedArt(0.5f) }
        HelpTopic(
            stringResource(R.string.tweaks_anim_help_three_title),
            stringResource(R.string.tweaks_anim_help_three_body_fmt, Copy.ANIM_DETAIL_TITLE)
        )
        HelpTopic(
            stringResource(R.string.tweaks_anim_help_off_title),
            stringResource(R.string.tweaks_anim_help_off_body)
        )
        HelpTopic(
            stringResource(R.string.tweaks_anim_help_permission_title),
            stringResource(R.string.tweaks_anim_help_permission_body)
        )
    }
}
