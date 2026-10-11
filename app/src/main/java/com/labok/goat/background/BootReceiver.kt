package com.labok.goat.background

import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.domain.DpiFixer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.labok.goat.data.tweaks.Resolution
import com.labok.goat.data.tweaks.StatusIcons
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.rotate.ShakeRotate
import android.os.UserManager

/**
 * LOCKED_BOOT_COMPLETED: 잠금 해제 전(Direct Boot)이라 WorkManager를 쓸 수 없으므로 한 번만 바로 확인한다.
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED: 즉시 확인은 Worker의 0초 단계가 맡고, 이후 재확인 일정을 건다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settings = SettingsRepository.from(context)
        val log = LogRepository.from(context)
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        // 앱별 화면 배율 설정: 알림창 크기 조절을 켜 두었으면 부팅·업데이트 뒤 다시 띄운다
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) AppDensityNotifier.start(context)
        // 흔들어서 화면 회전: 켜 두었으면 다시 띄운다
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) ShakeRotate.start(context)
        // 상태 표시줄 아이콘: 재부팅으로 숨김 목록이 비워지는 기기가 있어 다시 숨긴다(시스템 설정 권한, 잠금 해제 뒤)
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) StatusIcons.reapply(context, "부팅 감지($action)")
        // 해상도 낮추기: 확인하지 않은 채 재부팅됐으면 잠금을 풀기 전에도 원래 해상도로 되돌린다(기록은 기기 보호 저장소)
        Resolution.revertIfPending(context, "확인 전 재부팅", force = true)

        if (isBoot && !settings.applyOnBoot) {
            log.add("부팅 감지($action), 부팅 시 자동 적용 꺼짐")
            return
        }

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                log.add("부팅 감지 (잠금 해제 전)")
                val pending = goAsync()
                Thread {
                    try {
                        DpiFixer.from(context).ensure("locked-boot", automatic = true)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
                if (!unlocked) return
                val reason = if (action == Intent.ACTION_BOOT_COMPLETED) "boot" else "update"
                log.add(if (reason == "boot") "부팅 감지" else "앱 업데이트 감지")
                DpiWorker.enqueue(context, reason)
                FoldWatchService.syncWithSettings(context)
                DensityWatchWorker.sync(context)
            }
        }
    }
}
