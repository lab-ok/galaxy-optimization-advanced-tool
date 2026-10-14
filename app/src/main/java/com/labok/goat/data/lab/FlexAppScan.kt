package com.labok.goat.data.lab

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * 앱이 플렉스 화면(반쯤 접으면 위아래로 나뉘는 자체 화면)을 지원하는지 추정한다. 공식 표시가 없어서 앱 설치 파일을 읽어 본다.
 * - 접힘에 맞춰 화면을 바꾸는 앱은 구글 접힘 감지 기능(Jetpack WindowManager)의 `FoldingFeature`를 쓴다. 쓰지 않으면 앱을 만들 때
 *   코드 줄이기(R8)가 이 이름을 지워 버리므로, dex에 `androidx/window/layout/FoldingFeature`가 남아 있으면 지원으로 본다.
 *   (`androidx/window/extensions`·`sidecar`는 쓰지 않아도 남는 이름이라 표지로 쓰지 않는다.)
 * - 앱이 삼성 패널을 거부한다고 적어 두었으면(`com.samsung.android.unsupports_flexpanel`) 지원하지 않는 것으로 본다.
 * 코드만 넣고 실제로 화면을 나누지 않는 앱도 있어 추정이다. 결과는 앱 버전별로 저장해 두고 버전이 바뀌면 다시 읽는다.
 */
object FlexAppScan {
    private const val PREF = "flex_app_scan2"
    private const val OPT_OUT = "com.samsung.android.unsupports_flexpanel"
    private val MARKERS = listOf("androidx/window/layout/FoldingFeature").map { it.toByteArray() }

    /** [pkg]가 플렉스 화면을 지원하는지(저장해 둔 결과가 있으면 그걸 쓴다). 설치 파일을 읽으므로 작업 스레드에서 부른다. */
    fun supports(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_META_DATA) }.getOrNull() ?: return false
        val key = "$pkg@${info.longVersionCode}"
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.contains(key)) return prefs.getBoolean(key, false)
        val app = info.applicationInfo ?: return false
        val optOut = app.metaData?.getBoolean(OPT_OUT, false) == true
        val files = listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()
        val yes = !optOut && files.any { hasMarker(File(it)) }
        prefs.edit().putBoolean(key, yes).apply()
        return yes
    }

    /** 조사용 후보 표지: 플렉스 전용 화면을 가진 앱(갤러리·유튜브·카메라)만 가진 흔적을 찾으려고 진단에서 앱마다 비교한다. */
    internal val CANDIDATES = listOf(
        "androidx/window/layout/FoldingFeature",
        "androidx/window/layout/WindowInfoTracker",
        "androidx/window/extensions",
        "androidx/window/sidecar",
        "HALF_OPENED",
        "isTableTop",
        "TABLETOP",
        "FlexMode",
        "flexmode",
        "flex_mode",
        "FLEX_MODE",
        "semIsFlex",
        "FoldStateListener",
        "SemWindowManager",
        "com/samsung/android/sdk"
    )

    /** [pkg] 설치 파일에 [CANDIDATES] 중 무엇이 들어 있는지와 삼성·플렉스 관련 메타데이터(진단용, 작업 스레드에서 부른다). */
    fun inspect(context: Context, pkg: String): String {
        val app = runCatching { context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA) }.getOrNull() ?: return "(설치 안 됨)"
        val files = listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()
        val found = sortedSetOf<String>()
        files.forEach { path ->
            runCatching {
                ZipFile(File(path)).use { zip ->
                    zip.entries().asSequence().filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }.forEach { e ->
                        val bytes = zip.getInputStream(e).use { it.readBytes() }
                        CANDIDATES.forEach { m -> if (m !in found && indexOf(bytes, bytes.size, m.toByteArray()) >= 0) found.add(m) }
                    }
                }
            }
        }
        val meta = app.metaData?.keySet()?.filter { Regex("(?i)samsung|flex|fold|window|resiz").containsMatchIn(it) }?.joinToString { "$it=${app.metaData?.get(it)}" }.orEmpty()
        return "표지[${found.joinToString()}] 메타[${meta.ifBlank { "-" }}]"
    }

    private fun hasMarker(apk: File): Boolean = runCatching {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                .any { e -> zip.getInputStream(e).use { contains(it) } }
        }
    }.getOrDefault(false)

    /** 스트림에 표지 문자열 중 하나라도 있는지. 덩어리 경계에 걸친 문자열도 놓치지 않게 앞 덩어리 끝을 겹쳐 본다(시험할 수 있게 뗐다). */
    internal fun contains(input: InputStream, markers: List<ByteArray> = MARKERS): Boolean {
        val keep = markers.maxOf { it.size } - 1
        val buf = ByteArray(1 shl 16)
        var len = 0
        while (true) {
            val n = input.read(buf, len, buf.size - len)
            if (n <= 0) return false
            len += n
            if (markers.any { indexOf(buf, len, it) >= 0 }) return true
            val tail = minOf(keep, len)
            System.arraycopy(buf, len - tail, buf, 0, tail)
            len = tail
        }
    }

    private fun indexOf(buf: ByteArray, len: Int, m: ByteArray): Int {
        outer@ for (i in 0..len - m.size) {
            if (buf[i] != m[0]) continue
            for (j in 1 until m.size) if (buf[i + j] != m[j]) continue@outer
            return i
        }
        return -1
    }
}
