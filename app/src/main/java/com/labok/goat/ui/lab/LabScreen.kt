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
import androidx.compose.runtime.mutableIntStateOf
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
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.ApiProbe
import com.labok.goat.data.lab.GoatFlexPanel
import com.labok.goat.data.lab.MemoShare.withoutGoat
import com.labok.goat.data.lab.OttFlexTest
import com.labok.goat.data.lab.SettingsSnapshot
import com.labok.goat.data.lab.ShareProbe
import com.labok.goat.data.lab.TentMode
import com.labok.goat.data.lab.UnfoldLock
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidButtonPair
import com.labok.goat.ui.liquid.LiquidChips
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
    var tentInfo by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = Space.Edge)
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
        // OTT 플렉스 시험(11-02 소장님): 넷플릭스·디즈니+ 등에서 플렉스 모드로 할 수 있는 것을 차례로 시험하고 결과를 보낸다(OTT는 화면 찍기가 막혀 상태만 기록)
        GlassCard {
            Text("OTT 플렉스 시험", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "누른 뒤 10초 안에 넷플릭스·디즈니+ 같은 OTT 앱에서 영상을 틀고, 가로로 들어 반쯤 접어 두세요. 진동이 울릴 때마다 다음 단계로 넘어가며 약 25초 동안 반쯤 접힘·가로 맞춤·삼성 패널 등록·앱 방향 요청 무시를 바꿔 시험합니다. 끝나면 알림을 눌러 결과를 보내 주세요.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("OTT 플렉스 시험 시작", modifier = Modifier.fillMaxWidth(), onClick = { OttFlexTest.start(context) })
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("GOAT 패널 미리 보기(8초)", primary = false, modifier = Modifier.fillMaxWidth(), onClick = { GoatFlexPanel.preview(context) })
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("포스터 표시 미리 보기(8초)", primary = false, modifier = Modifier.fillMaxWidth(), onClick = { GoatFlexPanel.previewPoster(context) })
        }
        Spacer(Modifier.height(Gap.Card))
        // 펼침 잠금화면 실험(11-06 소장님): 잠긴 채 펼칠 때 PIN 입력창 대신 시계·배경 잠금화면에 머물게 할 수 있는지 실기에서 비교한다
        GlassCard {
            var unfoldMode by remember { mutableIntStateOf(UnfoldLock.mode(context)) }
            Text("펼침 잠금화면 실험", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "안 깨움: 접힌 채 화면이 꺼지면 접힘 상태를 닫힘으로 고정해 펼쳐도 화면이 켜지지 않게 하고, 전원 버튼으로 켜면 풉니다. 다시 끔: 펼쳐서 켜지면 바로 다시 끕니다. 입력창 닫기·껐다 켜기: 잠긴 채 펼칠 때 PIN 입력창 대신 잠금화면에 머물게 합니다. 잠금은 풀지 않습니다. 결과는 문제 신고의 앱 로그로 보내 주세요. Shizuku가 필요합니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidChips(
                options = listOf(UnfoldLock.MODE_OFF, UnfoldLock.MODE_NO_WAKE, UnfoldLock.MODE_STAY_OFF, UnfoldLock.MODE_BACK, UnfoldLock.MODE_SCREEN),
                selected = unfoldMode,
                label = {
                    when (it) {
                        UnfoldLock.MODE_NO_WAKE -> "안 깨움"
                        UnfoldLock.MODE_STAY_OFF -> "다시 끔"
                        UnfoldLock.MODE_BACK -> "입력창 닫기"
                        UnfoldLock.MODE_SCREEN -> "껐다 켜기"
                        else -> "끔"
                    }
                },
                onSelect = {
                    unfoldMode = it
                    UnfoldLock.setMode(context, it)
                    GoatService.sync(context)
                }
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("조사 파일 만들기", primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { UnfoldLock.diagnose(context) }
                    val uri = withContext(Dispatchers.IO) { PublicLogFile.saveNew(context, PublicLogFile.timestampedName("unfold-lock"), text) }
                    uri?.let { share(context, it) }
                }
            })
        }
        Spacer(Modifier.height(Gap.Card))
        // 텐트 모드 시험(11-01 소장님): 폴드를 반쯤 접어 엎어 세우고 바깥은 시계·유튜브, 안쪽은 간접조명으로 두 화면을 함께 쓸 수 있는지 본다
        GlassCard(Modifier.animateContentSize()) {
            Text("텐트 모드 진단", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "텐트 모드는 앰비언트 모드 메뉴로 옮겼습니다. 여기에는 두 화면 동시 표시를 시험하고 진단 파일을 보내는 도구만 둡니다.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("전체 시험 후 진단 보내기", primary = false, modifier = Modifier.fillMaxWidth(), onClick = { TentActivity.start(context, TentActivity.MODE_ALL) })
            Spacer(Modifier.height(Gap.Item))
            Text("텐트 자세로 세워 두고 누르면 표시 방식을 차례로 약 40초 동안 시험한 뒤 진단 파일 공유 창이 뜹니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("듀얼 화면(공식)", primary = false, modifier = Modifier.fillMaxWidth(), onClick = { TentActivity.start(context, TentActivity.MODE_OFFICIAL) })
            Spacer(Modifier.height(Gap.Item))
            LiquidButton("기기 상태·화면 목록 보기", primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    tentInfo = withContext(Dispatchers.IO) {
                        val states = TentMode.states() ?: "Shizuku 연결이 필요합니다."
                        val shown = TentMode.displays(context).joinToString("\n") { "화면 ${it.first}: ${it.second.x}x${it.second.y} 상태 ${it.third}" }
                        "$states\n$shown\n물리 화면 ${TentMode.physicalIds()}"
                    }
                }
            })
            if (tentInfo.isNotEmpty()) {
                Spacer(Modifier.height(Gap.Item))
                Text(tentInfo, color = c.ink, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
            }
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
    context.startActivity(Intent.createChooser(send, "실험실 결과 보내기").withoutGoat(context))
}
