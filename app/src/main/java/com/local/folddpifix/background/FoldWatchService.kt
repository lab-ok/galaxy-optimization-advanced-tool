package com.local.folddpifix.background

import com.local.folddpifix.AppInfo
import com.local.folddpifix.domain.ScreenLearner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.local.folddpifix.R
import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.DpiFixer
import com.local.folddpifix.domain.DpiPolicy
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.ui.MainActivity

/**
 * 접기/펼치기 감시. 폴링하지 않고 DisplayManager.DisplayListener 이벤트만 받는다.
 * Galaxy Fold는 접고 펼칠 때 기본 display(0)의 물리 화면이 바뀌고, AOSP는 강제 density를
 * 물리 화면(uniqueId)별로 저장하므로 화면을 바꾸면 다른 density가 적용될 수 있다.
 * 프로세스가 살아 있어야 이벤트를 받을 수 있어 foreground service로 동작한다.
 * 한 번 적용한 값은 재부팅 전까지 화면별로 유지되므로, 두 화면이 모두 확인되면 스스로 종료하고
 * 다음 부팅 때 다시 시작한다.
 */
class FoldWatchService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var fixer: DpiFixer

    /** 이번 실행 동안 목표값으로 확인된 화면. 두 화면 모두 확인되면 감시를 끈다(재부팅 전까지 유지되므로). */
    private val confirmed = mutableSetOf<String>()

    private val check = Runnable { checkAndMaybeStop("display 변경") }

    private fun checkAndMaybeStop(reason: String, retried: Boolean = false) {
        val decision = fixer.ensure(reason, automatic = true)
        // 외부 변경은 사용자가 고를 때까지 기다린다. 그동안 감시할 일이 없으니 바로 끈다(감지는 DensityWatchWorker가 맡음).
        if (decision == DpiPolicy.Decision.EXTERNAL_CHANGE) {
            LogRepository.from(this).add("외부 DPI 변경 선택 대기, 감시 종료")
            stopSelf()
            return
        }
        // 처음 보는 화면은 두 번 읽혀야 학습되므로 잠시 뒤 한 번만 더 확인한다.
        if (!retried && ScreenLearner.hasPendingCandidate) {
            handler.postDelayed({ checkAndMaybeStop("$reason 확인", retried = true) }, ScreenLearner.STABLE_MS + 200)
            return
        }
        confirmed += fixer.satisfiedScreenKeys()
        val settings = SettingsRepository.from(this)
        if (ScreenPolicy.allConfirmed(settings.screens, confirmed)) {
            LogRepository.from(this).add("두 화면 모두 목표 DPI 확인, 재부팅 전까지 유지되므로 감시 종료")
            stopSelf()
        }
    }

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            // 접기/펼치기 애니메이션 동안 이벤트가 연달아 오므로 마지막 이벤트 뒤 한 번만 확인한다.
            handler.removeCallbacks(check)
            handler.postDelayed(check, DEBOUNCE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fixer = DpiFixer.from(this)
        thread = HandlerThread("fold-watch").also { it.start() }
        handler = Handler(thread.looper)
        startAsForeground()
        getSystemService(DisplayManager::class.java).registerDisplayListener(listener, handler)
        LogRepository.from(this).add("접기/펼치기 감시 시작")
        handler.post { checkAndMaybeStop("감시 시작") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = SettingsRepository.from(this)
        if (!s.watchFold || !s.autoRecover) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener)
        handler.removeCallbacks(check)
        thread.quitSafely()
        LogRepository.from(this).add("접기/펼치기 감시 중지")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "디스플레이 전환 감지", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dpi)
            .setContentTitle(AppInfo.NAME)
            .setContentText("디스플레이 전환 시 밀도를 확인하는 중")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "fold_watch"
        private const val NOTIFICATION_ID = 1
        private const val DEBOUNCE_MS = 1_500L

        /** 설정에 맞춰 감시 서비스를 켜거나 끈다. */
        fun syncWithSettings(context: Context) {
            val s = SettingsRepository.from(context)
            val intent = Intent(context, FoldWatchService::class.java)
            if (s.watchFold && s.autoRecover) {
                try {
                    context.startForegroundService(intent)
                } catch (t: Throwable) {
                    LogRepository.from(context).add("감시 서비스 시작 실패: ${t.javaClass.simpleName}: ${t.message}")
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
