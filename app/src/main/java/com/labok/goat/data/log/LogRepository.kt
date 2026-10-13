package com.labok.goat.data.log

import android.content.Context
import android.util.Log
import com.labok.goat.data.settings.Storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱 로그. 진단 로그용으로 앱 내부 파일에 최근 [MAX_LINES]줄을 두고,
 * [publicSink]가 있으면 같은 줄을 사용자가 열 수 있는 공용 파일(Download/GOAT)에도 계속 덧붙인다.
 *
 * 기록 규칙(문제 신고로 원인을 찾을 수 있게):
 * - 저장되는 설정을 바꾸는 함수는 그 함수 안에서 한 줄 남긴다(부르는 쪽이 아니라 저장하는 쪽). 예: [com.labok.goat.data.rotate.ShakeRotate.setLevel],
 *   [com.labok.goat.data.appsize.DensityShell.set], [com.labok.goat.data.appsize.AppDensityNotifier.setEnabled].
 * - 시스템을 바꾸는 동작(DPI 적용·회전 잠금·권한 받기·무선 디버깅)은 성공과 실패를 모두, 실패면 이유까지 남긴다.
 * - 자동으로 일어난 일(부팅 재적용·흔들어서 회전·상주 서비스 시작과 멈춤)은 무엇이 계기였는지 함께 남긴다.
 * - 자주 반복될 수 있는 기록(흔들림을 놓친 이유 등)은 부르는 쪽에서 횟수를 줄인다(예: 5초에 한 번).
 * - 문구는 "기능: 무엇 → 결과(이유)" 꼴로, 기능 이름은 화면의 메뉴 이름을 쓴다.
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
        private const val TAG = "GOAT"
        private val lock = Any()

        fun timeFormat() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

        fun from(context: Context): LogRepository {
            val app = context.applicationContext
            return LogRepository(Storage.logFile(app)) { PublicLogFile.post(app, it) }
        }
    }
}
