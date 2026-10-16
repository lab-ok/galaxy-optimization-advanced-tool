package com.labok.goat.ui.lab

import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.lab.FoldAppScale
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 실험실 '접힘별 앱 배율'. 앱마다 펼친 화면·접은 화면의 앱별 배율을 따로 정하면, 접고 펼 때 GOAT가 그 화면용 값으로 바꿔 건다([FoldAppScale]).
 * 위: 사용 스위치·지금 화면·연결 상태. 아래: 정한 앱을 먼저, 그다음 최근 사용 순의 모든 앱. 앱을 누르면 펼침·접힘 값을 고른다.
 */
@Composable
internal fun LabFoldScaleScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val shell by DensityShell.connected.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY || shell != null
    val foldable = remember { DeviceKind.isFoldable(context) }
    var on by remember { mutableStateOf(FoldAppScale.enabled(context)) }
    var rules by remember { mutableStateOf(FoldAppScale.rules(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var role by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var openRow by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        role = withContext(Dispatchers.IO) { FoldAppScale.currentRole(context)?.label }
    }

    fun change(pkg: String, rule: FoldAppScale.Rule) {
        FoldAppScale.setRule(context, pkg, rule)
        rules = FoldAppScale.rules(context)
        // 켜져 있으면 지금 화면 값을 바로 건다(그 앱은 다시 시작된다)
        if (on && ready) scope.launch { applied = withContext(Dispatchers.IO) { FoldAppScale.applyNow(context, "값 바꿈") } }
    }

    val q = query.trim()
    val all = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
    val (picked, rest) = all.partition { rules.containsKey(it.pkg) }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Item),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.FEATURE_LAB_FOLD_SCALE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_FOLD_SCALE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidSwitchRow(
                        if (on) "사용 중" else "사용 안 함",
                        when {
                            !foldable -> Copy.LAB_FOLD_SCALE_NOT_FOLD
                            !ready -> Copy.LAB_FOLD_SCALE_NEED_SHELL
                            else -> "지금 화면: ${role ?: "확인 중"} · 정한 앱 ${rules.size}개"
                        },
                        on,
                        foldable && ready
                    ) { want ->
                        on = want
                        FoldAppScale.setEnabled(context, want)
                        if (want) scope.launch { applied = withContext(Dispatchers.IO) { FoldAppScale.applyNow(context, "켜기") } }
                    }
                    if (applied >= 0) {
                        Spacer(Modifier.height(Gap.Item))
                        Text(if (applied == 0) "지금 화면 값과 이미 같습니다." else "앱 ${applied}개를 지금 화면 값으로 바꿨습니다.", color = c.ink, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.LAB_FOLD_SCALE_NOTE, color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(key = "search") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                LiquidTextField(
                    query,
                    { query = it },
                    "앱 이름 검색",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    leading = { Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) }
                )
            }
        }
        if (apps == null) item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
        if (picked.isNotEmpty()) {
            item(key = "pickedTitle") { SectionTitle("정한 앱 ${picked.size}개") }
            items(picked, key = { "p:" + it.pkg }) { app ->
                FoldScaleRow(app, rules[app.pkg], openRow == app.pkg, { openRow = if (openRow == app.pkg) null else app.pkg }) { change(app.pkg, it) }
            }
        }
        if (rest.isNotEmpty()) {
            item(key = "restTitle") { SectionTitle("최근 사용 순") }
            items(rest, key = { "r:" + it.pkg }) { app ->
                FoldScaleRow(app, null, openRow == app.pkg, { openRow = if (openRow == app.pkg) null else app.pkg }) { change(app.pkg, it) }
            }
        }
        if (apps != null && all.isEmpty()) item(key = "empty") { SectionTitle("찾는 앱이 없습니다.") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val c = LocalLiquid.current
    Text(
        text,
        color = c.muted,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Gap.CardPadding, vertical = 4.dp)
    )
}

/** 앱 한 줄: 아이콘·이름·정한 값. 누르면 펼침·접힘 값을 고르는 칩이 펼쳐진다. */
@Composable
private fun FoldScaleRow(app: AppEntry, rule: FoldAppScale.Rule?, open: Boolean, onToggle: () -> Unit, onChange: (FoldAppScale.Rule) -> Unit) {
    val c = LocalLiquid.current
    val shape = RoundedCornerShape(20.dp)
    val r = rule ?: FoldAppScale.Rule(0, 0)
    Column(
        Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, if (open) c.ink.copy(alpha = 0.5f) else c.line, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
            Spacer(Modifier.width(12.dp))
            Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                if (rule == null) "정하지 않음" else "펼침 ${FoldAppScale.label(r.inner)} · 접힘 ${FoldAppScale.label(r.outer)}",
                color = c.muted,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (open) {
            Spacer(Modifier.height(Gap.Item))
            Text("펼친 화면", color = c.ink, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(Gap.Item))
            LiquidChips(options = FoldAppScale.CHOICES, selected = r.inner, label = { FoldAppScale.label(it) }, onSelect = { onChange(r.copy(inner = it)) })
            Spacer(Modifier.height(Gap.Section))
            Text("접은 화면", color = c.ink, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(Gap.Item))
            LiquidChips(options = FoldAppScale.CHOICES, selected = r.outer, label = { FoldAppScale.label(it) }, onSelect = { onChange(r.copy(outer = it)) })
        }
    }
}
