package com.labok.goat.data.lab

import android.content.Context
import android.content.Intent
import android.os.Build
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 실험실: 삼성 공유 화면(다이렉트 공유·즐겨찾기·앱 숨기기)을 GOAT가 다룰 수 있는지 가릴 정보를 모은다(읽기만 한다).
 * - 공유 화면을 띄우는 앱과 그 앱의 설정 저장소(콘텐츠 제공자) 이름
 * - 설정 키 중 share·direct·chooser·sharesheet가 들어간 것
 * - 공유 받기(ACTION_SEND)를 하는 앱 화면 목록과, 그 화면이 앱 실행 화면도 겸하는지(겸하면 숨기면 앱이 안 열린다)
 */
object ShareProbe {
    private val WORDS = Regex("(?i)share|direct|chooser|sharesheet|sharelive")

    fun run(context: Context): String = buildString {
        fun sh(cmd: String) = ShizukuAccess.shell(cmd)?.let { it.out.ifBlank { it.err } }?.trim()?.ifBlank { "(없음)" } ?: "Shizuku 연결 안 됨"
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        appendLine("Shizuku: ${ShizukuAccess.status.value}")
        appendLine()
        appendLine("== 공유 화면을 띄우는 앱 ==")
        appendLine(sh("cmd package resolve-activity --brief -a android.intent.action.CHOOSER"))
        appendLine()
        appendLine("== 공유 관련 패키지 ==")
        appendLine(sh("pm list packages | grep -i -E 'share|intentresolver|homeup|goodlock'"))
        appendLine()
        appendLine("== 설정 키(share·direct·chooser) ==")
        listOf("system", "secure", "global").forEach { t ->
            val r = ShizukuAccess.shell("settings list $t")
            if (r == null) appendLine("[$t] Shizuku 연결 안 됨") else r.out.lineSequence().filter { WORDS.containsMatchIn(it.substringBefore('=')) }.forEach { appendLine("[$t] $it") }
        }
        appendLine()
        appendLine("== 공유 관련 패키지의 설정 저장소·공유 화면 구성요소 ==")
        val pkgs = ShizukuAccess.shell("pm list packages | grep -i -E 'share|intentresolver|homeup'")?.out.orEmpty()
            .lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
        pkgs.forEach { p ->
            appendLine("[$p]")
            appendLine(sh("dumpsys package $p | grep -i -E 'Provider\\{|authority|Chooser|ShareSheet|DirectShare|Favorite|Hidden' | head -40"))
        }
        appendLine()
        appendLine("== 공유 받기 화면(ACTION_SEND) ==")
        val pm = context.packageManager
        val send = Intent(Intent.ACTION_SEND).setType("*/*")
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName + "/" + it.activityInfo.name }.toSet()
        val targets = pm.queryIntentActivities(send, 0)
        appendLine("${targets.size}개")
        targets.forEach { r ->
            val name = r.activityInfo.packageName + "/" + r.activityInfo.name
            appendLine("$name${if (name in launchers) " (앱 실행 화면 겸함)" else ""}")
        }
        appendLine()
        appendLine("== 구성요소 끄기 권한(셸) ==")
        appendLine(sh("dumpsys package com.android.shell | grep -i -E 'CHANGE_COMPONENT_ENABLED_STATE' | head -3"))
    }
}
