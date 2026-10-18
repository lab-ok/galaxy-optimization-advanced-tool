package com.labok.goat.data.tweaks

import android.content.Context
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * 애니메이션 속도 유지. 세 배율(Settings.Global) 가운데 하나라도 바뀌면 WorkManager 콘텐츠 URI 트리거로 깨어나
 * [AnimationSpeed.restoreIfReset]을 한 번 부르고 다음 변경을 다시 기다린다(상주 프로세스·주기 확인 없음, [com.labok.goat.background.DensityWatchWorker]와 같은 방식).
 * 개발자 옵션을 끄면 기기가 배율을 기본으로 되돌려 "가끔 풀린다"는 문의가 있었다(2026-10-13 메일, SM-F976N).
 */
class AnimationKeepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { AnimationSpeed.restoreIfReset(applicationContext) }
        rearm(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "animation_keep"

        private fun request() = OneTimeWorkRequestBuilder<AnimationKeepWorker>()
            .setConstraints(
                Constraints.Builder().apply {
                    AnimationSpeed.Kind.entries.forEach { addContentUriTrigger(Settings.Global.getUriFor(it.key), false) }
                }
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
                    .setTriggerContentMaxDelay(Duration.ofSeconds(10))
                    .build()
            )
            .build()

        /** 되돌릴 일이 있으면 감지를 걸고(이미 걸려 있으면 그대로), 없으면 해제한다. */
        fun sync(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (AnimationSpeed.keepNeeded(context)) {
                wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request())
            } else {
                wm.cancelUniqueWork(UNIQUE_NAME)
            }
        }

        private fun rearm(context: Context) {
            if (!AnimationSpeed.keepNeeded(context)) return
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
        }
    }
}
