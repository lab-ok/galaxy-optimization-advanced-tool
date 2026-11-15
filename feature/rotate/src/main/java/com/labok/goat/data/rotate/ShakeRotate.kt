package com.labok.goat.data.rotate

import com.labok.goat.AppHooks
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.core.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 흔들어서 화면 회전의 설정과 회전 잠금 쓰기.
 * 회전 잠금은 시스템 설정(accelerometer_rotation·user_rotation)이라, 고급 화면 배율 설정과 같은 WRITE_SECURE_SETTINGS로 바꾼다.
 */
object ShakeRotate {
    private const val PREF = "shake_rotate"

    // 1.1의 'shake_rotate'(중요도 낮음)와 다른 채널. 채널 중요도는 만든 뒤 바꿀 수 없어 새 이름을 쓴다.
    private const val CHANNEL_ID = "shake_rotate_quiet"

    fun canWrite(context: Context) = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Rotation writes use System settings; secure-only controls keep using canWrite(). */
    fun canWriteRotation(context: Context) = canWrite(context) || Settings.System.canWrite(context)

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** 민감도 저장 이름. 1~20 단계(예전 판정)에서 1~30 단계로 바뀌어 예전 값은 뜻이 달라 새 이름을 쓴다. */
    private const val LEVEL_KEY = "level30"

    /** 흔들기 민감도 단계([ShakeRotatePolicy.MIN_LEVEL]~[ShakeRotatePolicy.MAX_LEVEL], 클수록 예민). */
    fun level(context: Context): Int = prefs(context).getInt(LEVEL_KEY, ShakeRotatePolicy.DEFAULT_LEVEL).coerceIn(ShakeRotatePolicy.MIN_LEVEL, ShakeRotatePolicy.MAX_LEVEL)

    /** 흔드는 빠르기(1=천천히 ~ [ShakeRotatePolicy.MAX_PACE]=빠르게). 자동 맞추기가 그 사람의 리듬으로 정한다. */
    fun pace(context: Context): Int = prefs(context).getInt("pace", 1).coerceIn(1, ShakeRotatePolicy.MAX_PACE)

    /** 손목 회전 기준(0=보지 않음 ~ [ShakeRotatePolicy.MAX_TURN]). */
    fun turn(context: Context): Int = prefs(context).getInt("turn", DEFAULT_TURN).coerceIn(0, ShakeRotatePolicy.MAX_TURN)

    /** 흔든 것으로 칠 휘두름 수(2~4). */
    fun swings(context: Context): Int = prefs(context).getInt("swings", ShakeRotatePolicy.Detector.PEAKS).coerceIn(2, 4)

    /** 한 번 톡 친 것·부딪힌 것을 거른다. */
    fun rejectTaps(context: Context): Boolean = prefs(context).getBoolean("taps", true)

    /** 빠른 회전(기본 켬): 시스템 자동 회전을 기다리지 않고 센서로 든 방향을 바로 정해 잠근다. 애매하면 시스템에 맡긴다. */
    fun fastTurn(context: Context): Boolean = prefs(context).getBoolean("fastTurn", true)

