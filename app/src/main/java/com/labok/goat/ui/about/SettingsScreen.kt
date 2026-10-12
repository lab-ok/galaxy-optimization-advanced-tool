package com.labok.goat.ui.about

import com.labok.goat.ui.components.rowListEdges
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import com.labok.goat.ui.liquid.UiHaptics
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.labok.goat.BuildConfig
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.text.Copy
import kotlin.math.roundToInt

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
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "settingsTitle") { SectionTitle(Copy.SETTINGS_SECTION) }
        item(key = "update") { Box(Modifier.widthIn(max = ContentMaxWidth)) { UpdateCard(manual = true) } }
        item(key = "motion") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.MOTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.MOTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    var v by remember { mutableIntStateOf(UiMotion.percent) }
                    LiquidSlider(
                        value = v, range = 0..UiMotion.MAX_PERCENT,
                        onValueChange = { v = it; UiMotion.set(context, it) },
                        onValueChangeFinished = { UiMotion.set(context, v) },
                        valueText = { "$it%" }, description = Copy.MOTION_TITLE,
                    )
                    var demo by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Copy.MOTION_PREVIEW, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        LiquidSwitch(demo, { demo = it })
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
                    LiquidSlider(
                        value = h, range = 0..UiHaptics.MAX, onValueChange = { h = it; UiHaptics.set(context, it) },
                        onValueChangeFinished = { UiHaptics.set(context, h) },
                        valueText = { if (it == 0) "끔" else "$it%" }, description = Copy.HAPTIC_TITLE,
                    )
                }
            }
        }
        item(key = "logfile") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                var logFile by remember { mutableStateOf(PublicLogFile.enabled(context)) }
                GlassCard {
                    LiquidSwitchRow(Copy.LOG_FILE_TITLE, Copy.LOG_FILE_BODY, logFile, true) { on -> logFile = on; PublicLogFile.setEnabled(context, on) }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.muted)
    }
}
