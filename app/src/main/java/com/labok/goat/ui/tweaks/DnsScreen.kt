package com.labok.goat.ui.tweaks

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.data.tweaks.PrivateDns
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.rememberLiquidHaptics
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 개인 DNS: 지금 서버, 서버 고르기(누르면 바로 적용), 직접 입력, 연결 확인, 원래대로, 주의.
 * 서버를 바꾸면 [PrivateDns.verify]가 시스템이 그 서버에 실제로 연결되는지 지켜보고, 연결되지 않으면 바꾸기 전 서버로
 * 저절로 되돌린다(막힌 와이파이에서 인터넷이 끊긴 채 남지 않게). 다른 곳(설정 앱)에서 바꿔도 돌아올 때 다시 읽는다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DnsScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    var version by remember { mutableIntStateOf(0) }
    val current = remember(version) { PrivateDns.read(context) }
    val link by PrivateDns.link.collectAsStateWithLifecycle()
    // 직접 입력 칸: 펼쳤는지와 적은 주소(지금 직접 입력 주소를 쓰는 중이면 그 주소로 시작)
    var customOpen by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(current.host?.takeIf { current.provider == PrivateDns.Provider.CUSTOM }.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { version++; PrivateDns.refresh(context); onPauseOrDispose { } }
    // 연결 확인이 끝나면: 되돌렸으면 알리고, 연결됐으면 가볍게 톡
    LaunchedEffect(Unit) {
        PrivateDns.verified.collect { reverted ->
            version++
            if (reverted) { haptics.reject(); Toast.makeText(context, Copy.DNS_TOAST_REVERTED, Toast.LENGTH_LONG).show() } else haptics.tick()
        }
    }

    fun choose(provider: PrivateDns.Provider, host: String? = null) {
        if (!permitted || busy) return
        val before = PrivateDns.read(context)
        if (provider != PrivateDns.Provider.CUSTOM && provider == before.provider) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { PrivateDns.set(context, provider, host, "고르기") }
            busy = false
            version++
            when {
                !ok -> {
                    haptics.reject()
                    Toast.makeText(context, if (provider == PrivateDns.Provider.CUSTOM) Copy.DNS_TOAST_BAD_HOST else Copy.DNS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
                }
                // 서버를 고른 경우만 연결을 확인한다(자동·끄기는 확인할 서버가 없다)
                provider != PrivateDns.Provider.AUTOMATIC && provider != PrivateDns.Provider.DISABLED -> { haptics.snap(); PrivateDns.verify(context, before) }
                else -> { haptics.snap(); PrivateDns.refresh(context) }
            }
        }
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        // 직접 입력 칸을 누르면 키보드가 올라온 만큼 목록을 줄여, 칸이 키보드에 가리지 않고 보이는 곳으로 올라오게 한다
        modifier = Modifier.fillMaxWidth().imePadding(),
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DNS_INTRO_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DNS_INTRO_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    val shown = current.provider.takeIf { it != PrivateDns.Provider.AUTOMATIC && it != PrivateDns.Provider.DISABLED } ?: PrivateDns.Provider.ADGUARD
                    DnsArt(blocking = shown.blocksAds, label = if (shown == PrivateDns.Provider.CUSTOM) "직접 입력" else shown.label)
                }
            }
        }
        if (!permitted) item(key = "permission") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DNS_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "now") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DNS_NOW_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(
                        current.label + " · " + when {
                            current.provider == PrivateDns.Provider.AUTOMATIC -> Copy.DNS_LINK_AUTO
                            current.provider == PrivateDns.Provider.DISABLED -> Copy.DNS_LINK_OFF
                            link == PrivateDns.Link.CONNECTED -> Copy.DNS_LINK_CONNECTED
                            link == PrivateDns.Link.FAILED -> Copy.DNS_LINK_FAILED
                            link == PrivateDns.Link.CHECKING -> Copy.DNS_LINK_CHECKING
                            else -> Copy.DNS_LINK_NO_NETWORK
                        },
                        color = c.muted, style = MaterialTheme.typography.bodySmall,
                    )
                    if (PrivateDns.saved(context) != null) {
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.DNS_RESTORE, primary = false, enabled = permitted && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { PrivateDns.restore(context, "원래대로 버튼") }
                                busy = false
                                version++
                                PrivateDns.refresh(context)
                                Toast.makeText(context, if (ok) Copy.DNS_TOAST_RESTORED else Copy.DNS_TOAST_FAILED, Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }
        item(key = "providers") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DNS_PICK_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DNS_PICK_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Tight))
                    PrivateDns.Provider.entries.forEach { p ->
                        val selected = current.provider == p
                        LiquidRadioRow(p.label, p.desc, selected, enabled = permitted && !busy) {
                            haptics.segment()
                            if (p == PrivateDns.Provider.CUSTOM) customOpen = !customOpen else { customOpen = false; choose(p) }
                        }
                        if (p == PrivateDns.Provider.CUSTOM) AnimatedVisibility(
                            customOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
                        ) {
                            // 칸을 누르면 키보드가 올라오는 동안 따라 올려, 칸과 '바꾸기' 버튼이 함께 키보드 위에 보이게 한다
                            val bring = remember { BringIntoViewRequester() }
                            var focused by remember { mutableStateOf(false) }
                            val ime = WindowInsets.ime.getBottom(LocalDensity.current)
                            LaunchedEffect(focused, ime) { if (focused && ime > 0) bring.bringIntoView() }
                            Column(
                                Modifier
                                    .padding(start = 36.dp)
                                    .bringIntoViewRequester(bring)
                                    .onFocusChanged { focused = it.hasFocus },
                            ) {
                                Spacer(Modifier.height(Gap.Tight))
                                LiquidTextField(
                                    custom, { custom = it.trim() }, Copy.DNS_CUSTOM_HINT,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                )
                                Spacer(Modifier.height(Gap.Item))
                                LiquidButton(
                                    Copy.DNS_CUSTOM_APPLY, enabled = permitted && !busy && PrivateDns.validHost(custom.lowercase()),
                                    modifier = Modifier.fillMaxWidth(), onClick = { choose(PrivateDns.Provider.CUSTOM, custom) },
                                )
                                Spacer(Modifier.height(Gap.Tight))
                            }
                        }
                    }
                }
            }
        }
        item(key = "caution") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.DNS_CAUTION_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.DNS_CAUTION_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 사용 방법. */
