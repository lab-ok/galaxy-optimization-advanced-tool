package com.labok.goat.data.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.UserManager
import android.provider.MediaStore
import com.labok.goat.data.settings.Storage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 로그를 사용자가 바로 열 수 있는 파일로 저장한다: 내장 저장소 `Download/GOAT/goat-YYMMDD-log.txt`.
 * 이름은 MIME(text/plain)과 같은 .txt로 끝나야 한다. `.log`로 저장하면 시스템이 `.txt`를 덧붙여 이름이 바뀌고,
 * 다음 기록 때 같은 이름으로 찾지 못해 기록마다 '(1)', '(2)' 같은 새 파일이 생긴다.
 * MediaStore를 쓰므로 저장소 권한이 필요 없다. 잠금 해제 전(Direct Boot)에는 공용 저장소를 쓸 수 없어
 * 앱 내부 대기 파일에 모았다가 다음 기록 때 함께 옮긴다.
 */
object PublicLogFile {

    const val FOLDER = "GOAT"
    private val lock = Any()
    private val dayFormat = SimpleDateFormat("yyMMdd", Locale.US)

    /** 사용자에게 보여 줄 저장 위치. */
    val displayPath: String get() = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 기록 파일을 남길지(홈 맨 아래 스위치). 기본은 꺼짐: 끄면 다운로드 폴더에 아무 파일도 만들지 않는다.
     * 앱 안의 짧은 기록(고급 정보·문제 신고용, 앱 저장소 안)은 그대로 남는다.
     */
    fun enabled(context: Context) = context.applicationContext.getSharedPreferences("public_log", Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences("public_log", Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
    }

    /** 일반 기록: 백그라운드 스레드에서 덧붙인다(메인 스레드를 막지 않음). */
    fun post(context: Context, text: String) {
        val app = context.applicationContext
        if (!enabled(app)) return
        executor.execute { append(app, text) }
    }

    /** 즉시 기록(강제 종료처럼 프로세스가 곧 끝나는 경우). */
    fun append(context: Context, text: String) = synchronized(lock) {
        val app = context.applicationContext
        if (!enabled(app)) return@synchronized
        val pending = Storage.pendingPublicLog(app)
        val unlocked = app.getSystemService(UserManager::class.java).isUserUnlocked
        if (!unlocked) {
            runCatching {
                pending.parentFile?.mkdirs()
                pending.appendText(text)
            }
            return@synchronized
        }
        val carry = runCatching { if (pending.exists()) pending.readText() else "" }.getOrDefault("")
        val wrote = runCatching {
            val uri = uriForToday(app) ?: return@runCatching false
            app.contentResolver.openOutputStream(uri, "wa")?.use { it.write((carry + text).toByteArray()) } != null
        }.getOrDefault(false)
        if (wrote) {
            if (carry.isNotEmpty()) pending.delete()
        }
        // 쓰지 못했으면 앱 안에 모아 두었다가 다음에 오늘 파일을 찾으면 함께 쓴다(새 파일을 계속 만들지 않는다)
        else {
            runCatching {
                pending.parentFile?.mkdirs()
                pending.appendText(text)
            }
        }
    }

    /** 오늘 로그 파일(있으면). 공유에 첨부한다. */
    fun todayUri(context: Context): Uri? = synchronized(lock) {
        find(context.applicationContext, todayName())
    }

    /** "goat-<kind>-yyMMdd-HHmmss.txt"처럼 시각을 붙인 새 파일 이름. */
    fun timestampedName(kind: String): String = "goat-$kind-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.txt"

    /** [saveNew]로 만든 파일 끝에 [text]를 덧붙인다(실시간 기록용). 실패하면 false. */
    fun appendTo(context: Context, uri: Uri, text: String): Boolean = synchronized(lock) {
        runCatching { context.applicationContext.contentResolver.openOutputStream(uri, "wa")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
    }

    /** Download/GOAT에 새 파일로 저장하고 Uri를 돌려준다. */
    fun saveNew(context: Context, name: String, text: String): Uri? = synchronized(lock) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/")
        }
        val resolver = context.applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } ?: return null
        uri
    }

    /**
     * GOAT가 만든 오늘 로그 파일을 찾는다. 같은 이름의 파일을 다른 앱(예: 앱 ID를 바꾸기 전 GOAT)이 이미 만들어 두었으면
     * 시스템이 새 파일 이름에 '(1)'을 붙이고, 그 파일은 이 앱에 보이지 않는다. 그래서 정확한 이름이 아니라
     * "goat-YYMMDD-log"로 시작하는 이 앱의 파일을 찾는다(보이는 것은 이 앱이 만든 파일뿐). 여럿이면 가장 최근 것.
     */
    private fun find(context: Context, name: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        val stem = name.removeSuffix(".txt")
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf("$stem%.txt", relative),
            "${MediaStore.MediaColumns._ID} DESC"
        )?.use { c ->
            if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        return null
    }

    private fun todayName() = "goat-${dayFormat.format(Date())}-log.txt"

    /**
     * 오늘 로그 파일. ① 오늘 만든 파일의 Uri를 기억해 두었으면 그것을 쓰고, ② 없으면 이름으로 찾고, ③ 그래도 없을 때만 새로 만든다.
     * 새로 만드는 것은 하루 한 번뿐이다: 기기에 따라 미디어 저장소가 다시 색인되며 이 앱이 만든 파일을 못 찾게 되는 일이 있어,
     * 그때마다 새로 만들면 '(1)'·'(2)'처럼 번호 붙은 파일이 계속 쌓인다. 오늘 이미 만들었는데 쓰지 못하면 null을 돌려
     * 앱 안에 모아 둔다.
     */
    private fun uriForToday(context: Context): Uri? {
        val name = todayName()
        val day = name.removeSuffix(".txt")
        val p = context.getSharedPreferences("public_log", Context.MODE_PRIVATE)
        val remembered = p.getString("uri", null)?.takeIf { p.getString("day", null) == day }?.let(Uri::parse)
        if (remembered != null && writable(context, remembered)) return remembered
        find(context, name)?.let {
            p.edit().putString("day", day).putString("uri", it.toString()).apply()
            return it
        }
        if (p.getString("created", null) == day) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/")
        }
        val made = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        p.edit().putString("day", day).putString("uri", made.toString()).putString("created", day).apply()
        return made
    }

    /** 이 Uri에 덧붙여 쓸 수 있는지(지워졌거나 권한을 잃었으면 false). */
    private fun writable(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.openFileDescriptor(uri, "wa")?.use { true } ?: false }.getOrDefault(false)
}
