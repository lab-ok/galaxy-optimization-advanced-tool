package com.local.folddpifix.ui.lab

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.folddpifix.data.lab.SettingsSnapshot
import com.local.folddpifix.data.log.PublicLogFile
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LocalLiquid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 실험실(lab 빌드 전용): 삼성 '앱 화면 크게/작게'가 어떤 시스템 설정 키에 저장되는지 찾는다.
 * 바꾸기 전 설정 전체를 저장 → 설정 앱에서 앱 하나의 크기를 바꿈 → 바꾼 뒤 비교해 차이를 파일로 저장·공유.
 */
@Composable
internal fun LabSheet() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val beforeFile = remember { File(context.filesDir, "lab_settings_before.tsv") }
    var status by remember { mutableStateOf(if (beforeFile.exists()) "이전에 저장한 '바꾸기 전' 기록이 있습니다." else "") }
    var preview by remember { mutableStateOf("") }
    var hasBefore by remember { mutableStateOf(beforeFile.exists()) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
    ) {
        Text("설정 변경 비교", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Text("개발자 확인용 기능입니다. 공개 버전에는 들어가지 않습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        GlassCard(padding = 16.dp) {
            Text("앱별 화면 크기 저장 위치 찾기", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "① [바꾸기 전 저장]을 누릅니다.\n② 설정 → 디스플레이 → 앱 화면 크게/작게에서 앱 하나의 값을 바꿉니다.\n③ 돌아와 [바꾼 뒤 비교]를 누르면 바뀐 설정 키를 파일로 저장하고 공유 창을 엽니다.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidButton("바꾸기 전 저장", modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val snap = withContext(Dispatchers.IO) { SettingsSnapshot.take(context).also { beforeFile.writeText(SettingsSnapshot.serialize(it)) } }
                        hasBefore = true
                        preview = ""
                        status = "저장했습니다 · " + snap.entries.joinToString(" · ") { "${it.key} ${it.value.size}개" }
                    }
                })
                LiquidButton("바꾼 뒤 비교", modifier = Modifier.weight(1f), primary = false, enabled = hasBefore, onClick = {
                    scope.launch {
                        val (diff, uri) = withContext(Dispatchers.IO) {
                            val before = SettingsSnapshot.parse(beforeFile.readText())
                            val after = SettingsSnapshot.take(context)
                            val diff = SettingsSnapshot.diff(before, after)
                            val name = "foldfit-lab-settings-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.txt"
                            val text = "== 차이 ==\n$diff\n== 바꾸기 전 ==\n${SettingsSnapshot.serialize(before)}\n== 바꾼 뒤 ==\n${SettingsSnapshot.serialize(after)}"
                            diff to PublicLogFile.saveNew(context, name, text)
                        }
                        preview = diff.lineSequence().take(30).joinToString("\n")
                        status = if (uri != null) "다운로드/FoldFit에 저장했습니다." else "파일을 저장하지 못했습니다."
                        uri?.let { share(context, it) }
                    }
                })
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(status, color = c.ink, style = MaterialTheme.typography.bodySmall)
            }
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(preview, color = c.ink, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
            }
        }
    }
}

private fun share(context: Context, uri: android.net.Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "실험실 결과 보내기"))
}
