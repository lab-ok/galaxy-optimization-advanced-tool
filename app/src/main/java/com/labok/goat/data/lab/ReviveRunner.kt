package com.labok.goat.data.lab

import android.content.Context
import android.net.Uri
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.data.shizuku.ShizukuAccess
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

    /** [shareReady]: 방금 끝난 시도의 결과 파일. 앱 화면이 받아 공유 창을 한 번 띄우고 [consumeShare]로 지운다. */
    data class State(val running: Boolean = false, val head: String = "", val shareReady: Uri? = null)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    /** 자동 공유를 띄웠으면 지운다(같은 결과로 두 번 띄우지 않게). */
    fun consumeShare() {
        stateFlow.value = stateFlow.value.copy(shareReady = null)
    }

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
            stateFlow.value = State(running = false, head = head.toString(), shareReady = uri)
            // 시도 중 앞에 띄운 다른 앱(크롬 등) 위로 GOAT를 다시 올려, 끝나자마자 결과 공유 창이 뜨게 한다
            // (뒤에 있는 앱은 직접 창을 띄울 수 없어 셸로 GOAT를 앞으로 가져온다)
            if (uri != null) ShizukuAccess.shell("am start -n ${app.packageName}/.ui.MainActivity -f 0x20000000")
        }
    }
}
