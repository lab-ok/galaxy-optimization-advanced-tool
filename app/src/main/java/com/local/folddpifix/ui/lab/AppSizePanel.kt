package com.local.folddpifix.ui.lab

import com.local.folddpifix.ui.shizuku.ShizukuGuide
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.data.lab.AppDensityNotifier
import com.local.folddpifix.data.lab.AppDensityProbe
import com.local.folddpifix.data.lab.AppUsage
import com.local.folddpifix.data.lab.DensityServer
import com.local.folddpifix.data.lab.DensityShell
import com.local.folddpifix.data.shizuku.ShizukuAccess
import com.local.folddpifix.ui.components.CommandBox
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LiquidChips
import com.local.folddpifix.ui.liquid.LiquidSwitch
import com.local.folddpifix.ui.liquid.LiquidTextField
import com.local.folddpifix.ui.liquid.LocalLiquid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap?, val lastUsed: Long)

/**
 * 앱별 화면 크기 화면의 본체.
 * - 셸 권한(Shizuku 또는 PC 도우미)이 없으면 Shizuku 설정 안내를 보여 준다.
 * - 있으면: 알림창 조절 켜기, 앱 검색, '적용된 앱'(모두 기본으로 되돌리기 포함), '최근 사용 순' 전체 목록.
 * - 앱을 누르면 기본·320~510 단계가 펼쳐지고, 고르면 바로 적용된다(그 앱은 다시 시작된다).
 */
