package com.local.folddpifix.ui.shizuku

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.local.folddpifix.data.shizuku.ShizukuAccess
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LocalLiquid

/**
 * Shizuku 설정 안내: 단계마다 버튼 하나로 진행하고, 끝난 단계는 돌아올 때마다 자동으로 확인해 체크한다.
 * ① 설치 → ② 무선 디버깅 켜기(FoldFit이 가진 WRITE_SECURE_SETTINGS로 바로 켬) → ③ Shizuku 페어링·시작 → ④ FoldFit 허용.
 * 마지막에 재부팅 뒤 자동 시작(Shizuku 설정 '부팅 시 시작')을 알려 준다.
 */
@Composable
internal fun ShizukuGuide(
    state: ShizukuAccess.State,
    intro: String = "PC 없이 쓰려면 무료 앱 Shizuku가 필요합니다. 아래 순서대로 버튼만 누르면 됩니다. 처음 한 번만 하면 됩니다.",
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
    val wireless = remember(tick, state) { running || ShizukuAccess.wirelessDebugOn(context) }
    val done = listOf(installed, wireless, running, state == ShizukuAccess.State.READY)
    val current = done.indexOfFirst { !it }.let { if (it < 0) done.size else it }
    var note by remember { mutableIntStateOf(0) }  // 1: 무선 디버깅을 직접 켜야 함
    var asked by remember { mutableStateOf(false) }
    // 자동으로 할 수 있는 단계는 차례가 오면 바로 한다: 무선 디버깅 켜기, FoldFit 허용 창 띄우기
    LaunchedEffect(current) {
        if (current == 1) {
            if (ShizukuAccess.enableWirelessDebug(context)) tick++ else note = 1
        }
    }
    LaunchedEffect(state) {
        if (state == ShizukuAccess.State.NEEDS_PERMISSION && !asked) { asked = true; ShizukuAccess.requestPermission() }
    }

    Text(intro, color = c.muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Step(1, "Shizuku 설치", done[0], current == 0) {
        Hint("무료 앱입니다. 설치가 끝나면 이 화면으로 돌아오세요.")
        Action("설치하기") { openStore(context) }
    }
    Step(2, "무선 디버깅 켜기", done[1], current == 1) {
        Hint("Shizuku가 폰 안에서 권한을 얻는 통로입니다. Wi-Fi에 연결돼 있어야 하고, '이 네트워크에서 허용할까요?'가 뜨면 허용을 누르세요.")
        if (note == 1) Hint("개발자 옵션에서 '무선 디버깅'을 직접 켜고 돌아와 주세요.", warn = true)
        Action("켜기") {
            if (ShizukuAccess.enableWirelessDebug(context)) { tick++ } else { note = 1; openDeveloper(context) }
        }
    }
    Step(3, "Shizuku 페어링하고 시작", done[2], current == 2) {
        Hint(
            "① [Shizuku 열기] → '무선 디버깅으로 시작'의 [페어링]을 누릅니다. 알림 허용을 물으면 허용합니다.\n" +
                "② [무선 디버깅 화면] → '페어링 코드로 기기 페어링'을 누르면 6자리 숫자가 나옵니다.\n" +
                "③ 화면 위에서 Shizuku 알림을 내려 그 숫자를 입력합니다.\n" +
                "④ Shizuku로 돌아가 [시작]을 누른 뒤 이 화면으로 돌아오세요.",
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton("Shizuku 열기", modifier = Modifier.weight(1f), onClick = { openShizuku(context) })
            LiquidButton("무선 디버깅 화면", modifier = Modifier.weight(1f), primary = false, onClick = { openDeveloper(context) })
        }
    }
    Step(4, "FoldFit 허용", done[3], current == 3) {
        Hint("Shizuku가 묻는 창에서 '항상 허용'을 누르세요.")
        Action("허용하기") { ShizukuAccess.requestPermission() }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "무선 디버깅 켜기와 허용 창 띄우기는 차례가 오면 자동으로 합니다. 페어링만 직접 해 주세요. " +
            "끝나면 재부팅 뒤에도 Shizuku가 Wi-Fi에서 스스로 다시 켜집니다.",
        color = c.muted, style = MaterialTheme.typography.labelSmall,
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
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$no", color = if (done || active) c.onInk else c.muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                title + if (done) " · 완료" else "",
                color = if (done) c.muted else c.ink,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (active) {
                Spacer(Modifier.height(4.dp))
                body()
            }
        }
    }
}

@Composable
private fun Hint(text: String, warn: Boolean = false) {
    val c = LocalLiquid.current
    Text(text, color = if (warn) c.danger else c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    LiquidButton(label, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onClick)
}

private fun openStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${ShizukuAccess.PACKAGE}"))
    val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/"))
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}

private fun openShizuku(context: Context) {
    context.packageManager.getLaunchIntentForPackage(ShizukuAccess.PACKAGE)?.let { runCatching { context.startActivity(it) } }
}

private fun openDeveloper(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
}
