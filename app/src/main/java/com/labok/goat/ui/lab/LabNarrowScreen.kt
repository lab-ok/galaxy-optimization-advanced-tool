package com.labok.goat.ui.lab

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.background.GoatService
import com.labok.goat.data.lab.NarrowApps
import com.labok.goat.data.lab.StatusGestures
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.Section
import com.labok.goat.ui.appsize.ago
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLoop
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

/** 실험실 '앱 폭 좁히기': 고른 앱을 세로로 길고 좁게 띄운다([NarrowApps]). 소개·붙일 쪽·앱 목록 순. */
@Composable
internal fun LabNarrowScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val supported = remember { NarrowApps.supported() }
    val ready = shizuku == ShizukuAccess.State.READY && supported
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    var chosen by remember { mutableStateOf(NarrowApps.ratios(context)) }
    var open by remember { mutableStateOf<String?>(null) }
    var side by remember { mutableStateOf(NarrowApps.side(context)) }
    var bar by remember { mutableStateOf(NarrowApps.controller(context)) }
    var gesture by remember { mutableStateOf(StatusGestures.enabled(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    // 바꾸는 중인 앱(셸이 끝날 때까지 스위치를 잠근다)
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    fun choose(app: AppEntry, ratio: NarrowApps.Ratio?) = scope.launch {
        if (chosen[app.pkg] == ratio) return@launch
        busy[app.pkg] = true
        val err = withContext(Dispatchers.IO) { NarrowApps.set(context, app.pkg, ratio) }
        chosen = NarrowApps.ratios(context)
        busy.remove(app.pkg)
        GoatService.sync(context)
        if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
    }

    // 앱별 화면 배율처럼 '적용된 앱'과 나머지(최근 사용 순)로 나눈다. 검색어가 있으면 이름으로 거른다
    val q = query.trim()
    val list = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
    val applied = list.filter { it.pkg in chosen }
    val rest = list.filter { it.pkg !in chosen }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.FEATURE_LAB_NARROW, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_NARROW_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    NarrowArt(side)
                    Spacer(Modifier.height(Gap.Item))
                    Text(if (!supported) Copy.LAB_NARROW_FOLD8_ONLY else if (ready) Copy.LAB_NARROW_NOTE else Copy.LAB_FLEX_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "side") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.LAB_NARROW_SIDE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.LAB_NARROW_SIDE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidChips(
                        options = listOf(NarrowApps.Side.LEFT, NarrowApps.Side.RIGHT),
                        selected = side,
                        label = { it.label },
                        onSelect = { s ->
                            side = s
                            scope.launch {
                                val err = withContext(Dispatchers.IO) { NarrowApps.setSide(context, s) }
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
        item(key = "bar") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.LAB_NARROW_BAR, Copy.LAB_NARROW_BAR_BODY, bar, ready || bar) { on ->
                        bar = on
                        scope.launch {
                            withContext(Dispatchers.IO) { NarrowApps.setController(context, on) }
                            GoatService.sync(context)
                        }
                    }
                }
            }
        }
        item(key = "gesture") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.LAB_NARROW_GESTURE, Copy.LAB_NARROW_GESTURE_BODY, gesture, supported) { on ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { StatusGestures.setEnabled(context, on) }
                            gesture = StatusGestures.enabled(context)
                            if (!ok) Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
                        }
                    }
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
        if (apps == null) {
            item(key = "loading") { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
        }
        if (applied.isNotEmpty()) {
            item(key = "h-applied") {
                Section("적용된 앱 ${applied.size}", Modifier.animateItem()) {
                    Text(
                        Copy.LAB_NARROW_CLEAR,
                        color = c.danger,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = ready) {
                            scope.launch {
                                val failed = withContext(Dispatchers.IO) { NarrowApps.clearAll(context) }
                                chosen = NarrowApps.ratios(context)
                                GoatService.sync(context)
                                if (failed > 0) Toast.makeText(context, "${failed}개 앱을 되돌리지 못했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            items(applied, key = { it.pkg }) { app ->
                NarrowRow(app, chosen[app.pkg], open == app.pkg, ready && busy[app.pkg] != true, { open = if (open == app.pkg) null else app.pkg }, { choose(app, it) }, Modifier.animateItem())
            }
        }
        item(key = "h-rest") { Section("최근 사용 순", Modifier.animateItem()) }
        if (apps != null && rest.isEmpty()) {
            item(key = "empty") { Text("찾는 앱이 없습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp)) }
        }
        items(rest, key = { it.pkg }) { app ->
            NarrowRow(app, chosen[app.pkg], open == app.pkg, ready && busy[app.pkg] != true, { open = if (open == app.pkg) null else app.pkg }, { choose(app, it) }, Modifier.animateItem())
        }
    }
}

/** 앱 한 줄(앱별 화면 배율과 같은 모양): 아이콘·이름·마지막 사용·지금 비율. 누르면 비율 고르기(기본·16:9·3:2)가 펼쳐진다. */
@Composable
private fun NarrowRow(
    app: AppEntry,
    ratio: NarrowApps.Ratio?,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSelect: (NarrowApps.Ratio?) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalLiquid.current
    val on = ratio != null
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (expanded) c.ink.copy(alpha = 0.5f) else c.line, label = "narrowRow")
    Column(
        modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, border, shape)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))) { app.icon?.let { Image(it, null, Modifier.size(38.dp)) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = c.ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.lastUsed > 0) Text(ago(app.lastUsed), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            val fill by animateColorAsState(if (on) c.ink else c.line.copy(alpha = 0.5f), label = "narrowPill")
            val ink by animateColorAsState(if (on) c.onInk else c.muted, label = "narrowPillText")
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(ratio?.label ?: Copy.APP_SIZE_DEFAULT, color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
                LiquidChips(
                    options = listOf<NarrowApps.Ratio?>(null) + NarrowApps.Ratio.entries,
                    selected = ratio,
                    label = { it?.label ?: Copy.APP_SIZE_DEFAULT },
                    onSelect = { if (enabled) onSelect(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Gap.Item))
                Text(Copy.LAB_NARROW_ROW_HINT, color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * 소개 그림: 커버 화면 테두리 안에서 꽉 찬 앱이 세로로 좁아지며 양옆에 검은 여백이 생기고, 잠시 뒤 다시 넓어진다.
 * 좁아진 앱은 고른 쪽([side])에 붙는다.
 */
@Composable
private fun NarrowArt(side: NarrowApps.Side) {
    val t = artLoop(3600)
    ArtCanvas(150.dp) { c, _ ->
        fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        // 0.15~0.4 좁아짐, 0.75~0.95 다시 넓어짐
        val k = if (t < 0.6f) ease((t - 0.15f) / 0.25f) else 1f - ease((t - 0.75f) / 0.2f)
        val h = size.height - 8.dp.toPx()
        val w = h * 1248f / 1972f
        val left = (size.width - w) / 2
        val top = 4.dp.toPx()
        val r = CornerRadius(12.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.85f * k), Offset(left, top), Size(w, h), r)
        val inset = 3.dp.toPx()
        val fullW = w - inset * 2
        val appH = h - inset * 2
        val narrowW = appH * 9f / 16f
        val appW = fullW + (narrowW - fullW) * k
        val pos = when (side) {
            NarrowApps.Side.LEFT -> 0f
            NarrowApps.Side.CENTER -> 0.5f
            NarrowApps.Side.RIGHT -> 1f
        }
        val x = left + inset + (fullW - appW) * pos
        drawRoundRect(c.surface, Offset(x, top + inset), Size(appW, appH), CornerRadius(9.dp.toPx()))
        // 앱 안의 줄(글·버튼): 폭에 맞춰 함께 줄어든다
        val pad = 8.dp.toPx()
        val lineH = 5.dp.toPx()
        listOf(0.18f to 0.7f, 0.3f to 0.9f, 0.4f to 0.55f, 0.52f to 0.8f).forEach { (y, len) ->
            drawRoundRect(c.ink.copy(alpha = 0.55f), Offset(x + pad, top + appH * y), Size((appW - pad * 2) * len, lineH), CornerRadius(lineH / 2))
        }
        drawRoundRect(c.ink, Offset(x + appW * 0.25f, top + appH * 0.8f), Size(appW * 0.5f, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
        drawRoundRect(c.ink, Offset(left, top), Size(w, h), r, style = Stroke(1.6.dp.toPx()))
    }
}
