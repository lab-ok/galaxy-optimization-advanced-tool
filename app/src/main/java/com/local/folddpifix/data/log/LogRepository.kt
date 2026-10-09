package com.local.folddpifix.data.log

import com.local.folddpifix.data.settings.Storage
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱 로그. 진단 로그용으로 앱 내부 파일에 최근 [MAX_LINES]줄을 두고,
 * [publicSink]가 있으면 같은 줄을 사용자가 열 수 있는 공용 파일(Download/FoldFit)에도 계속 덧붙인다.
 */
class LogRepository(private val file: File, private val publicSink: ((String) -> Unit)? = null) {

    fun add(message: String, now: Long = System.currentTimeMillis()) {
        val line = "${timeFormat().format(Date(now))} $message"
        Log.i(TAG, message)
        publicSink?.let { sink -> runCatching { sink(line.replace('\n', ' ') + "\n") } }
        synchronized(lock) {
            val lines = read().toMutableList()
            lines += line.replace('\n', ' ')
            val kept = lines.takeLast(MAX_LINES)
            file.parentFile?.mkdirs()
            file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }

    fun read(): List<String> = synchronized(lock) {
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }
    }

    fun clear() = synchronized(lock) { file.delete() }

    companion object {
        const val MAX_LINES = 300
        private const val TAG = "FoldDpiFix"
        private val lock = Any()

        fun timeFormat() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

        fun from(context: Context): LogRepository {
            val app = context.applicationContext
            return LogRepository(Storage.logFile(app)) { PublicLogFile.post(app, it) }
        }
    }
}
