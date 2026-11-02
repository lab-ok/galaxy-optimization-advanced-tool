package com.labok.goat.data.lab

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.lab.FlexPanelState
import com.labok.goat.ui.lab.FlexPanelUi
import java.util.concurrent.Executors

/**
 * GOAT 플렉스 패널(11-02 소장님: 넷플릭스·디즈니+처럼 반쯤 접어도 아래 조작이 안 뜨는 앱에 GOAT가 직접 띄운다, 넷플릭스 테이블탑 화면을 따라).
 * 앱별 모드가 'GOAT 패널'인 앱을 반쯤 접으면 아래 절반(접는 선 아래)에 둥근 타일 패널을 띄운다([FlexPanelUi]).
 * - 제목·진행·재생·이동·속도: Shizuku로 그 앱의 미디어 세션에 붙어([MediaRemote]) 직접 조작한다.
 * - 세션이 없거나 이동을 지원하지 않으면 이동은 영상 좌우를 두 번 누르기(넷플릭스·디즈니+의 10초 이동) 또는 미디어 키로 대신한다(옵션).
 * - 밝기는 패널을 띄운 동안 수동 밝기로 바꾸고, 닫을 때 원래 자동 밝기 설정으로 되돌린다. 볼륨은 미디어 음량.
 * 옵션: 이동 시간(5·10·15·30초), 이동 방식(자동·두 번 누르기·미디어 키), 진행 막대·속도·밝기·볼륨·전체 화면 타일 보이기.
 */
object GoatFlexPanel {
    private const val PREF = "goat_flex_panel"
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    const val SEEK_AUTO = 0
    const val SEEK_TAP = 1
    const val SEEK_KEY = 2
    val SEEK_MODES = listOf(SEEK_AUTO, SEEK_TAP, SEEK_KEY)
    fun seekModeLabel(m: Int) = when (m) {
        SEEK_TAP -> "두 번 누르기"
        SEEK_KEY -> "미디어 키"
        else -> "자동"
    }
    val SEEK_SECONDS = listOf(5, 10, 15, 30)

    fun seekMode(c: Context) = prefs(c).getInt("seek_mode", SEEK_AUTO)
    fun setSeekMode(c: Context, v: Int) = prefs(c).edit().putInt("seek_mode", v).apply()
    fun seekSec(c: Context) = prefs(c).getInt("seek_sec", 10)
    fun setSeekSec(c: Context, v: Int) = prefs(c).edit().putInt("seek_sec", v).apply()

    /** 타일 보이기(키: progress·speed·brightness·volume·fullscreen). */
    fun shown(c: Context, key: String) = prefs(c).getBoolean("show_$key", true)
    fun setShown(c: Context, key: String, on: Boolean) = prefs(c).edit().putBoolean("show_$key", on).apply()

