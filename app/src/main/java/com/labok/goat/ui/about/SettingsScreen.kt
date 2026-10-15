package com.labok.goat.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.BuildConfig
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.components.rowListEdges
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiHaptics
import com.labok.goat.ui.liquid.UiInk
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy

/**
 * 앱 설정(사이드바 맨 아래). 위는 '설정'(업데이트·UI 유연성·햅틱 세기·기록 파일), 아래는 '정보'(앱 정보·변경 기록·필요 권한)로 나눈다.
 * UI 유연성은 조절바를 움직이는 동안 바로 반영돼, 아래 미리 보기 스위치와 조절바 자신의 물방울로 느낌을 확인한다.
 * @param onAbout 앱 정보 시트를 연다.
 */
@Composable
internal fun SettingsScreen(contentPadding: PaddingValues, onAbout: () -> Unit, onChangelog: () -> Unit, onPermissions: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "settingsTitle") { SectionTitle(Copy.SETTINGS_SECTION) }
        item(key = "update") { Box(Modifier.widthIn(max = ContentMaxWidth)) { UpdateCard(manual = true) } }
        item(key = "motion") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.MOTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.MOTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    var v by remember { mutableIntStateOf(UiMotion.percent) }
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = v,
                        range = 0..UiMotion.MAX_PERCENT,
                        onValueChange = {
                            v = it
                            UiMotion.set(context, it)
                        },
                        onValueChangeFinished = { UiMotion.set(context, v) },
                        valueText = { "$it%" },
                        description = Copy.MOTION_TITLE
                    )
                    var demo by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Copy.MOTION_PREVIEW, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        LiquidSwitch(demo, { demo = it }, label = Copy.MOTION_PREVIEW)
                    }
                }
            }
        }
        item(key = "speed") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.SPEED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.SPEED_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    var sp by remember { mutableIntStateOf(UiMotion.speedPercent) }
                    // 조절바 손잡이 방울도 바뀐 속도로 움직여 바로 느껴 본다
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = sp,
                        range = UiMotion.SPEED_MIN..UiMotion.SPEED_MAX,
                        onValueChange = {
                            sp = it
                            UiMotion.setSpeed(context, it)
                        },
                        onValueChangeFinished = { UiMotion.setSpeed(context, sp) },
                        valueText = { "$it%" },
                        description = Copy.SPEED_TITLE
                    )
                }
            }
        }
        item(key = "ink") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    NewTag(NewBadge.INK_TONES, Modifier.padding(bottom = Gap.Item))
                    Text(Copy.INK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.INK_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidChips(
                        options = UiInk.Mode.entries,
                        selected = UiInk.mode,
                        label = { if (it == UiInk.Mode.THEME) Copy.INK_THEME else Copy.INK_DEFAULT },
                        onSelect = { UiInk.set(context, it) }
                    )
                    // 갤럭시 테마일 때: 색상 팔레트의 강조색 1~3을 진하게·중간 두 단계로 보여 주고 하나를 고른다
                    AnimatedVisibility(UiInk.mode == UiInk.Mode.THEME) {
                        Column {
                            Spacer(Modifier.height(Gap.Item))
                            InkTones()
                        }
                    }
                }
            }
        }
        item(key = "haptic") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.HAPTIC_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HAPTIC_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    var h by remember { mutableIntStateOf(UiHaptics.level) }
                    // 조절바 칸이 바뀔 때마다 그 세기로 틱이 울려(조절바의 기본 동작) 바로 느껴 본다
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSlider(
                        value = h,
                        range = 0..UiHaptics.MAX,
                        onValueChange = {
                            h = it
                            UiHaptics.set(context, it)
                        },
                        onValueChangeFinished = { UiHaptics.set(context, h) },
                        valueText = { if (it == 0) "끔" else "$it%" },
                        description = Copy.HAPTIC_TITLE
                    )
                }
            }
        }
        // Shizuku: 무선 디버깅은 Shizuku를 시작할 때만 필요하다. 여러 기능이 같이 쓰는 연결이라 앱 설정에 둔다.
        item(key = "shizuku") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                var autoOff by remember { mutableStateOf(ShizukuAccess.autoOffEnabled(context)) }
                GlassCard {
                    LiquidSwitchRow(Copy.APP_SIZE_AUTO_OFF_TITLE, Copy.APP_SIZE_AUTO_OFF_BODY, autoOff, true) { on ->
                        autoOff = on
                        ShizukuAccess.setAutoOff(context, on)
                    }
                    // 이 기기에서 무선 디버깅을 끄자 Shizuku가 멈췄으면 GOAT가 옵션을 스스로 껐다는 것을 알린다
                    if (ShizukuAccess.autoOffStopsShizuku(context)) {
                        Spacer(Modifier.height(Gap.Item))
                        Text(Copy.AUTO_OFF_STOPS_SHIZUKU, color = c.danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(key = "logfile") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                var logFile by remember { mutableStateOf(PublicLogFile.enabled(context)) }
                GlassCard {
                    LiquidSwitchRow(Copy.LOG_FILE_TITLE, Copy.LOG_FILE_BODY, logFile, true) { on ->
                        logFile = on
                        PublicLogFile.setEnabled(context, on)
                    }
                }
            }
        }
        item(key = "infoTitle") { SectionTitle(Copy.INFO_SECTION) }
        item(key = "info") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Column(Modifier.rowListEdges()) {
                        val perms = AppPermissions.all
                        InfoLink(Copy.MENU_ABOUT, "GOAT ${BuildConfig.VERSION_NAME} · 라이선스·문의", onAbout)
                        InfoLink(Copy.INFO_CHANGELOG, CHANGELOG.first().let { "최근 ${it.version.removePrefix("GOAT ")} · ${it.date}" }, onChangelog)
                        InfoLink(Copy.INFO_PERMISSIONS, "권한 ${perms.size}개 중 ${perms.count { AppPermissions.granted(context, it) }}개 허용됨", onPermissions)
                    }
                }
            }
        }
    }
}

