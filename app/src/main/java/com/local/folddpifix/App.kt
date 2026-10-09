package com.local.folddpifix

import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.data.log.CrashRecorder
import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.settings.SettingsRepository
import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        DpiManager.exemptHiddenApis()
        // 대각선 직접 입력 기능을 없앴다(사용자 요청). 이전 버전 입력값이 남아 있으면 계산에서 빼기 위해 지운다.
        if (SettingsRepository.from(this).clearLegacyDiagonalInput()) {
            LogRepository.from(this).add("이전 버전의 대각선 입력값을 지웠습니다(기능 제거)")
        }
    }
}
