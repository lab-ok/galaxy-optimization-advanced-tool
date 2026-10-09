package com.local.folddpifix.background

import com.local.folddpifix.domain.DpiFixer
import com.local.folddpifix.domain.DpiPolicy
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

/**
 * 부팅 뒤 Samsung/One UI가 늦게 density를 덮어쓰는 경우에 대비해
 * [DpiPolicy.BOOT_CHECK_OFFSETS_SEC] 시점마다 확인하고, 다를 때만 재적용한다.
 * BroadcastReceiver에서 sleep하지 않도록 WorkManager 작업으로 돌린다.
 */
class DpiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON) ?: "boot"
        val fixer = DpiFixer.from(applicationContext)
        var elapsed = 0L
        for ((index, offset) in DpiPolicy.BOOT_CHECK_OFFSETS_SEC.withIndex()) {
            delay((offset - elapsed) * 1000)
            elapsed = offset
            val last = index == DpiPolicy.BOOT_CHECK_OFFSETS_SEC.lastIndex
            val label = if (last) "$reason ${offset}s 최종 확인" else "$reason ${offset}s"
            when (fixer.ensure(label, automatic = true)) {
                // 더 확인해도 달라질 것이 없는 상태면 남은 확인을 하지 않는다.
                DpiPolicy.Decision.BLOCKED,
                DpiPolicy.Decision.SKIP_DISABLED,
                DpiPolicy.Decision.NO_PERMISSION,
                DpiPolicy.Decision.NO_TARGET,
                DpiPolicy.Decision.RATE_LIMITED -> return Result.success()
                else -> Unit
            }
        }
        return Result.success()
    }

    companion object {
        private const val KEY_REASON = "reason"
        private const val UNIQUE_NAME = "dpi_recover"

        /** 같은 이름의 작업은 하나만 유지한다. 새 트리거가 오면 이전 일정을 대체한다. */
        fun enqueue(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<DpiWorker>()
                .setInputData(workDataOf(KEY_REASON to reason))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
