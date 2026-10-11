package com.labok.goat.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.labok.goat.data.tweaks.Resolution

/**
 * 해상도 낮추기의 2차 안전망: 확인을 기다리던 중 앱 프로세스가 죽어 카운트다운이 멈췄어도, 시간이 지나면 원래 해상도로 되돌린다.
 * (1차는 앱 프로세스의 카운트다운, 3차는 부팅 수신기.)
 */
class ResolutionRevertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Resolution.revertIfPending(applicationContext, "확인하지 않아 자동 되돌림(예약 작업)")
        return Result.success()
    }
}