@Composable
internal fun DnsHelpSheet() {
    SheetColumn("사용 방법") {
        HelpTopic(
            "1. 무엇을 하나요",
            "인터넷 주소를 찾아 주는 DNS 서버를 광고 차단 서버로 바꿉니다. 앱과 웹이 광고·추적 주소를 찾으면 서버가 알려 주지 않아 " +
                "광고가 빈칸으로 사라집니다. 앱을 따로 설치하거나 VPN을 켤 필요가 없고, 따로 실행되는 앱이 없어 배터리 부담이 적습니다.",
        ) { DnsArt(blocking = true, label = PrivateDns.Provider.ADGUARD.label) }
        HelpTopic(
            "2. 어떤 서버를 고르나요",
            "광고를 줄이려면 'AdGuard 광고 차단'이 가장 무난합니다. 아이가 쓰는 폰은 'AdGuard 가족 보호', 광고는 그대로 두고 " +
                "악성 사이트만 막으려면 'Cloudflare 악성 차단'이나 'Quad9'을 고르세요. NextDNS처럼 직접 만든 차단 목록이 있으면 '직접 입력'에 주소를 적습니다.",
        )
        HelpTopic(
            "3. 연결 확인과 자동 되돌림",
            "서버를 바꾸면 시스템이 그 서버에 암호화 연결(DNS over TLS)이 되는지 바로 확인합니다. 회사·학교·공공 와이파이처럼 " +
                "이 연결을 막는 곳에서는 인터넷이 끊길 수 있어, ${PrivateDns.CHECK_MS / 1000}초 안에 연결되지 않으면 바꾸기 전 서버로 저절로 되돌립니다. " +
                "인터넷이 아예 없을 때는 되돌리지 않습니다. 연결된 뒤 '지금 서버'에서 연결 상태를 확인할 수 있습니다.",
        )
        HelpTopic(
            "4. 막지 못하는 광고와 되돌리기",
            "유튜브·인스타그램처럼 광고를 본문과 같은 주소에서 보내는 앱의 광고는 막지 못합니다. 어떤 사이트가 열리지 않으면 " +
                "'자동(기본)'으로 바꾸거나 '원래대로'를 누르세요. 설정 > 연결 > 기타 연결 설정 > 개인 DNS에서도 바꿀 수 있습니다.",
        )
        HelpTopic(
            "5. 권한과 유지",
            "고급 화면 배율 설정과 같은 시스템 설정 권한으로 바꾸고, 재부팅해도 유지됩니다. GOAT는 인터넷 권한이 없어 " +
                "어떤 주소를 찾는지 보거나 보내지 않습니다. 연결 여부만 시스템에 묻습니다.",
        )
    }
}
