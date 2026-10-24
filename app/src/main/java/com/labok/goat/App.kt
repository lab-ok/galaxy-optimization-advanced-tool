package com.labok.goat

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.labok.goat.background.GoatService
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.camera.CameraMute
import com.labok.goat.data.display.DpiManager
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.data.lab.NarrowApps
import com.labok.goat.data.lab.NarrowBar
import com.labok.goat.data.lab.QsGrid
import com.labok.goat.data.lab.StatusGestures
import com.labok.goat.data.lab.VolumeRotate
import com.labok.goat.data.log.CrashRecorder
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.data.tweaks.AnimationKeepWorker
import com.labok.goat.data.tweaks.AnimationSpeed
import com.labok.goat.data.tweaks.Doze
import com.labok.goat.data.tweaks.ForceDark
import com.labok.goat.data.tweaks.Gos
import com.labok.goat.data.tweaks.Resolution
import com.labok.goat.data.update.OtherUserCleanup
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.liquid.UiHaptics
import com.labok.goat.ui.liquid.UiInk
import com.labok.goat.ui.liquid.UiMotion
import com.labok.goat.ui.nav.NewBadge

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        // 부팅 직후 잠금 해제 전(Direct Boot)에는 부팅 수신기 때문에 앱이 먼저 뜨는데, 이때 일반 저장소(설정)는 열 수 없어
        // 아래 초기화가 'credential encrypted storage' 예외로 앱을 죽였다(10-17 실기기 크래시). 잠금이 풀린 뒤에 한 번 한다.
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        runCatching { unregisterReceiver(this) }
                        initUnlocked()
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            return
        }
        initUnlocked()
    }

    /** 일반 저장소가 열린 뒤의 초기화(설정 읽기, 감시 시작 등). */
    private fun initUnlocked() {
        // 앱 설정의 UI 낭창거림을 첫 화면 전에 읽는다
        UiMotion.load(this)
        UiHaptics.load(this)
        UiInk.load(this)
        UiGoat.load(this)
        NewBadge.load(this)
        LabUnlock.load(this)
        // 볼륨 버튼 회전: 강제 종료로 접근성 서비스가 빠졌으면 다시 켠다
        runCatching { VolumeRotate.restoreIfWanted(this) }
        // 예전 실험실 빠른 설정 격자가 바꿔 둔 설정이 남아 있으면 되돌린다(1.15.3에서 실험을 접음)
        Thread { runCatching { QsGrid.restoreLeftovers(this) } }.start()
        // 1.17.8의 실험실 '충전 실측 용량'을 뺐다(1.17.9): 켜 두었던 기록과 알림 채널을 지운다
        Thread {
            runCatching {
                deleteSharedPreferences("charge_meter")
                getSystemService(NotificationManager::class.java).deleteNotificationChannel("charge_meter_quiet")
            }
        }.start()
        // 애니메이션 속도 유지 감지를 건다(고른 값이 있고 유지가 켜져 있을 때)
        runCatching {
            AnimationSpeed.seedIfMissing(this)
            AnimationKeepWorker.sync(this)
        }
        // 기본값으로 풀린 사이 앱이 멈춰 있었으면(강제 종료 등) 바뀜 감지가 지나가 버리므로, 앱이 뜰 때 한 번 직접 확인한다(1.17.5 전수 점검)
        Thread { runCatching { AnimationSpeed.restoreIfReset(this) } }.start()
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
        // 앱 폭 좁히기(실험실): 삼성이 부팅 때 호환성 설정을 지우므로 Shizuku가 연결될 때 고른 앱을 다시 건다
        NarrowApps.watch(this)
        // 예전 업데이트가 듀얼 메신저·보안 폴더에도 깐 GOAT를 Shizuku가 연결되면 지운다(기본 사용자에서만)
        OtherUserCleanup.watch(this)
        // 좁힌 앱 컨트롤러: 업데이트·강제 종료 뒤에도 상주 서비스가 맨 앞 앱을 따라가도록 다시 띄운다
        if (NarrowBar.enabled(this)) GoatService.sync(this)
        // 상단바 제스처(접근성 서비스): 강제 종료로 시스템이 뺐으면 다시 켠다
        StatusGestures.restoreIfWanted(this)
        // 해상도 낮추기: 확인을 기다리던 중 앱이 다시 시작됐으면 남은 시간으로 이어 가거나 되돌린다
        Resolution.resume(this)
        // 대각선 직접 입력 기능을 없앴다(사용자 요청). 이전 버전 입력값이 남아 있으면 계산에서 빼기 위해 지운다.
        if (SettingsRepository.from(this).clearLegacyDiagonalInput()) {
            LogRepository.from(this).add("이전 버전의 대각선 입력값을 지웠습니다(기능 제거)")
        }
    }
}
