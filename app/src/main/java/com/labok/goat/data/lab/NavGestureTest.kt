package com.labok.goat.data.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 실험실: 굿락 홈업 멀티핑거 제스처를 버튼식 내비게이션에서도 쓸 수 있는지 설정값만 바꿔 시험한다(11-03 소장님, 디시 댓글 요청).
 * 홈업은 '스와이프 제스처(여러 방향)'에서만 동작한다고 안내되어 있어, 버튼은 남기고 제스처 쪽 값만 켜는 두 조합을 시험한다.
 * - 조합 1: 삼성 내비게이션 값만 버튼으로(global navigation_bar_gesture_while_hidden 0)
 * - 조합 2: 제스처 층만 켜기(삼성 층 com.samsung.internal.systemui.navbar.gestural_no_hint가 있으면 그것, 없으면 안드로이드 navbar.gestural).
 *   1.44.3은 안드로이드 층만 켰는데 폴드8 제스처 모드는 삼성 층을 쓴다(11-03 진단)
 * 바꾸기 전 값을 저장해 두고 [restore]로 되돌린다. 시험 중에는 알림에 [원래대로] 버튼을 띄워 내비게이션이 막혀도 알림창에서 되돌릴 수 있다.
 * 작업 스레드에서 부른다.
 */
object NavGestureTest {
    private const val PREF = "lab_nav_gesture"
    private const val CHANNEL = "lab_nav_gesture"
    private const val NOTIFICATION = 3201
    private const val NAVBAR = "com.android.internal.systemui.navbar"
    private const val SEC_GESTURAL = "com.samsung.internal.systemui.navbar.gestural_no_hint"

    // 삼성(com.samsung.internal…)과 안드로이드(com.android.internal…) 내비게이션 층을 모두 가리킨다
    private fun isNavbar(name: String) = "systemui.navbar." in name

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 시험 중인지(되돌릴 값이 저장돼 있는지). */
    fun testing(context: Context) = prefs(context).contains("hidden")

    /** 지금 내비게이션 관련 값(화면 표시용). Shizuku가 없으면 null. */
    fun status(): String? {
        val r = ShizukuAccess.shell(
            "echo 삼성 방식 $(settings get global navigation_bar_gesture_while_hidden) · 제스처 종류 $(settings get global navigation_bar_gesture_detail_type) · " +
                "안드로이드 방식 $(settings get secure navigation_mode) · 세 손가락 $(settings get global three_finger_gesture) · 네 손가락 $(settings get global four_finger_gesture); " +
                "cmd overlay list android | grep navbar"
        ) ?: return null
        return r.out.lines().joinToString("\n") { it.trim().replace("$NAVBAR.", "").replace("com.samsung.internal.systemui.navbar.", "삼성 ") }
    }

    /** 조합 [combo](1 또는 2)를 적용한다. 성공하면 null, 실패하면 이유. */
    fun apply(context: Context, combo: Int): String? {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return "Shizuku 연결이 필요합니다."
        if (!testing(context)) {
            val hidden = ShizukuAccess.shell("settings get global navigation_bar_gesture_while_hidden")?.out?.trim() ?: return "설정값을 읽지 못했습니다."
            val mode = ShizukuAccess.shell("settings get secure navigation_mode")?.out?.trim().orEmpty()
            val overlay = ShizukuAccess.shell("cmd overlay list android | grep navbar | grep '\\[x\\]'")?.out?.lines()?.firstOrNull { it.isNotBlank() }?.substringAfter("] ")?.trim().orEmpty()
            prefs(context).edit().putString("hidden", hidden).putString("mode", mode).putString("overlay", overlay).apply()
        }
        val sec = ShizukuAccess.shell("cmd overlay list android | grep -c '$SEC_GESTURAL\$'")?.out?.trim()?.toIntOrNull() ?: 0
        val gestural = "cmd overlay enable-exclusive --category ${if (sec > 0) SEC_GESTURAL else "$NAVBAR.gestural"}"
        val cmd = when (combo) {
            1 -> "settings put global navigation_bar_gesture_while_hidden 0"
            2 -> gestural
            // 버튼 하나로 둘 다(11-03 소장님 "버튼 하나로 네비바랑 제스처 둘 다 되게"): 삼성 값을 버튼으로 바꿔 버튼 바를 띄우고,
            // 시스템 UI가 버튼식 층으로 다 바꿀 때까지(2초) 기다린 뒤 제스처 층과 안드로이드 제스처 방식을 다시 켠다
            else -> "settings put global navigation_bar_gesture_while_hidden 0; sleep 2; $gestural; settings put secure navigation_mode 2"
        }
        val r = ShizukuAccess.shell(cmd) ?: return "Shizuku 연결이 필요합니다."
        val err = if (r.ok) null else r.err.ifBlank { r.out }.take(120).ifBlank { "실패" }
        LogRepository.from(context).add("실험실 홈업 멀티핑거 시험: 조합 $combo 적용" + (err?.let { " 실패($it)" } ?: "") + " · ${status()?.replace("\n", " / ")}")
        notifyRestore(context, combo)
        // 시스템이 곧바로 되돌리는지 보려고 3초 뒤 값을 한 번 더 남긴다
        Thread.sleep(3000)
        LogRepository.from(context).add("실험실 홈업 멀티핑거 시험: 조합 $combo 3초 뒤 · ${status()?.replace("\n", " / ")}")
        return err
    }

    /** 바꾸기 전 값으로 되돌린다. 성공하면 null, 실패하면 이유. */
    fun restore(context: Context): String? {
        val p = prefs(context)
        val hidden = p.getString("hidden", null) ?: return null
        val mode = p.getString("mode", "").orEmpty()
        val overlay = p.getString("overlay", "").orEmpty()
        val cmds = buildList {
            if (hidden != "null") add("settings put global navigation_bar_gesture_while_hidden $hidden")
            // 바꾸기 전에 켜진 내비게이션 층이 없었으면(기본 3버튼) 시험으로 켠 층을 끈다
            // 1.44.3은 삼성 층 이름(com.samsung…)을 알아보지 못해, 원래 켜져 있던 삼성 제스처 층을 되살리지 않고 모두 껐다(11-03 진단)
            if (isNavbar(overlay)) {
                add("cmd overlay enable-exclusive --category $overlay")
            } else {
                add("for o in $(cmd overlay list android | grep navbar | grep '\\[x\\]' | sed 's/.*] //'); do cmd overlay disable \$o; done")
            }
            if (mode.toIntOrNull() != null) add("settings put secure navigation_mode $mode")
        }
        val r = ShizukuAccess.shell(cmds.joinToString("; ")) ?: return "Shizuku 연결이 필요합니다."
        p.edit().clear().apply()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        LogRepository.from(context).add("실험실 홈업 멀티핑거 시험: 원래대로(${cmds.joinToString(" / ")})" + if (r.ok) "" else " 일부 실패(${r.err.take(80)})")
        return if (r.ok) null else r.err.take(120).ifBlank { "일부 실패" }
    }

    private fun notifyRestore(context: Context, combo: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "실험실 내비게이션 시험", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val pi = PendingIntent.getBroadcast(context, 0, Intent(context, NavGestureReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("홈업 멀티핑거 시험 중 · 조합 $combo")
            .setContentText("내비게이션이 이상하면 [원래대로]를 누르세요")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "원래대로", pi)
            .build()
        runCatching { nm.notify(NOTIFICATION, n) }
    }
}

/** 시험 알림의 [원래대로] 버튼. */
class NavGestureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                NavGestureTest.restore(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
