package com.local.folddpifix.ui.appsize

import android.app.Activity
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.local.folddpifix.data.appsize.AppDensityNotifier
import com.local.folddpifix.data.appsize.AppUsage
import com.local.folddpifix.data.appsize.DensityServer
import com.local.folddpifix.data.appsize.DensityShell
import com.local.folddpifix.data.appsize.ForegroundWatcher
import com.local.folddpifix.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 알림 버튼을 받아 방금 쓰던 앱의 화면 크기를 한 단계 바꾸는 투명 화면.
 * 삼성은 크기를 바꾸면 그 앱을 닫으므로, 바꾼 뒤 그 앱을 다시 열어 준다. 알림에서 바로 앱을 여는 것은
 * Android 12부터 화면(Activity)으로만 할 수 있어 브로드캐스트 대신 이 화면을 쓴다.
 */
class DensityStepActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val step = intent.getIntExtra(EXTRA_STEP, 0)
        val main = Handler(Looper.getMainLooper())
        // 방금 쓰던 앱: 작업 스택 감시가 돌고 있으면 그 값, 아니면 사용 기록(기기에 따라 늦을 수 있음)
        val target = if (ForegroundWatcher.running) ForegroundWatcher.current else AppUsage.foregroundApp(this)
        Thread {
            val msg = runCatching { change(target, step) }.getOrElse { "바꾸지 못했습니다: ${it.message}" }
            main.post {
                AppDensityNotifier.show(this, msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                finish()
                overridePendingTransition(0, 0)
            }
        }.start()
    }

    private fun change(target: String?, step: Int): String {
        if (!ForegroundWatcher.running && !AppUsage.hasAccess(this)) return "지금 앱을 찾지 못했습니다. Shizuku를 켜거나 실험실에서 사용 기록 접근을 허용해 주세요."
        target ?: return "지금 열려 있는 앱이 없습니다."
        // 프로세스가 막 떠서 Shizuku 바인더가 아직 오지 않았을 수 있다: 잠깐 기다린다
        ShizukuAccess.watch(this)
        repeat(20) { if (Shizuku.pingBinder()) return@repeat; Thread.sleep(75) }
        ShizukuAccess.refresh()
        if (DensityShell.connected.value == null && ShizukuAccess.status.value != ShizukuAccess.State.READY) return "Shizuku가 꺼져 있습니다."
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
        val now = DensityShell.get(this, target)
        val base = if (now > 0) now else Resources.getSystem().configuration.densityDpi
        val next = when {
            step == 0 -> 0
            step < 0 -> DensityServer.STEPS.lastOrNull { it < base } ?: return "$label: 이미 가장 작습니다($base)."
            else -> DensityServer.STEPS.firstOrNull { it > base } ?: return "$label: 이미 가장 큽니다($base)."
        }
        if (next == now) return "$label: 이미 ${if (now == 0) "기본" else "$now"}입니다."
        DensityShell.set(this, target, next)
        // 삼성이 그 앱을 닫는 것을 기다렸다가 다시 연다
        Thread.sleep(400)
        packageManager.getLaunchIntentForPackage(target)?.let { runCatching { startActivity(it) } }
        return AppDensityNotifier.describe(this, target)
    }

    companion object {
        const val EXTRA_STEP = "step"
    }
}
