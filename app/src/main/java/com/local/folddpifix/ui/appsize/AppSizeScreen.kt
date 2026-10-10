package com.local.folddpifix.ui.appsize

import com.local.folddpifix.domain.AppDensityPolicy
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.data.appsize.AppDensityNotifier
import com.local.folddpifix.data.appsize.AppList
import com.local.folddpifix.data.appsize.AppUsage
import com.local.folddpifix.data.appsize.DensityServer
import com.local.folddpifix.data.appsize.DensityShell
import com.local.folddpifix.data.shizuku.ShizukuAccess
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.ui.liquid.LiquidChips
import com.local.folddpifix.ui.liquid.LiquidSwitch
import com.local.folddpifix.ui.liquid.LiquidTextField
import com.local.folddpifix.ui.liquid.LocalLiquid
import com.local.folddpifix.ui.liquid.LocalReduceMotion
import com.local.folddpifix.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap?, val lastUsed: Long)

/** 넓은 내부 화면에서도 읽기 좋은 최대 폭(기본 기능 화면과 같다). */
private val MAX_WIDTH = 640.dp

/**
 * 앱마다 크기 따로: 삼성 '앱 화면 크게/작게'와 같은 설정을 FoldFit에서 바꾼다.
 * - 셸 권한(Shizuku 또는 PC 도우미)이 없으면 연결 안내 카드만 보여 주고 [onGuide]로 연결 시트를 연다.
 * - 있으면: 알림창 조절, 검색, '적용된 앱', '최근 사용 순' 목록. 앱을 누르면 기본·320~510이 펼쳐진다.
 * 목록은 LazyColumn이라 앱이 '적용된 앱'과 '최근 사용 순' 사이를 오갈 때 자리 이동이 애니메이션된다.
 */
