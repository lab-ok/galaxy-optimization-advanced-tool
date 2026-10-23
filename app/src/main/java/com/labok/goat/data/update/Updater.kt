package com.labok.goat.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.labok.goat.BuildConfig
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱 안 업데이트: GitHub 릴리스(최신 판)의 버전을 이 앱 버전과 비교하고, 새 판이면 APK를 내려받아 설치한다.
 * - 확인: `api.github.com/repos/<저장소>/releases/latest`의 tag_name(예: "1.3")과 첨부 APK, 릴리스 노트의 SHA-256.
 * - 버전 채널(2026-10-23): 번호는 [앱].[안정].[시험]이다. 안정 버전은 정식 릴리스, 시험 버전(실험실 업데이트)은 GitHub '미리 보기(pre-release)'로
 *   올린다. 실험실을 연 사람([testChannel])만 미리 보기까지 포함한 목록에서 가장 높은 판을 받고, 나머지는 정식 릴리스(latest)만 받는다.
 *   '안정 버전으로 돌아가기'는 실험실을 다시 숨겨 이 채널을 끈다(낮은 판으로 내려 설치하지는 않는다).
 * - 내려받기: 앱 캐시에 받고, 크기와 (노트에 있으면) SHA-256이 맞을 때만 설치한다.
 * - 설치: Shizuku가 연결돼 있으면 `pm install -r -S 크기`로 묻지 않고 바로 설치하고(앱이 다시 시작된다), 없으면
 *   시스템 설치 창(PackageInstaller)을 띄운다. 처음 한 번은 'GOAT의 앱 설치 허용'을 켜야 한다.
 * 서명이 다른 APK는 안드로이드가 설치를 거절하므로 같은 키로 서명한 판만 설치된다.
 */
object Updater {
    const val REPO = "lab-ok/galaxy-optimization-advanced-tool"
    private const val PREF = "updater"

    /** 홈을 열거나 홈으로 돌아올 때 자동으로 확인하는 간격. */
    private const val AUTO_CHECK_MS = 30 * 60 * 1000L

    data class Release(val version: String, val apkUrl: String, val size: Long, val sha256: String?, val notes: String, val page: String, val test: Boolean = false)

    /** 시험 버전도 받는지: 실험실을 연 사람만. */
    fun testChannel() = LabUnlock.unlocked

    /** 이 앱이 시험 버전인지: 버전 끝자리(시험 빌드 번호)가 0이 아니다. */
    fun isTestBuild(version: String = BuildConfig.VERSION_NAME) = version.split('.').getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { it != 0 } ?: false

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val state = MutableStateFlow<State>(State.Idle)
    val status: StateFlow<State> = state.asStateFlow()

    /** 버전 비교: 점으로 나눈 숫자를 앞에서부터 비교한다("1.10" > "1.9", "1.2" == "1.2.0"). 숫자가 아닌 꼬리(-beta 등)는 무시한다. */
    internal fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** 릴리스 노트의 "SHA-256: `…`" 줄에서 64자리 해시(소문자). 없으면 null(그때는 크기만 확인한다). */
    internal fun shaOf(notes: String): String? = Regex("SHA-256:\\s*`?([0-9a-fA-F]{64})").find(notes)?.groupValues?.get(1)?.lowercase()

    /** 릴리스 JSON → [Release](첨부 중 .apk 하나). APK가 없으면 null. */
    internal fun parse(json: String): Release? = parse(JSONObject(json))