@Composable
internal fun AppSizePanel() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shell by DensityShell.connected.collectAsState()
    val shizuku by ShizukuAccess.status.collectAsState()
    val ready = shell != null || shizuku == ShizukuAccess.State.READY
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var values by remember { mutableStateOf(emptyMap<String, Int>()) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var usage by remember { mutableStateOf(AppUsage.hasAccess(context)) }
    var notify by remember { mutableStateOf(AppDensityNotifier.enabled(context)) }
    var confirmReset by remember { mutableStateOf(false) }
    var showPc by remember { mutableStateOf(false) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { notify = true; AppDensityNotifier.setEnabled(context, true) }
    }

    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(Unit) {
        usage = AppUsage.hasAccess(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(usage) { apps = withContext(Dispatchers.IO) { loadApps(context) } }
    // Shizuku로 연결되면 사용 기록 접근(최근 사용 순·알림 조절에 필요)을 스스로 허용한다
    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuAccess.State.READY && !usage) {
            usage = withContext(Dispatchers.IO) { ShizukuAccess.grantUsageAccess(context) }
        }
    }
    LaunchedEffect(ready, shell, apps) {
        val list = apps
        if (!ready || list == null) { supported = null; values = emptyMap(); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            supported = runCatching { DensityShell.supported() }.getOrDefault(false)
            if (supported == true) values = list.associate { it.pkg to runCatching { DensityShell.get(context, it.pkg) }.getOrDefault(-1) }
        }
    }

    fun apply(app: AppEntry, dpi: Int) {
        scope.launch {
            val now = withContext(Dispatchers.IO) { runCatching { DensityShell.set(context, app.pkg, dpi); DensityShell.get(context, app.pkg) } }
            now.onSuccess { values = values + (app.pkg to it); message = "${app.label}: ${if (it == 0) "기본" else "$it dpi"}" }
                .onFailure { message = "바꾸지 못했습니다: ${it.message}" }
        }
    }

    if (!ready) {
        GlassCard(padding = 16.dp) {
            Text("시작하기", fontWeight = FontWeight.SemiBold, color = c.ink)
            ShizukuGuide(shizuku)
            Text(
                if (showPc) "PC로 하기 접기" else "PC로 하기",
                color = c.muted, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { showPc = !showPc }.padding(4.dp),
            )
            if (showPc) {
                Text(
                    "PC PowerShell(platform-tools 폴더)에서 아래 명령을 한 번 실행하면 셸 도우미가 켜집니다. " +
                        "도우미는 FoldFit을 닫거나 30분 동안 쓰지 않으면 스스로 꺼집니다.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                val cmd = remember { DensityServer.startCommand() }
                CommandBox(cmd, onCopy = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
                })
            }
        }
        return
    }
    if (supported == false) {
        GlassCard(padding = 16.dp) {
            Text("이 기기에서는 쓸 수 없습니다", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text("셸 권한은 받았지만 이 기기에는 삼성 앱별 화면 크기 함수가 없습니다(One UI 9 이상 필요).", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    // 설정: 알림창 조절, 사용 기록, 연결 상태
    GlassCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("알림창에서 바로 조절", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    "알림의 [작게]·[기본]·[크게]로 지금 쓰고 있는 앱의 화면 크기를 한 단계씩 바꿉니다.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            LiquidSwitch(notify, { on ->
                if (on && !ExternalChangeNotifier.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notify = on; AppDensityNotifier.setEnabled(context, on)
                }
            })
        }
        if (!usage) {
            Spacer(Modifier.height(10.dp))
            Text(
                "최근 사용 순 정렬과 알림 조절에는 '사용 기록 접근'이 필요합니다.",
                color = c.danger, style = MaterialTheme.typography.bodySmall,
            )
            LiquidButton("사용 기록 접근 허용", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), primary = false, onClick = {
                scope.launch {
                    usage = withContext(Dispatchers.IO) { ShizukuAccess.status.value == ShizukuAccess.State.READY && ShizukuAccess.grantUsageAccess(context) }
                    if (!usage) runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
            })
        }
        if (shell == null && !ShizukuAccess.bootStartOn(context)) Text(
            "Shizuku의 '부팅 시 시작'이 꺼져 있어 재부팅하면 다시 켜야 합니다. Shizuku → 설정에서 켜 두세요.",
            color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
        )
        if (shell != null) {
            LiquidButton("PC 셸 도우미 끄기", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), primary = false, onClick = {
                scope.launch(Dispatchers.IO) { DensityShell.stop() }
            })
        }
    }
    Spacer(Modifier.height(12.dp))
    LiquidTextField(
        query, { query = it }, "앱 이름 검색",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        leading = { Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) },
    )
    if (message.isNotEmpty()) Text(message, color = c.ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, start = 6.dp))
    Spacer(Modifier.height(12.dp))

    val list = apps
    if (list == null) {
        Text("앱 목록을 불러오는 중…", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
        return
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
    val applied = shown.filter { (values[it.pkg] ?: 0) > 0 }
    val rest = shown.filter { (values[it.pkg] ?: 0) <= 0 }

    if (applied.isNotEmpty()) {
        GlassCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("적용된 앱 ${applied.size}", fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Text(
                    "모두 기본으로", color = c.danger, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { confirmReset = !confirmReset }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            AnimatedVisibility(confirmReset, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text(
                        "적용된 ${applied.size}개 앱을 모두 기본 크기로 되돌립니다. 열려 있던 앱은 다시 시작됩니다.",
                        color = c.danger, style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidButton("취소", modifier = Modifier.weight(1f), primary = false, onClick = { confirmReset = false })
                        LiquidButton("모두 되돌리기", modifier = Modifier.weight(1f), danger = true, onClick = {
                            confirmReset = false
                            val targets = applied.toList()
                            scope.launch {
                                var ok = 0
                                withContext(Dispatchers.IO) {
                                    targets.forEach { app -> if (runCatching { DensityShell.set(context, app.pkg, 0) }.isSuccess) ok++ }
                                }
                                values = values + targets.associate { it.pkg to 0 }
                                message = "${ok}개 앱을 기본 크기로 되돌렸습니다."
                            }
                        })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            applied.forEach { app -> AppRow(app, values[app.pkg], open == app.pkg, { open = if (open == app.pkg) null else app.pkg }) { apply(app, it) } }
        }
        Spacer(Modifier.height(12.dp))
    }
    GlassCard(padding = 14.dp) {
        Text(
            if (usage) "최근 사용 순" else "모든 앱(이름 순)",
            fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        if (rest.isEmpty()) Text("찾는 앱이 없습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
        rest.forEach { app -> AppRow(app, values[app.pkg], open == app.pkg, { open = if (open == app.pkg) null else app.pkg }) { apply(app, it) } }
    }
}

@Composable
private fun AppRow(app: AppEntry, value: Int?, expanded: Boolean, onToggle: () -> Unit, onSelect: (Int) -> Unit) {
    val c = LocalLiquid.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) {
                app.icon?.let { Image(it, null, Modifier.size(36.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            val on = value != null && value > 0
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.ink else c.line.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    when (value) { null -> "…"; -1 -> "?"; 0 -> "기본"; else -> "$value" },
                    color = if (on) c.onInk else c.muted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp)) {
                LiquidChips(
                    options = listOf(0) + DensityServer.STEPS,
                    selected = value?.takeIf { it == 0 || it in DensityServer.STEPS } ?: 0,
                    label = { if (it == 0) "기본" else "$it" },
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "숫자가 작을수록 작게 보입니다. 바꾸면 이 앱이 다시 시작됩니다.",
                    color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val used = AppUsage.lastUsed(context)
    val size = (36 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    return AppDensityProbe.launcherApps(context)
        .filter { it.pkg != context.packageName }
        .map { app ->
            val icon = runCatching { pm.getApplicationIcon(app.pkg).toBitmap(size, size).asImageBitmap() }.getOrNull()
            AppEntry(app.label, app.pkg, icon, used[app.pkg] ?: 0L)
        }
        .sortedWith(compareByDescending<AppEntry> { it.lastUsed }.thenBy { it.label })
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "방금 사용"
        m < 60 -> "${m}분 전"
        m < 24 * 60 -> "${m / 60}시간 전"
        else -> "${m / (24 * 60)}일 전"
    }
}
