package com.labok.goat.data.tweaks

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 개인 DNS: 설정 > 연결 > 기타 연결 설정 > 개인 DNS와 같은 전역 설정([MODE]·[HOST])을 바꾼다.
 * 광고·추적 도메인을 막아 주는 DNS 서버(DNS over TLS)를 고르면 모든 앱과 브라우저의 광고가 줄어든다.
 * 전역 설정이라 고급 화면 배율 설정과 같은 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로 바꾸고, 재부팅해도 유지된다.
 *
 * 바꾸기 전 값은 한 번만 [Saved]로 남겨 '원래대로'로 되돌린다. 바꾼 뒤에는 [verify]가 시스템이 그 서버에 실제로
 * 연결됐는지([connection])를 지켜보고, 시간 안에 연결되지 않으면 바꾸기 전 서버로 저절로 되돌린다(서버가 막힌 와이파이에서
 * 인터넷이 끊긴 채 남지 않게). 화면을 떠나도 끝까지 확인하도록 화면이 아니라 여기서 돈다.
 */
object PrivateDns {
    /** 시스템 설정 키(Settings.Global.PRIVATE_DNS_MODE·SPECIFIER와 같다, 숨은 상수라 문자열로 둔다). */
    const val MODE = "private_dns_mode"
    const val HOST = "private_dns_specifier"
    private const val OFF = "off"
    private const val AUTO = "opportunistic"
    private const val HOSTNAME = "hostname"
    private const val PREF = "private_dns"

    /** 고를 수 있는 서버. [host]가 null이면 서버 없이 끔·자동. */
    enum class Provider(val label: String, val host: String?, val desc: String) {
        AUTOMATIC(Copy.DNS_P_AUTO, null, Copy.DNS_P_AUTO_DESC),
        ADGUARD(Copy.DNS_P_ADGUARD, "dns.adguard-dns.com", Copy.DNS_P_ADGUARD_DESC),
        ADGUARD_FAMILY(Copy.DNS_P_FAMILY, "family.adguard-dns.com", Copy.DNS_P_FAMILY_DESC),
        CLOUDFLARE(Copy.DNS_P_CLOUDFLARE, "one.one.one.one", Copy.DNS_P_CLOUDFLARE_DESC),
        CLOUDFLARE_SECURITY(Copy.DNS_P_CF_SECURITY, "security.cloudflare-dns.com", Copy.DNS_P_CF_SECURITY_DESC),
        QUAD9(Copy.DNS_P_QUAD9, "dns.quad9.net", Copy.DNS_P_QUAD9_DESC),
        CUSTOM(Copy.DNS_P_CUSTOM, null, Copy.DNS_P_CUSTOM_DESC),
        DISABLED(Copy.DNS_P_OFF, null, Copy.DNS_P_OFF_DESC),
        ;

        /** 광고를 막는 서버인지(그림에서 광고 칸이 사라지는지). 직접 입력은 대개 NextDNS 같은 차단 서버라 막는 것으로 본다. */
        val blocksAds: Boolean get() = this == ADGUARD || this == ADGUARD_FAMILY || this == CUSTOM
    }

    /** 지금 설정. [host]는 hostname 모드일 때만. */
    data class Current(val mode: String, val host: String?) {
        val provider: Provider
            get() = when (mode) {
                OFF -> Provider.DISABLED
                HOSTNAME -> Provider.entries.firstOrNull { it.host != null && it.host.equals(host, ignoreCase = true) } ?: Provider.CUSTOM
                else -> Provider.AUTOMATIC
            }

        /** 화면·홈 카드에 쓰는 한 줄. */
        val label: String
            get() = provider.let { if (it == Provider.CUSTOM) host.orEmpty() else it.label }
    }

    fun read(context: Context): Current {
        val r = context.contentResolver
        // 값이 없으면 시스템 기본값(자동)이다
        val mode = runCatching { Settings.Global.getString(r, MODE) }.getOrNull() ?: AUTO
        val host = runCatching { Settings.Global.getString(r, HOST) }.getOrNull()
        return Current(mode, host.takeIf { mode == HOSTNAME })
    }

    /** 서버를 골라 쓰는 중인지(홈 카드 '켜짐'). 자동·끄기는 쓰지 않는 것으로 본다. */
    fun inUse(context: Context) = read(context).provider.let { it != Provider.AUTOMATIC && it != Provider.DISABLED }

