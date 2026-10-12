package com.labok.goat.ui.tweaks

import android.provider.Settings
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.Hotspot
import com.labok.goat.ui.components.CommandBox
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.copyText
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 핫스팟 제한 우회(초보자용 단계 화면):
 * 소개 → (Shizuku 연결) → 경고(처음 한 번 동의) → [제한 풀기] 한 번 → 적용됨이면 [핫스팟 설정 열기]·[원래대로]
 * → 자동이 막힌 기기면 '직접 하기' 단계 안내(번호·복사 버튼·APN 추가 화면·[다 했어요]) → 되돌리기(항상 보임).
 * 기기마다 막히는 곳이 달라, 마지막 시도의 출처·실패 이유를 한 줄로 보여 준다.
 * @param onConnect Shizuku 연결 안내를 연다.
 */
@Composable
internal fun HotspotScreen(contentPadding: PaddingValues, onConnect: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuAccess.State.READY
    var agreed by remember { mutableStateOf(Hotspot.agreed(context)) }
    var applied by remember { mutableStateOf<Boolean?>(null) }
    var apn by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ShizukuAccess.watch(context) }
    fun refresh() { applied = Hotspot.applied(context) }
    LifecycleResumeEffect(ready) {
        if (ready) scope.launch {
            withContext(Dispatchers.IO) { refresh(); apn = Hotspot.current()?.apn?.takeIf { it.isNotEmpty() } }
            note = Hotspot.lastNote
        }
        onPauseOrDispose { }
    }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_LONG).show()
    fun openTether() {
        // 삼성·AOSP 테더링 화면(숨은 동작) → 안 되면 무선 설정
        val tries = listOf(Intent("android.settings.TETHER_SETTINGS"), Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        tries.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }
    fun run(block: () -> Unit) { busy = true; scope.launch { withContext(Dispatchers.IO) { block(); refresh() }; note = Hotspot.lastNote; busy = false } }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.HOTSPOT_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HOTSPOT_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!ready) item(key = "connect") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.GOS_NEED_SHIZUKU_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DOZE_NEED_SHIZUKU, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.GOS_CONNECT, onClick = onConnect, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "warn") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(accent = if (agreed) null else c.ink) {
                    Text(Copy.HOTSPOT_WARN_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HOTSPOT_WARN_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(Copy.HOTSPOT_WARN_BODY2, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    if (!agreed) {
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.HOTSPOT_WARN_OK, modifier = Modifier.fillMaxWidth(), onClick = { Hotspot.agree(context); agreed = true })
                    }
                }
            }
        }
        // 1단계: 버튼 하나(적용 전) / 적용됨(적용 후)
        if (agreed) item(key = "main") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard(accent = if (applied == true) c.ink else null) {
                    Text(if (applied == true) Copy.HOTSPOT_APPLIED_TITLE else Copy.HOTSPOT_STEP_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(if (applied == true) Copy.HOTSPOT_APPLIED_BODY else Copy.HOTSPOT_STEP_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    if (note.isNotEmpty()) Text(note, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    if (applied == true) {
                        LiquidButton(Copy.HOTSPOT_OPEN_TETHER, modifier = Modifier.fillMaxWidth(), onClick = { openTether() })
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.HOTSPOT_ROLLBACK, modifier = Modifier.fillMaxWidth(), primary = false, enabled = ready && !busy, onClick = {
                            run { val ok = Hotspot.remove(context); scope.launch { toast(if (ok) Copy.HOTSPOT_TOAST_REMOVED else Copy.HOTSPOT_TOAST_REMOVE_FAILED) } }
                        })
                    } else {
                        LiquidButton(Copy.HOTSPOT_UNLOCK, modifier = Modifier.fillMaxWidth(), enabled = ready && !busy, onClick = {
                            run {
                                val r = Hotspot.add(context)
                                scope.launch {
                                    when (r) {
                                        Hotspot.Result.ADDED -> { toast(Copy.HOTSPOT_TOAST_ADDED); openTether() }
                                        Hotspot.Result.ALREADY -> toast(Copy.HOTSPOT_TOAST_ALREADY)
                                        Hotspot.Result.NO_SHIZUKU -> toast(Copy.GOS_TOAST_FAILED)
                                        Hotspot.Result.NO_APN, Hotspot.Result.BLOCKED -> manual = true
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
        // 2단계(자동이 막힌 기기): 번호 순서대로 따라 하는 직접 하기
        if (agreed && applied != true && manual) item(key = "manual") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.HOTSPOT_GUIDE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HOTSPOT_GUIDE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Section))
                    Text("① APN 추가 화면을 엽니다", color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.HOTSPOT_OPEN_EDITOR, modifier = Modifier.fillMaxWidth(), primary = false, enabled = ready, onClick = {
                        scope.launch { if (!withContext(Dispatchers.IO) { Hotspot.openEditor() }) toast(Copy.GOS_TOAST_FAILED) }
                    })
                    Spacer(Modifier.height(Gap.Section))
                    Text("② 이름 칸에 복사해 붙여 넣습니다", color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    CommandBox(Hotspot.NAME, onCopy = { copyText(context, Hotspot.NAME) })
                    Spacer(Modifier.height(Gap.Section))
                    Text("③ APN 칸에 붙여 넣습니다", color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    CommandBox(apn ?: "통신사 기본 APN(모르면 고객센터에 문의)", onCopy = { apn?.let { copyText(context, it) } })
                    Spacer(Modifier.height(Gap.Section))
                    Text("④ APN 유형 칸에 붙여 넣고 저장합니다", color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    CommandBox(Hotspot.DUN_FULLWIDTH, onCopy = { copyText(context, Hotspot.DUN_FULLWIDTH) })
                    Spacer(Modifier.height(Gap.Section))
                    Text("⑤ 저장했으면 눌러 주세요", color = c.ink)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.HOTSPOT_DONE_MANUAL, modifier = Modifier.fillMaxWidth(), onClick = {
                        run { if (Hotspot.ours().orEmpty().isEmpty()) Hotspot.setApplied(context, true) }
                    })
                }
            }
        }
        // 되돌리기(언제든): 동의했으면 늘 보인다
        if (agreed) item(key = "rollback") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.HOTSPOT_ROLLBACK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.HOTSPOT_ROLLBACK_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.HOTSPOT_RESTORE_ALL, modifier = Modifier.fillMaxWidth(), primary = false, enabled = ready && !busy, onClick = {
                        run { val ok = Hotspot.restoreDefaults(context); scope.launch { toast(if (ok) Copy.HOTSPOT_TOAST_RESTORED else Copy.HOTSPOT_TOAST_RESTORE_FAILED) } }
                    })
                    Spacer(Modifier.height(Gap.Item))
                    Text(Copy.HOTSPOT_RESTORE_HINT, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun HotspotHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "일부 통신사는 핫스팟(테더링) 데이터를 별도 APN(유형 dun)으로만 보내게 해 두고 그 경로를 제한합니다. 지금 쓰는 인터넷 APN과 같은 값으로 " +
                "유형만 dun인 APN을 하나 더하면, 핫스팟도 일반 데이터 경로를 씁니다.",
        )
        HelpTopic(
            "2. 꼭 알아 두세요",
            "요금제의 테더링 제한을 우회하므로 통신사 약관 위반이 될 수 있고, 그 책임은 사용자에게 있습니다. 통신사·요금제·One UI 버전에 따라 동작하지 않을 수 있습니다.",
        )
        HelpTopic(
            "3. 추가하는 방법",
            "[테더링 APN 추가]를 누르면 Shizuku로 지금 쓰는 APN을 읽어 같은 값의 APN을 '${Hotspot.NAME}' 이름으로 넣습니다. " +
                "기기가 이를 막으면 [APN 추가 화면 열기]로 숨겨진 APN 추가 화면을 열고, 이름·APN·APN 유형을 복사해 넣은 뒤 저장합니다. " +
                "APN 유형 칸에 'dun' 글자를 막는 기기가 있어 전각 글자(${Hotspot.DUN_FULLWIDTH})를 씁니다. 추가한 뒤 핫스팟을 껐다 켜세요.",
        )
        HelpTopic(
            "4. 되돌리기",
            "[추가한 APN 지우기]로 GOAT가 넣은 APN을 지웁니다. 직접 추가했거나 지우지 못하면 설정 → 일반 → 초기화 → 모바일 네트워크 설정 초기화로 원래대로 돌아갑니다.",
        )
    }
}
