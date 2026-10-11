package com.labok.goat.data.rotate

import com.labok.goat.background.GoatService
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
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.domain.ShakeRotatePolicy
import com.labok.goat.ui.MainActivity

/**
 * 흔들어서 화면 회전의 설정과 회전 잠금 쓰기.
 * 회전 잠금은 시스템 설정(accelerometer_rotation·user_rotation)이라, 화면 배율 동기화와 같은 WRITE_SECURE_SETTINGS로 바꾼다.
 */
object ShakeRotate {
    private const val PREF = "shake_rotate"
    // 1.1의 'shake_rotate'(중요도 낮음)와 다른 채널. 채널 중요도는 만든 뒤 바꿀 수 없어 새 이름을 쓴다.
    private const val CHANNEL_ID = "shake_rotate_quiet"

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** 민감도 저장 이름. 1~20 단계(예전 판정)에서 1~30 단계로 바뀌어 예전 값은 뜻이 달라 새 이름을 쓴다. */
    private const val LEVEL_KEY = "level30"

    /** 흔들기 민감도 단계([ShakeRotatePolicy.MIN_LEVEL]~[ShakeRotatePolicy.MAX_LEVEL], 클수록 예민). */
    fun level(context: Context): Int =
        prefs(context).getInt(LEVEL_KEY, ShakeRotatePolicy.DEFAULT_LEVEL).coerceIn(ShakeRotatePolicy.MIN_LEVEL, ShakeRotatePolicy.MAX_LEVEL)

    /** 흔드는 빠르기(1=천천히 ~ [ShakeRotatePolicy.MAX_PACE]=빠르게). 자동 맞추기가 그 사람의 리듬으로 정한다. */
    fun pace(context: Context): Int = prefs(context).getInt("pace", 1).coerceIn(1, ShakeRotatePolicy.MAX_PACE)

    /** 손목 회전 기준(0=보지 않음 ~ [ShakeRotatePolicy.MAX_TURN]). */
    fun turn(context: Context): Int = prefs(context).getInt("turn", DEFAULT_TURN).coerceIn(0, ShakeRotatePolicy.MAX_TURN)

    /** 흔든 것으로 칠 휘두름 수(2~4). */
    fun swings(context: Context): Int = prefs(context).getInt("swings", ShakeRotatePolicy.Detector.PEAKS).coerceIn(2, 4)

    /** 한 번 톡 친 것·부딪힌 것을 거른다. */
    fun rejectTaps(context: Context): Boolean = prefs(context).getBoolean("taps", true)

    /** 고급 설정 하나를 바꾼다. 화면이 바로 따라 바뀌고, 돌고 있는 감지에도 바로 반영된다. */
    fun setAdvanced(context: Context, pace: Int? = null, turn: Int? = null, swings: Int? = null, rejectTaps: Boolean? = null, how: String) {
        prefs(context).edit().apply {
            pace?.let { putInt("pace", it) }
            turn?.let { putInt("turn", it) }
            swings?.let { putInt("swings", it) }
            rejectTaps?.let { putBoolean("taps", it) }
        }.apply()
        LogRepository.from(context).add(
            "흔들어서 화면 회전 고급 설정($how): " + listOfNotNull(
                pace?.let { "빠르기 $it" }, turn?.let { "손목 회전 $it" }, swings?.let { "횟수 $it" }, rejectTaps?.let { "탭 거르기 $it" },
            ).joinToString(", "),
        )
        changed.value++
        if (enabled(context)) start(context)
    }

    /** 고급 설정을 처음 값으로. */
    fun resetAdvanced(context: Context) =
        setAdvanced(context, pace = 1, turn = DEFAULT_TURN, swings = ShakeRotatePolicy.Detector.PEAKS, rejectTaps = true, how = "기본값")

    private const val DEFAULT_TURN = 4

    /** 설정이 바뀔 때마다 늘어난다(자동 맞추기 시트에서 바꿔도 화면이 바로 따라 바뀌게). */
    val changed = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** 돌릴 때 진동 세기(0=끔, 1~[ShakeHaptics.MAX_STRENGTH]). */
    fun vibration(context: Context): Int =
        prefs(context).getInt("vibration", ShakeHaptics.DEFAULT_STRENGTH).coerceIn(0, ShakeHaptics.MAX_STRENGTH)

    fun setVibration(context: Context, strength: Int) {
        prefs(context).edit().putInt("vibration", strength).apply()
        LogRepository.from(context).add("흔들어서 화면 회전 진동 세기: $strength")
    }

    fun setLevel(context: Context, level: Int, how: String) {
        prefs(context).edit().putInt(LEVEL_KEY, level).apply()
        changed.value++
        LogRepository.from(context).add("흔들어서 화면 회전 민감도: $level($how)")
        if (enabled(context)) start(context)  // 서비스가 새 민감도를 읽게 한다
    }

    /** 흔든 순간 자동 회전을 잠깐 켜 둔 동안 true(화면의 '자동 회전이 켜져 있습니다' 안내를 띄우지 않는다). */
    @Volatile
    var turning = false

    /** 자동 맞추기 중이면 true. 그동안 서비스는 흔들어도 돌리지 않는다(같은 프로세스 안에서만 본다). */
    @Volatile
    var calibrating = false

    /** 빠른 설정의 자동 회전이 켜져 있으면 true. 켜져 있으면 흔들기 전에도 화면이 저절로 돌아간다. */
    fun autoRotateOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** 자동 회전만 끈다(지금 방향 그대로 잠근다). */
    fun turnOffAutoRotate(context: Context): Boolean =
        lock(context, currentRotation(context)).also { if (it) LogRepository.from(context).add("흔들어서 화면 회전: 자동 회전 끄기(사용자)") }

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
            start(context)
        } else {
            val auto = prefs(context).getInt("auto_before", 1)
            prefs(context).edit().putBoolean("on", false).apply()
            GoatService.sync(context)
            runCatching { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, auto) }
            log.add("흔들어서 화면 회전 꺼짐(자동 회전 ${if (auto == 1) "켜기" else "끄기"}로 되돌림)")
        }
        return true
    }

    /**
     * 켜 두었으면 GOAT 상주 서비스에서 흔들기 감지를 돌린다(부팅·업데이트·앱 재시작 뒤에도). 이미 돌고 있으면 민감도만 다시 읽힌다.
     * 알림창 앱별 배율 조절과 같은 서비스·알림 하나를 쓴다([GoatService]).
     */
    fun start(context: Context) {
        if (enabled(context) && canWrite(context)) GoatService.sync(context)
    }

    /** 지금 화면 방향(기본 디스플레이 기준). 서비스처럼 화면이 없는 곳에서도 읽힌다. */
    fun currentRotation(context: Context): Int =
        runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation }
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
            NotificationChannel(CHANNEL_ID, "흔들어서 화면 회전", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("흔들어서 화면 회전")
            .setContentText("흔들면 든 방향으로 돌립니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
