package com.labok.goat.data.lab

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.labok.goat.data.log.LogRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 실험실 메모장: 긴 글을 임시 txt 파일로 만들어 공유한다. 메신저는 긴 글을 그대로 보내지 못하는 경우가 있어,
 * 글을 파일로 바꿔 첨부하게 하는 것이 목적이다. 파일은 앱 캐시의 share 폴더에만 두고, 공유를 마치고 돌아오면 지운다.
 * 다른 앱의 공유 메뉴에도 'txt 파일로 공유'([com.labok.goat.ui.lab.MemoShareActivity])로 나타난다(실험실을 연 경우에만).
 */
object MemoShare {
    private const val PREFIX = "memo_"

    /** [text]를 임시 txt 파일로 저장하고 공유용 주소를 돌려준다(작업 스레드). */
    fun save(context: Context, text: String): Uri {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "$PREFIX$stamp.txt").apply { writeText(text) }
        LogRepository.from(app).add("메모장(실험실): 임시 파일 저장 (${text.length}자)")
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    /** 공유 창을 여는 인텐트. GOAT 자신은 받는 앱 목록에서 뺀다(다시 GOAT로 공유되는 고리를 막는다). */
    fun chooser(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "txt 파일로 공유").apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, "com.labok.goat.ui.lab.MemoShareActivity")))
        }
    }

    /** 공유가 끝나면 임시 파일을 모두 지운다. */
    fun clear(context: Context) {
        // share 폴더는 공유용 임시 파일만 두는 곳이라 남은 파일(예전 판이 남긴 것 포함)을 모두 지운다
        val n = File(context.applicationContext.cacheDir, "share").listFiles()?.count { it.delete() } ?: 0
        if (n > 0) LogRepository.from(context).add("메모장(실험실): 공유를 마쳐 임시 파일 ${n}개 지움")
    }

    /** 다른 앱 공유 메뉴의 'txt 파일로 공유'를 켜거나 끈다(실험실을 열었을 때만 보이게). */
    fun setShareTarget(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, "com.labok.goat.ui.lab.MemoShareActivity"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
