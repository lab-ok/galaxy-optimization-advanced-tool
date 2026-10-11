package com.labok.goat.data.tweaks

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.labok.goat.data.log.LogRepository
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
        AUTOMATIC("자동(기본)", null, "통신사·와이파이의 DNS를 쓰고, 지원하면 암호화합니다."),
        ADGUARD("AdGuard 광고 차단", "dns.adguard-dns.com", "광고·추적·악성 사이트를 막습니다. 가장 많이 쓰는 설정입니다."),
        ADGUARD_FAMILY("AdGuard 가족 보호", "family.adguard-dns.com", "광고 차단에 더해 성인 사이트를 막고 안전 검색을 켭니다."),
        CLOUDFLARE("Cloudflare", "one.one.one.one", "광고는 막지 않고, 빠르고 기록을 남기지 않는 DNS입니다."),
        CLOUDFLARE_SECURITY("Cloudflare 악성 차단", "security.cloudflare-dns.com", "피싱·악성 코드 사이트만 막습니다."),
        QUAD9("Quad9", "dns.quad9.net", "비영리 단체가 운영하며 악성 사이트를 막습니다."),
        CUSTOM("직접 입력", null, "NextDNS 등 다른 서버 주소를 적습니다."),
        DISABLED("끄기", null, "개인 DNS를 쓰지 않습니다."),
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

    /** 서버 주소로 쓸 수 있는 꼴인지(점이 있는 도메인, 글자·숫자·하이픈). */
    fun validHost(host: String): Boolean =
        host.length in 4..253 && '.' in host && host.split('.').all { it.isNotEmpty() && it.length <= 63 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' } && !it.startsWith('-') && !it.endsWith('-') }

    /**
     * [provider]로 바꾼다([custom]은 직접 입력 주소). 처음 바꿀 때 원래 값을 남긴다. 성공하면 true.
     */
    fun set(context: Context, provider: Provider, custom: String? = null, how: String): Boolean {
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
        val s = saved(context) ?: return set(context, Provider.AUTOMATIC, how = how)
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
     * 바꾼 직후 연결을 1초마다 확인한다. 바꾼 직후 시스템은 이전 서버의 '확인됨'을 잠깐 그대로 두므로 연결됨이 [STABLE]번
     * 잇달아 나와야 성공으로 보고, 실패가 [FAILS]번 잇달아 나오거나 [CHECK_MS] 안에 확정되지 않으면 [previous]로 되돌린다.
     */
    fun verify(context: Context, previous: Current) {
        val app = context.applicationContext
        checking?.cancel()
        linkState.value = Link.CHECKING
        checking = scope.launch {
            val start = System.currentTimeMillis()
            var streak = 0
            var fails = 0
            var result: Link
            while (true) {
                delay(1000)
                val now = connection(app)
                streak = if (now == Link.CONNECTED) streak + 1 else 0
                fails = if (now == Link.FAILED) fails + 1 else 0
                // 다시 확인하는 동안 잠깐 '확인 안 됨'이 될 수 있어 실패도 [FAILS]번 잇달아 나와야 실패로 본다
                result = when {
                    now == Link.CONNECTED && streak < STABLE -> Link.CHECKING
                    now == Link.FAILED && fails < FAILS -> Link.CHECKING
                    else -> now
                }
                if (result == Link.CONNECTED || result == Link.FAILED) break
                if (System.currentTimeMillis() - start > CHECK_MS) { result = Link.FAILED; break }
            }
            val revert = result == Link.FAILED
            if (revert) set(app, previous.provider, previous.host, "연결되지 않아 자동 되돌림")
            linkState.value = connection(app)
            results.tryEmit(revert)
        }
    }

    /** 바꾼 뒤 연결을 기다리는 최대 시간. 이 안에 연결되지 않으면 되돌린다. */
    const val CHECK_MS = 12_000L
    /** 연결됨이 이만큼 잇달아(1초 간격) 나와야 성공으로 본다. */
    private const val STABLE = 4
    /** 실패가 이만큼 잇달아 나와야 실패로 본다. */
    private const val FAILS = 3

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
