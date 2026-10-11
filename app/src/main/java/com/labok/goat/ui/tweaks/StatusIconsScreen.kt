package com.labok.goat.ui.tweaks

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.data.tweaks.StatusIcons
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy

/**
 * 상태 표시줄 아이콘 숨기기: 숨길 아이콘(자주 숨기는 것·연결 표시 둘로 나눔) 스위치, 재부팅 뒤 다시 숨기기, 모두 보이기.
 * 스위치를 켜면 상태 표시줄에서 바로 사라진다. 다른 곳에서 바꿔도 돌아올 때 다시 읽는다.
 */
@Composable
internal fun StatusIconsScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val hidden = remember(version) { StatusIcons.hidden(context) }
    var keep by remember { mutableStateOf(StatusIcons.keep(context)) }
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }

    // 한 번에 설정 하나만 바꾸는 가벼운 쓰기라 바로 부른다(DNS·주사율처럼 셸·연결 확인이 없다)
    fun toggle(icon: StatusIcons.Icon, hide: Boolean) {
        val ok = StatusIcons.set(context, icon, hide, "스위치")
        version++
        if (!ok) Toast.makeText(context, Copy.ICONS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
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
                    Text(Copy.ICONS_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.ICONS_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    StatusIconsArt(hidden)
                }
            }
        }
        if (!permitted) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.ICONS_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        listOf(false to (Copy.ICONS_COMMON_TITLE to Copy.ICONS_COMMON_BODY), true to (Copy.ICONS_CAREFUL_TITLE to Copy.ICONS_CAREFUL_BODY))
            .forEach { (careful, texts) ->
                item(key = "icons:$careful") {
                    Box(Modifier.widthIn(max = ContentMaxWidth)) {
                        GlassCard {
                            Text(texts.first, fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(texts.second, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            StatusIcons.Icon.entries.filter { it.careful == careful }.forEach { icon ->
                                Spacer(Modifier.height(Gap.Item))
                                LiquidSwitchRow(icon.label, icon.desc, icon in hidden, permitted) { toggle(icon, it) }
                            }
                        }
                    }
                }
            }
        item(key = "keep") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.ICONS_KEEP_TITLE, Copy.ICONS_KEEP_BODY, keep, enabled = true) { on -> keep = on; StatusIcons.setKeep(context, on) }
                    if (hidden.isNotEmpty()) {
                        Spacer(Modifier.height(Gap.Section))
                        LiquidButton(Copy.ICONS_RESTORE, primary = false, enabled = permitted, modifier = Modifier.fillMaxWidth(), onClick = {
                            val ok = StatusIcons.restore(context, "모두 보이기 버튼")
                            version++
                            Toast.makeText(context, if (ok) Copy.ICONS_TOAST_RESTORED else Copy.ICONS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun StatusIconsHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "NFC·알람·블루투스처럼 늘 켜 두는 기능의 아이콘을 상태 표시줄에서 숨깁니다. 아이콘만 숨길 뿐 기능은 그대로 켜져 동작하고, " +
                "알림 아이콘과 시계·배터리·신호는 그대로 둡니다.",
        ) { StatusIconsArt(setOf(StatusIcons.Icon.NFC, StatusIcons.Icon.ALARM)) }
        HelpTopic(
            "2. 바로 적용과 되돌리기",
            "스위치를 켜는 순간 사라지고 끄면 다시 나타납니다. " +
                "안드로이드의 시스템 UI 튜너와 같은 설정을 쓰므로 Good Lock 등 다른 앱의 숨김 설정과 서로 영향을 줍니다. " +
                "'모두 보이기'는 GOAT가 숨긴 것만 되돌리고 다른 앱이 숨긴 것은 그대로 둡니다.",
        )
        HelpTopic(
            "3. 재부팅 뒤",
            "일부 갤럭시는 재부팅하면 숨김이 모두 풀립니다. '재부팅 뒤 다시 숨기기'를 켜 두면 부팅 직후 숨김이 모두 풀린 경우에만 다시 숨깁니다. " +
                "다른 앱에서 일부를 다시 보이게 한 것은 덮어쓰지 않습니다. 숨긴 것을 모두 다시 보이게 하려면 '모두 보이기'를 쓰세요.",
        )
        HelpTopic(
            "4. 숨겨지지 않는 아이콘",
            "기기와 One UI 버전마다 아이콘 이름이 달라, 일부 아이콘은 스위치를 켜도 사라지지 않을 수 있습니다. 그 경우 다른 기능에는 영향이 없습니다. " +
                "위치·핫스팟·VPN처럼 개인정보와 관련된 표시는 꼭 필요할 때만 숨기세요.",
        )
        HelpTopic("5. 권한", "고급 화면 배율 설정과 같은 시스템 설정 권한으로 바꿉니다. 이 권한을 처음 받을 때만 Shizuku나 PC가 한 번 필요합니다.")
    }
}