    /** 서버 주소로 쓸 수 있는 꼴인지(점이 있는 도메인, 영문 소문자·숫자·하이픈만. 대문자는 [set]이 소문자로 바꾼다). */
    fun validHost(host: String): Boolean =
        host.length in 4..253 && '.' in host && host.split('.').all { part ->
            part.isNotEmpty() && part.length <= 63 && part.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } && !part.startsWith('-') && !part.endsWith('-')
        }

    /**
     * [provider]로 바꾼다([custom]은 직접 입력 주소). 처음 바꿀 때 원래 값을 남긴다. 성공하면 true.
     */
    fun set(context: Context, provider: Provider, custom: String? = null, how: String): Boolean {
        // 사용자가 새로 고르면 앞 선택의 연결 확인은 그만둔다(끝난 뒤 앞 선택으로 되돌리지 않게)
        checking?.cancel()
        return change(context, provider, custom, how)
    }

    private fun change(context: Context, provider: Provider, custom: String?, how: String): Boolean {
        val before = read(context)
        remember(context, before)
        val host = if (provider == Provider.CUSTOM) custom?.trim()?.lowercase() else provider.host
        if (provider == Provider.CUSTOM && (host == null || !validHost(host))) {
            LogRepository.from(context).add("개인 DNS($how): 주소 '${custom.orEmpty()}'가 올바르지 않아 바꾸지 않음")
            return false
        }
        val wrote = write(context, when (provider) { Provider.DISABLED -> OFF; Provider.AUTOMATIC -> AUTO; else -> HOSTNAME }, host)
        // 다시 읽어 실제로 바뀌었는지 확인한다
        val after = read(context)
        val ok = wrote && after.provider == provider && (host == null || after.host.equals(host, ignoreCase = true))
        LogRepository.from(context).add(
            "개인 DNS($how): ${before.label} → ${after.label}" + if (ok) "" else " 실패(${if (wrote) "결과 다름" else "시스템 설정 권한 없음"})",
        )
        return ok
    }

    /** GOAT로 처음 바꾸기 전의 값(되돌리기용). */
    data class Saved(val mode: String, val host: String?)

    fun saved(context: Context): Saved? = prefs(context).getString("mode", null)?.let { Saved(it, prefs(context).getString("host", null)) }

    /** 처음 바꾸기 전 값으로 되돌리고 기억을 지운다. */
    fun restore(context: Context, how: String): Boolean {
        checking?.cancel()
        val s = saved(context) ?: return change(context, Provider.AUTOMATIC, null, how)
        val before = read(context)
        val ok = write(context, s.mode, s.host) && read(context).mode == s.mode
        if (ok) prefs(context).edit().clear().apply()
        LogRepository.from(context).add("개인 DNS 원래대로($how): ${before.label} → ${Current(s.mode, s.host).label}${if (ok) "" else " 실패"}")
        return ok
    }

    private fun remember(context: Context, current: Current) {
        if (saved(context) != null) return
        prefs(context).edit().putString("mode", current.mode).putString("host", current.host).apply()
    }

    private fun write(context: Context, mode: String, host: String?): Boolean = runCatching {
        val r = context.contentResolver
        // 주소를 먼저 쓰고 모드를 바꾼다: 모드가 바뀌는 순간 시스템이 새 주소로 연결을 시작한다
        if (host != null) Settings.Global.putString(r, HOST, host)
        Settings.Global.putString(r, MODE, mode)
    }.getOrDefault(false)

    /** 지금 인터넷 연결에서 개인 DNS가 실제로 어떻게 동작하는지. */
    enum class Link {
        /** 고른 서버에 암호화해 연결됐다. */
        CONNECTED,
        /** 서버에 연결하지 못했다(이 네트워크에서 막혔거나 주소가 틀림). 인터넷이 끊길 수 있다. */
        FAILED,
        /** 확인 중(바꾼 직후). */
        CHECKING,
        /** 인터넷 연결이 없거나 개인 DNS를 쓰지 않는다. */
        NONE,
    }

    /**
     * 시스템이 판단한 지금 연결 상태. 서버를 골랐는데 연결의 개인 DNS 서버 이름이 그 주소이고 인터넷 확인(VALIDATED)을
     * 통과하면 연결됨, 확인을 통과하지 못하면 실패로 본다(바꾼 직후 몇 초는 확인 중일 수 있다).
     */
    fun connection(context: Context): Link {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Link.NONE
        val net = cm.activeNetwork ?: return Link.NONE
        val lp = runCatching { cm.getLinkProperties(net) }.getOrNull() ?: return Link.NONE
        val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull() ?: return Link.NONE
        val want = read(context)
        if (want.mode != HOSTNAME) return Link.NONE
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            lp.isPrivateDnsActive && lp.privateDnsServerName.equals(want.host, ignoreCase = true) && validated -> Link.CONNECTED
            lp.privateDnsServerName.equals(want.host, ignoreCase = true) && !validated -> Link.FAILED
            else -> Link.CHECKING
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var checking: Job? = null
    private val linkState = MutableStateFlow(Link.NONE)

    /** 지금 연결 상태(확인 중에는 [Link.CHECKING]). */
    val link: StateFlow<Link> = linkState.asStateFlow()

    /** 확인이 끝났을 때 한 번: true면 연결되지 않아 되돌렸다. 화면이 알림·진동에 쓴다. */
    private val results = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val verified: SharedFlow<Boolean> = results.asSharedFlow()

    /** 연결 상태를 다시 읽는다(화면에 돌아올 때). 확인 중이면 그대로 둔다. */
    fun refresh(context: Context) {
        if (checking?.isActive == true) return
        linkState.value = connection(context)
    }

    /**
     * 바꾼 직후 연결을 1초마다 [Judge]에 넣어 판정한다. 실패로 판정되면 [previous]로 되돌린다.
     * 인터넷이 없으면(NONE) 실패로 세지 않고 '인터넷에 연결되면 확인' 상태로 끝낸다. 사용자가 그 사이 다른 것을 고르면
     * [set]이 이 확인을 취소하고, 혹시 설정이 확인 대상과 달라져 있으면 되돌리지 않고 끝낸다.
     */
    fun verify(context: Context, previous: Current) {
        val app = context.applicationContext
        val target = read(app)
        checking?.cancel()
        linkState.value = Link.CHECKING
        checking = scope.launch {
            val judge = Judge()
            var result: Link
            while (true) {
                delay(1000)
                if (read(app) != target) { linkState.value = connection(app); return@launch }
                result = judge.feed(connection(app)) ?: continue
                break
            }
            val log = LogRepository.from(app)
            when (result) {
                Link.FAILED -> {
                    log.add("개인 DNS 연결 확인: ${target.label}에 연결되지 않음 → 이전 설정으로 되돌림")
                    change(app, previous.provider, previous.host, "연결되지 않아 자동 되돌림")
                }
                Link.CONNECTED -> log.add("개인 DNS 연결 확인: ${target.label} 암호화 연결됨")
                else -> log.add("개인 DNS 연결 확인: 인터넷 연결이 없어 확인을 미룸(되돌리지 않음)")
            }
            linkState.value = connection(app)
            results.tryEmit(result == Link.FAILED)
        }
    }

    /**
     * 1초마다 읽은 연결 상태로 확인 결과를 정한다(시험할 수 있게 시간·시스템과 떼어 둔다).
     * - 바꾼 직후 시스템은 이전 서버의 '확인됨'을 잠깐 그대로 두므로 연결됨이 [STABLE]번 잇달아 나와야 성공.
     * - 다시 확인하는 동안 잠깐 '확인 안 됨'이 될 수 있어 실패도 [FAILS]번 잇달아 나와야 실패.
     * - [LIMIT]번(=[CHECK_MS]) 안에 확정되지 않으면: 인터넷이 없던(NONE) 상태면 NONE(되돌리지 않음), 아니면 실패.
     * 확정되기 전에는 null.
     */
    internal class Judge {
        private var ok = 0
        private var bad = 0
        private var count = 0

        fun feed(now: Link): Link? {
            count++
            ok = if (now == Link.CONNECTED) ok + 1 else 0
            bad = if (now == Link.FAILED) bad + 1 else 0
            return when {
                ok >= STABLE -> Link.CONNECTED
                bad >= FAILS -> Link.FAILED
                count >= LIMIT -> if (now == Link.NONE) Link.NONE else Link.FAILED
                else -> null
            }
        }
    }

    /** 바꾼 뒤 연결을 기다리는 최대 시간. 이 안에 연결되지 않으면 되돌린다. */
    const val CHECK_MS = 12_000L
    /** [CHECK_MS] 동안 1초마다 읽는 횟수. */
    private const val LIMIT = (CHECK_MS / 1000).toInt()
    /** 연결됨이 이만큼 잇달아(1초 간격) 나와야 성공으로 본다. */
    internal const val STABLE = 4
    /** 실패가 이만큼 잇달아 나와야 실패로 본다. */
    internal const val FAILS = 3

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
