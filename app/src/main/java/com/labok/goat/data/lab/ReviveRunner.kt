package com.labok.goat.data.lab

import android.content.Context
import android.net.Uri
import com.labok.goat.data.log.PublicLogFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 삼성 터치패널 살리기 시도를 화면과 떼어 앱 전체 범위에서 돌린다. 시도 중 화면이 분할되거나 크기가 바뀌면 GOAT 화면이
 * 다시 만들어져 화면에 묶인 작업과 결과가 사라졌다(실기 영상). 그래서 시작하자마자 Download/GOAT에 결과 파일을 만들고 한 줄씩
 * 바로 덧붙이며, 마지막 파일 위치는 저장해 두어 화면이 다시 만들어져도 [결과 파일 공유]를 보여 준다.
 */
object ReviveRunner {
    private const val PREF = "flex_revive"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class State(val running: Boolean = false, val head: String = "")

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    /** 마지막 결과 파일(없으면 null). */
    fun lastFile(context: Context): Uri? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("uri", null)?.let(Uri::parse)

    fun start(context: Context) {
        if (stateFlow.value.running) return
        val app = context.applicationContext
        stateFlow.value = State(running = true)
        scope.launch {
            val uri = PublicLogFile.saveNew(app, PublicLogFile.timestampedName("flex-revive"), "")
            uri?.let { app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("uri", it.toString()).apply() }
            val head = StringBuilder()
            var inLog = false
            runCatching {
                FlexPanel.revive(app) { line ->
                    uri?.let { PublicLogFile.appendTo(app, it, line) }
                    // 화면에는 로그 덩어리를 빼고 한 일만 짧게 보여 준다
                    when {
                        line.startsWith("── ") -> inLog = true
                        line.startsWith("──") -> inLog = false
                        !inLog && head.lines().size < 14 -> {
                            head.append(line)
                            stateFlow.value = State(running = true, head = head.toString())
                        }
                    }
                }
            }.onFailure { e -> uri?.let { PublicLogFile.appendTo(app, it, "\n(중단: ${e.message})\n") } }
            stateFlow.value = State(running = false, head = head.toString())
        }
    }
}
