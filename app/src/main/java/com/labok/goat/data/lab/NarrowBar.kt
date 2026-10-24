package com.labok.goat.data.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets as ComposeInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import com.labok.goat.ui.lab.NarrowControls
import com.labok.goat.ui.lab.NarrowKey
import com.labok.goat.ui.liquid.LiquidTheme
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 좁힌 앱 옆 여백 컨트롤러(실험실, [NarrowApps]): 앱 폭 좁히기로 생긴 검은 여백에 GOAT 리퀴드 컨트롤러([NarrowControls])를 띄운다.
 * 맨 앞 앱이 좁힌 앱이고 화면을 꽉 채울 때만 보인다(팝업 창·분할 화면이면 숨김). GOAT 상주 서비스가 [update]를 부른다.
 * 버튼은 Shizuku 셸 권한으로 앱 가운데를 쓸어 넘긴다(위아래·좌우). 붙일 쪽과 끄기는 상단바 꾹 누르기로 한다.
 * 여백 폭은 앱이 쓰는 높이(화면 높이 − 아래 내비게이션 막대)를 비율로 나눈 폭을 화면 폭에서 뺀 값이다(에뮬레이터 실측 1049px와 일치).
 */
object NarrowBar {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor() }
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var shownOn = -1

    /** 창을 붙였는지(addView는 바로 반영되지 않아 isAttachedToWindow로 보면 두 번 붙이려다 실패했다, 실기기 로그). */
    private var added = false

    /** 지금 컨트롤러를 띄운 앱. */
    @Volatile private var current: String? = null

    /** 지금 앱이 있는 화면 번호. */
    @Volatile private var display: Int = Display.DEFAULT_DISPLAY

    /** 컨트롤러에 그릴 최근 앱 아이콘. 바뀌면 Compose가 다시 그린다. */
    private var recentIcons by mutableStateOf<List<ImageBitmap>>(emptyList())
    private var recentTasks: List<ShizukuAccess.RecentTask> = emptyList()

    /** 앱 아이콘 비트맵(서비스·상단바 두 작업 스레드에서 부르므로 잠가서 쓴다): 앞 앱이 바뀔 때마다 다시 그리지 않게 둔다. */
    private val iconCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 12
    }

    /**
     * 맨 앞 앱을 따라가야 하는지: 전체 좁히기가 켜져 있으면 새로 앞에 온 앱을 좁히고, 상단바 숨기기·여백 버튼을 맞춘다.
     * 예전에는 여백 버튼이 켜져 있을 때만 따라가, 버튼을 끄면 새로 연 앱이 좁아지지 않았다(1.24.9 점검).
     */
    fun enabled(context: Context) = NarrowApps.on(context) && NarrowApps.global(context)

    /**
     * 맨 앞 앱이 [pkg]로 바뀌었을 때(작업 스레드에서 부른다): 좁힌 앱이 화면을 꽉 채우고 있으면 그 화면에 띄우고, 아니면 내린다.
     * 최근 앱 아이콘도 이때 새로 읽는다.
     */
    fun update(context: Context, pkg: String?) {
        val app = context.applicationContext
        val task = ShizukuAccess.focusedTask()
        // 맨 앞 화면이 권한 요청 창처럼 다른 앱이어도 작업의 바탕 앱 기준으로 좁힌다(에뮬레이터 연락처: 권한 창이 먼저 떴다)
        // 홈·최근 앱 화면은 앱이 아니다: 런처를 좁히거나 그 위에 버튼·상단바 숨기기를 남기지 않는다(1.24.10 에뮬레이터: 홈으로 가도 남음)
        val owner = if (task?.home == true) null else task?.basePkg ?: pkg
        // 전체 좁히기가 켜져 있으면 새로 앞에 온 앱을 먼저 좁힌다
        if (owner != null) NarrowApps.onForeground(app, owner)
        // 1 = 전체 화면. 팝업 창(5)·분할 화면(6)에서는 여백이 없으니 띄우지 않는다(1.24.3 실기기: 팝업 창 위에 떴다)
        val narrowed = enabled(app) && owner != null && NarrowApps.isNarrow(app, owner) && (task == null || task.windowingMode == 1)
        // 좁힌 앱이 상단바 아이콘 자리와 겹치면 SystemUI가 상단바에 반투명 막을 씌운다(AOSP LetterboxAppearanceCalculator.isScrimNeeded).
        // 셸로는 끌 수 없어, 좁힌 앱이 화면을 채우는 동안 상단바를 숨긴다(설정으로 끌 수 있음, 1.24.10)
        if (narrowed && NarrowApps.hideStatus(app)) StatusBarHider.startAuto(app) else StatusBarHider.stopAuto(app)
        val want = narrowed && NarrowApps.controller(app)
        if (want) {
            current = owner
            display = task?.displayId ?: Display.DEFAULT_DISPLAY
            loadRecents(app)
        }
        main.post { if (want) attach(app) else detach(app) }
    }

    private fun loadRecents(context: Context) {
        val pm = context.packageManager
        // 아이콘은 버튼 지름(최대 72dp)의 0.78배로 그린다: 그 크기로 읽어야 흐리게 늘어나지 않는다
        val size = (56 * context.resources.displayMetrics.density).roundToInt()
        val tasks = ShizukuAccess.recentTasks(6).filter { it.pkg != context.packageName }.take(5)
        val pairs = tasks.mapNotNull { t ->
            val icon = synchronized(iconCache) { iconCache[t.pkg] }
                ?: runCatching { pm.getApplicationIcon(t.pkg).toBitmap(size, size).asImageBitmap() }.getOrNull()?.also { synchronized(iconCache) { iconCache[t.pkg] = it } }
            icon?.let { t to it }
        }
        main.post {
            recentTasks = pairs.map { it.first }
            recentIcons = pairs.map { it.second }
        }
    }

    /** 붙일 쪽이 바뀌었을 때: 떠 있으면 새 여백 자리로 옮긴다. */
    fun relayout(context: Context) {
        val app = context.applicationContext
        main.post { if (view != null) attach(app) }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        StatusBarHider.stopAuto(app)
        main.post { detach(app) }
    }

    /**
     * 여백(px): 화면 [w]×[h], 아래 내비게이션 막대 [nav]일 때 [ratio](세로÷가로) 세로 앱 옆에 남는 폭 전체. 남지 않으면 0.
     * 앱이 가운데 붙으면 이 값의 절반씩 양옆에 생긴다.
     */
    internal fun margin(w: Int, h: Int, nav: Int, ratio: Float = 16f / 9): Int = (w - ((h - nav) / ratio).roundToInt()).coerceAtLeast(0)

    /** 컨트롤러를 둘 쪽과 폭: 앱이 왼쪽에 붙으면 오른쪽 여백 전체, 오른쪽이면 왼쪽, 가운데면 오른쪽 절반. */
    internal fun slot(side: NarrowApps.Side, margin: Int): Pair<Int, Int> = when (side) {
        NarrowApps.Side.LEFT -> Gravity.END to margin
        NarrowApps.Side.RIGHT -> Gravity.START to margin
        NarrowApps.Side.CENTER -> Gravity.END to margin / 2
    }

    /** 앱 가운데의 x(쓸어 넘길 자리): 앱이 붙은 쪽에 따라 다르다. */
    internal fun appCenterX(side: NarrowApps.Side, w: Int, margin: Int): Float = when (side) {
        NarrowApps.Side.LEFT -> (w - margin) / 2f
        NarrowApps.Side.RIGHT -> margin + (w - margin) / 2f
        NarrowApps.Side.CENTER -> w / 2f
    }

    private fun windowContext(context: Context): Context {
        val d = context.getSystemService(DisplayManager::class.java).getDisplay(display) ?: return context
        return context.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private fun attach(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            io.execute {
                ShizukuAccess.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
                if (Settings.canDrawOverlays(context)) main.post { attach(context) } else LogRepository.from(context).add("좁힌 앱 컨트롤러: 다른 앱 위에 표시 권한 없음")
            }
            return
        }
        // 다른 화면으로 옮겨 갔으면(접고 펴기) 창을 새로 만든다
        if (view != null && shownOn != display) detach(context)
        val v = view ?: create(windowContext(context)).also {
            view = it
            shownOn = display
        }
        val lp = layout(v.context) ?: return detach(context)
        val wm = v.context.getSystemService(WindowManager::class.java)
        runCatching {
            if (added) {
                wm.updateViewLayout(v, lp)
            } else {
                wm.addView(v, lp)
                added = true
            }
        }.onFailure { LogRepository.from(context).add("좁힌 앱 컨트롤러: 창을 띄우지 못함(${it.message})") }
    }

    private fun create(context: Context): ComposeView {
        val o = OverlayOwner().also { owner = it }
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                LiquidTheme {
                    // 키보드가 뜨면 키보드 위로 올라가고 넘기기 버튼만 남긴다
                    val density = LocalDensity.current
                    val ime = ComposeInsets.ime.getBottom(density)
                    NarrowControls(
                        modifier = Modifier.padding(bottom = if (ime > 0) with(density) { ime.toDp() } + 8.dp else 0.dp),
                        compact = ime > 0,
                        recents = recentIcons,
                        onRecent = { i -> recentTasks.getOrNull(i)?.let { t -> io.execute { ShizukuAccess.openRecent(t.taskId) } } },
                        onKey = { key -> press(context, key) }
                    )
                }
            }
        }
    }

    private fun detach(context: Context) {
        val v = view ?: return
        view = null
        shownOn = -1
        if (added) runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
        added = false
        owner?.destroy()
        owner = null
    }

    /** 지금 화면의 (폭, 높이, 여백). */
    private fun geometry(context: Context): Triple<Int, Int, Int> {
        val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val b = metrics.bounds
        val nav = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val ratio = current?.let { NarrowApps.ratioOf(context, it) }?.value ?: (16f / 9)
        return Triple(b.width(), b.height(), margin(b.width(), b.height(), nav, ratio))
    }

    /** 지금 화면 크기로 계산한 창 배치. 여백이 너무 좁으면(버튼이 들어가지 않으면) null. */
    private fun layout(context: Context): WindowManager.LayoutParams? {
        val (gravity, width) = slot(NarrowApps.side(context), geometry(context).third)
        if (width < (MIN_DP * context.resources.displayMetrics.density)) return null
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 포커스를 받지 않는 창은 기본으로 키보드 위에 그려져 자판을 가렸다(에뮬레이터 실측). ALT_FOCUSABLE_IM이면 키보드 아래에
            // 놓여, 키보드 높이(ime 인셋)를 받아 키보드 위로 올라가고 넘기기 버튼만 남긴다
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity or Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GOAT narrow bar"
        }
    }

    private fun press(context: Context, key: NarrowKey) {
        val app = context.applicationContext
        val (w, h, m) = geometry(context)
        val side = NarrowApps.side(app)
        val id = display
        io.execute {
            val x = appCenterX(side, w, m)
            val appW = (w - m).toFloat()
            val ok = when (key) {
                // 다음은 아래에서 위로, 이전은 위에서 아래로 앱 가운데를 쓸어 넘긴다
                NarrowKey.NEXT -> ShizukuAccess.injectSwipe(id, x, h * 0.72f, x, h * 0.28f)
                NarrowKey.PREV -> ShizukuAccess.injectSwipe(id, x, h * 0.28f, x, h * 0.72f)
                // 오른쪽 버튼은 다음 글(손가락이 오른쪽에서 왼쪽으로), 왼쪽 버튼은 이전 글
                NarrowKey.RIGHT -> ShizukuAccess.injectSwipe(id, x + appW * 0.3f, h * 0.5f, x - appW * 0.3f, h * 0.5f)
                NarrowKey.LEFT -> ShizukuAccess.injectSwipe(id, x - appW * 0.3f, h * 0.5f, x + appW * 0.3f, h * 0.5f)
            }
            if (!ok) LogRepository.from(app).add("좁힌 앱 컨트롤러: ${key.name} 실패(Shizuku 확인)")
        }
    }

    /** 상주 서비스 알림(이 기능만 켜져 있을 때). */
    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "좁힌 앱 컨트롤러", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 22, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("좁힌 앱 컨트롤러")
            .setContentText("좁힌 앱 옆 여백에 넘기기 버튼을 띄웁니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private const val CHANNEL_ID = "narrow_bar_quiet"

    /** 버튼이 들어갈 가장 좁은 여백(dp). */
    private const val MIN_DP = 40

    /** 서비스에서 띄운 ComposeView가 쓸 생명주기·상태 저장(액티비티가 없어서 직접 둔다). */
    private class OverlayOwner :
        LifecycleOwner,
        SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performRestore(Bundle())
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