@Composable
internal fun AppSizeScreen(contentPadding: PaddingValues, onGuide: () -> Unit, onReset: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shell by DensityShell.connected.collectAsState()
    val shizuku by ShizukuAccess.status.collectAsState()
    val changed by DensityShell.changes.collectAsState()
    val ready = shell != null || shizuku == ShizukuAccess.State.READY
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var values by remember { mutableStateOf(emptyMap<String, Int>()) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var usage by remember { mutableStateOf(AppUsage.hasAccess(context)) }
    var notify by remember { mutableStateOf(AppDensityNotifier.enabled(context)) }
    var resumed by remember { mutableIntStateOf(0) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { notify = true; AppDensityNotifier.setEnabled(context, true) }
    }

    LaunchedEffect(Unit) {
        ShizukuAccess.watch(context)
        AppDensityNotifier.start(context)  // 켜 둔 알림 조절이 업데이트 등으로 멈춰 있으면 다시 띄운다
    }
    // 다른 곳(알림창·삼성 설정)에서 바꾸고 돌아와도 바로 보이도록 돌아올 때마다 다시 읽는다
    LifecycleResumeEffect(Unit) {
        usage = AppUsage.hasAccess(context)
        resumed++
        onPauseOrDispose { }
    }
    LaunchedEffect(usage) { apps = withContext(Dispatchers.IO) { loadApps(context) } }
    // Shizuku로 연결되면 사용 기록 접근(최근 사용 순·알림 조절에 필요)을 스스로 허용한다
    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuAccess.State.READY && !usage) usage = withContext(Dispatchers.IO) { ShizukuAccess.grantUsageAccess(context) }
    }
    LaunchedEffect(ready, shell, apps, changed, resumed) {
        val list = apps
        if (!ready || list == null) { supported = null; values = emptyMap(); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            supported = runCatching { DensityShell.supported() }.getOrDefault(false)
            // 이전 값은 그대로 두고 새 값으로 한꺼번에 바꾼다(다시 읽는 동안 '…'로 깜빡이지 않게)
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

    val q = query.trim()
    val list = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
    val applied = list.filter { (values[it.pkg] ?: 0) > 0 }
    val rest = list.filter { (values[it.pkg] ?: 0) <= 0 }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Appear(0) {
                GlassCard(padding = 16.dp) {
                    Text(Copy.APP_SIZE_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.APP_SIZE_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when {
            !ready -> item(key = "connect") {
                Appear(1) {
                    GlassCard(padding = 16.dp) {
                        Text(Copy.APP_SIZE_CONNECT_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.APP_SIZE_CONNECT_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        LiquidButton(Copy.APP_SIZE_CONNECT_ACTION, modifier = Modifier.fillMaxWidth(), onClick = onGuide)
                    }
                }
            }
            supported == false -> item(key = "unsupported") {
                Appear(1) {
                    GlassCard(padding = 16.dp) {
                        Text("이 기기에서는 쓸 수 없습니다", fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.APP_SIZE_UNSUPPORTED, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {
                item(key = "notify") {
                    Appear(1) {
                        GlassCard(padding = 16.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Copy.APP_SIZE_NOTIFY_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                                    Text(Copy.APP_SIZE_NOTIFY_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
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
                                Text(Copy.APP_SIZE_USAGE_NEEDED, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                                LiquidButton("사용 기록 접근 허용", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), primary = false, onClick = {
                                    scope.launch {
                                        usage = withContext(Dispatchers.IO) { shizuku == ShizukuAccess.State.READY && ShizukuAccess.grantUsageAccess(context) }
                                        if (!usage) runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                    }
                                })
                            }
                            if (shell == null && !ShizukuAccess.bootStartOn(context)) {
                                Text(Copy.APP_SIZE_BOOT_OFF, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            }
                            if (shell != null) {
                                LiquidButton("PC 셸 도우미 끄기", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), primary = false, onClick = {
                                    scope.launch(Dispatchers.IO) { DensityShell.stop() }
                                })
                            }
                        }
                    }
                }
                item(key = "search") {
                    Appear(2) {
                        Column(Modifier.widthIn(max = MAX_WIDTH)) {
                            LiquidTextField(
                                query, { query = it }, "앱 이름 검색",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                leading = { Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) },
                            )
                            AnimatedVisibility(message.isNotEmpty()) {
                                Text(message, color = c.ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, start = 6.dp))
                            }
                        }
                    }
                }
                if (apps == null) item(key = "loading") {
                    Text("앱 목록을 불러오는 중…", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
                }
                if (applied.isNotEmpty()) {
                    item(key = "h-applied") {
                        Section("적용된 앱 ${applied.size}", Modifier.animateItem()) {
                            Text(
                                "모두 기본으로", color = c.danger, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(applied, key = { it.pkg }) { app ->
                        AppRow(app, values[app.pkg], open == app.pkg, Modifier.animateItem(), { open = if (open == app.pkg) null else app.pkg }) { apply(app, it) }
                    }
                }
                item(key = "h-rest") { Section(if (usage) "최근 사용 순" else "모든 앱(이름 순)", Modifier.animateItem()) }
                if (apps != null && rest.isEmpty()) item(key = "empty") {
                    Text("찾는 앱이 없습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
                }
                items(rest, key = { it.pkg }) { app ->
                    AppRow(app, values[app.pkg], open == app.pkg, Modifier.animateItem(), { open = if (open == app.pkg) null else app.pkg }) { apply(app, it) }
                }
            }
        }
    }
}

/** 처음 나타날 때 차례로 떠오르며 들어온다([order]번째, 70ms 간격). */
@Composable
private fun Appear(order: Int, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val k = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(360, delayMillis = 70 * order, easing = FastOutSlowInEasing)) }
    Box(Modifier.widthIn(max = MAX_WIDTH).graphicsLayer { alpha = k.value; translationY = (1f - k.value) * 18.dp.toPx() }) { content() }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalLiquid.current
    Row(modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(start = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** 앱 한 줄: 아이콘·이름·마지막 사용·지금 크기. 누르면 단계 선택이 펼쳐진다. */
@Composable
private fun AppRow(app: AppEntry, value: Int?, expanded: Boolean, modifier: Modifier, onToggle: () -> Unit, onSelect: (Int) -> Unit) {
    val c = LocalLiquid.current
    val on = value != null && value > 0
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (expanded) c.ink.copy(alpha = 0.5f) else c.line, label = "row")
    Column(
        modifier
            .widthIn(max = MAX_WIDTH)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            // 크기 표시: 바뀌면 숫자가 위아래로 갈리며 바뀌고, 적용된 앱은 잉크색으로 채워진다
            val fill by animateColorAsState(if (on) c.ink else c.line.copy(alpha = 0.5f), label = "pill")
            val ink by animateColorAsState(if (on) c.onInk else c.muted, label = "pillText")
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                AnimatedContent(
                    value,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "value",
                ) { v ->
                    Text(
                        when (v) { null -> "…"; -1 -> "?"; 0 -> "기본"; else -> "$v" },
                        color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
                LiquidChips(
                    options = listOf(0) + AppDensityPolicy.STEPS,
                    selected = value?.takeIf { it == 0 || it in AppDensityPolicy.STEPS } ?: 0,
                    label = { if (it == 0) "기본" else "$it" },
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Copy.APP_SIZE_ROW_HINT, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val used = AppUsage.lastUsed(context)
    val size = (38 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    return AppList.launcherApps(context)
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
