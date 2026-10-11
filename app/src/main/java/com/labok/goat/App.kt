package com.labok.goat

import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.display.DpiManager
import com.labok.goat.data.log.CrashRecorder
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.settings.SettingsRepository
import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        DpiManager.exemptHiddenApis()
        // Shizuku가 켜지며(재부팅 뒤 자동 시작 포함) GOAT에 바인더를 건네면, 옵션에 따라 무선 디버깅을 끈다
        if (ShizukuAccess.autoOffEnabled(this)) ShizukuAccess.watch(this)
        // 배율을 바꿔 삼성이 GOAT를 닫았다가 다시 열린 경우 등: 켜 둔 알림창 배율 조절을 다시 띄운다
        AppDensityNotifier.start(this)
        ShakeRotate.start(this)
        // 카메라 촬영음 끄기: 재부팅·업데이트로 돌아오면 Shizuku가 연결될 때 다시 끈다
        CameraMute.watch(this)
        // 대각선 직접 입력 기능을 없앴다(사용자 요청). 이전 버전 입력값이 남아 있으면 계산에서 빼기 위해 지운다.
        if (SettingsRepository.from(this).clearLegacyDiagonalInput()) {
            LogRepository.from(this).add("이전 버전의 대각선 입력값을 지웠습니다(기능 제거)")
        }
    }
}
