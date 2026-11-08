package com.labok.goat.data.lab

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.labok.goat.data.log.LogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 실험실 '위스퍼 음성 입력'(11-08 소장님): 삼성 키보드의 마이크를 누르면 삼성 음성 입력 대신 고른 음성 입력기(위스퍼 계열:
 * FUTO Voice Input, WhisperInput 등)로 넘긴다. 삼성 키보드는 음성 입력을 삼성·Google 두 가지로만 고정해 두어(FUTO 문서)
 * 설정으로는 바꿀 수 없다. 삼성 음성 입력(com.samsung.android.svoiceime)이 별도 입력기라면, 마이크를 누를 때 시스템의
 * '지금 입력기'(Secure.DEFAULT_INPUT_METHOD)가 그쪽으로 바뀐다(추정, 조사로 확인). 그 순간 GOAT가 고른 입력기로 바꾸고,
 * 그 입력기가 끝나 다시 삼성 음성 입력으로 돌아오면 삼성 키보드로 되돌린다. 입력기 설정은 이미 받은 보안 설정 쓰기 권한으로 바꾼다.
 *
 * 조사: [startProbe]부터 [stopProbe]까지 입력기 바뀜을 모두 기록해 화면에 보여 주고, 삼성 음성 입력으로 바뀐 적이 있는지 판정한다.
 */
object VoiceRedirect {
    const val SAMSUNG_VOICE = "com.samsung.android.svoiceime"
    private const val PREF = "voice_redirect"

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** 삼성 키보드 마이크로 음성 입력기로 바뀌는 것을 한 번이라도 봤는지(가로챌 수 있는 설정인지). */
    fun triggerSeen(context: Context) = prefs(context).getBoolean("trigger_seen", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        LogRepository.from(context).add("실험 위스퍼 음성 입력: " + if (on) "켬" else "끔")
        if (on) {
            watch(context)
        } else if (!probing.value) {
            unwatch(context)
        }
    }

    /** 넘겨 줄 입력기 id(패키지/클래스). */
    fun target(context: Context): String? = prefs(context).getString("target", null)

    fun setTarget(context: Context, id: String) {
        prefs(context).edit().putString("target", id).apply()
    }

    /** 지금 입력기 id. */
    fun current(context: Context): String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    /** 켜 둔(시스템 설정의 키보드 목록에서 사용으로 둔) 입력기들. */
    fun enabledImes(context: Context): List<InputMethodInfo> = context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty()

    /** 설치된 모든 입력기. */
    fun allImes(context: Context): List<InputMethodInfo> = context.getSystemService(InputMethodManager::class.java)?.inputMethodList.orEmpty()

    /** 넘겨 줄 후보: 삼성 키보드·삼성 음성 입력을 뺀 입력기. 음성 입력 모드를 가진 것을 앞에. */
    fun candidates(context: Context): List<InputMethodInfo> = allImes(context)
        .filter { it.packageName != SAMSUNG_VOICE && !it.packageName.startsWith("com.samsung.android.honeyboard") }
        .sortedByDescending { hasVoice(it) }

    fun hasVoice(info: InputMethodInfo): Boolean = (0 until info.subtypeCount).any { info.getSubtypeAt(it).mode == "voice" }

    fun label(context: Context, id: String?): String {
        if (id == null) return "(없음)"
        val info = allImes(context).firstOrNull { it.id == id } ?: return id
        return info.loadLabel(context.packageManager).toString()
    }

    /** 고른 입력기가 키보드 목록에서 켜져 있는지(꺼져 있으면 시스템이 바꾸기를 받아 주지 않는다). */
    fun targetEnabled(context: Context): Boolean = target(context)?.let { t -> enabledImes(context).any { it.id == t } } ?: false

    /** 위스퍼 음성 입력기 FUTO Voice Input(오프라인 위스퍼, voiceinput.futo.tech). */
    const val FUTO = "org.futo.voiceinput"

