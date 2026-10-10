package com.local.folddpifix.ui.components

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.liquid.LocalLiquid

/** 넓은 내부 화면에서도 읽기 좋은 카드 최대 폭(모든 기능 화면 공통). */
internal val ContentMaxWidth = 640.dp

/** 기능 화면 목록의 여백: 좌우 16dp, 제목 줄·내비게이션 막대 아래로 내용이 비치도록 위아래는 안쪽 여백에 더한다. */
internal fun featurePadding(inner: PaddingValues) = PaddingValues(
    start = 16.dp, end = 16.dp,
    top = inner.calculateTopPadding() + 4.dp,
    bottom = inner.calculateBottomPadding() + 28.dp,
)

/** 아래에서 올라오는 시트의 공통 골격: 세로 스크롤, 좌우 20dp, 내비게이션 막대 위로 28dp. [title]이 있으면 맨 위에 큰 제목. */
@Composable
internal fun SheetColumn(
    title: String? = null,
    modifier: Modifier = Modifier,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
        horizontalAlignment = horizontalAlignment,
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LocalLiquid.current.ink)
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

/** 되돌리기 어려운 동작을 한 번 더 묻는 빨간 상자: "⚠ [message]", 설명([note]), 그 아래 버튼 줄([actions]). */
@Composable
internal fun DangerConfirmBox(message: String, note: String? = null, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.danger.copy(alpha = 0.08f), shape)
            .border(1.dp, c.danger.copy(alpha = 0.5f), shape)
            .padding(14.dp),
    ) {
        Text("⚠ $message", color = c.danger, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        if (note != null) Text(note, color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

/**
 * 글자를 클립보드에 복사한다. Android 13부터는 시스템이 복사 확인을 띄우므로, 앱이 따로 알려야 하면(12 이하) true.
 */
internal fun copyText(context: Context, text: String, label: String = "FoldFit"): Boolean {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/** 개발자 옵션 화면을 연다. 개발자 옵션이 꺼져 있으면 켜는 곳(휴대전화 정보)으로 보낸다. */
internal fun openDeveloperOptions(context: Context) {
    val devOn = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    val intent = Intent(if (devOn) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
