package com.local.folddpifix.ui.lab

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import com.local.folddpifix.data.lab.AppDensityProbe
import com.local.folddpifix.data.lab.DensityServer
import com.local.folddpifix.data.lab.DensityShell
import com.local.folddpifix.ui.liquid.LiquidChips
import androidx.compose.runtime.collectAsState
import com.local.folddpifix.ui.components.CommandBox
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.local.folddpifix.data.lab.ApiProbe
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
 * 실험실 기능 '앱별 화면 크기'(lab 빌드 전용). 접고 펼 때 DPI를 맞추는 기본 기능과는 다른 갈래로,
 * 앱마다 화면 크기를 다르게 두는 기능을 만들기 위한 조사 단계다.
 * 지금은 삼성 '앱 화면 크게/작게'가 어떤 시스템 설정 키에 저장되는지 찾는다:
 * 바꾸기 전 설정 전체를 저장 → 설정 앱에서 앱 하나의 크기를 바꿈 → 바꾼 뒤 비교해 차이를 파일로 저장·공유.
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
    ) {
        GlassCard(padding = 16.dp) {
            Text("앱마다 화면 크기를 다르게", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "기본 기능(화면 크기 맞추기)과는 별개로, 앱별로 화면 크기를 따로 두는 실험실입니다. " +
                    "삼성 '앱 화면 크게/작게'와 같은 설정을 씁니다. 아래 조사 도구는 시험용이며, 공개 버전에는 들어가지 않습니다.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        ShellDensityCard()
        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 16.dp) {
            Text("설정 변경 비교", fontWeight = FontWeight.SemiBold, color = c.ink)
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
        Spacer(Modifier.height(12.dp))
        AppDensityCard()
        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 16.dp) {
            Text("시스템 함수 탐색", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "기기의 시스템 서비스를 모두 훑어 화면 밀도·확대·배율과 관련된 함수 목록을 파일로 만듭니다. 함수를 실행하지는 않습니다.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            var probing by remember { mutableStateOf(false) }
            LiquidButton(if (probing) "탐색 중…" else "탐색하고 파일로 보내기", modifier = Modifier.fillMaxWidth(), enabled = !probing, onClick = {
                probing = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        val name = "foldfit-lab-api-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.txt"
                        PublicLogFile.saveNew(context, name, ApiProbe.run(context))
                    }
                    probing = false
                    uri?.let { share(context, it) }
                }
            })
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

/**
 * 앱별 밀도 직접 시험: 앱을 고르고 현재 값을 읽거나, 밀도를 넣어 바꿔 본다.
 * 바꾸기 전 값을 기록해 두므로 [되돌리기]로 원래 값으로 돌려놓을 수 있다.
 */
@Composable
private fun AppDensityCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(emptyList<AppDensityProbe.App>()) }
    var pkg by remember { mutableStateOf("") }
    var dpi by remember { mutableStateOf("360") }
    var result by remember { mutableStateOf("") }
    var original by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { AppDensityProbe.launcherApps(context) } }

    GlassCard(padding = 16.dp) {
        Text("앱별 밀도 직접 시험", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(
            "삼성 시스템 함수(getCustomDensity·setUserCustomDensity)를 직접 불러 봅니다. 권한이 모자라면 필요한 권한 이름이 결과에 나옵니다.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text("앱 고르기", color = c.ink, style = MaterialTheme.typography.labelLarge)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            apps.forEach { app ->
                val selected = app.pkg == pkg
                Text(
                    "${app.label}  ·  ${app.pkg}",
                    color = if (selected) c.onInk else c.ink,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) c.ink else Color.Transparent)
                        .clickable { pkg = app.pkg; original = null; result = "" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dpi, onValueChange = { dpi = it.filter(Char::isDigit).take(3) },
            label = { Text("바꿀 밀도(dpi)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton("현재 값 읽기", modifier = Modifier.weight(1f), primary = false, enabled = pkg.isNotEmpty(), onClick = {
                scope.launch {
                    result = withContext(Dispatchers.IO) { AppDensityProbe.read(pkg) }
                    if (original == null) original = result
                }
            })
            LiquidButton("이 값으로 바꾸기", modifier = Modifier.weight(1f), enabled = pkg.isNotEmpty() && dpi.isNotEmpty(), onClick = {
                scope.launch {
                    if (original == null) original = withContext(Dispatchers.IO) { AppDensityProbe.read(pkg) }
                    result = withContext(Dispatchers.IO) { AppDensityProbe.write(pkg, dpi.toInt()) }
                }
            })
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(result, color = c.ink, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp))
            if ("MANAGE_ACTIVITY_TASKS" in result) {
                // 앱에는 줄 수 없는 시스템 권한이라, 같은 함수를 adb 셸로 부르는 명령을 보여 준다.
                val cmds = remember(pkg, dpi) { AppDensityProbe.shellCommands(pkg, dpi.toIntOrNull() ?: 0) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "이 함수는 시스템 권한(MANAGE_ACTIVITY_TASKS)이 필요해 앱에서는 부를 수 없습니다. PC의 adb 셸에는 이 권한이 있으니 아래 명령으로 시험해 보세요.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
                cmds.lines().filter { it.contains("adb ") }.forEach { line ->
                    val cmd = line.substringAfter(": ").trim().let { if (it.startsWith("adb")) it else line.trim() }
                    Spacer(Modifier.height(6.dp))
                    CommandBox(".\\" + cmd, onCopy = {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText("adb", ".\\" + cmd))
                    })
                }
            }
            Spacer(Modifier.height(6.dp))
            LiquidButton("결과 파일로 보내기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        val name = "foldfit-lab-density-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.txt"
                        PublicLogFile.saveNew(
                            context, name,
                            "패키지: $pkg\n\n== 처음 값 ==\n${original.orEmpty()}\n\n== 마지막 결과 ==\n$result\n\n== PC 명령 ==\n" +
                                AppDensityProbe.shellCommands(pkg, dpi.toIntOrNull() ?: 0),
                        )
                    }
                    uri?.let { share(context, it) }
                }
            })
        }
        Text(
            "되돌리려면 설정 → 앱 화면 크게/작게에서 그 앱을 '시스템 설정'으로 바꾸면 됩니다.",
            color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 앱별 화면 크기: PC에서 셸 도우미([DensityServer])를 한 번 띄우면, 앱마다 삼성 6단계 중 하나를 고를 수 있다.
 * 바꾼 값은 시스템에 저장돼 재부팅해도 유지되므로, 도우미는 값을 바꿀 때만 켜 두면 된다.
 */
@Composable
private fun ShellDensityCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shell by DensityShell.connected.collectAsState()
    var apps by remember { mutableStateOf(emptyList<AppDensityProbe.App>()) }
    var values by remember { mutableStateOf(emptyMap<String, Int>()) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { AppDensityProbe.launcherApps(context) } }
    LaunchedEffect(shell, apps) {
        if (shell == null) { supported = null; values = emptyMap(); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            supported = runCatching { DensityShell.supported() }.getOrDefault(false)
            if (supported == true) values = apps.associate { it.pkg to runCatching { DensityShell.get(it.pkg) }.getOrDefault(-1) }
        }
    }

    GlassCard(padding = 16.dp) {
        Text("앱별 화면 크기", fontWeight = FontWeight.SemiBold, color = c.ink)
        when {
            shell == null -> {
                Text(
                    "삼성 '앱 화면 크게/작게'를 FoldFit에서 바꿉니다. 이 기능은 adb 셸 권한이 필요해서, PC에서 아래 명령으로 " +
                        "셸 도우미를 한 번 켜야 합니다. 켜지면 이 카드가 바로 바뀝니다.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                val cmd = remember { DensityServer.startCommand() }
                CommandBox(cmd, onCopy = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
                })
                Text(
                    "바꾼 값은 시스템에 저장돼 재부팅해도 유지됩니다. 도우미는 FoldFit을 닫거나 30분 동안 쓰지 않으면 스스로 꺼집니다.",
                    color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
                )
            }
            supported == false -> {
                Text("셸 도우미는 켜졌지만 이 기기에는 삼성 앱별 화면 크기 함수가 없습니다.", color = c.danger, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                LiquidButton("셸 도우미 끄기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
                    scope.launch(Dispatchers.IO) { DensityShell.stop() }
                })
            }
            else -> {
                Text(
                    "앱을 누르고 크기를 고르세요. 숫자가 작을수록 작게 보입니다. 바꾸면 그 앱이 다시 시작되며 바로 적용됩니다.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        val v = values[app.pkg]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { open = if (open == app.pkg) null else app.pkg }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                when (v) { null -> "…"; -1 -> "?"; 0 -> "기본"; else -> "$v" },
                                color = if (v != null && v > 0) c.ink else c.muted,
                                fontWeight = if (v != null && v > 0) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (open == app.pkg) {
                            LiquidChips(
                                options = listOf(0) + DensityServer.STEPS,
                                selected = v?.takeIf { it == 0 || it in DensityServer.STEPS } ?: 0,
                                label = { if (it == 0) "기본" else "$it" },
                                onSelect = { dpi ->
                                    scope.launch {
                                        val now = withContext(Dispatchers.IO) {
                                            runCatching { DensityShell.set(app.pkg, dpi); DensityShell.get(app.pkg) }
                                        }
                                        now.onSuccess { values = values + (app.pkg to it); message = "${app.label}: ${if (it == 0) "기본" else "$it dpi"}" }
                                            .onFailure { message = "바꾸지 못했습니다: ${it.message}" }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            )
                        }
                    }
                }
                if (message.isNotEmpty()) Text(message, color = c.ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                LiquidButton("셸 도우미 끄기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
                    scope.launch(Dispatchers.IO) { DensityShell.stop() }
                })
            }
        }
    }
}
