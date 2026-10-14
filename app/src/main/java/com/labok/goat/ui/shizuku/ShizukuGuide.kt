package com.labok.goat.ui.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.openDeveloperOptions
import com.labok.goat.ui.components.settingsIntent
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text

/**
 * Shizuku 설정 안내: 단계마다 버튼 하나로 진행하고, 끝난 단계는 돌아올 때마다 자동으로 확인해 체크한다.
 * ① 개발자 옵션 켜기(빌드 번호 7번) → ② 설치 → ③ 무선 디버깅 켜기(GOAT가 가진 WRITE_SECURE_SETTINGS로 바로 켬)
 * → ④ Shizuku 페어링·시작 → ⑤ GOAT 허용. 개발자 옵션은 무선 디버깅의 전제라 맨 앞에 둔다(초보자가 가장 많이 막힌 곳:
 * 무선 디버깅 [켜기]가 휴대전화 정보로만 보내고 무엇을 할지 알려 주지 않았다).
 * 마지막에 재부팅 뒤 자동 시작(Shizuku 설정 '부팅 시 시작')을 알려 준다.
 */
@Composable
internal fun ShizukuGuide(
    state: ShizukuAccess.State,
    intro: String
) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        ShizukuAccess.watch(context)
        tick++
        onPauseOrDispose { }
    }
    val installed = state != ShizukuAccess.State.NOT_INSTALLED
    val running = state == ShizukuAccess.State.NEEDS_PERMISSION || state == ShizukuAccess.State.READY
    // 시스템 설정 값이라 화면으로 돌아올 때(tick)마다 다시 읽는다
    val devOn = remember(tick, state) { running || developerOptionsOn(context) }
    val wireless = remember(tick, state) { running || ShizukuAccess.wirelessDebugOn(context) }
    val done = listOf(devOn, installed, wireless, running, state == ShizukuAccess.State.READY)
    val current = done.indexOfFirst { !it }.let { if (it < 0) done.size else it }
    var note by remember { mutableIntStateOf(0) } // 1: 무선 디버깅을 직접 켜야 함
    var asked by remember { mutableStateOf(false) }
    // 허용 창은 차례가 오면 바로 띄운다. 무선 디버깅은 기기 설정이라 사용자가 [켜기]를 눌렀을 때만 켠다
    // (화면을 열 때마다 자동으로 켜면 사용자가 꺼도 다시 켜진다).
    LaunchedEffect(state) {
        if (state == ShizukuAccess.State.NEEDS_PERMISSION && !asked) {
            asked = true
            ShizukuAccess.requestPermission()
        }
    }

    Text(intro, color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Gap.Card))
    Step(1, "개발자 옵션 켜기", done[0], current == 0) {
        DevOptionsStepArt()
        SettingsPath("설정", "휴대전화 정보", "소프트웨어 정보", "빌드 번호 7번")
        Hint(
            "① [휴대전화 정보 열기] → '소프트웨어 정보'를 누릅니다.\n" +
                "② '빌드 번호'를 빠르게 7번 누릅니다. 잠금 화면 비밀번호를 물으면 입력합니다.\n" +
                "③ \"개발자 모드를 켰습니다\"가 뜨면 이 화면으로 돌아오세요."
        )
        Hint("'빌드 번호'는 휴대전화 정보 첫 화면이 아니라 '소프트웨어 정보' 안에 있습니다.")
        Action("휴대전화 정보 열기") { openPhoneInfo(context) }
    }
    Step(2, "Shizuku 설치", done[1], current == 1) {
        StoreStepArt()
        SettingsPath("Play 스토어", "Shizuku", "설치")
        Hint("무료 앱입니다. 설치가 끝나면 이 화면으로 돌아오세요.")
        Action("설치하기") { openStore(context) }
    }
    Step(3, "무선 디버깅 켜기", done[2], current == 2) {
        WirelessStepArt()
        SettingsPath("설정", "개발자 옵션", "디버깅", "무선 디버깅")
        Hint("'개발자 옵션'은 1단계를 마치면 설정 맨 아래(휴대전화 정보 근처)에 새로 생깁니다. 그 안을 아래로 내리면 '디버깅' 묶음에 '무선 디버깅'이 있습니다.")
        Hint("Shizuku가 폰 안에서 권한을 얻는 통로입니다. '이 네트워크에서 허용할까요?'가 뜨면 허용을 누르세요.")
        // 무선 디버깅은 Wi-Fi에 연결돼 있어야만 켜진다(아니면 'Wi-Fi에 연결하세요'만 뜨고 꺼진다). 연결 상태를 읽으려면
        // 네트워크 상태 권한이 필요한데 GOAT는 쓰지 않으므로(읽으려다 앱이 종료됐다), 항상 먼저 알려 주고 Wi-Fi 설정으로 가는 길을 둔다
        Hint("Wi-Fi에 연결돼 있어야 켜집니다. 연결이 안 돼 있으면 먼저 Wi-Fi에 연결해 주세요.")
        if (note == 1) Hint("방금 열린 개발자 옵션 화면을 한참 아래로 내리면 '디버깅' 묶음에 '무선 디버깅'이 있습니다. 스위치를 켜고 돌아와 주세요.", warn = true)
        Spacer(Modifier.height(Gap.Item))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton("켜기", modifier = Modifier.weight(1f), onClick = {
                if (ShizukuAccess.enableWirelessDebug(context)) {
                    tick++
                } else {
                    note = 1
                    openDeveloperOptions(context)
                }
            })
            LiquidButton("Wi-Fi 설정", modifier = Modifier.weight(1f), primary = false, onClick = {
                runCatching { context.startActivity(settingsIntent(Settings.ACTION_WIFI_SETTINGS)) }
            })
        }
    }
    Step(4, "Shizuku 페어링하고 시작", done[3], current == 3) {
        PairingStepArt()
        SettingsPath("Shizuku", "무선 디버깅으로 시작", "페어링")
        SettingsPath("설정", "개발자 옵션", "무선 디버깅", "페어링 코드로 기기 페어링")
        SettingsPath("알림창", "Shizuku 알림", "6자리 코드 입력")
        Hint(
            "① [Shizuku 열기] → '무선 디버깅으로 시작'의 [페어링]을 누릅니다. 알림 허용을 물으면 허용합니다.\n" +
                "② [무선 디버깅 화면] → '페어링 코드로 기기 페어링'을 누르면 6자리 숫자가 나옵니다.\n" +
                "③ 화면 위에서 Shizuku 알림을 내려 그 숫자를 입력합니다.\n" +
                "④ Shizuku로 돌아가 [시작]을 누른 뒤 이 화면으로 돌아오세요."
        )
        Spacer(Modifier.height(Gap.Item))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton("Shizuku 열기", modifier = Modifier.weight(1f), onClick = { openShizuku(context) })
            LiquidButton("무선 디버깅 화면", modifier = Modifier.weight(1f), primary = false, onClick = { openDeveloperOptions(context) })
        }
    }
    Step(5, "GOAT 허용", done[4], current == 4) {
        AllowStepArt()
        SettingsPath("Shizuku 창", "항상 허용")
        Hint("Shizuku가 묻는 창에서 '항상 허용'을 누르세요.")
        Action("허용하기") { ShizukuAccess.requestPermission() }
    }
    Spacer(Modifier.height(Gap.Item))
    Text(
        "[켜기]를 누르면 GOAT가 무선 디버깅을 대신 켜고, Shizuku가 켜지면 허용 창을 바로 띄웁니다.",
        color = c.muted,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun Step(no: Int, title: String, done: Boolean, active: Boolean, body: @Composable () -> Unit) {
    val c = LocalLiquid.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .then(
                    when {
                        done || active -> Modifier.background(c.ink, CircleShape)
                        else -> Modifier.border(1.5.dp, c.line, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "✓" else "$no", color = if (done || active) c.onInk else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                title + if (done) " · 완료" else "",
                color = if (done) c.muted else c.ink,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            if (active) {
                Spacer(Modifier.height(Gap.Item))
                body()
            }
        }
    }
}

/**
 * 설정 메뉴 위치 길: "설정 › 개발자 옵션 › 디버깅 › 무선 디버깅"처럼 어디로 들어가야 하는지 칩으로 보여 준다.
 * 마지막 칸(누를 곳)은 잉크로 채워 눈에 띄게 하고, 폭이 모자라면 다음 줄로 넘긴다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPath(vararg steps: String) {
    val c = LocalLiquid.current
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap.Tight),
        verticalArrangement = Arrangement.spacedBy(Gap.Tight)
    ) {
        steps.forEachIndexed { i, step ->
            val last = i == steps.lastIndex
            Box(
                Modifier
                    .then(if (last) Modifier.background(c.ink, CircleShape) else Modifier.border(1.dp, c.line, CircleShape))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(step, color = if (last) c.onInk else c.ink, style = MaterialTheme.typography.labelMedium, fontWeight = if (last) FontWeight.Bold else FontWeight.Medium)
            }
            if (!last) Text("›", color = c.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun Hint(text: String, warn: Boolean = false) {
    val c = LocalLiquid.current
    // ①②③처럼 여러 줄로 된 순서 설명은 줄마다 살짝 띄워 한 줄씩 읽히게 한다
    Column(Modifier.padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(Gap.Tight)) {
        text.split('\n').forEach { line ->
            Text(line, color = if (warn) c.danger else c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(Gap.Item))
    LiquidButton(label, modifier = Modifier.fillMaxWidth(), onClick = onClick)
}

private fun openStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuAccess.PACKAGE}"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}

private fun openShizuku(context: Context) {
    context.packageManager.getLaunchIntentForPackage(ShizukuAccess.PACKAGE)?.let { runCatching { context.startActivity(it) } }
}

/** 개발자 옵션이 켜져 있는지(빌드 번호를 7번 눌러 켬). */
private fun developerOptionsOn(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

/** 휴대전화 정보 화면(여기서 '소프트웨어 정보' → '빌드 번호')을 연다. 없으면 설정 첫 화면. */
private fun openPhoneInfo(context: Context) {
    runCatching { context.startActivity(settingsIntent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
