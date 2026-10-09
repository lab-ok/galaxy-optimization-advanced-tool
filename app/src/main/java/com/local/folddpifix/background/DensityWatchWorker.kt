package com.local.folddpifix.background

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.DpiFixer
import com.local.folddpifix.domain.DpiPolicy
import java.time.Duration

/**
 * DPI 변경 감지. 시스템이 화면 밀도를 바꾸면 Settings.Secure "display_density_forced"가 바뀐다
 * (redroid Android 14에서 wm density 변경 시 갱신 확인). WorkManager의 콘텐츠 URI 트리거로 그 값이 바뀔 때만
 * 깨어나므로 상주 프로세스나 주기 확인이 없다(배터리 영향 없음). 깨어나면 한 번 확인하고 다음 변경을 다시 기다린다.
 *
 * 앱이 직접 적용한 변경도 트리거되지만 앱이 기억한 값과 같아 그냥 넘어간다. 외부 변경이면 알림으로 선택을 묻는다.
 */
class DensityWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.from(applicationContext)
        if (settings.autoRecover) {
            // 부팅 직후 기기가 기본값으로 되돌린 것은 부팅 복원(DpiWorker)이 맡는다.
            val booting = SystemClock.elapsedRealtime() < BOOT_GRACE_MS
            val decision = DpiFixer.from(applicationContext).ensure("DPI 변경 감지", automatic = true, physicalIsReset = booting)
            if (decision == DpiPolicy.Decision.EXTERNAL_CHANGE) {
                settings.externalChange?.let { ExternalChangeNotifier.show(applicationContext, it) }
            }
        }
        rearm(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "density_watch"
        private const val BOOT_GRACE_MS = 3 * 60_000L
        private val DENSITY_URI = Settings.Secure.getUriFor("display_density_forced")

        private fun request() = OneTimeWorkRequestBuilder<DensityWatchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .addContentUriTrigger(DENSITY_URI, false)
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
                    .setTriggerContentMaxDelay(Duration.ofSeconds(10))
                    .build()
            )
            .build()

        /** 자동 적용이 켜져 있으면 감지를 걸고, 꺼져 있으면 해제한다. 이미 걸려 있으면 그대로 둔다. */
        fun sync(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (SettingsRepository.from(context).autoRecover) {
                wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request())
            } else {
                wm.cancelUniqueWork(UNIQUE_NAME)
            }
        }

        /** 실행 중인 작업이 끝난 뒤 다음 변경을 기다리도록 이어 붙인다. */
        private fun rearm(context: Context) {
            if (!SettingsRepository.from(context).autoRecover) return
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
        }
    }
}
