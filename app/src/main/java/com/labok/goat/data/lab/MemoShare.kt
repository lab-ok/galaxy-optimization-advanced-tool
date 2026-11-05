package com.labok.goat.data.lab

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.lab.ClipShareActivity
import com.labok.goat.ui.text.Copy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 'TXT로 공유'(1.49.0 정식): 다른 앱의 공유 메뉴([com.labok.goat.ui.lab.MemoShareActivity])로 받은 긴 글을
 * 임시 txt 파일로 만들어 다시 공유한다. 메신저는 긴 글을 그대로 보내지 못하는 경우가 있어 파일로 첨부하게 하는 것이 목적이다.
 * 파일은 앱 캐시의 share 폴더에만 두고, 공유를 마치고 돌아오면 지운다.
 */
object MemoShare {
    private const val PREFIX = "memo_"

    /** [text]를 임시 txt 파일로 저장하고 공유용 주소를 돌려준다(작업 스레드). */
    fun save(context: Context, text: String): Uri {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "$PREFIX$stamp.txt").apply { writeText(text) }
        LogRepository.from(app).add("TXT로 공유: 임시 파일 저장 (${text.length}자)")
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    /** 공유할 파일 하나: 주소와 종류(MIME). */
    data class Shared(val uri: Uri, val mime: String)

    /**
     * 클립보드 내용을 항목마다 공유용 파일로 저장한다(작업 스레드). 글은 TXT로, 이미지·문서처럼 주소(content://)로 들어온 것은
     * 원래 파일 그대로 복사한다. 안드로이드는 클립보드를 읽는 앱에 그 주소를 읽을 권한을 잠깐 준다(에뮬레이터 실측).
     * 예전에는 모든 항목을 글로 바꿔(coerceToText) 이미지가 깨진 글자로 든 TXT가 됐다. 저장할 것이 없으면 빈 목록.
     */
    fun saveClip(context: Context, clip: ClipData): List<Shared> {
        val app = context.applicationContext
        val out = mutableListOf<Shared>()
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val uri = item.uri
            val copied = if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) copyFile(app, uri, clip.description.let { d -> (0 until d.mimeTypeCount).map(d::getMimeType) }) else null
            if (copied != null) {
                out += copied
                continue
            }
            if (item.intent != null && item.text == null && uri == null) continue
            val text = (item.text ?: item.htmlText ?: uri?.toString())?.toString().orEmpty()
            if (text.isNotBlank()) out += Shared(save(app, text), "text/plain")
        }
        return out
    }

    /** [uri]의 파일을 share 폴더로 복사한다. 이름은 원래 이름, 없으면 종류에 맞는 확장자로 짓는다. 못 읽으면 null. */
    private fun copyFile(context: Context, uri: Uri, clipMimes: List<String>): Shared? = runCatching {
        val r = context.contentResolver
        val mime = r.getType(uri) ?: clipMimes.firstOrNull { it != ClipDescription.MIMETYPE_TEXT_URILIST && it != "*/*" } ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val stamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date())
        val shown = runCatching { r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
        val base = (shown ?: "clip_$stamp.$ext").replace(Regex("[\\/:*?\"<>|]"), "_")
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        var file = File(dir, base)
        var n = 1
        while (file.exists()) file = File(dir, base.substringBeforeLast('.') + "_" + n++ + "." + base.substringAfterLast('.', ext))
        val bytes = r.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        LogRepository.from(context).add("클립보드 공유: 파일 저장 ($mime, ${bytes}바이트)")
        Shared(FileProvider.getUriForFile(context, "${context.packageName}.files", file), mime)
    }.getOrNull()

    /** 여러 파일을 한 번에 공유하는 창. 하나면 [chooser]와 같다. */
    fun chooser(context: Context, files: List<Shared>): Intent {
        if (files.size == 1 && files[0].mime == "text/plain") return chooser(context, files[0].uri)
        val send = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files[0].uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.uri }))
        }.apply {
            val kinds = files.map { it.mime }.distinct()
            type = kinds.singleOrNull() ?: kinds.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
            clipData = ClipData.newRawUri("", files[0].uri).also { c -> files.drop(1).forEach { c.addItem(ClipData.Item(it.uri)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, Copy.text(R.string.labdata_memo_share_clipboard)).withoutGoat(context)
    }

    /** 공유 창을 여는 인텐트. GOAT 자신은 받는 앱 목록에서 뺀다(다시 GOAT로 공유되는 고리를 막는다). */
    fun chooser(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, Copy.text(R.string.labdata_memo_share_txt)).withoutGoat(context)
    }

    /**
     * 공유 창에서 GOAT 자신(TXT로 공유·전사문 저장·클립보드 공유)을 받는 앱 목록에서 뺀다.
     * GOAT가 보낸 것이 다시 GOAT로 돌아오는 고리를 막는다(권한 설정 안내·문제 신고·실험 결과 공유도 같이 쓴다, 1.51.0 QA).
     */
    fun Intent.withoutGoat(context: Context): Intent = putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        arrayOf(
            ComponentName(context, "com.labok.goat.ui.lab.MemoShareActivity"),
            ComponentName(context, "com.labok.goat.ui.lab.TranscriptShareActivity"),
            ComponentName(context, "com.labok.goat.ui.lab.ClipShareActivity")
        )
    )

    /** 공유가 끝나면 임시 파일을 모두 지운다. */
    fun clear(context: Context) {
        // share 폴더는 공유용 임시 파일만 두는 곳이라 남은 파일(예전 판이 남긴 것 포함)을 모두 지운다
        val n = File(context.applicationContext.cacheDir, "share").listFiles()?.count { it.delete() } ?: 0
        if (n > 0) LogRepository.from(context).add("TXT로 공유: 공유를 마쳐 임시 파일 ${n}개 지움")
    }

    /**
     * 공유 입구를 맞춘다. '클립보드 공유'(빠른 설정 타일, GOAT 아이콘을 길게 누르면 나오는 바로가기)와 다른 앱 공유 메뉴의
     * 'TXT로 공유'는 정식 기능이라 늘 켠다. '전사문 저장'은 아직 실험실 기능이라 실험실을 열었을 때([lab])만 켠다.
     */
    fun setShareTarget(context: Context, lab: Boolean) {
        val pm = context.packageManager
        fun enable(name: String, on: Boolean) {
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(ComponentName(context, name), state, PackageManager.DONT_KILL_APP)
        }
        enable("com.labok.goat.ui.lab.MemoShareActivity", true)
        enable("com.labok.goat.ui.lab.TxtShareTileService", true)
        enable("com.labok.goat.ui.lab.TranscriptShareActivity", lab)
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        val intent = Intent(context, ClipShareActivity::class.java).setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(Copy.text(R.string.labdata_memo_share_clipboard))
            .setLongLabel(Copy.text(R.string.labdata_memo_share_clipboard))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_txt))
            .setIntent(intent)
            .build()
        runCatching { sm.dynamicShortcuts = listOf(shortcut) }
    }

    private const val SHORTCUT_ID = "clip_txt_share"
}