    fun setFastTurn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("fastTurn", on).apply()
        LogRepository.from(context).add("흔들어서 화면 회전: 빠른 회전 ${if (on) "켜짐" else "꺼짐"}")
    }

    /** 큰 화면(폴드를 펼쳤을 때·태블릿)에서만 흔들어서 돌린다. 접었을 때(외부 화면)는 흔들어도 돌리지 않는다. */
    fun largeOnly(context: Context): Boolean = prefs(context).getBoolean("largeOnly", false)

    /** 지금 기본 화면이 큰 화면인지: 짧은 변이 [LARGE_DP] 이상(폴드 내부 화면·태블릿). */
    @Suppress("DEPRECATION")
    fun isLargeScreen(context: Context): Boolean {
        val m = DisplayMetrics()
        context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return ShakeRotatePolicy.isLarge(minOf(m.widthPixels, m.heightPixels), m.densityDpi)
    }

    /** 고급 설정 하나를 바꾼다. 화면이 바로 따라 바뀌고, 돌고 있는 감지에도 바로 반영된다. */
    fun setAdvanced(context: Context, pace: Int? = null, turn: Int? = null, swings: Int? = null, rejectTaps: Boolean? = null, largeOnly: Boolean? = null, how: String) {
        prefs(context).edit().apply {
            largeOnly?.let { putBoolean("largeOnly", it) }
            pace?.let { putInt("pace", it) }
            turn?.let { putInt("turn", it) }
            swings?.let { putInt("swings", it) }
            rejectTaps?.let { putBoolean("taps", it) }
        }.apply()
        LogRepository.from(context).add(
            "흔들어서 화면 회전 고급 설정($how): " + listOfNotNull(
                pace?.let { "속도 $it" },
                turn?.let { "손목 회전 $it" },
                swings?.let { "횟수 $it" },
                rejectTaps?.let { "탭 무시 $it" },
                largeOnly?.let { "큰 화면에서만 $it" }
            ).joinToString(", ")
        )
        changed.value++
        if (enabled(context)) start(context)
    }

    /** 고급 설정을 처음 값으로. */
    /** 고급 설정(흔들기 인식 세부값)만 기본값으로. '큰 화면에서만'은 고급 설정 밖(돌릴 때)으로 옮겨 그대로 둔다. */
    fun resetAdvanced(context: Context) = setAdvanced(context, pace = 1, turn = DEFAULT_TURN, swings = ShakeRotatePolicy.Detector.PEAKS, rejectTaps = true, how = "기본값")

    private const val DEFAULT_TURN = 4

    /** 설정이 바뀔 때마다 늘어난다(자동 맞추기 시트에서 바꿔도 화면이 바로 따라 바뀌게). */
    val changed = MutableStateFlow(0)

    /** 돌릴 때 진동 세기(0=끔, 1~[ShakeHaptics.MAX_STRENGTH]). */
    fun vibration(context: Context): Int = prefs(context).getInt("vibration", ShakeHaptics.DEFAULT_STRENGTH).coerceIn(0, ShakeHaptics.MAX_STRENGTH)

    fun setVibration(context: Context, strength: Int) {
        prefs(context).edit().putInt("vibration", strength).apply()
        LogRepository.from(context).add("흔들어서 화면 회전 진동 세기: $strength")
    }

    fun setLevel(context: Context, level: Int, how: String) {
        prefs(context).edit().putInt(LEVEL_KEY, level).apply()
        changed.value++
        LogRepository.from(context).add("흔들어서 화면 회전 민감도: $level($how)")
        if (enabled(context)) start(context) // 서비스가 새 민감도를 읽게 한다
    }

    /** 흔든 순간 자동 회전을 잠깐 켜 둔 동안 true(화면의 '자동 회전이 켜져 있습니다' 안내를 띄우지 않는다). */
    @Volatile
    var turning = false

    /** 자동 맞추기 중이면 true. 그동안 서비스는 흔들어도 돌리지 않는다(같은 프로세스 안에서만 본다). */
    @Volatile
    var calibrating = false

    /** 빠른 설정의 자동 회전이 켜져 있으면 true. 켜져 있으면 흔들기 전에도 화면이 저절로 돌아간다. */
    fun autoRotateOn(context: Context) = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** 자동 회전만 끈다(지금 방향 그대로 잠근다). */
    fun turnOffAutoRotate(context: Context): Boolean = lock(context, currentRotation(context)).also { if (it) LogRepository.from(context).add("흔들어서 화면 회전: 자동 회전 끄기(사용자)") }

    /**
     * 켜면 지금 화면 방향 그대로 회전을 잠그고 감시를 시작한다. 끄면 켜기 전의 자동 회전 설정으로 되돌린다.
     * 권한이 없어 잠그지 못하면 켜지 않고 false.
     */
    fun setEnabled(context: Context, on: Boolean): Boolean {
        val log = LogRepository.from(context)
        val cr = context.contentResolver
        if (on) {
            val auto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
            if (!lock(context, currentRotation(context))) return false
            prefs(context).edit().putBoolean("on", true).putInt("auto_before", auto).apply()
            log.add("흔들어서 화면 회전 켜짐(자동 회전 ${if (auto == 1) "켜져 있었음" else "꺼져 있었음"} → 잠금)")
            applySuggestions(context)
            start(context)
        } else {
            val auto = prefs(context).getInt("auto_before", 1)
            prefs(context).edit().putBoolean("on", false).apply()
            AppHooks.syncService(context)
            runCatching { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, auto) }
            log.add("흔들어서 화면 회전 꺼짐(자동 회전 ${if (auto == 1) "켜기" else "끄기"}로 되돌림)")
            applySuggestions(context)
        }
        return true
    }

    /**
     * 흔들기 회전 중에는 시스템 회전 제안 버튼(자동 회전이 꺼진 채 폰을 돌리면 잠깐 뜨는 버튼)을 숨길지. 기본 켜짐.
     * 흔들기 회전은 자동 회전을 끈 채 쓰므로 이 버튼이 자주 튀어나와 거슬렸다(11-01 소장님: GOAT 설정으로 넣기).
     */
    fun hideSuggestions(context: Context): Boolean = prefs(context).getBoolean("hide_suggest", true)

    fun setHideSuggestions(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("hide_suggest", on).apply()
        applySuggestions(context)
    }

    /**
     * 보안 설정 show_rotation_suggestions를 지금 상태에 맞춘다: 흔들기 회전이 켜져 있고 숨기기를 골랐으면 0, 아니면 숨기기 전 값으로 되돌린다.
     * 숨기기 전 값은 한 번만 저장해 두었다가 되돌릴 때 쓴다. 기기에 따라 재부팅하면 기본값으로 돌아가므로 [start]에서도 다시 맞춘다.
     */
    fun applySuggestions(context: Context) {
        if (!canWrite(context)) return
        val cr = context.contentResolver
        val p = prefs(context)
        runCatching {
            if (enabled(context) && hideSuggestions(context)) {
                if (!p.contains("suggest_before")) p.edit().putInt("suggest_before", Settings.Secure.getInt(cr, SHOW_ROTATION_SUGGESTIONS, 1)).apply()
                if (Settings.Secure.getInt(cr, SHOW_ROTATION_SUGGESTIONS, 1) != 0) {
                    Settings.Secure.putInt(cr, SHOW_ROTATION_SUGGESTIONS, 0)
                    LogRepository.from(context).add("흔들어서 화면 회전: 회전 제안 버튼 숨김")
                }
            } else if (p.contains("suggest_before")) {
                Settings.Secure.putInt(cr, SHOW_ROTATION_SUGGESTIONS, p.getInt("suggest_before", 1))
                p.edit().remove("suggest_before").apply()
                LogRepository.from(context).add("흔들어서 화면 회전: 회전 제안 버튼 되돌림")
            }
        }
    }

    /**
     * 켜 두었으면 GOAT 상주 서비스에서 흔들기 감지를 돌린다(부팅·업데이트·앱 재시작 뒤에도). 이미 돌고 있으면 민감도만 다시 읽힌다.
     * 알림창 앱별 배율 조절과 같은 서비스·알림 하나를 쓴다([GoatService]).
     */
    fun start(context: Context) {
        applySuggestions(context)
        if (enabled(context) && canWrite(context)) AppHooks.syncService(context)
    }

    /** 지금 화면 방향(기본 디스플레이 기준). 서비스처럼 화면이 없는 곳에서도 읽힌다. */
    fun currentRotation(context: Context): Int = runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation }
        .getOrElse { Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0) }

    /** 자동 회전을 끄고 [rotation] 방향으로 잠근다. */
    fun lock(context: Context, rotation: Int): Boolean = runCatching {
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(cr, Settings.System.USER_ROTATION, rotation)
    }.getOrElse {
        LogRepository.from(context).add("회전 잠금 실패: ${it.message}")
        false
    }

    fun label(rotation: Int) = when (rotation) {
        0 -> "세로"
        2 -> "거꾸로 세로"
        else -> "가로"
    }

    /**
     * 흔들기만 켜져 있을 때의 알림. 감지를 멈추지 않게 하려고만 띄우므로 가장 조용하게 둔다(채널은 최소로 만들지만,
     * 포그라운드 서비스 알림은 시스템이 '낮음'으로 올린다: 소리·진동 없이 알림창에만 있다).
     */
    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, Copy.text(R.string.rotate_shake_notification_title), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(context, 20, AppHooks.mainIntent(context), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle(Copy.text(R.string.rotate_shake_notification_title))
            .setContentText(Copy.text(R.string.rotate_shake_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    /** 시스템 회전 제안 버튼 표시(보안 설정, 공개 상수가 없어 문자열로 쓴다). */
    private const val SHOW_ROTATION_SUGGESTIONS = "show_rotation_suggestions"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
