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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
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

    // 품질·앰비언트 프리셋(11-02 고도화). 유리 품질 0 끔·1 낮음·2 균형·3 높음, 앰비언트 0 끔·1 고정 색·2 포스터·3 화면 분석, 강도 0 은은·1 균형·2 몰입
    val QUALITIES = listOf(0, 1, 2, 3)
    fun qualityLabel(v: Int) = listOf("끔", "낮음", "균형", "높음")[v]
    val AMBIENT_MODES = listOf(0, 1, 2, 3)
    fun ambientLabel(v: Int) = listOf("끔", "고정 색", "포스터", "화면 분석")[v]
    val INTENSITIES = listOf(0, 1, 2, 3)
    fun intensityLabel(v: Int) = listOf("은은", "균형", "선명", "몰입")[v.coerceIn(0, 3)]
    fun quality(c: Context) = prefs(c).getInt("quality", 2)
    fun setQuality(c: Context, v: Int) = prefs(c).edit().putInt("quality", v).apply()
    fun ambientMode(c: Context) = prefs(c).getInt("ambient", 3)
    fun setAmbientMode(c: Context, v: Int) = prefs(c).edit().putInt("ambient", v).apply()

    /**
     * 앱별 앰비언트(11-02 소장님: 패널에서 실시간·포스터·앱 색상·없음을 바로 고르게). 고른 적이 없으면 전체 설정을 따르되,
     * 보호 콘텐츠 앱(넷플릭스 등)은 화면이 검게 찍혀 실시간이 의미가 없으므로 기본을 포스터로 한다.
     */
    fun ambientModeFor(c: Context, pkg: String): Int {
        val p = prefs(c)
        if (pkg.isNotEmpty() && p.contains("ambient_$pkg")) return p.getInt("ambient_$pkg", 3)
        val g = ambientMode(c)
        return if (g == 3 && DRM_APPS.any { pkg.startsWith(it) }) 2 else g
    }

    fun setAmbientModeFor(c: Context, pkg: String, v: Int) = prefs(c).edit().putInt("ambient_$pkg", v).apply()

    /** 패널에서 앰비언트를 바꿨다: 이 앱에 저장하고 앰비언트를 새 방식으로 다시 시작한다(메인 스레드). */
    fun pickAmbient(context: Context, mode: Int) {
        val app = context.applicationContext
        pkg?.let { if (it.isNotEmpty()) setAmbientModeFor(app, it, mode) }
        state.ambPicker = false
        state.ambMode = mode
        resetAmbientState()
        startAmbient(app, lastSaver, mode)
    }

    @Volatile private var lastSaver = false

    /**
     * 바닥 반사 블러(%, 10~200, 기본 30): 거리 블러 곡선 전체에 곱한다(11-02 소장님 "전체 블러·버튼 블러 설정 옵션").
     * 기본 30%·버튼 60dp는 소장님이 실기에서 맞춘 값(11-02 "이게 내가 원하는 설정이야 이걸 기본값으로")이다.
     */
    fun floorBlur(c: Context) = prefs(c).getInt("floor_blur", 30)
    fun setFloorBlur(c: Context, v: Int) = prefs(c).edit().putInt("floor_blur", v.coerceIn(10, 200)).apply()

    /** 버튼 유리 블러(dp, 0~100, 기본 60): 카드 안에서만 반사를 한 번 더 흐린다. */
    fun buttonBlur(c: Context) = prefs(c).getInt("button_blur", 60)
    fun setButtonBlur(c: Context, v: Int) = prefs(c).edit().putInt("button_blur", v.coerceIn(0, 100)).apply()

    fun intensity(c: Context) = prefs(c).getInt("intensity", 1)
    fun setIntensity(c: Context, v: Int) = prefs(c).edit().putInt("intensity", v).apply()
    fun haptics(c: Context) = prefs(c).getBoolean("haptics", true)
    fun setHaptics(c: Context, v: Boolean) = prefs(c).edit().putBoolean("haptics", v).apply()
    fun motion(c: Context) = prefs(c).getBoolean("motion", true)
    fun setMotion(c: Context, v: Boolean) = prefs(c).edit().putBoolean("motion", v).apply()

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
            LogRepository.from(app).add("GOAT 플렉스 패널: $label 띄움 · 미디어 세션 ${c?.packageName ?: "없음"} · 받은 길 ${MediaRemote.way} · 동작 0x${java.lang.Long.toHexString(c?.playbackState?.actions ?: 0)}")
            main.post {
                // 상태 머신: 퇴장 중·등장 중·보이는 중이면 창을 새로 만들지 않고 그 자리에서 다시 보이게 한다(깜박임·중복 창 방지)
                if (view != null && phase != Phase.HIDDEN) {
                    controller = c
                    this.pkg = pkg
                    state.app = label
                    state.hasSession = c != null
                    main.removeCallbacks(exitGuard)
                    phase = Phase.ENTERING
                    state.visible = true
                } else {
                    attach(app, pkg, label, c)
                }
            }
            artFromUri(app, c)
        }
    }

    /**
     * 포스터 진단·보충(11-02 최종 통합 지시): 보호 화면에서 넷플릭스는 포스터, 디즈니+는 앱 색으로 대신되는 차이는 세션이 그림을
     * 주느냐에 달렸다. 세션 메타데이터에 어떤 그림 키가 있는지 기록하고, 그림(비트맵)은 없고 주소(URI)만 있으면 그 주소에서
     * 공개 포스터를 받아 쓴다(content:// 또는 https, 3초 제한). 영상 화면은 손대지 않는다(io 스레드).
     */
    private fun artFromUri(app: Context, c: MediaController?) {
        val md = c?.metadata ?: return
        val bmpKeys = listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uriKeys = listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val hasBmp = bmpKeys.filter { md.getBitmap(it) != null }.map { it.substringAfterLast('.') }
        val uris = uriKeys.mapNotNull { k -> md.getString(k)?.takeIf { it.isNotBlank() }?.let { k.substringAfterLast('.') to it } }
        LogRepository.from(app).add("GOAT 플렉스 패널: 세션 그림 · 비트맵 ${hasBmp.ifEmpty { listOf("없음") }.joinToString()} · 주소 ${uris.map { it.first + "(" + it.second.substringBefore(':') + ")" }.ifEmpty { listOf("없음") }.joinToString()}")
        if (hasBmp.isNotEmpty()) return
        val uri = uris.firstOrNull()?.second ?: return
        val bmp = runCatching {
            if (uri.startsWith("http")) {
                val con = java.net.URL(uri).openConnection().apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                con.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            } else {
                app.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
        LogRepository.from(app).add("GOAT 플렉스 패널: 포스터 주소에서 받기 ${if (bmp != null) "성공 ${bmp.width}×${bmp.height}" else "실패"}")
        if (bmp != null) {
            main.post { if (controller === c && state.art == null) state.art = bmp.asImageBitmap() }
        }
    }

    /** 패널 생명주기. 바꾸는 곳은 [show]·[hide]·[onEntered]·[onExited]·[detach] 한 군데씩이다. */
    private enum class Phase { HIDDEN, ENTERING, VISIBLE, EXITING }

    private var phase = Phase.HIDDEN
    private var exitContext: Context? = null

    // 퇴장 애니메이션 끝 알림이 오지 않을 때(창이 이미 떨어짐 등)만 쓰는 안전장치
    private val exitGuard = Runnable { exitContext?.let { if (phase == Phase.EXITING) detach(it) } }

    /** 등장 애니메이션이 끝났다(화면이 알림). */
    private fun onEntered() {
        if (phase == Phase.ENTERING) phase = Phase.VISIBLE
    }

    /** 퇴장 애니메이션이 끝났다(화면이 알림): 이때 창을 뗀다. 그 사이 다시 보이게 했으면 떼지 않는다. */
    private fun onExited(context: Context) {
        if (phase == Phase.EXITING) detach(context)
    }

    /** 설정 화면에서 패널 모양을 8초 동안 미리 본다(지금 재생 중인 미디어가 있으면 그 정보로). */
    fun preview(context: Context) {
        posterPreview = false
        show(context, "", "GOAT")
        main.postDelayed({ hide(context) }, 8000)
    }

    /** 실험실: 포스터 표시를 8초 동안 미리 본다(세션 포스터 대신 시험용 포스터를 그려 넣는다). */
    fun previewPoster(context: Context) {
        posterPreview = true
        show(context, "", "GOAT")
        main.postDelayed({ state.art = samplePoster().asImageBitmap() }, 600)
        main.postDelayed({
            hide(context)
            posterPreview = false
        }, 8000)
    }

    @Volatile private var posterPreview = false

    /** 시험용 포스터(세로 2:3): 노을 그라데이션, 해, 산 실루엣, 굵은 제목 — 뒤집혔는지·잘렸는지·가장자리가 부드러운지 한눈에 보인다. */
    private fun samplePoster(): android.graphics.Bitmap {
        val w = 600
        val h = 900
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF1B1F5E.toInt(), 0xFFE8526B.toInt(), 0xFFFFB347.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFE08A.toInt()
        c.drawCircle(w * 0.5f, h * 0.62f, w * 0.18f, p)
        p.color = 0xFF14122A.toInt()
        val m = android.graphics.Path().apply {
            moveTo(0f, h * 0.78f)
            lineTo(w * 0.3f, h * 0.6f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.75f, h * 0.55f)
            lineTo(w.toFloat(), h * 0.74f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        c.drawPath(m, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 96f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = android.graphics.Paint.Align.CENTER
        c.drawText("GOAT", w / 2f, h * 0.18f, p)
        p.textSize = 34f
        c.drawText("POSTER TEST ↑", w / 2f, h * 0.26f, p)
        return b
    }

    /** 아래로 내려가며 사라진 뒤 창을 뗀다. */
    fun hide(context: Context) {
        val app = context.applicationContext
        main.post {
            if (view == null || phase == Phase.EXITING || phase == Phase.HIDDEN) return@post
            phase = Phase.EXITING
            exitContext = app
            state.visible = false
            main.removeCallbacks(exitGuard)
            main.postDelayed(exitGuard, 900)
        }
    }

    private fun attach(context: Context, p: String, label: String, c: MediaController?) {
        detach(context)
        // 첫 프레임은 보이지 않는 상태(투명·아래)로 그리고, 붙은 뒤에 등장을 시작한다(완성된 패널이 한 프레임 번쩍 보이지 않게)
        state.visible = false
        if (!Settings.canDrawOverlays(context)) {
            io.execute { ShizukuAccess.shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow") }
        }
        pkg = p
        controller = c
        state.app = label
        state.hasSession = c != null
        // 이전 패널·이전 앱의 색·포스터 상태가 새 패널 첫 프레임에 보이지 않게 중립으로 되돌린다(11-02 첫 등장 분홍빛 깜박임)
        resetAmbientState()
        state.tint = when {
            "netflix" in p -> androidx.compose.ui.graphics.Color(0xFF8E0E14)
            "disney" in p -> androidx.compose.ui.graphics.Color(0xFF1B3D8F)
            "coupang" in p -> androidx.compose.ui.graphics.Color(0xFF2A6CF0)
            "youtube" in p -> androidx.compose.ui.graphics.Color(0xFFB0201C)
            else -> androidx.compose.ui.graphics.Color(0xFF3A4A6B)
        }
        // 설정·접근성(줄이기 동작·고대비)·절전 반영
        val saver = context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode
        state.quality = if (saver) quality(context).coerceAtMost(1) else quality(context)
        state.intensity = intensity(context)
        state.frostDp = buttonBlur(context).toFloat()
        state.haptics = haptics(context)
        state.motion = motion(context) && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        state.highContrast = Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        // GOAT 잉크(설정의 잉크 색, 다크 모드 단계). 패널은 늘 어두운 리퀴드 바탕이다
        runCatching { com.labok.goat.ui.liquid.UiInk.load(context) }
        state.ink = if (com.labok.goat.ui.liquid.UiInk.mode == com.labok.goat.ui.liquid.UiInk.Mode.THEME) {
            androidx.compose.ui.graphics.Color(context.getColor(com.labok.goat.ui.liquid.UiInk.tone.colorRes(true)))
        } else {
            androidx.compose.ui.graphics.Color(0xFFEBEBEB)
        }
        state.appIcon = runCatching { context.packageManager.getApplicationIcon(p).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        val brand = state.tint.toArgb()
        state.ambLeft = brand
        state.ambCenter = brand
        state.ambRight = brand
        state.ambAll = brand
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
                    onSeek = { dir ->
                        // 두 번 누르기 자리는 지금 화면 크기로(회전했을 수 있다)
                        val pt = android.graphics.Point()
                        @Suppress("DEPRECATION")
                        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(pt)
                        seek(context, dir, pt.x, pt.y)
                    },
                    onSeekTo = { f -> seekTo(f) },
                    onSpeed = { speed(context) },
                    onSpeedSet = { s -> setSpeed(context, s) },
                    onBrightness = { v -> brightness(context, v) },
                    onVolume = { v -> volume(context, v) },
                    onFullscreen = {
                        hide(context)
                        io.execute { FlexPanel.resetPosture(context) }
                    },
                    onLock = { setLock(wctx, realHeight(context.getSystemService(DisplayManager::class.java)) / 2, !state.locked) },
                    onEntered = { onEntered() },
                    onExited = { onExited(context) }
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            realHeight(context.getSystemService(DisplayManager::class.java)) / 2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "GOAT flex panel"
            // 시스템 막대(아래 3버튼 내비게이션 등) 높이만큼 창이 위로 밀려, 패널 위 경계가 접는 선보다 높게 왔다(11-02 에뮬레이터 세로 화면에서
            // 92px). 화면 실제 크기 기준으로 맨 아래에 붙여 패널 위 경계가 정확히 화면 절반(접는 선)에 오게 한다
            fitInsetsTypes = 0
        }
        runCatching { wm.addView(v, lp) }.onFailure {
            LogRepository.from(context).add("GOAT 플렉스 패널: 창을 띄우지 못함(${it.message})".take(300))
            o.destroy()
            owner = null
            return
        }
        view = v
        // 화면이 돌면(가로↔세로) 창 높이를 지금 화면의 절반으로 다시 맞춘다. 붙일 때 크기로 고정했더니 회전 뒤 패널이 화면의 80%를 덮었다(11-02 에뮬레이터)
        val dm = context.getSystemService(DisplayManager::class.java)
        val relayout = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY || view !== v) return
                fit()
                // 회전 알림 시점에 실제 크기가 아직 옛 값일 때가 있어(11-02 에뮬레이터: 세로 절반 높이로 남아 패널이 화면의 80%를 덮음) 한 번 더 본다
                main.postDelayed({ if (view === v) fit() }, 350)
            }

            fun fit() {
                // 창 문맥의 크기는 회전을 늦게 따라와(에뮬레이터에서 그대로였다) 화면의 실제 크기로 잰다
                val half = realHeight(dm) / 2
                if (lp.height != half) {
                    lp.height = half
                    runCatching { wm.updateViewLayout(v, lp) }
                    shield?.let { sh ->
                        (sh.layoutParams as? WindowManager.LayoutParams)?.let { slp ->
                            slp.height = half
                            runCatching { wm.updateViewLayout(sh, slp) }
                        }
                    }
                }
            }
        }
        dm.registerDisplayListener(relayout, main)
        displayListener = relayout
        // 붙이는 사이에 화면이 돌았으면 알림을 놓친다 — 붙인 직후 한 번 맞춘다
        main.postDelayed({ if (view === v) relayout.fit() }, 350)
        phase = Phase.ENTERING
        main.post { if (phase == Phase.ENTERING) state.visible = true }
        lastSaver = saver
        startAmbient(context, saver, ambientModeFor(context, p))
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

    private var displayListener: DisplayManager.DisplayListener? = null

    /** 기본 화면의 지금 높이(회전 반영). */
    private fun realHeight(dm: DisplayManager): Int {
        val pt = android.graphics.Point()
        @Suppress("DEPRECATION")
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(pt)
        return pt.y
    }

    private fun detach(context: Context) {
        phase = Phase.HIDDEN
        displayListener?.let { l -> runCatching { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(l) } }
        displayListener = null
        main.removeCallbacks(exitGuard)
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
        ambToken++
        blackSince = 0L
        state.art = null
        state.field = null
        state.fieldPx = null
        lastTarget = null
        synchronized(blendLock) {
            blender = FieldBlender()
            nearBlender = FieldBlender()
            hingeBlender = FieldBlender()
        }
        state.near = null
        state.hinge = null
        brightnessModeBefore?.let { m -> io.execute { ShizukuAccess.shell("settings put system screen_brightness_mode $m") } }
        brightnessModeBefore = null
        LogRepository.from(context).add("GOAT 플렉스 패널: 닫음")
    }

    private fun resetAmbientState() {
        state.ambReady = false
        state.posterShown = false
        state.ambSource = ""
        state.ambLeft = com.labok.goat.ui.lab.NEUTRAL
        state.ambCenter = com.labok.goat.ui.lab.NEUTRAL
        state.ambRight = com.labok.goat.ui.lab.NEUTRAL
        state.ambAll = com.labok.goat.ui.lab.NEUTRAL
        state.field = null
        state.fieldPx = null
        state.near = null
        state.hinge = null
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
            // 방금 속도를 바꿨으면 앱이 새 속도를 알려 줄 때까지(최대 1.5초) 옛 값으로 되돌리지 않는다. 예전에는 0.5초마다 옛 속도로
            // 덮어써 눌러도 바뀌지 않는 것처럼 보였다(11-02 소장님 "재생속도 조절 안되는 느낌")
            if (SystemClock.elapsedRealtime() > speedHoldUntil) state.speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: state.speed
            state.canSpeed = ps != null && ps.actions and PlaybackState.ACTION_SET_PLAYBACK_SPEED != 0L
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

    @Volatile private var speedHoldUntil = 0L

    private fun speed(context: Context) {
        val next = speeds[(speeds.indexOfFirst { kotlin.math.abs(it - state.speed) < 0.01f } + 1).mod(speeds.size)]
        setSpeed(context, next)
    }

    /**
     * 속도를 바꾸고 0.9초 뒤 앱이 실제로 받았는지 확인한다. 앱이 속도 변경을 지원하지 않거나(세션 동작에 SET_PLAYBACK_SPEED 없음)
     * 받고도 그대로면 화면 값을 실제 속도로 되돌리고 그 사실을 보여 준다(누를 때마다 값만 바뀌고 영상은 그대로인 일이 없게).
     */
    private fun setSpeed(context: Context, s: Float) {
        val c = controller ?: return
        speedHoldUntil = SystemClock.elapsedRealtime() + 1500
        state.speed = s
        state.speedNote = ""
        io.execute {
            val r = runCatching { c.transportControls.setPlaybackSpeed(s) }
            Thread.sleep(900)
            val actual = c.playbackState?.playbackSpeed ?: 0f
            val supported = (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SET_PLAYBACK_SPEED != 0L
            val ok = r.isSuccess && kotlin.math.abs(actual - s) < 0.01f
            LogRepository.from(context).add("GOAT 플렉스 패널: 속도 ${s}x 요청 · 앱이 알린 속도 ${actual}x · 지원 표시 $supported · ${if (ok) "반영" else "반영 안 됨"}${r.exceptionOrNull()?.let { " (${it.message})" } ?: ""}".take(300))
            main.post {
                if (controller !== c) return@post
                if (!ok) {
                    speedHoldUntil = 0L
                    if (actual > 0f) state.speed = actual
                    state.speedNote = if (supported) "앱이 속도를 바꾸지 않았습니다" else "이 앱은 속도 변경을 받지 않습니다"
                }
            }
        }
    }

    // ---- 앰비언트 엔진 연결 ----
    @Volatile private var ambToken = 0
    private var artPalette: AmbientColor.Palette? = null
    private var artFor: androidx.compose.ui.graphics.ImageBitmap? = null

    /**
     * 앰비언트 색을 고른다(패널이 떠 있는 동안 따로 한 줄). 설정에 따라:
     * - 화면 분석: Shizuku 셸 화면 찍기로 지금 화면을 아주 작게(64칸) 받아 위 절반(영상 쪽)만 [AmbientColor.extract]한다. 기기 안에서만 계산하고
     *   저장·전송하지 않는다. 보호 콘텐츠(DRM·보안 창)는 시스템이 검게 찍어 주므로 분석하지 않고 포스터 → 앱 색으로 대신하며 그 사실을 표시한다.
     *   변화가 없으면 0.45초 → 1.5초로 늦추고(절전이면 두 배), 패널이 닫히면 멈춘다.
     * - 포스터: 미디어 세션 포스터에서 뽑는다. 없으면 앱 색.
     * - 고정 색: 앱 대표 색.
     */
    private fun startAmbient(context: Context, saver: Boolean, mode: Int) {
        context.resources.displayMetrics.let { screenWdp = it.widthPixels / it.density }
        floorScale = floorBlur(context) / 100f
        state.ambMode = mode
        val token = ++ambToken
        state.ambientOn = mode != 0
        if (mode == 0) {
            state.ambSource = "앰비언트 끔"
            return
        }
        kotlin.concurrent.thread(name = "goat-ambient", isDaemon = true) {
            // 11-02 소장님: 넷플릭스·디즈니+에서 앰비언트가 5초에 한 번쯤만 바뀐다. 예전에는 대표색이 비슷하면 '정지'로 보고 2초로 늦췄고
            // (절전이면 4초) 보호 화면에서는 포스터 색이 늘 같아 곧바로 느린 주기로 빠졌다. 이제는 화면 이미지 자체의 공간 변화량으로
            // 정지를 판단하고, 움직이면 0.25초마다, 8번 연속 거의 같을 때만 1초로 늦춘다(절전 1.5배). 캡처→분석→필드→반영 시각을 5초마다 기록한다
            var period = ACTIVE_MS
            var still = 0
            var prevFrame: IntArray? = null
            val tel = Telemetry()
            activeTel = tel
            if (mode == 3) startAnalysis(context, token, tel)
            // 실시간 미러(11-02): 셸 도우미가 화면을 작은 가상 화면으로 미러링해 초당 최대 20장을 계속 보낸다. 실패하면 아래 예전 캡처로
            if (mode == 3 && runMirror(context, token, tel, saver)) return@thread
            while (ambToken == token) {
                val t0 = SystemClock.elapsedRealtime()
                if (mode == 3) {
                    // 캡처 줄: 위 절반(영상 쪽)만 찍어 '최신 하나'로 넘기고(앞 것이 아직 안 쓰였으면 버린다) 바로 다음 캡처로 간다.
                    // 패널(아래 절반)은 찍지 않아 패널 색이 다시 들어가는 일이 없다. 분석은 [startAnalysis] 줄이 한다
                    // 4배 해상도(384칸)로 한 번 찍는다: 근거리 띠는 그대로 쓰고, 기본 필드는 4×4 평균으로 줄여 쓴다(11-02 근거리 화질 지시).
                    // 셸 캡처는 화면 전체 바이트를 어차피 흘려 받으므로, 칸을 늘려도 캡처 시간은 거의 같다(실측은 파이프라인 로그)
                    val f = TentMode.topFrame(NEAR_W, 0.5f)
                    val t1 = SystemClock.elapsedRealtime()
                    tel.capture(t1 - t0, f != null)
                    var changed = false
                    if (f != null) {
                        val px0 = IntArray(f.width * f.height)
                        f.getPixels(px0, 0, f.width, 0, 0, f.width, f.height)
                        if (latest.getAndSet(Frame(px0, f.width, f.height, t1)) != null) tel.dropped++
                        // 공간 변화량: 같은 자리 칸끼리 색 차이(평균색이 비슷한 다른 장면도 잡는다)
                        changed = (prevFrame?.let { frameDiff(it, px0) } ?: 1f) > CHANGE_MIN
                        prevFrame = px0
                    } else {
                        tel.fallback++
                    }
                    still = if (changed) 0 else still + 1
                    period = if (still >= 8) STILL_MS else ACTIVE_MS
                } else {
                    // 포스터·고정 색: 포스터가 바뀔 때만 다시 보면 된다
                    val pal = if (mode == 2) artPaletteNow() else null
                    val src = if (pal != null) "포스터" else "앱 색"
                    post(context, token, tel, src, pal, if (pal != null) artFieldNow() else null, false, t0)
                    period = 2000L
                }
                tel.maybeLog(context, period, saver, state.ambSource)
                // 처리에 걸린 시간을 뺀 만큼만 쉰다(밀리면 쉬지 않고 바로 최신 프레임을 찍는다)
                val spent = SystemClock.elapsedRealtime() - t0
                val wait = ((if (saver) period * 3 / 2 else period) - spent).coerceAtLeast(0L)
                if (wait > 0) Thread.sleep(wait)
            }
        }
    }

    /** 캡처 한 장. [near]가 있으면(미러 2판) [px]는 이미 기본 해상도이고, [near]는 접는 선 쪽 띠를 원본 해상도로 담는다. */
    private class Frame(val px: IntArray, val w: Int, val h: Int, val at: Long, val near: IntArray? = null, val nw: Int = 0, val nh: Int = 0)

    /**
     * [MirrorCapture] 도우미를 Shizuku 셸로 띄우고 프레임을 계속 읽어 [latest]에 넣는다. 패널이 닫히면(토큰 바뀜) 도우미를 끝낸다.
     * 화면이 돌면 도우미가 스스로 끝나므로 다시 띄운다. 한 장도 못 받고 두 번 실패하면 false(예전 screencap 캡처로 넘어간다).
     */
    private fun runMirror(context: Context, token: Int, tel: Telemetry, saver: Boolean): Boolean {
        val apk = context.applicationInfo.sourceDir ?: return false
        var gotAny = false
        var failures = 0
        while (ambToken == token) {
            // 근거리 띠 768칸 원본 + 기본 128칸(도우미가 줄여 보냄). 프레임 상한 60장(절전 30장) — 11-02 소장님 "프레임 60으로"
            val p = ShizukuAccess.stream(MirrorCapture.command(apk, MIRROR_W, if (saver) 30 else 60, 0.5f, ReflectionField.NEAR_FRAC, BASE_W)) ?: return gotAny
            kotlin.concurrent.thread(name = "goat-mirror-watch", isDaemon = true) {
                while (ambToken == token && runCatching { p.exitValue() }.isFailure) Thread.sleep(200)
                runCatching { p.destroy() }
            }
            var frames = 0
            runCatching {
                val inp = java.io.DataInputStream(java.io.BufferedInputStream(p.inputStream, 1 shl 16))
                val head = ByteArray(20)
                var nb = ByteArray(0)
                var bb = ByteArray(0)
                var last = SystemClock.elapsedRealtime()
                while (ambToken == token) {
                    inp.readFully(head)
                    if (le(head, 0) != MirrorCapture.MAGIC) error("형식이 맞지 않음")
                    val nw = le(head, 4)
                    val nh = le(head, 8)
                    val bw = le(head, 12)
                    val bh = le(head, 16)
                    if (nb.size != nw * nh * 4) nb = ByteArray(nw * nh * 4)
                    if (bb.size != bw * bh * 4) bb = ByteArray(bw * bh * 4)
                    inp.readFully(nb)
                    inp.readFully(bb)
                    val near = rgbaToArgb(nb, nw * nh)
                    val base = rgbaToArgb(bb, bw * bh)
                    val now = SystemClock.elapsedRealtime()
                    tel.capture(now - last, true)
                    last = now
                    if (latest.getAndSet(Frame(base, bw, bh, now, near, nw, nh)) != null) tel.dropped++
                    frames++
                    gotAny = true
                    tel.maybeLog(context, 0, saver, state.ambSource + " · 실시간 미러")
                }
            }
            runCatching { p.destroy() }
            if (frames == 0) {
                failures++
                if (failures >= 2) {
                    LogRepository.from(context).add("GOAT 플렉스 패널: 실시간 미러를 띄우지 못해 예전 캡처로 바꿈")
                    return gotAny
                }
            }
        }
        return true
    }

    private fun rgbaToArgb(b: ByteArray, n: Int) = IntArray(n) { i ->
        val o = i * 4
        (0xFF shl 24) or ((b[o].toInt() and 0xFF) shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() and 0xFF)
    }

    /** 반사 그림 가로 [cols]칸 중 한 칸이 패널(화면 폭)에서 몇 dp인지. 블러 곡선(dp)을 칸 단위로 바꾼다. */
    @Volatile private var screenWdp = 1848f / 2.625f

    private fun cellDpFor(cols: Int) = screenWdp / cols.coerceAtLeast(1)

    /** 실험실 「바닥 반사 블러」 배율(앰비언트를 시작할 때 읽는다). */
    @Volatile private var floorScale = 0.3f

    private fun le(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    /** 캡처 줄이 넘긴 최신 프레임(분석 줄이 가져가면 비운다). 분석이 밀리면 덮어써서 오래된 프레임이 쌓이지 않는다. */
    private val latest = java.util.concurrent.atomic.AtomicReference<Frame?>(null)

    /**
     * 분석 줄(화면 분석 모드): 최신 프레임을 가져와 보호 화면 판정 → 대표색 → 반사 필드를 만들고 메인 스레드에 넘긴다.
     * 보호 화면이면 포스터 → 앱 색으로 대신한다. 캡처 줄과 따로 돌아 캡처를 기다리게 하지 않는다.
     */
    private fun startAnalysis(context: Context, token: Int, tel: Telemetry) {
        latest.set(null)
        kotlin.concurrent.thread(name = "goat-ambient-analyze", isDaemon = true) {
            var prev: IntArray? = null
            while (ambToken == token) {
                val hi = latest.getAndSet(null)
                if (hi == null) {
                    Thread.sleep(15)
                    continue
                }
                val t1 = SystemClock.elapsedRealtime()
                // 기본 필드·판정은 4×4 평균으로 줄인 96칸으로(예전과 같은 크기), 근거리 띠만 고해상도 원본으로
                // 미러 2판은 기본(128칸)과 근거리 원본 띠가 따로 온다. 예전 screencap은 고해상도 한 장을 여기서 줄인다
                val k = if (hi.near != null) (hi.nw / hi.w).coerceAtLeast(1) else (hi.w / BASE_W).coerceAtLeast(1)
                val fr = if (hi.near != null || k == 1) hi else Frame(ReflectionField.boxDown(hi.px, hi.w, hi.h, k), hi.w / k, hi.h / k, hi.at)
                // 11-02 실기 로그: 0.8% 기준이면 영상 프레임 대부분이 '변화 없음'으로 버려져 반사가 초당 3~6번만 바뀌었다 → 0.15%
                val changed = prev?.let { frameDiff(it, fr.px) > CHANGE_MIN } ?: true
                prev = fr.px
                if (changed) tel.changed++
                // 보호 화면 판정: 거의 다 검은데(85% 넘게) 앱이 재생 중이면 보호 콘텐츠로 본다. 자막·조작 버튼만 찍혀
                // 보호 화면과 분석이 번갈아 바뀌던 것을 막는다(가설, 실기 기록으로 확인한다)
                val dark = darkFraction(fr.px)
                val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
                // 대표색은 4×4로 줄인 그림으로(실기 속도: 전체 칸으로 하면 프레임당 수 ms~수십 ms)
                val pal0 = if (fr.w >= 64 && fr.h >= 16) AmbientColor.extract(ReflectionField.boxDown(fr.px, fr.w, fr.h, 4), fr.w / 4, fr.h / 4) else AmbientColor.extract(fr.px, fr.w, fr.h)
                // 11-02 실사용 검수: 유튜브 어두운 장면(실제 검은 영상)이 보호 화면으로 잘못 잡혀 연출로 바뀔 수 있었다. 화면만으로는
                // 검은 영상과 DRM을 구분할 수 없어, 보호 콘텐츠 앱(넷플릭스·디즈니+·쿠팡플레이 등)은 바로, 그 밖의 앱은 재생 중
                // 거의 검은 화면이 4초 넘게 이어질 때만 보호 화면으로 본다(짧은 암전은 그대로 어둡게 비친다)
                val blackish = pal0.protectedLike || (dark > 0.85f && playing)
                blackSince = if (!blackish) {
                    0L
                } else if (blackSince == 0L) {
                    t1
                } else {
                    blackSince
                }
                val drmApp = DRM_APPS.any { pkg?.startsWith(it) == true }
                val protectedNow = blackish && (drmApp || t1 - blackSince >= 4000L)
                var pal: AmbientColor.Palette? = null
                var field: Field? = null
                var keep = false
                var src: String
                if (!protectedNow) {
                    slowBlend = false
                    pal = pal0
                    if (changed || state.fieldPx == null) {
                        // 위 화면을 실제 기하 그대로 비춘다(11-02 소장님: 여백이 있으면 굳이 붙일 필요 없다 — 1.42.10~15는 검은 띠를 빼고
                        // 영상 끝을 접는 선에 붙였는데, 자막이 나타나면 내용 범위가 바뀌어 반사가 위아래로 밀려 더 부자연스러웠다).
                        // 진짜 거울처럼 접는 선 바로 위 화면이 그대로 접는 선 바로 아래에 오고, 검은 여백이면 그 자리는 어둡게 비친다
                        field = if (hi.near != null) {
                            withNearBand(makeField(fr.px, fr.w, fr.h), hi.near, hi.nw, hi.nh, k)
                        } else {
                            withNear(makeField(fr.px, fr.w, fr.h), hi, 0, hi.h - (hi.h % k) - 1, k)
                        }
                    } else {
                        keep = true
                    }
                    src = "화면 분석"
                } else {
                    tel.black++
                    pal = artPaletteNow()
                    // Cinematic Ambient Fallback: 보호 화면은 영상을 볼 수 없으므로(DRM, 우회하지 않음) 포스터(없으면 앱 색)로 만든
                    // 반사에 느리고 절제된 빛 흐름을 얹는다. 실제 영상 반사가 아니라는 것을 머리글에 밝힌다
                    val base = artFieldSource() ?: tintSource()
                    field = fieldFrom(cinematic(base.first, base.second, base.third, SystemClock.elapsedRealtime()), base.second, base.third)
                    slowBlend = true
                    src = if (pal != null) "보호 화면 · 포스터 연출(실제 영상 아님)" else "보호 화면 · 앱 색 연출(실제 영상 아님)"
                    tel.fallback++
                }
                tel.analyze(SystemClock.elapsedRealtime() - t1)
                post(context, token, tel, src, pal, field?.withHinge(hingeRow(hi)), keep, fr.at)
            }
        }
    }

    /** 분석 결과를 메인 스레드에서 화면 상태에 넣는다. [capturedAt]부터 반영까지 걸린 시간을 계측한다. */
    private fun post(context: Context, token: Int, tel: Telemetry, src: String, p: AmbientColor.Palette?, fNew: Field?, keep: Boolean, capturedAt: Long) {
        main.post {
            if (ambToken != token) return@post
            tel.applied(SystemClock.elapsedRealtime() - capturedAt)
            state.ambSource = src
            state.ambReady = true
            // 포스터를 쓰는 경우(포스터 모드·보호 화면의 포스터 연출)는 반사 대신 포스터 원본을 똑바로 보여 준다(11-02 소장님)
            // 앱 색 대체도 단색 대신 앱 아이콘을 크게 흐린 빛으로 보여 준다(11-02 소장님)
            state.posterShown = "포스터" in src || "앱 색" in src || posterPreview
            // 무엇을 보일지 따로 정한다(11-02 소장님 "앱 색상이 포스터랑 합쳐져 있니?" — 예전에는 앱 색상에서도 포스터가 있으면 포스터를 보였다)
            state.posterKind = if ("포스터" in src || posterPreview) {
                2
            } else if ("앱 색" in src) {
                1
            } else {
                0
            }
            if (fNew == null && !keep) {
                if (state.field != null) blendTo(token, null)
            } else if (fNew != null && (slowBlend || lastTarget == null || fieldChange(lastTarget!!, fNew.px) > CHANGE_MIN)) {
                if (SystemClock.elapsedRealtime() - clarityLogAt > 1000) {
                    clarityLogAt = SystemClock.elapsedRealtime()
                    val c = listOf(0.1f, 0.5f, 0.9f).map { ReflectionField.clarity(fNew.px, fNew.w, (fNew.h * it).toInt()) }
                    LogRepository.from(context).add("GOAT 플렉스 패널: 반사 선명도 위 %.3f · 가운데 %.3f · 아래 %.3f".format(c[0], c[1], c[2]))
                }
                tel.fieldUpdates++
                lastFieldAt = SystemClock.elapsedRealtime()
                android.util.Log.i("GOATAMB", "field-applied captured=$capturedAt now=$lastFieldAt")
                lastTarget = fNew.px
                blendTo(token, fNew)
            }
            val c = p?.let { intArrayOf(it.left, it.center, it.right, it.overall) } ?: IntArray(4) { state.tint.toArgb() }
            state.ambLeft = c[0]
            state.ambCenter = c[1]
            state.ambRight = c[2]
            state.ambAll = c[3]
        }
    }

    /** 화면이 검게 찍히는 보호 콘텐츠 앱(접두사). 이 앱들은 검은 화면을 바로 보호 화면으로 본다. */
    private val DRM_APPS = listOf("com.netflix.", "com.disney.", "com.coupang.mobile.play", "com.amazon.avod", "net.cj.cjhv.gs.tving", "kr.co.captv.pooqV2", "com.frograms.wplay")

    @Volatile private var blackSince = 0L

    /** 반사를 다시 만들 최소 화면 변화(같은 자리 칸 색 차이 평균). */
    private const val CHANGE_MIN = 0.0015f

    private const val ACTIVE_MS = 250L
    private const val STILL_MS = 1000L

    @Volatile private var lastFieldAt = 0L

    /** 두 프레임(같은 크기)의 같은 자리 칸 색 차이 평균(0~1). */
    private fun frameDiff(a: IntArray, b: IntArray): Float {
        if (a.size != b.size) return 1f
        var s = 0L
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            s += kotlin.math.abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)) + kotlin.math.abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF)) + kotlin.math.abs((x and 0xFF) - (y and 0xFF))
        }
        return s / (a.size * 3f * 255f)
    }

    /** 거의 검은 칸의 비율. */
    private fun darkFraction(px: IntArray): Float {
        var d = 0
        for (c in px) if ((c shr 16 and 0xFF) < 16 && (c shr 8 and 0xFF) < 16 && (c and 0xFF) < 16) d++
        return d / px.size.toFloat()
    }

    /** 앰비언트 파이프라인 계측: 5초마다 한 줄로 남긴다(캡처·분석·화면 변화·필드 갱신·반영 지연·대체 경로). */
    private class Telemetry {
        var since = SystemClock.elapsedRealtime()
        var captures = 0
        var ok = 0
        var captureMs = 0L
        var analyzeMs = 0L
        var analyzed = 0
        var changed = 0
        var black = 0
        var fallback = 0
        var dropped = 0

        @Volatile var fieldUpdates = 0

        @Volatile var appliedMs = 0L

        @Volatile var applies = 0

        fun capture(ms: Long, success: Boolean) {
            captures++
            if (success) ok++
            captureMs += ms
        }

        fun analyze(ms: Long) {
            analyzed++
            analyzeMs += ms
        }

        @Volatile var stallMs = 0L

        @Volatile var stalls = 0

        @Volatile var interval = 0f

        /** 전환이 끝나 다음 샘플을 기다리며 멈춰 있던 시간. */
        fun stall(ms: Long) {
            stallMs += ms
            stalls++
        }

        fun applied(ms: Long) {
            appliedMs += ms
            applies++
        }

        fun maybeLog(context: Context, period: Long, saver: Boolean, src: String) {
            val now = SystemClock.elapsedRealtime()
            val span = now - since
            if (span < 5000) return
            LogRepository.from(context).add(
                (
                    "GOAT 앰비언트 파이프라인 %.1f초: 캡처 %d회(성공 %d·평균 %dms·버림 %d) · 분석 평균 %dms · 화면 변화 %d회 · 보호(검음) %d회 · 대체 %d회 · 필드 갱신 %d회 · 캡처→반영 평균 %dms · 주기 %dms%s · %s"
                    ).format(
                    span / 1000f, captures, ok, if (captures > 0) captureMs / captures else 0, dropped, if (analyzed > 0) analyzeMs / analyzed else 0,
                    changed, black, fallback, fieldUpdates, if (applies > 0) appliedMs / applies else 0, period, if (saver) "(절전 1.5배)" else "", src
                ).take(300)
            )
            // 시간 보간 계측(11-02 근거리·시간 보간 지시): 샘플 간격 추정과, 전환이 끝나 다음 샘플을 기다리며 멈춰 있던 시간
            LogRepository.from(context).add(
                "GOAT 앰비언트 시간 보간: 샘플 간격 추정 %dms · 전환 뒤 멈춤 %d회 평균 %dms(필드 갱신 %d회 중)".format(
                    interval.toInt(),
                    stalls,
                    if (stalls > 0) stallMs / stalls else 0,
                    fieldUpdates
                )
            )
            val fps = fieldUpdates * 1000f / span.coerceAtLeast(1)
            main.post { state.ambFps = fps }
            stallMs = 0
            stalls = 0
            since = now
            captures = 0
            ok = 0
            captureMs = 0
            analyzeMs = 0
            analyzed = 0
            changed = 0
            black = 0
            fallback = 0
            dropped = 0
            fieldUpdates = 0
            appliedMs = 0
            applies = 0
        }
    }

    private var clarityLogAt = 0L

    private class Field(val px: IntArray, val w: Int, val h: Int, val near: IntArray? = null, val nw: Int = 0, val nh: Int = 0, val hinge: IntArray? = null)

    /** 접는 선 바로 위 화면 한 줄(고해상도, 다듬지 않은 실제 색). 레터박스 검은 여백이면 검은색 그대로다. */
    private fun hingeRow(hi: Frame): IntArray = hi.near?.let { it.copyOfRange((hi.nh - 1) * hi.nw, hi.nh * hi.nw) } ?: hi.px.copyOfRange((hi.h - 1) * hi.w, hi.h * hi.w)

    /** 미러 2판: 이미 잘라 온 근거리 원본 띠([nw]×[nh], 맨 아래 줄이 접는 선 쪽)로 근거리 반사를 만든다. */
    private fun withNearBand(base: Field, near: IntArray, nw: Int, nh: Int, scale: Int): Field {
        if (nh < 8) return base
        // 색 보정은 GPU가 하고, 블러는 줄마다 거리 곡선대로 CPU에서(미끄러지는 창 흐림이라 빠르다). 띠는 위 10%로 좁혀 칸 수도 줄었다
        return Field(base.px, base.w, base.h, ReflectionField.buildNear(near, nw, nh, cellDpFor(nw), ReflectionField.Spec(blurScale = floorScale)), nw, nh)
    }

    private fun Field.withHinge(row: IntArray) = Field(px, w, h, near, nw, nh, row)

    /** 근거리 띠용 캡처 폭(기본 필드의 4배). 11-02 소장님 "블러할 때 화질 더 올려줘": 384 → 512칸(미러는 GPU가 줄여 주므로 캡처 비용은 그대로) */
    private const val NEAR_W = 512

    /** 기본 반사 필드 폭(4×4 평균): 96 → 128칸. */
    private const val BASE_W = 128

    /** 실시간 미러 폭: 근거리 띠를 이 해상도 그대로 받는다(11-02 "블러 윗부분 원본이 도드라짐" → 512에서 768). 기본은 6×6 평균으로 128칸 */
    private const val MIRROR_W = 768

    /**
     * 기본 필드에 근거리 고해상도 띠를 붙인다. [top]~[bot]은 고해상도 원본에서 실제 내용이 있는 줄(레터박스 제외)이고,
     * 그 아래쪽 끝(접는 선 쪽) [ReflectionField.NEAR_FRAC]만큼을 띠로 쓴다 — 기본 필드가 같은 내용 줄을 패널 전체에 펴므로
     * 띠는 정확히 패널 위 NEAR_FRAC 자리에 겹친다(같은 좌표계).
     */
    private fun withNear(base: Field, hi: Frame, top: Int, bot: Int, scale: Int): Field {
        if (scale <= 1) return base
        val rows = bot - top + 1
        val nh = (rows * ReflectionField.NEAR_FRAC).toInt()
        if (nh < 8) return base
        val from = bot - nh + 1
        val src = IntArray(hi.w * nh) { i -> AmbientColor.grade(hi.px[from * hi.w + i]) }
        val near = ReflectionField.buildNear(src, hi.w, nh, cellDpFor(hi.w), ReflectionField.Spec(blurScale = floorScale))
        return Field(base.px, base.w, base.h, near, hi.w, nh)
    }

    /** 보호 화면 연출 중이면 목표 사이를 천천히(τ 0.9초) 이어 1초 간격 목표가 끊기지 않고 흐르게 한다. */
    @Volatile private var slowBlend = false

    /** 포스터를 화면 반사와 같은 96칸 원본(위 화면 자리)으로 줄인 것. */
    private fun artFieldSource(): Triple<IntArray, Int, Int>? {
        val art = state.art ?: return null
        // 11-02 소장님 "포스터도 선명도 올려야": 화면 반사와 같은 384칸(4배)으로 줄여 근거리 띠까지 만든다(예전 96칸은 확대하면 뭉개졌다).
        // 세로는 4의 배수로 맞춘다(4×4 평균으로 기본 필드를 만들 때 남는 줄이 없게)
        val bmp = art.asAndroidBitmap()
        val h = ((NEAR_W * bmp.height / bmp.width.coerceAtLeast(1)).coerceIn(32, NEAR_W) / 4) * 4
        val small = android.graphics.Bitmap.createScaledBitmap(bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false), NEAR_W, h, true)
        val px = IntArray(NEAR_W * h)
        small.getPixels(px, 0, NEAR_W, 0, 0, NEAR_W, h)
        return Triple(px, NEAR_W, h)
    }

    /** 고해상도 원본(폭 [w], 384 또는 96)으로 기본 필드 + 근거리 띠를 만든다. 96칸이면 띠 없이 기본 필드만. */
    private fun fieldFrom(px: IntArray, w: Int, h: Int): Field {
        val k = (w / BASE_W).coerceAtLeast(1)
        if (k == 1) return makeField(px, w, h)
        val base = makeField(ReflectionField.boxDown(px, w, h, k), w / k, h / k)
        return withNear(base, Frame(px, w, h, 0L), 0, h - 1, k)
    }

    /** 포스터가 없을 때: 앱 색으로 가운데 밝고 양옆이 가라앉은 원본. */
    private fun tintSource(): Triple<IntArray, Int, Int> {
        val t = state.tint.toArgb()
        val w = BASE_W
        val h = 48
        val px = IntArray(w * h) { i ->
            val x = (i % w) / (w - 1f)
            val g = 0.55f + 0.45f * (1f - kotlin.math.abs(x - 0.5f) * 2f)
            scale(t, g)
        }
        return Triple(px, w, h)
    }

    /**
     * 시네마틱 연출: 가로로 아주 천천히 지나가는 빛(약 9초 주기)과 숨쉬듯 느린 밝기(약 6초)를 원본에 곱한다.
     * 변화 폭은 ±20% 안이라 눈에 띄는 깜박임이 없다.
     */
    internal fun cinematic(px: IntArray, w: Int, h: Int, tMs: Long): IntArray {
        val ph = (tMs % 9000L) / 9000f * 2f * Math.PI.toFloat()
        val breath = 0.92f + 0.08f * kotlin.math.sin((tMs % 6000L) / 6000f * 2f * Math.PI.toFloat())
        return IntArray(w * h) { i ->
            val x = (i % w) / w.toFloat()
            val g = breath * (0.86f + 0.2f * (0.5f + 0.5f * kotlin.math.sin(x * 4.4f - ph)))
            scale(px[i], g)
        }
    }

    private fun scale(c: Int, g: Float): Int {
        val r = ((c shr 16 and 0xFF) * g).toInt().coerceIn(0, 255)
        val gg = ((c shr 8 and 0xFF) * g).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * g).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
    }

    /** 마지막으로 넘긴 반사 목표(같은 목표를 다시 넘기지 않게). */
    private var lastTarget: IntArray? = null

    // ---- 반사 필드 시간 전환(11-02 최종 통합 지시) ----
    // 예전에는 새 필드가 올 때마다 이전 필드와 0.22초 교차 전환을 처음부터 다시 시작해, 0.25초마다 새 필드가 오면 늘 반쯤 섞인
    // 상태에서 다시 시작했다(전환이 끊기고 뒤처짐). 이제 [FieldBlender]가 지금 보이는 상태에서 새 목표로 이어 가고,
    // 전환 줄이 움직이는 동안만 약 60fps로 작은 그림(96×56)을 세 장 돌려 쓰며 갱신한다. 다 오면 멈춘다
    private var blender = FieldBlender()

    /** 근거리 고해상도 띠의 시간 전환(기본 필드와 같은 스프링·같은 샘플 간격). */
    private var nearBlender = FieldBlender()

    /** 접는 선 경계 줄의 시간 전환. */
    private var hingeBlender = FieldBlender()
    private val hingeBmp = arrayOfNulls<android.graphics.Bitmap>(3)
    private val hingePx = arrayOfNulls<IntArray>(3)
    private val nearBmp = arrayOfNulls<android.graphics.Bitmap>(3)
    private val nearPx = arrayOfNulls<IntArray>(3)

    /** 전환이 끝나 멈춘 시각(0 = 움직이는 중). 다음 목표가 오면 그 사이 멈춘 시간을 계측한다. */
    @Volatile private var idleSince = 0L

    @Volatile private var activeTel: Telemetry? = null
    private val blendLock = Any()
    private var blendFor = -1
    private val blendBmp = arrayOfNulls<android.graphics.Bitmap>(3)
    private val blendPx = arrayOfNulls<IntArray>(3)

    /** 새 반사 목표를 전환 줄에 넘긴다(null이면 투명으로 사라진다). 메인 스레드. */
    private fun blendTo(token: Int, f: Field?) {
        val now = SystemClock.uptimeMillis()
        synchronized(blendLock) {
            if (f == null) {
                blender.fadeOut()
                nearBlender.fadeOut()
            } else {
                blender.setTarget(f.px, f.w, f.h, now, slowBlend)
                if (f.near != null) nearBlender.setTarget(f.near, f.nw, f.nh, now, slowBlend) else nearBlender.fadeOut()
                f.hinge?.let { hingeBlender.setTarget(it, it.size, 1, now, slowBlend) }
            }
        }
        // 전환이 끝나 멈춰 있던 시간(다음 샘플이 오기 전 정지 구간). 1.5초 넘게 멈춘 건 실제 정지 화면으로 보고 빼다
        val idle = idleSince
        if (idle > 0L && now - idle < 1500L) activeTel?.stall(now - idle)
        activeTel?.interval = blender.interval
        idleSince = 0L
        if (blendFor != token) {
            blendFor = token
            startBlend(token)
        } else {
            kickBlend()
        }
    }

    // ---- 전환 줄: 화면 VSync(Choreographer)에 맞춰 돈다(11-02 "Continuous Real-Time Ambient Rendering") ----
    // 예전에는 Thread.sleep(16) 루프라 화면 새로 고침과 박자가 어긋나 프레임 간격이 들쭉날쭉했다. 이제 전용 HandlerThread의
    // Choreographer 프레임 콜백으로 VSync마다 한 걸음씩 나아가고, 다 오면(정지 화면) 콜백을 더 걸지 않아 쉰다
    private var blendThread: android.os.HandlerThread? = null
    private var blendHandler: android.os.Handler? = null
    private var blendCallback: android.view.Choreographer.FrameCallback? = null

    @Volatile private var blendScheduled = false
    private var blendSlot = 0

    private fun startBlend(token: Int) {
        blendThread?.quitSafely()
        val ht = android.os.HandlerThread("goat-ambient-blend").apply { start() }
        blendThread = ht
        val h = android.os.Handler(ht.looper)
        blendHandler = h
        h.post {
            val ch = android.view.Choreographer.getInstance()
            var lastNanos = 0L
            val cb = object : android.view.Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    blendScheduled = false
                    if (ambToken != token) {
                        ht.quitSafely()
                        return
                    }
                    val dt = if (lastNanos == 0L) 16f else ((frameTimeNanos - lastNanos) / 1_000_000f).coerceIn(1f, 50f)
                    lastNanos = frameTimeNanos
                    val moving = blendFrame(token, dt)
                    if (moving) {
                        blendScheduled = true
                        ch.postFrameCallback(this)
                    } else {
                        if (idleSince == 0L) idleSince = SystemClock.uptimeMillis()
                        lastNanos = 0L
                    }
                }
            }
            blendCallback = cb
            blendScheduled = true
            ch.postFrameCallback(cb)
        }
    }

    /** 새 목표가 왔다: 전환 줄이 쉬고 있으면 다음 VSync부터 다시 돈다. */
    private fun kickBlend() {
        val h = blendHandler ?: return
        h.post {
            val cb = blendCallback ?: return@post
            if (!blendScheduled) {
                blendScheduled = true
                android.view.Choreographer.getInstance().postFrameCallback(cb)
            }
        }
    }

    /** 한 VSync 걸음: 기본 필드·근거리 띠·경계 줄을 같은 dt로 나아가 메인에 넘긴다. 아직 움직이면 true. */
    private fun blendFrame(token: Int, dt: Float): Boolean {
        val slot = blendSlot
        var w = 0
        var h = 0
        var clear = false
        var nearClear: Boolean
        var nearImg: androidx.compose.ui.graphics.ImageBitmap?
        var hingeImg: androidx.compose.ui.graphics.ImageBitmap? = null
        val px: IntArray
        synchronized(blendLock) {
            w = blender.w
            h = blender.h
            if (w <= 0) return false
            if (blendPx[slot]?.size != w * h) {
                blendPx[slot] = IntArray(w * h)
                blendBmp[slot] = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            }
            px = blendPx[slot]!!
            blender.step(dt, px)
            clear = blender.isClear()
            val nw = nearBlender.w
            val nh = nearBlender.h
            if (nw > 0) {
                if (nearPx[slot]?.size != nw * nh) {
                    nearPx[slot] = IntArray(nw * nh)
                    nearBmp[slot] = android.graphics.Bitmap.createBitmap(nw, nh, android.graphics.Bitmap.Config.ARGB_8888)
                }
                nearBlender.step(dt, nearPx[slot]!!)
                nearClear = nearBlender.isClear()
                nearBmp[slot]!!.setPixels(nearPx[slot]!!, 0, nw, 0, 0, nw, nh)
                nearImg = if (nearClear) null else nearBmp[slot]!!.asImageBitmap()
            } else {
                nearImg = null
            }
            val hw = hingeBlender.w
            if (hw > 0) {
                if (hingePx[slot]?.size != hw) {
                    hingePx[slot] = IntArray(hw)
                    hingeBmp[slot] = android.graphics.Bitmap.createBitmap(hw, 1, android.graphics.Bitmap.Config.ARGB_8888)
                }
                hingeBlender.step(dt, hingePx[slot]!!)
                hingeBmp[slot]!!.setPixels(hingePx[slot]!!, 0, hw, 0, 0, hw, 1)
                hingeImg = hingeBmp[slot]!!.asImageBitmap()
            }
        }
        val bmp = blendBmp[slot]!!
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val img = bmp.asImageBitmap()
        val ni = nearImg
        val hg = hingeImg
        blendSlot = (slot + 1) % 3
        shownFirst = token
        main.post {
            if (ambToken != token) return@post
            state.near = ni
            if (hg != null) state.hinge = hg
            if (clear) {
                state.field = null
                state.fieldPx = null
            } else {
                state.fieldPx = px
                state.fieldW = w
                state.fieldH = h
                state.field = img
            }
        }
        return synchronized(blendLock) { blender.active || nearBlender.active || hingeBlender.active } || pendingFirst(token)
    }

    /** 첫 목표는 전환 없이 바로 한 번 그려야 한다(step이 첫 프레임에서 '다 옴'을 돌려줘도). */
    @Volatile private var shownFirst = -1
    private fun pendingFirst(token: Int) = shownFirst != token

    /** 다듬은 색으로 반사 필드를 만들고 그림으로 바꾼다(작업 스레드). */
    private fun makeField(px: IntArray, w: Int, h: Int): Field {
        // 색 보정(채도·대비)은 GPU가 그릴 때 한다(11-02 실기 속도: 칸마다 OKLab 보정이 프레임당 수십 ms였다)
        val spec = ReflectionField.Spec(cellDp = cellDpFor(w), blurScale = floorScale)
        val f = ReflectionField.build(px, w, h, spec)
        return Field(f, w, spec.outH)
    }

    /** 두 필드의 평균 색 차이(0~1). */
    private fun fieldChange(a: IntArray, b: IntArray): Float {
        if (a.size != b.size) return 1f
        var s = 0L
        var i = 0
        while (i < a.size) {
            s += kotlin.math.abs((a[i] shr 16 and 0xFF) - (b[i] shr 16 and 0xFF)) + kotlin.math.abs((a[i] shr 8 and 0xFF) - (b[i] shr 8 and 0xFF)) + kotlin.math.abs((a[i] and 0xFF) - (b[i] and 0xFF))
            i += 7
        }
        return s / ((a.size / 7f) * 3f * 255f)
    }

    private var artField: Field? = null
    private var artFieldFor: androidx.compose.ui.graphics.ImageBitmap? = null

    /** 포스터로 만든 반사 필드(포스터가 바뀔 때만). 보호 화면에서 화면 대신 쓴다. */
    private fun artFieldNow(): Field? {
        val art = state.art ?: return null
        if (art === artFieldFor) return artField
        // 화면 반사와 같은 크기(기본 96칸 + 384칸 근거리 띠)로 만들어 포스터 ↔ 화면 사이도 끊기지 않고 이어서 전환된다
        val src = artFieldSource() ?: return null
        artField = fieldFrom(src.first, src.second, src.third)
        artFieldFor = art
        return artField
    }

    /** 세션 포스터의 색(포스터가 바뀌었을 때만 다시 계산). */
    private fun artPaletteNow(): AmbientColor.Palette? {
        val art = state.art ?: return null
        if (art === artFor) return artPalette
        val bmp = art.asAndroidBitmap()
        val small = android.graphics.Bitmap.createScaledBitmap(bmp, 48, (48 * bmp.height / bmp.width.coerceAtLeast(1)).coerceAtLeast(1), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        artPalette = AmbientColor.extract(px, small.width, small.height)
        artFor = art
        return artPalette
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