/** 묶음 제목(설정·정보). */
@Composable
private fun SectionTitle(text: String) {
    val c = LocalLiquid.current
    Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
        Text(text, color = c.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    }
}

/** 정보 항목: 제목과 세부 한 줄, 누르면 시트를 연다(오른쪽 화살표). */
@Composable
private fun InfoLink(label: String, detail: String, onClick: () -> Unit) {
    val c = LocalLiquid.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = Gap.RowPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.muted)
    }
}

/**
 * 갤럭시 색상 팔레트에서 잉크 색 고르기: 강조색마다 진하게·중간 두 칸을 가운데 정렬로 놓는다.
 * 고른 칸 뒤에는 그 색의 물방울(칩과 같은 [rememberSoftBlob])이 깔리고, 다른 칸을 고르면 출렁이며 미끄러져 간다.
 */
@Composable
private fun InkTones() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val reduce = LocalReduceMotion.current
    val haptics = rememberLiquidHaptics()
    val tones = UiInk.Tone.ALL
    val index = tones.indexOf(UiInk.tone).coerceAtLeast(0)
    val centers = remember { mutableStateListOf<Float>().apply { repeat(tones.size) { add(0f) } } }
    val blob = rememberSoftBlob()
    val path = remember { Path() }
    var rowH by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableIntStateOf(-1) }
    val halo = with(LocalDensity.current) { 46.dp.toPx() }
    val cx = centers[index]
    LaunchedEffect(index, cx, rowH) {
        if (cx <= 0f || rowH <= 0f) return@LaunchedEffect
        blob.moveTo(cx, rowH / 2, halo, halo, animate = placed != -1 && placed != index && !reduce)
        placed = index
    }
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        Canvas(Modifier.matchParentSize().onSizeChanged { rowH = it.height.toFloat() }) {
            blob.frame // 프레임마다 다시 그리기
            if (blob.blob.ready) drawPath(blob.blob.path(path), c.ink.copy(alpha = 0.35f))
        }
        Row(
            Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(Gap.Item, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tones.forEachIndexed { i, t ->
                val color = colorResource(t.colorRes(dark))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(34.dp)
                        .onGloballyPositioned { centers[i] = it.positionInParent().x + it.size.width / 2f }
                        .liquidPress(interaction, releaseHaptic = false)
                        .clip(CircleShape)
                        .background(color)
                        .clickable(interaction, indication = null) {
                            if (i != index) haptics.segment()
                            UiInk.setTone(context, t)
                        }
                        .semantics {
                            selected = i == index
                            contentDescription = "${Copy.INK_TONE} 강조색 ${t.accent} ${if (t.deep) "진하게" else "중간"}"
                        }
                )
            }
        }
    }
}
