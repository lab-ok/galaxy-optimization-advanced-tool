package com.local.folddpifix.background

import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.DpiFixer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.folddpifix.data.lab.AppDensityNotifier
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
        // 실험실: 알림창 앱 화면 크기 조절을 켜 두었으면 부팅·업데이트 뒤 다시 띄운다
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) AppDensityNotifier.show(context)

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