    /** 릴리스 목록 JSON에서 초안을 빼고 가장 높은 판(APK가 있는 것). [withTest]가 아니면 미리 보기도 뺀다. */
    internal fun newestOf(json: String, withTest: Boolean): Release? {
        val arr = JSONArray(json)
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            if (o.optBoolean("draft")) null else parse(o)
        }
        return newest(list, withTest)
    }

    /** [newestOf]의 고르기(시험할 수 있게 뗐다): 미리 보기는 [withTest]일 때만, 그중 가장 높은 판. */
    internal fun newest(list: List<Release>, withTest: Boolean): Release? =
        list.filter { withTest || !it.test }.fold(null as Release?) { best, r -> if (best == null || isNewer(r.version, best.version)) r else best }

    private fun parse(o: JSONObject): Release? {
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (!a.optString("name").endsWith(".apk")) continue
            val notes = o.optString("body")
            return Release(
                version = o.optString("tag_name"),
                apkUrl = a.optString("browser_download_url"),
                size = a.optLong("size"),
                sha256 = shaOf(notes),
                notes = notes,
                page = o.optString("html_url"),
                test = o.optBoolean("prerelease")
            )
        }
        return null
    }

    /** 지금 확인한다(작업 스레드). [quiet]면 실패해도 상태를 바꾸지 않는다(홈 자동 확인). */
    fun check(context: Context, quiet: Boolean = false) {
        val current = state.value
        if (current is State.Downloading || current is State.Installing) return
        if (!quiet) state.value = State.Checking
        val result = runCatching {
            val test = testChannel()
            val url = if (test) "https://api.github.com/repos/$REPO/releases?per_page=20" else "https://api.github.com/repos/$REPO/releases/latest"
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GOAT/${BuildConfig.VERSION_NAME}")
            }
            test to c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        }
        prefs(context).edit().putLong("checkedAt", System.currentTimeMillis()).apply()
        val (test, json) = result.getOrElse {
            if (!quiet) state.value = State.Failed("업데이트를 확인하지 못했습니다. 인터넷 연결을 확인해 주세요.")
            return
        }
        val release = runCatching { if (test) newestOf(json, withTest = true) else parse(json) }.getOrNull()
        state.value = when {
            release == null -> if (quiet) state.value else State.Failed("최신 릴리스에서 APK를 찾지 못했습니다.")
            isNewer(release.version, BuildConfig.VERSION_NAME) -> State.Available(release)
            else -> State.UpToDate
        }
    }

    /** 홈을 열 때: 마지막 확인에서 [AUTO_CHECK_MS]가 지났으면 조용히 확인한다(작업 스레드). */
    fun autoCheck(context: Context) {
        if (System.currentTimeMillis() - prefs(context).getLong("checkedAt", 0L) < AUTO_CHECK_MS && state.value !is State.Idle) return
        check(context, quiet = true)
    }

    /** 내려받아 설치한다(작업 스레드). */
    fun update(context: Context, release: Release) {
        val log = LogRepository.from(context)
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "GOAT-${release.version}.apk")
        state.value = State.Downloading(release, 0f)
        val downloaded = runCatching {
            val c = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "GOAT/${BuildConfig.VERSION_NAME}")
            }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) state.value = State.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            c.disconnect()
            md.digest().joinToString("") { "%02x".format(it) }
        }
        val sha = downloaded.getOrElse {
            state.value = State.Failed("다운로드하지 못했습니다. 인터넷 연결을 확인해 주세요.", release)
            log.add("업데이트 ${release.version}: 다운로드 실패(${it.message?.take(80)})")
            return
        }
        if ((release.size > 0 && file.length() != release.size) || (release.sha256 != null && release.sha256 != sha)) {
            file.delete()
            state.value = State.Failed("다운로드한 파일이 릴리스와 달라 설치하지 않았습니다.", release)
            log.add("업데이트 ${release.version}: 크기·SHA-256 불일치로 설치하지 않음")
            return
        }
        state.value = State.Installing(release)
        log.add("업데이트 ${release.version}: 다운로드 완료, 설치 시작(${if (ShizukuAccess.status.value == ShizukuAccess.State.READY) "Shizuku" else "시스템 설치 창"})")
        // 설치가 끝난 뒤 '업데이트됨 · 눌러서 열기' 알림을 띄울 수 있게, GOAT가 시작한 업데이트라고 표시해 둔다
        UpdateNotice.expect(context, release.version)
        // Shizuku가 있으면 묻지 않고 설치한다. 설치가 끝나면 시스템이 GOAT를 닫으므로 아래 줄은 실패했을 때만 이어진다.
        // 설치가 끝나면 GOAT를 다시 연다. Shizuku는 GOAT가 닫힐 때 GOAT가 띄운 셸도 함께 끝내므로, 설치 명령 뒤에
        // 이어 붙이면 실행되지 않는다(에뮬레이터에서 확인). 그래서 설치 전에 세션을 떼어 낸(setsid) 감시 셸을 먼저 띄워,
        // 새 버전이 깔린 것을 확인하면 GOAT를 연다. 60초 안에 바뀌지 않으면(설치 실패) 그냥 끝난다.
        if (ShizukuAccess.status.value == ShizukuAccess.State.READY) {
            val r = ShizukuAccess.shellWithInput(
                relaunchAfter(context.packageName, release.version) + "pm install -r -S ${file.length()}",
                file.inputStream()
            )
            if (r?.ok == true && r.out.contains("Success")) return
            log.add("업데이트 ${release.version}: Shizuku 설치 실패(${(r?.err ?: r?.out).orEmpty().take(80)}), 시스템 설치 창으로")
        }
        runCatching { installWithSystem(context, file) }.onFailure {
            state.value = State.Failed("설치 창을 열지 못했습니다.", release)
            log.add("업데이트 ${release.version}: 설치 창 실패(${it.message?.take(80)})")
        }
    }

    /** 새 버전([version])이 깔리면 [pkg]를 여는 떼어 낸 감시 셸(뒤에 설치 명령을 이어 붙인다). 시험할 수 있게 문자열만 만든다. */
    internal fun relaunchAfter(pkg: String, version: String): String {
        // 감시 스크립트는 작은따옴표로 감싸 바깥 셸이 $(seq 60)을 미리 풀지 않게 한다(미리 풀면 줄바꿈 때문에 for 문이 깨졌다)
        val watch = "for i in \$(seq 60); do sleep 1; " +
            "dumpsys package $pkg | grep -q \"versionName=$version\$\" && { am start -n $pkg/.ui.MainActivity; exit 0; }; done"
        return "setsid sh -c '$watch' </dev/null >/dev/null 2>&1 & "
    }

    /** 시스템 설치 창(PackageInstaller 세션). 사용자가 [설치]를 누르면 설치된다. */
    private fun installWithSystem(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("goat.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }

    /** 설치 결과: 사용자 확인이 필요하면 설치 창을 띄우고, 실패하면 상태에 남긴다. */
    class InstallResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                else -> {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                    val r = (state.value as? State.Installing)?.release
                    // 영어 코드 대신 이유와 해결 방법을 한국어로 보여 준다
                    state.value = State.Failed(InstallFailure.explain(status, msg), r)
                    UpdateNotice.consume(context)
                    LogRepository.from(context).add("업데이트 설치 실패($status): ${msg.take(80)}")
                }
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
