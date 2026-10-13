package com.labok.goat.ui.lab

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.data.lab.LabSetting
import com.labok.goat.data.lab.ScreenMode
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 실험실(염소를 열 번 눌러 연다). 정식 기능이 되기 전에 시험하는 기능을 둔다:
 * 비행기 모드 때 꺼지는 무선 고르기, 상태 표시줄 아이콘 숨기기, 화면 모드 바꾸기. 모두 처음 값을 남겨 [처음대로]로 되돌린다.
 */
@Composable
internal fun LabTweaksScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 설정을 바꾸면 하나 올려 카드들이 값을 다시 읽게 한다
    var version by remember { mutableIntStateOf(0) }

    /** [setting]을 [value]로 바꾸고(작업 스레드) 결과를 알린다. */
    fun write(setting: LabSetting, value: String?) = scope.launch {
        val ok = withContext(Dispatchers.IO) { setting.write(context, value) }
        version++
        if (!ok) Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
    }

    fun restore(setting: LabSetting) = scope.launch {
        val ok = withContext(Dispatchers.IO) { setting.restore(context) }
        version++
        Toast.makeText(context, if (ok) Copy.LAB_RESTORED else Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "airplane") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    val raw = remember(version) { AirplaneRadios.setting.read(context) }
                    Text(Copy.LAB_AIRPLANE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_AIRPLANE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    AirplaneRadios.Radio.entries.forEach { radio ->
                        Spacer(Modifier.height(Gap.Item))
                        LiquidSwitchRow("${radio.label} 켜 두기", Copy.LAB_AIRPLANE_ROW, AirplaneRadios.kept(raw, radio), true) { keep ->
                            write(AirplaneRadios.setting, AirplaneRadios.edit(raw, radio, keep))
                        }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = AirplaneRadios.setting.changed(context), modifier = Modifier.fillMaxWidth(), onClick = { restore(AirplaneRadios.setting) })
                }
            }
        }
        item(key = "icons") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    val raw = remember(version) { IconBlacklist.setting.read(context) }
                    Text(Copy.LAB_ICONS_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_ICONS_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    IconBlacklist.Slot.entries.forEach { slot ->
                        Spacer(Modifier.height(Gap.Item))
                        LiquidSwitchRow("${slot.label} 숨기기", slot.id, IconBlacklist.hidden(raw, slot), true) { hide ->
                            write(IconBlacklist.setting, IconBlacklist.edit(raw, slot, hide))
                        }
                    }
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = IconBlacklist.setting.changed(context), modifier = Modifier.fillMaxWidth(), onClick = { restore(IconBlacklist.setting) })
                }
            }
        }
        item(key = "screenMode") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    val setting = remember(version) { ScreenMode.active(context) }
                    val now = remember(version) { setting?.read(context) }
                    Text(Copy.LAB_SCREEN_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_SCREEN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    if (setting == null) {
                        Text(Copy.LAB_SCREEN_NONE, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("${setting.key} = ${now ?: "-"}", color = c.ink, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(Gap.Item))
                        val values = if (setting === ScreenMode.samsung) ScreenMode.SAMSUNG_VALUES.map { it to it } else ScreenMode.AOSP_VALUES
                        LiquidChips(
                            options = values.map { it.first },
                            selected = now ?: "",
                            label = { v -> values.first { it.first == v }.second },
                            onSelect = { write(setting, it) }
                        )
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.LAB_RESTORE, primary = false, enabled = setting.changed(context), modifier = Modifier.fillMaxWidth(), onClick = { restore(setting) })
                    }
                }
            }
        }
    }
}
