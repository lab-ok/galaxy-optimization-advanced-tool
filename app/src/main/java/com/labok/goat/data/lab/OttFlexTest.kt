package com.labok.goat.data.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import androidx.core.app.NotificationCompat
import com.labok.goat.R
import com.labok.goat.data.log.DiagnosticReport
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.log.PublicLogFile
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.liquid.UiGoat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first

/**
 * OTT 플렉스 시험(실험실, 11-02 소장님: 넷플릭스·디즈니+ 등에서 지원할 수 있는 것을 모두 시험하고 로그를 붙여 보내게).
 * OTT 앱은 화면 찍기가 막혀 있어(보안 창) 화면 대신 시스템 상태를 기록한다. 버튼을 누르고 10초 안에 OTT 앱에서 영상을 틀고
 * 가로로 들어 반쯤 접어 두면, 진동으로 단계마다 알리며 아래를 차례로 시험한다.
 * 1. 시작 상태 → 2. 반쯤 접힘 강제 → 3. 화면이 세로로 돌아갔으면 가로로 되돌림 → 4. 삼성 플렉스 패널에 등록하고 다시 접힘
 * → 5. 등록을 풀고 다시 접힘 → 6. 앱의 방향 요청을 무시하게 하고 다시 접힘(끝나면 원래 값으로). 단계마다 앞 앱, 화면 회전(실제·설정), 기기 상태, 앱 창의 크기·방향·회전, 앱이 요청한 방향,
 * 보안 창 여부, 패널 등록 여부를 남긴다. 끝나면 모두 되돌리고 진단 파일을 저장해 알림을 누르면 보낼 수 있게 한다.
 */
object OttFlexTest {
    private const val PAUSE_KEY = "ott_test"
    private const val CHANNEL = "ott_flex_test"
    private const val NOTIFICATION = 3201

    @Volatile
    var running = false
        private set

    fun start(context: Context) {
        if (running) return
        running = true
        val app = context.applicationContext
        thread(name = "ott-flex-test", isDaemon = true) {
            runCatching { run(app) }.onFailure { LogRepository.from(app).add("OTT 플렉스 시험 실패: ${it.javaClass.simpleName} ${it.message}".take(300)) }
            running = false
        }
    }

