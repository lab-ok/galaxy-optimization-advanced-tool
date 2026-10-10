package com.local.folddpifix.data.log

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.local.folddpifix.AppInfo
import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.plan
import com.local.folddpifix.domain.resolvedPanels
import java.util.Date
import java.util.concurrent.TimeUnit

/** 문제 분석용 진단 로그(텍스트). 기기·앱 버전, 설정, 디스플레이 상태, 크래시, 앱 로그, 자체 logcat을 담는다. */
object DiagnosticReport {

    fun build(context: Context): String = buildString {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(context.packageName, 0)
        val settings = SettingsRepository.from(context)
        val dpi = DpiManager(context)

        appendLine("# ${AppInfo.NAME} 진단 로그")
        appendLine("생성: ${LogRepository.timeFormat().format(Date())}")
        appendLine("앱: ${pkg.versionName} (${pkg.longVersionCode}) ${context.packageName}")
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · 빌드 ${Build.DISPLAY}")
        appendLine(
            "권한: WRITE_SECURE_SETTINGS=" +
                (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        )
        appendLine()

        appendLine("## 설정")
        appendLine("기준 화면=${settings.reference} 기준 밀도=${settings.targetDpi} 미세조정=${settings.adjust}")
        appendLine("자동 복구=${settings.autoRecover} 부팅 적용=${settings.applyOnBoot} 접기/펼치기 확인=${settings.watchFold}")
        appendLine("비활성 보고 PPI=${settings.inactivePpi}")
        appendLine()

        appendLine("## 화면(패널)")
        val plan = settings.plan()
        settings.screens.forEach { s ->
            val r = settings.resolvedPanels().first { it.screen.key == s.key }
            appendLine(
                "${s.key} 보고 ${"%.1f".format(s.ppi)}ppi/${"%.2f".format(s.diagonalInch)}\" → " +
                    "적용 ${"%.1f".format(r.screen.ppi)}ppi/${"%.2f".format(r.screen.diagonalInch)}\" (${r.source.label}${r.specLabel?.let { ", $it" } ?: ""})" +
                    (plan?.let { " 목표 ${it.targetFor(s).dpi}" } ?: "")
            )
        }
        appendLine()

        appendLine("## 디스플레이")
        runCatching {
            appendLine("현재: ${dpi.read()} · 화면 ${dpi.currentScreen()}")
            dpi.displays().forEach { appendLine(it.toString()) }
        }.onFailure { appendLine("읽기 실패: $it") }
        appendLine()

        appendLine("## 크래시 기록")
        appendLine(CrashRecorder.read(context).ifBlank { "(없음)" })
        appendLine()

        appendLine("## 앱 로그")
        LogRepository.from(context).read().forEach(::appendLine)
        appendLine()

        appendLine("## logcat (이 앱 프로세스, 최근 300줄)")
        appendLine(ownLogcat())
    }

    private fun ownLogcat(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "300", "--pid=${Process.myPid()}")
            .redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(5, TimeUnit.SECONDS)
        text
    }.getOrElse { "읽기 실패: $it" }
}
