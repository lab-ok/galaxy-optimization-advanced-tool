package com.labok.goat.data.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 상단바 숨기기(실험): 안드로이드 11부터 시스템 전체 전체 화면 설정(policy_control)이 없어졌고, 셸은 상단바 높이 오버레이도
 * 만들 수 없다(OverlayManagerService가 셸의 fabricate를 막음). 남은 방법은 '포커스를 가진 창이 상단바를 숨기게 하는 것'이다
 * (AOSP InsetsPolicy.getStatusControlTarget은 강제 표시 상황이 아니면 포커스 창을 따른다). 그래서 다른 앱 위에 1px 투명 창을
 * 포커스를 받게 띄우고 상단바를 숨긴다. 위 가장자리를 쓸어내리면 잠깐 보인다.
 * 이 창이 포커스를 쥐는 동안 키 입력(뒤로 가기·키보드)이 이 창으로 오므로, 키가 오면 포커스를 내려놓고(상단바가 다시 보임)
 * 뒤로 가기는 셸로 다시 보낸다. [다시 숨기기]로 창을 다시 띄우면 다시 숨는다. 포커스가 바뀌는 때를 GOAT 기록에 남긴다.
 */
object StatusBarHider {
    private const val CHANNEL_ID = "status_bar_hide"
    private const val NOTIFICATION_ID = 3201
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val onFlow = MutableStateFlow(false)
    val on: StateFlow<Boolean> = onFlow.asStateFlow()

    /** 켠다. 성공하면 null, 실패하면 이유. 다른 앱 위에 표시 권한은 Shizuku로 준다. */
    fun start(context: Context): String? {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) {
            ShizukuAccess.shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            if (!Settings.canDrawOverlays(app)) return "다른 앱 위에 표시 권한을 받지 못했습니다(Shizuku 연결 필요)."
        }
        main.post { attach(app) }
        onFlow.value = true
        notify(app)
        LogRepository.from(app).add("상단바 숨기기 켬")
        return null
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        main.post { detach(app) }
        onFlow.value = false
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        LogRepository.from(app).add("상단바 숨기기 끔")
    }

    /** 창을 다시 띄워 포커스를 다시 받는다(키 입력으로 내려놓은 뒤). */
    fun rehide(context: Context) {
        val app = context.applicationContext
        if (!onFlow.value) return
        main.post {
            detach(app)
            attach(app)
        }
    }

    private fun attach(context: Context) {
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java)
        val log = LogRepository.from(context)
        val v = object : View(context) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                log.add("상단바 숨기기: 포커스 ${if (hasWindowFocus) "받음(숨김)" else "잃음(상단바 보일 수 있음)"}")
                if (hasWindowFocus) hideBars(this)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // 키는 이 창이 받으면 안 된다: 포커스를 내려놓고, 뒤로 가기는 아래 앱에 다시 보낸다
                if (event.action == KeyEvent.ACTION_UP) {
                    stepAside(context, this)
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) Thread { ShizukuAccess.shell("input keyevent 4") }.start()
                    log.add("상단바 숨기기: 키(${KeyEvent.keyCodeToString(event.keyCode)})가 와서 포커스를 내려놓음")
                }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GOAT status bar hider"
        }
        runCatching { wm.addView(v, lp) }.onFailure { log.add("상단바 숨기기: 창을 띄우지 못함(${it.message})") }.onSuccess {
            view = v
            v.post { hideBars(v) }
        }
    }

    private fun hideBars(v: View) {
        v.windowInsetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.statusBars())
        }
    }

    private fun stepAside(context: Context, v: View) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching { context.getSystemService(WindowManager::class.java).updateViewLayout(v, lp) }
    }

    private fun detach(context: Context) {
        val v = view ?: return
        view = null
        runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
    }

    private fun notify(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "상단바 숨기기", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        fun action(what: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, Receiver::class.java).setAction(what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("상단바 숨기는 중")
            .setContentText("키보드나 뒤로 가기를 쓰면 상단바가 다시 보입니다")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "다시 숨기기", action(ACTION_REHIDE, 1))
            .addAction(0, "끄기", action(ACTION_STOP, 2))
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private const val ACTION_REHIDE = "com.labok.goat.STATUS_BAR_REHIDE"
    private const val ACTION_STOP = "com.labok.goat.STATUS_BAR_STOP"

    /** 알림의 [다시 숨기기]·[끄기]. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_REHIDE -> rehide(context)
                ACTION_STOP -> stop(context)
            }
        }
    }
}
