package com.local.folddpifix.data.log

import android.content.Context
import com.local.folddpifix.data.settings.Storage
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * 처리되지 않은 예외(앱 강제 종료)를 파일에 남긴다. 기록한 뒤에는 원래 처리기로 넘겨 시스템 동작은 그대로 둔다.
 * 진단 로그 내보내기에 포함된다.
 */
object CrashRecorder {

    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String {
        val f = Storage.crashFile(context)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(context: Context) {
        Storage.crashFile(context).delete()
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val entry = "=== ${LogRepository.timeFormat().format(Date())} 스레드 ${thread.name} ===\n$trace\n"
        val f = Storage.crashFile(context)
        f.parentFile?.mkdirs()
        val merged = (if (f.exists()) f.readText() else "") + entry
        f.writeText(merged.takeLast(MAX_BYTES))
        runCatching { PublicLogFile.append(context, "[강제 종료]\n$entry") }
        runCatching { LogRepository.from(context).add("앱 오류로 종료: ${error.javaClass.simpleName}: ${error.message}") }
    }
}
