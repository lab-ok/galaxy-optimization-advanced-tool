package com.labok.goat.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text

/** 넓은 내부 화면에서도 읽기 좋은 카드 최대 폭(모든 기능 화면 공통). */
internal val ContentMaxWidth = 640.dp

/** 기능 화면 목록의 여백: 좌우 16dp, 제목 줄·내비게이션 막대 아래로 내용이 비치도록 위아래는 안쪽 여백에 더한다. */
internal fun featurePadding(inner: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = inner.calculateTopPadding() + 4.dp,
    bottom = inner.calculateBottomPadding() + 28.dp
)

/** 아래에서 올라오는 시트의 공통 골격: 세로 스크롤, 좌우 20dp, 내비게이션 막대 위로 28dp. [title]이 있으면 맨 위에 큰 제목. */
@Composable
internal fun SheetColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LocalLiquid.current.ink)
            Spacer(Modifier.height(Gap.Title))
        }
        content()
    }
}

/** 되돌리기 어려운 동작을 한 번 더 묻는 빨간 상자: "⚠ [message]", 설명([note]), 그 아래 버튼 줄([actions]). */
@Composable
internal fun DangerConfirmBox(message: String, note: String? = null, actions: @Composable RowScope.() -> Unit) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.danger.copy(alpha = 0.08f), shape)
            .border(1.dp, c.danger.copy(alpha = 0.5f), shape)
            .padding(14.dp)
    ) {
        Text("⚠ $message", color = c.danger, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        if (note != null) Text(note, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Card))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/**
 * 글자를 클립보드에 복사한다. Android 13부터는 시스템이 복사 확인을 띄우므로, 앱이 따로 알려야 하면(12 이하) true.
 */
internal fun copyText(context: Context, text: String, label: String = "GOAT"): Boolean {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/**
 * 설정 화면을 여는 인텐트. 설정 앱은 따로 작업(task)에서 열리는데, 이미 열려 있던 설정 작업에 덧붙으면 뒤로 가기가
 * GOAT가 아니라 홈 화면으로 가 버렸다(에뮬레이터에서 확인). 매번 새 작업으로 열어(CLEAR_TASK) 할 일을 마치고
 * 뒤로 가기를 누르면 GOAT로 돌아오게 한다.
 */
internal fun settingsIntent(action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

/** 개발자 옵션 화면을 연다. 개발자 옵션이 꺼져 있으면 켜는 곳(휴대전화 정보)으로 보낸다. */
internal fun openDeveloperOptions(context: Context) {
    val devOn = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    val intent = settingsIntent(if (devOn) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

/** 사용 방법 한 항목: 제목, 움직이는 그림([art]), 설명. 초보자도 그림만 보고 알 수 있게 그림을 설명 위에 둔다. */
@Composable
internal fun HelpTopic(title: String, body: String, art: (@Composable () -> Unit)? = null) {
    val c = LocalLiquid.current
    GlassCard {
        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
        if (art != null) {
            Spacer(Modifier.height(Gap.Item))
            art()
        }
        Spacer(Modifier.height(Gap.Item))
        Text(body, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(Gap.Card))
}
