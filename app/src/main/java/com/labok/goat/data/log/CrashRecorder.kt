package com.labok.goat.data.log

import android.content.Context
import com.labok.goat.data.settings.Storage
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
        prefs(context).edit().remove(KEY_REPORTED).apply()
    }

    /**
     * 아직 보내지 않은 새 충돌이 있는지(홈 '비정상 종료 기록' 카드용). 예전에는 기록이 하나라도 있으면 카드가 계속 떠서,
     * 이미 보내 고친 옛 충돌(10-15·10-17)로 진단 파일을 보낸 뒤에도 '비정상 종료됐다'고 보였다(2026-10-19 사용자 신고).
     * 기록 파일 길이를 마지막으로 보낸 때와 비교한다. 진단 기록에는 지난 충돌까지 그대로 남긴다.
     */
    fun hasNew(context: Context): Boolean {
        val f = Storage.crashFile(context)
        return f.exists() && f.length() > prefs(context).getLong(KEY_REPORTED, 0L)
    }

    /** 진단 파일을 만들어 보냈을 때: 지금까지의 충돌은 알린 것으로 본다. */
    fun markReported(context: Context) {
        prefs(context).edit().putLong(KEY_REPORTED, Storage.crashFile(context).let { if (it.exists()) it.length() else 0L }).apply()
    }

    private const val KEY_REPORTED = "reported_bytes"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("crash_recorder", Context.MODE_PRIVATE)

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
