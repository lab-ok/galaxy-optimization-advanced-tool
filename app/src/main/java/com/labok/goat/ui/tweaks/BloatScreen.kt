package com.labok.goat.ui.tweaks

import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.Bloat
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitch
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 기본 앱 정리: 이 기기에 있는 목록 앱을 묶음별로 보여 주고, 스위치로 끄거나(멈춤) 다시 켠다. '주의' 앱은 한 번 더 묻는다.
 * 지우지 않고 끄기만 하므로 언제든 되돌릴 수 있다.
 */
@Composable
internal fun BloatScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    // 패키지 → 꺼져 있는지. null이면 아직 못 읽음.
    var found by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Bloat.App?>(null) }

    fun rescan() = scope.launch { found = withContext(Dispatchers.IO) { Bloat.scan() } }
    fun apply(app: Bloat.App, off: Boolean) {
        busy = app.pkg
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Bloat.set(context, app, off, "스위치") }
            busy = null
            if (!ok) Toast.makeText(context, Copy.BLOAT_FAILED, Toast.LENGTH_SHORT).show()
            rescan()
        }
    }

    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    LifecycleResumeEffect(ready) {
        if (ready) rescan()
        onPauseOrDispose { }
    }

    val apps = Bloat.CATALOG.filter { found?.containsKey(it.pkg) == true }
    // 앱 아이콘(꺼 둔 앱도 읽는다). 매니페스트 queries에 목록 패키지를 적어 두어 보인다.
    var icons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    LaunchedEffect(found) {
        val pkgs = found?.keys ?: return@LaunchedEffect
        icons = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pkgs.mapNotNull { p ->
                runCatching {
                    val info = pm.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                    p to pm.getApplicationIcon(info).toBitmap(iconPx, iconPx).asImageBitmap()
                }.getOrNull()
            }.toMap()
        }
    }
    val offCount = found?.count { it.value } ?: 0

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.BLOAT_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.BLOAT_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    CleanupArt()
                    if (found != null) {
                        Spacer(Modifier.height(Gap.Tight))
                        Text("이 기기에서 정리할 수 있는 앱 ${apps.size}개 · 꺼 둔 앱 ${offCount}개", color = c.ink, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (!ready) item(key = "connect") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.CAMERA_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.BLOAT_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.CAMERA_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        apps.groupBy { it.group }.forEach { (group, list) ->
            item(key = "group:$group") {
                Text(
                    group, fontWeight = FontWeight.SemiBold, color = c.ink,
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = Gap.CardPadding, top = Gap.Tight),
                )
            }
            items(list, key = { it.pkg }) { app ->
                val off = found?.get(app.pkg) == true
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            icons[app.pkg]?.let { bmp ->
                                Image(
                                    bmp, null,
                                    modifier = Modifier.size(40.dp).graphicsLayer { alpha = if (off) 0.4f else 1f },
                                    colorFilter = if (off) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.name + if (app.risk == Bloat.Risk.CARE) " · 주의" else "",
                                    fontWeight = FontWeight.SemiBold, color = if (off) c.muted else c.ink,
                                )
                                Text(app.note, color = c.muted, style = MaterialTheme.typography.bodySmall)
                                Text(if (off) "꺼 둠" else "켜져 있음", color = c.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            LiquidSwitch(!off, { on ->
                                if (!on && app.risk == Bloat.Risk.CARE) confirm = app else apply(app, off = !on)
                            }, enabled = ready && busy == null)
                        }
                    }
                }
            }
        }
        if (offCount > 0) item(key = "restore") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                LiquidButton(Copy.BLOAT_RESTORE_ALL, primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            Bloat.CATALOG.filter { found?.get(it.pkg) == true }.forEach { Bloat.set(context, it, off = false, how = "모두 다시 켜기") }
                        }
                        rescan()
                    }
                })
            }
        }
    }

    confirm?.let { app ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${app.name}을(를) 끌까요?") },
            text = { Text(app.note + " 언제든 다시 켤 수 있습니다.") },
            confirmButton = { TextButton(onClick = { confirm = null; apply(app, off = true) }) { Text("끄기") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
}

/** 사용 방법: 무엇을 하는지, 지우기와 끄기의 차이, Shizuku, 되돌리기, 업데이트 뒤. */
@Composable
internal fun BloatHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "쓰지 않는 삼성·제휴 기본 앱을 꺼서, 앱 목록·알림·백그라운드 동작에서 빼냅니다. 커뮤니티에서 많이 끄는 앱 가운데 " +
                "꺼도 전화·문자·설정·카메라 같은 기본 기능이 멈추지 않는 것만 골라 두었습니다.",
        ) { CleanupArt() }
        HelpTopic(
            "2. 지우지 않고 끕니다",
            "앱을 지우지 않고 '사용 안 함'으로 멈춥니다(pm disable-user). 저장 공간은 그대로지만, 스위치 하나로 언제든 다시 켤 수 있고 " +
                "공장 초기화 없이 원래대로 돌아옵니다. '주의'가 붙은 앱은 끄기 전에 무엇이 달라지는지 한 번 더 알려 줍니다.",
        )
        HelpTopic(
            "3. Shizuku가 필요한 이유",
            "앱을 끄는 명령은 셸 권한이 있어야 실행됩니다. Shizuku를 연결하면 GOAT가 그 권한으로 끄고 켭니다.",
        )
        HelpTopic(
            "4. 업데이트 뒤",
            "시스템 업데이트나 재부팅 뒤 다시 켜지는 앱(게임 최적화 서비스 등)도 있습니다. 화면을 열면 지금 상태를 다시 읽으니, 켜져 있으면 다시 끄세요.",
        )
    }
}
