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
 * 뒤로 가기는 포커스가 실제로 넘어간 뒤 셸로 다시 보낸다(넘어가기 전에 보내면 이 창이 다시 받아 여러 번 돌게 된다).
 * 고정: 포커스를 잃으면(키 입력·다른 창) [REGRAB_MS] 뒤부터 확인해, 키보드가 닫혀 있고 포커스가 일반 앱 창에 있으면
 * 포커스를 다시 받아 다시 숨는다. 키보드가 열려 있거나 알림창·시스템 창이 포커스를 가진 동안은 [POLL_MS]마다 다시 확인한다
 * (포커스를 빼앗으면 입력이 끊기거나 시스템 창이 키를 못 받는다). 상태는 Shizuku 셸로만 알 수 있어, Shizuku가 없으면
 * 뒤로 가기 뒤에만 다시 숨는다. [다시 숨기기]로 창을 다시 띄워도 다시 숨는다. 포커스가 바뀌는 때를 GOAT 기록에 남긴다.
 */
object StatusBarHider {
    private const val CHANNEL_ID = "status_bar_hide"
    private const val NOTIFICATION_ID = 3201
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    /** 다시 숨기기 예약 세대. 끄거나 새로 예약하면 올려서 이전 예약을 무효로 만든다. */
    @Volatile private var regrabGen = 0
    /** 포커스를 내려놓는 중에 받은 뒤로 가기. 포커스가 넘어가면 앱에 보낸다. */
    private var pendingBack = false
    /** 마지막으로 포커스를 내려놓게 한 키가 뒤로 가기였는지. */
    private var lastKeyBack = false
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
        regrabGen++
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
                if (hasWindowFocus) {
                    regrabGen++
                    hideBars(this)
                } else {
                    sendPendingBack()
                    if (onFlow.value) scheduleRegrab(context, afterBack = lastKeyBack)
                    lastKeyBack = false
                }
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // 키는 이 창이 받으면 안 된다: 포커스를 내려놓고, 뒤로 가기는 아래 앱에 다시 보낸다
                if (event.action == KeyEvent.ACTION_UP) {
                    val back = event.keyCode == KeyEvent.KEYCODE_BACK
                    lastKeyBack = back
                    if (back && !pendingBack) {
                        pendingBack = true
                        // 포커스가 넘어가지 않아도 늦게나마 보낸다
                        main.postDelayed({ sendPendingBack() }, BACK_FALLBACK_MS)
                    }
                    stepAside(context, this)
                    log.add("상단바 숨기기: 키(${KeyEvent.keyCodeToString(event.keyCode)}) 입력으로 포커스 해제")
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

    private fun sendPendingBack() {
        if (!pendingBack) return
        pendingBack = false
        Thread { ShizukuAccess.shell("input keyevent 4") }.start()
    }

    /** 포커스를 잃은 뒤 다시 숨기기를 예약한다. 포커스를 다시 받으면([regrabGen]이 바뀌면) 멈춘다. */
    private fun scheduleRegrab(context: Context, afterBack: Boolean) {
        val gen = ++regrabGen
        val power = context.getSystemService(android.os.PowerManager::class.java)
        var tries = 0
        var lastWhy: String? = null
        fun check(delay: Long) {
            main.postDelayed({
                if (gen != regrabGen || !onFlow.value) return@postDelayed
                // 화면이 꺼져 있으면 셸을 부르지 않고 느리게 기다린다
                if (power?.isInteractive == false) return@postDelayed check(OFF_POLL_MS)
                Thread {
                    val state = focusState()
                    main.post {
                        if (gen != regrabGen || !onFlow.value) return@post
                        // 기다리는 이유가 바뀔 때만 기록한다(실기에서 왜 다시 숨지 않는지 보이게)
                        state?.why?.takeIf { it != lastWhy }?.let { LogRepository.from(context).add("상단바 숨기기: $it") }
                        lastWhy = state?.why
                        when {
                            // 다시 띄웠는데도 포커스가 안 오면(다른 앱 위 표시를 막는 설정 화면 등) 점점 느리게 확인한다
                            state?.app == true -> {
                                regrab(context, ++tries)
                                check((POLL_MS * tries).coerceAtMost(OFF_POLL_MS))
                            }
                            state != null -> check(POLL_MS)
                            // Shizuku가 없으면 뒤로 가기 뒤에만 다시 숨는다(키보드 입력 중에는 빼앗지 않는다)
                            else -> {
                                LogRepository.from(context).add("상단바 숨기기: Shizuku가 없어 ${if (afterBack) "뒤로 가기 뒤라 다시 숨김" else "다시 숨기지 않음"}")
                                if (afterBack) regrab(context, 1)
                            }
                        }
                    }
                }.start()
            }, delay)
        }
        check(REGRAB_MS)
    }

    /** [app]이면 다시 숨겨도 된다. [why]는 기록용 설명(포커스 창·키보드 상태). */
    private class FocusState(val app: Boolean, val why: String)

    /**
     * 지금 포커스가 일반 앱 창에 있고 키보드가 닫혀 있으면 app, 키보드가 열려 있거나 시스템 창(알림창·잠금 화면 등)이면 기다린다.
     * 셸을 쓸 수 없으면 null.
     */
    private fun focusState(): FocusState? {
        val out = ShizukuAccess.shell(
            "dumpsys window | grep -m1 mCurrentFocus; dumpsys input_method | grep -m1 mInputShown="
        )?.out ?: return null
        // mIsInputViewShown은 키보드를 닫은 뒤에도 true로 남는 경우가 있어 mInputShown만 본다
        val ime = out.contains("mInputShown=true")
        // 앱 창은 'Window{… u0 패키지/액티비티}' 꼴이다. 이 창(GOAT status bar hider)이나 시스템 창은 '/'가 없다
        val focus = out.lineSequence().firstOrNull { "mCurrentFocus" in it }.orEmpty()
        val app = Regex("""u\d+ [\w.]+/[\w.$]+""").containsMatchIn(focus)
        val name = focus.substringAfter("u0 ", focus.substringAfter("=")).trim().trimEnd('}').ifEmpty { "없음" }
        return when {
            ime -> FocusState(false, "키보드가 열려 있어 대기($name)")
            !app -> FocusState(false, "시스템 창이 포커스를 가져 대기($name)")
            else -> FocusState(true, "앱 창($name)으로 포커스가 넘어가 다시 숨김")
        }
    }

    /**
     * 창을 다시 띄워 포커스를 다시 받는다. 처음 켤 때 실기에서 실제로 숨겨지는 방법을 그대로 쓴다(1.11.12의 '플래그만 지우기'는
     * 에뮬레이터에서는 됐지만 실기에서 뒤로 가기 뒤 다시 숨지 않는다는 보고가 있었다). [tries]번째 시도는 처음 세 번만 기록한다.
     */
    private fun regrab(context: Context, tries: Int) {
        if (view == null) return
        detach(context)
        attach(context)
        if (tries <= 3) LogRepository.from(context).add("상단바 숨기기: 창을 다시 띄움(${tries}번째)")
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
            .setContentText("뒤로 가기를 써도 곧 다시 숨깁니다. 키보드가 열려 있는 동안에는 보입니다")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "다시 숨기기", action(ACTION_REHIDE, 1))
            .addAction(0, "끄기", action(ACTION_STOP, 2))
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private const val REGRAB_MS = 600L
    private const val POLL_MS = 2_000L
    private const val OFF_POLL_MS = 10_000L
    private const val BACK_FALLBACK_MS = 400L
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
