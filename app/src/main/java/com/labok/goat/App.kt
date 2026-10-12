package com.labok.goat

import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.tweaks.ForceDark
import com.labok.goat.data.tweaks.Gos
import com.labok.goat.data.tweaks.Doze
import com.labok.goat.data.tweaks.Resolution
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
        // GOS 끄기: 재부팅·업데이트로 다시 켜지면 Shizuku가 연결될 때 다시 끈다
        Gos.watch(this)
        // 잠자기 모드 끄기: 재부팅하면 다시 켜지는 설정이라 부팅마다 한 번 Shizuku가 연결될 때 다시 끈다
        Doze.watch(this)
        // 앱 다크 모드 강제: 재부팅으로 꺼지는 시스템 속성이라 부팅마다 한 번 Shizuku가 연결될 때 다시 켠다
        ForceDark.watch(this)
        // 해상도 낮추기: 확인을 기다리던 중 앱이 다시 시작됐으면 남은 시간으로 이어 가거나 되돌린다
        Resolution.resume(this)
        // 대각선 직접 입력 기능을 없앴다(사용자 요청). 이전 버전 입력값이 남아 있으면 계산에서 빼기 위해 지운다.
        if (SettingsRepository.from(this).clearLegacyDiagonalInput()) {
            LogRepository.from(this).add("이전 버전의 대각선 입력값을 지웠습니다(기능 제거)")
        }
    }
}