    fun futoInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(FUTO, 0) }.isSuccess

    /** FUTO Voice Input의 입력기 id(설치돼 있을 때). */
    fun futoIme(context: Context): String? = allImes(context).firstOrNull { it.packageName == FUTO }?.id

    fun futoMicAllowed(context: Context) = context.packageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, FUTO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * 입력기 [id]를 키보드 목록에서 켠다(설정 화면에 가지 않고). 켜졌으면 true.
     * 키보드 목록 설정(enabled_input_methods)은 targetSdk 34부터 앱이 읽을 수 없어(에뮬레이터에서 앱이 죽음), Shizuku가 있으면
     * `ime enable`을 쓰고, 없으면 지금 켜진 입력기 목록에 [id]를 더해 보안 설정 쓰기로 덮어쓴다.
     */
    fun enableIme(context: Context, id: String): Boolean {
        if (enabledImes(context).any { it.id == id }) return true
        val viaShell = com.labok.goat.data.shizuku.ShizukuAccess.status.value == com.labok.goat.data.shizuku.ShizukuAccess.State.READY &&
            com.labok.goat.data.shizuku.ShizukuAccess.shell("ime enable $id")?.ok == true
        if (!viaShell) {
            val list = (enabledImes(context).map { it.id } + id).joinToString(":")
            runCatching { Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS, list) }
        }
        val ok = enabledImes(context).any { it.id == id }
        LogRepository.from(context).add("실험 위스퍼 음성 입력: 키보드 목록에서 $id 켜기(" + (if (viaShell) "Shizuku" else "설정 쓰기") + ") " + if (ok) "성공" else "실패")
        return ok
    }

    /** 입력기를 [id]로 바꾼다(보안 설정 쓰기). 바뀌었는지 다시 읽어 돌려준다. */
    fun switchTo(context: Context, id: String): Boolean {
        selfId = id
        selfAt = SystemClock.elapsedRealtime()
        runCatching { Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id) }
        return current(context) == id
    }

    // ── 조사 기록 ──
    private val probing = MutableStateFlow(false)
    val probeOn: StateFlow<Boolean> = probing.asStateFlow()
    private val events = MutableStateFlow<List<String>>(emptyList())
    val probeLog: StateFlow<List<String>> = events.asStateFlow()
    private val seen = MutableStateFlow(false)

    /** 조사 중 삼성 음성 입력으로 바뀐 적이 있는지. */
    val sawSamsungVoice: StateFlow<Boolean> = seen.asStateFlow()
    private val direct = MutableStateFlow(false)

    /**
     * 조사 중 삼성 키보드가 음성 모드 입력기(위스퍼 등)로 바로 넘어간 적이 있는지.
     * 11-08 실기: 위스퍼를 키보드 목록에 켜 두자 삼성 키보드 마이크가 삼성 음성 입력을 거치지 않고 위스퍼를 바로 열었다(가로채기 필요 없음).
     */
    val sawDirectVoice: StateFlow<Boolean> = direct.asStateFlow()

    fun startProbe(context: Context) {
        events.value = listOf(line("기록 시작 · 지금 입력기: ${label(context, current(context))} (${current(context)})"))
        seen.value = false
        direct.value = false
        probing.value = true
        watch(context)
    }

    fun stopProbe(context: Context) {
        probing.value = false
        val verdict = when {
            direct.value -> "삼성 키보드 마이크가 위스퍼 입력기를 바로 엶 → 정상(가로채기 필요 없음)"
            seen.value -> "마이크를 누르자 음성 입력기로 바뀜 → 가로채기 가능"
            else -> "마이크를 눌러도 입력기가 바뀌지 않음 → 삼성 키보드 음성 입력이 '삼성 음성 입력'이면 키보드 안에서 처리돼 가로챌 수 없음. 'Google 음성 입력'으로 바꾼 뒤 다시 기록"
        }
        events.value = events.value + line("기록 끝 · $verdict")
        LogRepository.from(context).add("실험 위스퍼 음성 입력 조사: $verdict · " + events.value.joinToString(" / ").take(600))
        if (!enabled(context)) unwatch(context)
    }

    private fun line(text: String) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + " " + text

    // ── 입력기 바뀜 감시 ──
    private var observer: ContentObserver? = null
    private var lastId: String? = null

    /** 삼성 음성 입력으로 넘어오기 전의 키보드(되돌릴 곳). */
    private var home: String? = null
    private var redirected = false
    private var redirectedAt = 0L

    @Synchronized
    fun watch(context: Context) {
        if (observer != null) return
        val app = context.applicationContext
        lastId = current(app)
        if (lastId?.startsWith(SAMSUNG_VOICE) == false) home = lastId
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onImeChanged(app)
        }
        app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, o)
        observer = o
    }

    @Synchronized
    fun unwatch(context: Context) {
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        redirected = false
    }

    /** 음성 입력기인지(음성 모드가 있는 입력기, 삼성 음성 입력 포함). */
    private fun isVoiceIme(app: Context, id: String?): Boolean {
        if (id == null) return false
        if (id.startsWith(SAMSUNG_VOICE)) return true
        return allImes(app).firstOrNull { it.id == id }?.let { hasVoice(it) } ?: false
    }

    /*
     * 11-08 실기 영상: 삼성 키보드를 '삼성 음성 입력'으로 두면 마이크를 눌러도 입력기가 바뀌지 않았다(키보드 안에서 처리, 가로챌 틈 없음).
     * 'Google 음성 입력'으로 두면 마이크가 Google 음성 입력기로 입력기를 바꾼다(추정, 조사로 확인). 그래서 삼성 키보드에서
     * 고른 입력기가 아닌 음성 입력기로 바뀌면 모두 가로챈다. GOAT가 직접 바꾼 것([switchTo], 2초 안)은 조사·판정에서 뺀다
     * (예전에는 [고른 입력기로 바꾸기] 시험을 마이크로 잘못 셌다).
     */
    private fun onImeChanged(app: Context) {
        val cur = current(app)
        val before = lastId
        if (cur == before) return
        lastId = cur
        val self = cur != null && cur == selfId && SystemClock.elapsedRealtime() - selfAt < 2_000
        val fromSamsung = before?.startsWith("com.samsung.android.honeyboard") == true
        val target = target(app)
        val trigger = !self && cur != null && cur != target && isVoiceIme(app, cur) && (fromSamsung || cur.startsWith(SAMSUNG_VOICE))
        if (trigger && !prefs(app).getBoolean("trigger_seen", false)) prefs(app).edit().putBoolean("trigger_seen", true).apply()
        if (probing.value) {
            if (trigger) seen.value = true
            if (!self && fromSamsung && cur != null && cur == target) direct.value = true
            events.value = (events.value + line("${label(app, before)} → ${label(app, cur)}" + if (self) " (GOAT가 바꿈)" else "")).takeLast(40)
        }
        if (!enabled(app) || target == null) {
            if (!isVoiceIme(app, cur) && cur != null) home = cur
            return
        }
        when {
            self -> Unit
            // 위스퍼 입력기가 끝나 앞 입력기(Google·삼성 음성 입력)로 돌아왔다: 삼성 키보드로 되돌린다
            redirected && cur != target -> {
                redirected = false
                if (cur == null || isVoiceIme(app, cur)) {
                    val back = home
                    if (back != null) switchTo(app, back)
                    LogRepository.from(app).add("실험 위스퍼 음성 입력: 끝, ${label(app, back)}(으)로 되돌림 · 걸린 시간 ${(SystemClock.elapsedRealtime() - redirectedAt) / 1000}초")
                }
            }
            // 삼성 키보드에서 마이크를 눌러 다른 음성 입력기로 바뀌었다: 고른 입력기로 넘긴다
            trigger -> {
                val ok = switchTo(app, target)
                redirected = ok
                redirectedAt = SystemClock.elapsedRealtime()
                LogRepository.from(app).add("실험 위스퍼 음성 입력: ${label(app, cur)} → ${label(app, target)} " + if (ok) "넘김" else "넘기지 못함(키보드 목록에서 켜져 있는지 확인)")
            }
            cur != null && cur != target && !isVoiceIme(app, cur) -> home = cur
        }
    }

    @Volatile private var selfId: String? = null

    @Volatile private var selfAt = 0L

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
