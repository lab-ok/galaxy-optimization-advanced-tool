package com.labok.goat.ui.tweaks

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.ForceDark
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 앱 다크 모드 강제: 다크 모드 없는 앱도 어둡게, 재부팅 뒤 다시 켜기, 시스템 다크 모드 상태(강제 다크의 전제)와
 * 디스플레이 설정 바로 가기, 주의. 강제 다크는 Shizuku 셸로 바꾸고, 돌아올 때마다 다시 읽는다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun DarkScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val state by ForceDark.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var keep by remember { mutableStateOf(ForceDark.keep(context)) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        scope.launch(Dispatchers.IO) { ForceDark.read(context) }
        onPauseOrDispose { }
    }

    fun change(block: () -> Boolean, okText: String) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            busy = false
            Toast.makeText(context, if (ok) okText else Copy.DARK_TOAST_FAILED, Toast.LENGTH_SHORT).show()
        }
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
                    Text(Copy.DARK_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DARK_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    DarkArt(on = state.forced == true)
                }
            }
        }
        if (!ready) {
            item(key = "connect") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.DARK_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.DARK_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.DARK_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item(key = "switches") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(
                        Copy.DARK_FORCE_TITLE,
                        when {
                            state.forced == null -> Copy.DARK_FORCE_UNKNOWN
                            state.forced == true && !state.night -> Copy.DARK_FORCE_WAITING
                            state.forced == true -> Copy.DARK_FORCE_ON
                            else -> Copy.DARK_FORCE_OFF
                        },
                        state.forced == true,
                        ready && !busy && state.forced != null
                    ) { on -> change({ ForceDark.setForced(context, on, "스위치") }, if (on) Copy.DARK_TOAST_FORCE_ON else Copy.DARK_TOAST_FORCE_OFF) }
                    Spacer(Modifier.height(Gap.Section))
                    LiquidSwitchRow(Copy.DARK_KEEP_TITLE, Copy.DARK_KEEP_BODY, keep, true) { on ->
                        keep = on
                        ForceDark.setKeep(context, on)
                    }
                }
            }
        }
        item(key = "night") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DARK_NIGHT_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(if (state.night) Copy.DARK_NIGHT_ON else Copy.DARK_NIGHT_OFF, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    // 강제 다크를 켰는데 다크 모드가 꺼져 있을 때만 강조한다(다크 모드를 원하지 않는 사람에게 권하지 않게)
                    LiquidButton(Copy.DARK_NIGHT_OPEN, primary = state.forced == true && !state.night, modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { Toast.makeText(context, Copy.DARK_TOAST_NO_SETTINGS, Toast.LENGTH_SHORT).show() }
                    })
                }
            }
        }
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DARK_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DARK_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.DARK_CAUTION_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun DarkHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "다크 모드를 지원하지 않아 밤에도 하얗게 빛나는 앱을, 시스템이 색을 바꿔 어둡게 그리게 합니다. " +
                "개발자 옵션의 '강제 다크 모드 재정의'와 같은 기능이며, 사진과 동영상 색은 바꾸지 않습니다."
        ) { DarkArt(on = true) }
        HelpTopic(
            "2. 시스템 다크 모드가 먼저 켜져 있어야 합니다",
            "안드로이드는 시스템 다크 모드가 켜져 있을 때만 강제 다크를 적용합니다. 그래서 스위치를 켤 때 다크 모드가 꺼져 있으면 함께 켜고, " +
                "끌 때는 GOAT가 켜기 전 방식(꺼짐·자동·예약 시각)으로 되돌립니다. 이미 열려 있던 앱은 다시 열어야 바뀝니다."
        )
        HelpTopic(
            "3. 다크 모드가 없는 앱 전체에 적용됩니다",
            "앱마다 따로 고르는 공식 방법이 없어 다크 모드가 없는 앱 전체에 적용됩니다. 앱이 스스로 강제 다크를 막아 둔 경우나 " +
                "이미 다크 모드가 있는 앱은 그대로입니다."
        )
        HelpTopic(
            "4. 생길 수 있는 화면 오류",
            "강제 다크는 시스템이 다른 앱의 색을 대신 바꾸는 기능이라 앱마다 어색한 부분이 생길 수 있습니다. 예를 들어 바탕은 어두워졌는데 " +
                "상태 표시줄 글자·아이콘이 검은색으로 남아 보이지 않거나, 홈 화면 폴더 뒤에 흰 네모가 보일 수 있습니다. " +
                "다른 앱 안의 일이라 GOAT가 고칠 수 없으니, 거슬리면 스위치를 꺼 주세요. 끄면 원래대로 돌아갑니다."
        )
        HelpTopic(
            "5. 재부팅 뒤",
            "이 설정은 재부팅하면 꺼집니다. '재부팅 뒤 다시 켜기'를 켜 두면 부팅마다 한 번, Shizuku가 연결될 때 다시 켭니다. " +
                "같은 부팅 안에서 개발자 옵션으로 끈 것은 그대로 둡니다. Shizuku 셸 권한으로 바꿉니다."
        )
    }
}