    private var view: ComposeView? = null
    private var owner: Owner? = null
    private var controller: MediaController? = null
    private var pkg: String? = null
    private var brightnessModeBefore: String? = null
    private val state = FlexPanelState()
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (view != null) main.postDelayed(this, 500)
        }
    }

    /** [pkg] 앱 위 아래 절반에 패널을 띄운다(아무 스레드에서나). */
    fun show(context: Context, pkg: String, label: String) {
        val app = context.applicationContext
        io.execute {
            val c = MediaRemote.controller(app, pkg)
            LogRepository.from(app).add("GOAT 플렉스 패널: $label 띄움 · 미디어 세션 ${c?.packageName ?: "없음"} · 동작 0x${java.lang.Long.toHexString(c?.playbackState?.actions ?: 0)}")
            main.post { attach(app, pkg, label, c) }
        }
    }

    /** 설정 화면에서 패널 모양을 8초 동안 미리 본다(지금 재생 중인 미디어가 있으면 그 정보로). */
    fun preview(context: Context) {
        show(context, "", "GOAT")
        main.postDelayed({ hide(context) }, 8000)
    }

    /** 아래로 내려가며 사라진 뒤 창을 뗀다. */
    fun hide(context: Context) {
        val app = context.applicationContext
        main.post {
            if (view == null) return@post
            state.visible = false
            main.postDelayed({ if (!state.visible) detach(app) }, 280)
        }
    }

    private fun attach(context: Context, p: String, label: String, c: MediaController?) {
        detach(context)
        if (!Settings.canDrawOverlays(context)) {
            io.execute { ShizukuAccess.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow") }
        }
        pkg = p
        controller = c
        state.app = label
        state.hasSession = c != null
        state.tint = when {
            "netflix" in p -> androidx.compose.ui.graphics.Color(0xFF8E0E14)
            "disney" in p -> androidx.compose.ui.graphics.Color(0xFF1B3D8F)
            "coupang" in p -> androidx.compose.ui.graphics.Color(0xFF2A6CF0)
            "youtube" in p -> androidx.compose.ui.graphics.Color(0xFFB0201C)
            else -> androidx.compose.ui.graphics.Color(0xFF3A4A6B)
        }
        refresh()
        val d = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val wctx = context.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val wm = wctx.getSystemService(WindowManager::class.java)
        val b = wm.maximumWindowMetrics.bounds
        val o = Owner().also { owner = it }
        val v = ComposeView(wctx).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                FlexPanelUi(
                    state = state,
                    seekSec = seekSec(context),
                    show = { shown(context, it) },
                    onClose = { hide(context) },
                    onPlayPause = { playPause(context) },
                    onSeek = { dir -> seek(context, dir, b.width(), b.height()) },
                    onSeekTo = { f -> seekTo(f) },
                    onSpeed = { speed(context) },
                    onBrightness = { v -> brightness(context, v) },
                    onVolume = { v -> volume(context, v) },
                    onFullscreen = {
                        hide(context)
                        io.execute { FlexPanel.resetPosture(context) }
                    },
                    onLock = { setLock(wctx, b.height() / 2, !state.locked) }
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            b.height() / 2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GOAT flex panel"
        }
        runCatching { wm.addView(v, lp) }.onFailure {
            LogRepository.from(context).add("GOAT 플렉스 패널: 창을 띄우지 못함(${it.message})".take(300))
            o.destroy()
            owner = null
            return
        }
        view = v
        state.visible = true
        // 볼륨·밝기 초기값
        val am = context.getSystemService(AudioManager::class.java)
        state.volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        state.brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        main.postDelayed(tick, 500)
    }

    private var shield: android.view.View? = null

    /** 영상(위 절반) 터치 잠금: 위 절반에 투명한 창을 덮어 누름을 받아 버린다. */
    private fun setLock(wctx: Context, half: Int, on: Boolean) {
        state.locked = on
        val wm = wctx.getSystemService(WindowManager::class.java)
        shield?.let { runCatching { wm.removeView(it) } }
        shield = null
        if (!on) return
        val v = android.view.View(wctx).apply { setOnTouchListener { _, _ -> true } }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            half,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            title = "GOAT flex lock"
        }
        runCatching { wm.addView(v, lp) }.onSuccess { shield = v }
    }

    private fun detach(context: Context) {
        main.removeCallbacks(tick)
        shield?.let { s -> runCatching { s.context.getSystemService(WindowManager::class.java).removeView(s) } }
        shield = null
        state.locked = false
        val v = view ?: return
        view = null
        runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
        owner?.destroy()
        owner = null
        controller = null
        state.art = null
        brightnessModeBefore?.let { m -> io.execute { ShizukuAccess.shell("settings put system screen_brightness_mode $m") } }
        brightnessModeBefore = null
        LogRepository.from(context).add("GOAT 플렉스 패널: 닫음")
    }

    /** 세션에서 제목·진행·재생 상태를 읽어 화면 상태에 넣는다(메인 스레드). */
    private fun refresh() {
        val c = controller ?: return
        runCatching {
            val md = c.metadata
            val ps = c.playbackState
            state.title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            state.subtitle = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            state.duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (state.art == null) {
                (md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
                    ?.let { state.art = it.asImageBitmap() }
            }
            state.playing = ps?.state == PlaybackState.STATE_PLAYING
            state.speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: state.speed
            state.position = ps?.let { if (it.state == PlaybackState.STATE_PLAYING) it.position + ((SystemClock.elapsedRealtime() - it.lastPositionUpdateTime) * it.playbackSpeed).toLong() else it.position } ?: 0L
            state.canSeek = ps != null && ps.actions and PlaybackState.ACTION_SEEK_TO != 0L && state.duration > 0
        }
    }

    private fun playPause(context: Context) {
        val c = controller
        io.execute {
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
            } else {
                ShizukuAccess.shell("input keyevent 85")
            }
        }
        state.playing = !state.playing
    }

    // 연달아 누른 이동을 모아 0.3초 동안 더 누르지 않으면 한 번에 옮긴다(세션 이동일 때). 두 번 누르기·미디어 키는 누를 때마다 보낸다
    private var pendingSec = 0
    private val flushSeek = Runnable {
        val total = pendingSec
        pendingSec = 0
        main.postDelayed({ if (pendingSec == 0) state.seekBadge = 0 }, 700)
        val c = controller ?: return@Runnable
        io.execute {
            val ps = c.playbackState ?: return@execute
            val now = ps.position + if (ps.state == PlaybackState.STATE_PLAYING) ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
            c.transportControls.seekTo((now + total * 1000L).coerceAtLeast(0))
        }
    }

    /** [dir]: -1 뒤로, +1 앞으로. */
    private fun seek(context: Context, dir: Int, w: Int, h: Int) {
        val sec = seekSec(context)
        val mode = seekMode(context)
        val c = controller
        val sessionSeek = mode == SEEK_AUTO && c?.playbackState?.let { it.actions and PlaybackState.ACTION_SEEK_TO != 0L } == true
        if (sessionSeek) {
            // 방향이 바뀌면 새로 센다
            if (pendingSec != 0 && (pendingSec > 0) != (dir > 0)) pendingSec = 0
            pendingSec += dir * sec
            state.seekBadge = pendingSec
            main.removeCallbacks(flushSeek)
            main.postDelayed(flushSeek, 300)
            return
        }
        state.seekBadge = dir * sec
        main.postDelayed({ state.seekBadge = 0 }, 700)
        io.execute {
            val canSeek = c?.playbackState?.let { it.actions and PlaybackState.ACTION_SEEK_TO != 0L } == true
            when {
                mode == SEEK_AUTO && c != null && canSeek -> {
                    val ps = c.playbackState!!
                    val now = ps.position + if (ps.state == PlaybackState.STATE_PLAYING) ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong() else 0L
                    c.transportControls.seekTo((now + dir * sec * 1000L).coerceAtLeast(0))
                }
                mode == SEEK_KEY -> ShizukuAccess.shell("input keyevent ${if (dir < 0) 89 else 90}")
                else -> {
                    // 영상(위 절반) 왼쪽·오른쪽을 빠르게 두 번 누른다(넷플릭스·디즈니+는 10초씩 이동)
                    val x = if (dir < 0) w / 4 else w * 3 / 4
                    val y = h / 4
                    ShizukuAccess.shell("input tap $x $y & sleep 0.09; input tap $x $y")
                }
            }
        }
    }

    private fun seekTo(fraction: Float) {
        val c = controller ?: return
        val d = state.duration
        if (d <= 0) return
        io.execute { c.transportControls.seekTo((d * fraction).toLong()) }
        state.position = (d * fraction).toLong()
    }

    private val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)

    private fun speed(context: Context) {
        val c = controller ?: return
        val next = speeds[(speeds.indexOfFirst { kotlin.math.abs(it - state.speed) < 0.01f } + 1).mod(speeds.size)]
        io.execute { runCatching { c.transportControls.setPlaybackSpeed(next) }.onFailure { LogRepository.from(context).add("GOAT 플렉스 패널: 속도 바꾸기 실패(${it.message})".take(300)) } }
        state.speed = next
    }

    private fun brightness(context: Context, v: Float) {
        state.brightness = v
        val value = (v * 255).toInt().coerceIn(1, 255)
        io.execute {
            if (brightnessModeBefore == null) {
                brightnessModeBefore = ShizukuAccess.shell("settings get system screen_brightness_mode")?.out?.trim()?.ifEmpty { null } ?: "1"
                ShizukuAccess.shell("settings put system screen_brightness_mode 0")
            }
            ShizukuAccess.shell("settings put system screen_brightness $value")
        }
    }

    private fun volume(context: Context, v: Float) {
        state.volume = v
        val am = context.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 서비스에서 띄운 ComposeView가 쓸 생명주기·상태 저장. */
    private class Owner :
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
