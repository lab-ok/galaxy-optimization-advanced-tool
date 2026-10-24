package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.labok.goat.data.lab.ApiProbe
import com.labok.goat.data.lab.SettingsSnapshot
import com.labok.goat.data.lab.ShareProbe
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidButtonPair
import com.labok.goat.ui.liquid.LiquidProgressButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 실험실 '조사 도구'. 기능을 만들 때 기기 설정과 시스템 함수를 살펴보는 시험 화면이다.
 * 사이드바 메뉴는 lab 빌드에서만 보인다(코드는 공개 빌드에도 들어 있지만 열 수 없다).
 * - 설정 변경 비교: 바꾸기 전·뒤 설정 전체를 비교해 바뀐 키를 파일로 저장·공유한다.
 * - 시스템 함수 탐색: 화면 밀도·배율 관련 시스템 함수 목록을 파일로 만든다(함수를 실행하지는 않는다).
 */
@Composable
internal fun LabScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val beforeFile = remember { File(context.filesDir, "lab_settings_before.tsv") }
    var status by remember { mutableStateOf(if (beforeFile.exists()) "이전에 저장한 '바꾸기 전' 기록이 있습니다." else "") }
    var preview by remember { mutableStateOf("") }
    var hasBefore by remember { mutableStateOf(beforeFile.exists()) }
    // 탐색·진단은 오래 걸려, 버튼 안에 물이 차오르며 지금 하는 일과 퍼센트를 보여 준다(null이면 쉬는 중)
    var apiProgress by remember { mutableStateOf<Float?>(null) }
    var apiStatus by remember { mutableStateOf("") }
    var shareProgress by remember { mutableStateOf<Float?>(null) }
    var shareStatus by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp)
    ) {
        GlassCard {
            Text("조사 도구", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "기능을 만들 때 기기의 설정·시스템 함수를 살펴보는 실험실 도구입니다. 결과는 파일로 저장해 보낼 수 있습니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(Gap.Card))
        // 비교 결과 줄이 생길 때 아래 카드가 튀지 않게 높이를 이어 준다(1.24.8 점검)
        GlassCard(Modifier.animateContentSize()) {
            Text("설정 변경 비교", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "① [바꾸기 전 저장]을 누릅니다.\n② 설정 → 디스플레이 → 앱 화면 크게/작게에서 앱 하나의 값을 바꿉니다.\n③ 돌아와 [바꾼 뒤 비교]를 누르면 바뀐 설정 키를 파일로 저장하고 공유 창을 엽니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Card))
            LiquidButtonPair(listOf("바꾸기 전 저장", "바꾼 뒤 비교")) { half ->
                LiquidButton("바꾸기 전 저장", modifier = half, onClick = {
                    scope.launch {
                        val snap = withContext(Dispatchers.IO) { SettingsSnapshot.take(context).also { beforeFile.writeText(SettingsSnapshot.serialize(it)) } }
                        hasBefore = true
                        preview = ""
                        status = "저장했습니다 · " + snap.entries.joinToString(" · ") { "${it.key} ${it.value.size}개" }
                    }
                })
                LiquidButton("바꾼 뒤 비교", modifier = half, primary = false, enabled = hasBefore, disabledHint = Copy.DISABLED_NO_BEFORE, onClick = {
                    scope.launch {
                        val (diff, uri) = withContext(Dispatchers.IO) {
                            val before = SettingsSnapshot.parse(beforeFile.readText())
                            val after = SettingsSnapshot.take(context)
                            val diff = SettingsSnapshot.diff(before, after)
                            val text = "== 차이 ==\n$diff\n== 바꾸기 전 ==\n${SettingsSnapshot.serialize(before)}\n== 바꾼 뒤 ==\n${SettingsSnapshot.serialize(after)}"
                            diff to PublicLogFile.saveNew(context, PublicLogFile.timestampedName("lab-settings"), text)
                        }
                        preview = diff.lineSequence().take(30).joinToString("\n")
                        status = if (uri != null) "다운로드/GOAT에 저장했습니다." else "파일을 저장하지 못했습니다."
                        uri?.let { share(context, it) }
                    }
                })
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Item))
                Text(status, color = c.ink, style = MaterialTheme.typography.bodySmall)
            }
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Item))
                Text(preview, color = c.ink, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
            }
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard {
            Text("시스템 함수 탐색", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "기기의 시스템 서비스를 모두 훑어 화면 밀도·확대·배율과 관련된 함수 목록을 파일로 만듭니다. 함수를 실행하지는 않습니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidProgressButton("탐색하고 파일로 보내기", progress = apiProgress, status = apiStatus, modifier = Modifier.fillMaxWidth(), onClick = {
                if (apiProgress != null) return@LiquidProgressButton
                apiProgress = 0f
                apiStatus = "탐색 시작"
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        ApiProbe.run(context) { p, what ->
                            scope.launch {
                                apiProgress = p
                                apiStatus = what
                            }
                        }
                    }
                    apiProgress = 1f
                    apiStatus = "파일 저장 중"
                    val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("lab-api"), text) }
                    apiProgress = null
                    uri?.let { share(context, it) }
                }
            })
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard {
            NewTag(NewBadge.SHARE_PROBE, Modifier.padding(bottom = Gap.Item))
            Text("공유 화면 진단", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "삼성 공유 화면의 다이렉트 공유·즐겨찾기·앱 숨기기를 GOAT가 바꿀 수 있는지 확인할 정보를 파일로 만듭니다. 읽기만 합니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidProgressButton("진단하고 파일로 보내기", progress = shareProgress, status = shareStatus, modifier = Modifier.fillMaxWidth(), onClick = {
                if (shareProgress != null) return@LiquidProgressButton
                shareProgress = 0f
                shareStatus = "진단 시작"
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        ShareProbe.run(context) { p, what ->
                            scope.launch {
                                shareProgress = p
                                shareStatus = what
                            }
                        }
                    }
                    shareProgress = 1f
                    shareStatus = "파일 저장 중"
                    val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("lab-share"), text) }
                    shareProgress = null
                    uri?.let { share(context, it) }
                }
            })
        }
    }
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "실험실 결과 보내기"))
}
