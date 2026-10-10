package com.local.folddpifix.data.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.UserManager
import android.provider.MediaStore
import com.local.folddpifix.data.settings.Storage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 로그를 사용자가 바로 열 수 있는 파일로 저장한다: 내장 저장소 `Download/FoldFit/foldfit-YYMMDD-log.txt`.
 * 이름은 MIME(text/plain)과 같은 .txt로 끝나야 한다. `.log`로 저장하면 시스템이 `.txt`를 덧붙여 이름이 바뀌고,
 * 다음 기록 때 같은 이름으로 찾지 못해 기록마다 '(1)', '(2)' 같은 새 파일이 생긴다.
 * MediaStore를 쓰므로 저장소 권한이 필요 없다. 잠금 해제 전(Direct Boot)에는 공용 저장소를 쓸 수 없어
 * 앱 내부 대기 파일에 모았다가 다음 기록 때 함께 옮긴다.
 */
object PublicLogFile {

    const val FOLDER = "FoldFit"
    private val lock = Any()
    private val dayFormat = SimpleDateFormat("yyMMdd", Locale.US)

    /** 사용자에게 보여 줄 저장 위치. */
    val displayPath: String get() = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** 일반 기록: 백그라운드 스레드에서 덧붙인다(메인 스레드를 막지 않음). */
    fun post(context: Context, text: String) {
        val app = context.applicationContext
        executor.execute { append(app, text) }
    }

    /** 즉시 기록(강제 종료처럼 프로세스가 곧 끝나는 경우). */
    fun append(context: Context, text: String) = synchronized(lock) {
        val app = context.applicationContext
        val pending = Storage.pendingPublicLog(app)
        val unlocked = app.getSystemService(UserManager::class.java).isUserUnlocked
        if (!unlocked) {
            runCatching { pending.parentFile?.mkdirs(); pending.appendText(text) }
            return@synchronized
        }
        runCatching {
            val carry = if (pending.exists()) pending.readText() else ""
            val uri = uriForToday(app) ?: return@runCatching
            app.contentResolver.openOutputStream(uri, "wa")?.use { it.write((carry + text).toByteArray()) }
            if (carry.isNotEmpty()) pending.delete()
        }
    }

    /** 오늘 로그 파일(있으면). 공유에 첨부한다. */
    fun todayUri(context: Context): Uri? = synchronized(lock) {
        find(context.applicationContext, todayName())
    }

    /** "foldfit-<kind>-yyMMdd-HHmmss.txt"처럼 시각을 붙인 새 파일 이름. */
    fun timestampedName(kind: String): String =
        "foldfit-$kind-${java.text.SimpleDateFormat("yyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.txt"

    /** Download/FoldFit에 새 파일로 저장하고 Uri를 돌려준다. */
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

    private fun find(context: Context, name: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relative),
            null,
        )?.use { c ->
            if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        return null
    }

    private fun todayName() = "foldfit-${dayFormat.format(Date())}-log.txt"

    private fun uriForToday(context: Context): Uri? {
        val name = todayName()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relative),
            null,
        )?.use { c ->
            if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
        }
        return context.contentResolver.insert(collection, values)
    }
}