    private fun run(context: Context) {
        val trail = StringBuilder()
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        fun note(t: String) {
            trail.appendLine("[${fmt.format(Date())}] $t")
            LogRepository.from(context).add("OTT 플렉스 시험: $t".take(300))
        }
        notify(context, "10초 안에 OTT 앱에서 영상을 틀고, 가로로 들어 반쯤 접어 두세요.", null)
        buzz(context)
        Thread.sleep(10_000)
        AutoFlex.setPaused(PAUSE_KEY, true)
        val cr = context.contentResolver
        val autoBefore = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0)
        val userBefore = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.USER_ROTATION, 0)
        val pkg = ShizukuAccess.focusedTask()?.let { it.basePkg ?: it.pkg }.orEmpty()
        val wasRegistered = pkg in FlexPanel.registered(context)
        val ignoreBefore = ShizukuAccess.shell("cmd window get-ignore-orientation-request -d 0")?.out?.let { Regex("ignoreOrientationRequest (true|false)").find(it)?.groupValues?.get(1) }
        note("앞 앱 $pkg · 자동 회전 $autoBefore · 설정 회전 $userBefore · 패널 등록 $wasRegistered · 가로로 듦 ${AutoFlex.horizontal.value} · 각도 ${AutoFlex.angle.value}")
        try {
            snapshot(context, pkg, "1. 시작", ::note)
            buzz(context)
            FlexPanel.forceHalfOpened(context)
            Thread.sleep(2000)
            snapshot(context, pkg, "2. 반쯤 접힘 강제", ::note)

            buzz(context)
            val now = rotation(context)
            if (now == 0 || now == 2) {
                val land = if (userBefore == 1 || userBefore == 3) userBefore else 3
                setRotation(context, land)
                note("화면이 세로($now)라 가로($land)로 돌림")
            } else {
                note("화면이 이미 가로($now)")
            }
            Thread.sleep(2500)
            snapshot(context, pkg, "3. 가로로 맞춤", ::note)

            buzz(context)
            FlexPanel.resetPosture(context)
            FlexPanel.setApp(context, pkg, true)
            Thread.sleep(800)
            FlexPanel.forceHalfOpened(context)
            Thread.sleep(2500)
            snapshot(context, pkg, "4. 삼성 패널 등록 + 반쯤 접힘", ::note)

            buzz(context)
            FlexPanel.resetPosture(context)
            FlexPanel.setApp(context, pkg, false)
            Thread.sleep(800)
            FlexPanel.forceHalfOpened(context)
            Thread.sleep(2500)
            snapshot(context, pkg, "5. 패널 등록 없이 반쯤 접힘", ::note)

            // 6. 앱이 요청한 방향을 무시하게 해(화면 회전을 따르게) 앱이 가로·가로 접는 선을 받는지 본다
            buzz(context)
            FlexPanel.resetPosture(context)
            ShizukuAccess.shell("cmd window set-ignore-orientation-request -d 0 true")
            Thread.sleep(800)
            FlexPanel.forceHalfOpened(context)
            Thread.sleep(2500)
            snapshot(context, pkg, "6. 방향 요청 무시 + 반쯤 접힘", ::note)
        } finally {
            ignoreBefore?.let { ShizukuAccess.shell("cmd window set-ignore-orientation-request -d 0 $it") }
            FlexPanel.resetPosture(context)
            if (pkg.isNotEmpty() && (pkg in FlexPanel.registered(context)) != wasRegistered) FlexPanel.setApp(context, pkg, wasRegistered)
            runCatching {
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.USER_ROTATION, userBefore)
                android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, autoBefore)
            }
            AutoFlex.setPaused(PAUSE_KEY, false)
            note("되돌림: 접힘 상태·패널 등록·회전 설정")
        }
        buzz(context)
        val text = "== OTT 플렉스 시험 ==\n$trail\n" + DiagnosticReport.build(context)
        val uri = PublicLogFile.saveNew(context, PublicLogFile.timestampedName("ott-flex-test"), text)
        if (uri == null) {
            notify(context, "시험은 끝났지만 진단 파일을 저장하지 못했습니다.", null)
            return
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "OTT 플렉스 시험 결과 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        notify(context, "시험이 끝났습니다. 눌러서 결과를 보내 주세요.", PendingIntent.getActivity(context, 31, chooser, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** 지금 상태를 여러 줄로 남긴다(로그 한 줄 300자 제한에 걸리지 않게 값만 뽑는다). */
    private fun snapshot(context: Context, pkg: String, title: String, note: (String) -> Unit) {
        val front = ShizukuAccess.focusedTask()?.let { it.basePkg ?: it.pkg }
        val user = ShizukuAccess.shell("settings get system user_rotation")?.out?.trim()
        val auto = ShizukuAccess.shell("settings get system accelerometer_rotation")?.out?.trim()
        val state = ShizukuAccess.shell("cmd device_state print-state")?.out?.trim()?.lines()?.firstOrNull()
        note("$title · 앞 앱 $front · 화면 회전 ${rotation(context)} · 설정 회전 $user · 자동 회전 $auto · 기기 상태 $state · 패널 등록 ${pkg in FlexPanel.registered(context)}")
        if (pkg.isEmpty()) return
        val windows = ShizukuAccess.shell("dumpsys window windows")?.out.orEmpty()
        val start = windows.indexOf(" $pkg/")
        if (start >= 0) {
            val block = windows.substring(start, minOf(windows.length, start + 6000))
            fun find(re: String) = Regex(re).find(block)?.value
            note(
                "   창: " + listOfNotNull(
                    block.lineSequence().first().trim().take(120),
                    find("mBounds=Rect\\([^)]*\\)"),
                    find("\\b(port|land)\\b")?.let { "방향 $it" },
                    find("mDisplayRotation=\\S+"),
                    find("mWindowingMode=\\S+|mode=\\S+"),
                    if (Regex("SECURE").containsMatchIn(block)) "보안 창" else "보안 표시 없음"
                ).joinToString(" · ")
            )
        } else {
            note("   창: $pkg 창을 찾지 못함")
        }
        // 앱 활동 기록(ActivityRecord) 줄부터 아래 80줄에서 앱이 요청한 방향과 시스템이 덮어쓴 방향을 찾는다
        val acts = ShizukuAccess.shell("dumpsys activity activities")?.out.orEmpty().lines()
        val at = acts.indexOfFirst { "ActivityRecord{" in it && "$pkg/" in it }
        if (at >= 0) {
            val o = acts.subList(at, minOf(acts.size, at + 80)).flatMap { l -> Regex("(requestedOrientation|overrideOrientation|screenOrientation)=\\S+").findAll(l).map { it.value }.toList() }.distinct().take(4)
            note("   요청 방향: ${acts[at].trim().take(100)} · ${o.ifEmpty { listOf("찾지 못함") }.joinToString(" · ")}")
        } else {
            note("   요청 방향: 활동 기록을 찾지 못함")
        }
        ShizukuAccess.shell("cmd window get-ignore-orientation-request -d 0")?.out?.trim()?.let { note("   방향 요청 무시: $it") }
        note("   앱이 받는 접힘 정보(GOAT 창 기준): ${foldInfo(context)}")
        note("   미디어 세션: ${MediaRemote.describe(context)}".take(600))
        val rot = ShizukuAccess.shell("dumpsys window displays | grep -m4 -oE 'mUserRotationMode=[^ ]+|mUserRotation=[^ ]+|mCurrentAppOrientation=[^ ]+|mLastOrientation=[^ ]+'")?.out?.trim().orEmpty()
        if (rot.isNotEmpty()) note("   회전 상태: " + rot.lines().joinToString(" · ") { it.trim() }.take(260))
    }

    /**
     * 앱이 받는 접힘 정보를 GOAT 창(다른 앱 위 창 문맥)으로 직접 받아 본다(Jetpack WindowManager, 앱들이 플렉스 화면을 정할 때 쓰는 것).
     * 반쯤 접힘·가로 접는 선이 나오면 시스템 신호는 정상이고, 그런데도 앱 화면이 안 바뀌면 앱 쪽 한계다.
     */
    private fun foldInfo(context: Context): String = runCatching {
        val d = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return "화면 없음"
        val wctx = context.createDisplayContext(d).createWindowContext(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val info = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(2000) { androidx.window.layout.WindowInfoTracker.getOrCreate(wctx).windowLayoutInfo(wctx).first() }
        } ?: return "2초 안에 받지 못함"
        info.displayFeatures.joinToString(" | ") { f ->
            val ff = f as? androidx.window.layout.FoldingFeature
            if (ff == null) {
                "기타 ${f.bounds}"
            } else {
                "상태 ${ff.state} · 접는 선 ${ff.orientation} · 나눔 ${ff.isSeparating} · 가림 ${ff.occlusionType} · 위치 ${ff.bounds}"
            }
        }.ifEmpty { "접힘 정보 없음(펼침으로 봄)" }
    }.getOrElse { "읽기 실패: ${it.javaClass.simpleName} ${it.message}" }

    private fun rotation(context: Context) = runCatching { context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation }.getOrDefault(-1)

    private fun setRotation(context: Context, r: Int) {
        val cr = context.contentResolver
        runCatching {
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0)
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.USER_ROTATION, r)
        }
        ShizukuAccess.shell("cmd window user-rotation lock $r")
    }

    private fun buzz(context: Context) = runCatching {
        context.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun notify(context: Context, text: String, tap: PendingIntent?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "OTT 플렉스 시험", NotificationManager.IMPORTANCE_HIGH))
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setLargeIcon(UiGoat.notificationIcon(context))
            .setContentTitle("OTT 플렉스 시험")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(tap != null)
        tap?.let { b.setContentIntent(it) }
        runCatching { nm.notify(NOTIFICATION, b.build()) }
    }
}
