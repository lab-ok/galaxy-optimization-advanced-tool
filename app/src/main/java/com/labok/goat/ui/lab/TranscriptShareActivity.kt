package com.labok.goat.ui.lab

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.labok.goat.data.lab.Transcript
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidRadioGroup
import com.labok.goat.ui.liquid.LiquidRadioRow
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 다른 앱의 공유 메뉴에 나타나는 '전사문 저장(나·상대)'(실험실). 삼성 음성 녹음의 전사문을 글(EXTRA_TEXT)이나
 * 텍스트 파일(EXTRA_STREAM)로 받아, 누가 '나'인지 한 번 고르면 나머지 화자를 '상대'로 바꿔 Download/GOAT/전사문에 저장한다.
 * 해석·변환은 [Transcript].
 */
class TranscriptShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = intent
        setContent {
            LiquidTheme {
                TranscriptSheet(shared, onClose = ::finish)
            }
        }
    }
}

private sealed interface Loaded {
    data object Loading : Loaded
    data class Ready(val name: String?, val text: String, val parsed: Transcript.Parsed?) : Loaded
    data object Empty : Loaded
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptSheet(shared: Intent?, onClose: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Loaded>(Loaded.Loading) }
    var me by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(shared) {
        loaded = withContext(Dispatchers.IO) { read(context, shared) }
        (loaded as? Loaded.Ready)?.parsed?.let { p ->
            // 지난번에 고른 이름(녹음 앱에서 바꾼 내 이름)이 있으면 미리 골라 둔다. '화자 1' 같은 번호는 녹음마다 달라 기억하지 않는다.
            me = TranscriptPrefs.me(context)?.takeIf { it in p.speakers }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.clearAndSetSemantics { contentDescription = Copy.SHEET_HANDLE }) { BottomSheetDefaults.DragHandle() } },
        // 다른 앱 위에 뜨는 창이라 앱 안 시트처럼 비치면 뒤 화면 글자와 겹친다: 불투명 바탕
        containerColor = c.bg,
        scrimColor = c.ink.copy(alpha = 0.16f)
    ) {
        SheetColumn(title = "전사문 저장") {
            when (val l = loaded) {
                Loaded.Loading -> Text("전사문을 읽는 중입니다…", color = c.muted)
                Loaded.Empty -> {
                    Text("받은 글이 없습니다. 음성 녹음에서 공유 → 텍스트 파일로 보내 주세요.", color = c.muted)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton("닫기", modifier = Modifier.fillMaxWidth(), primary = false, onClick = onClose)
                }
                is Loaded.Ready -> {
                    val p = l.parsed
                    if (p == null) {
                        Text("화자 표시를 찾지 못했습니다. 받은 글을 그대로 저장할 수 있습니다.", color = c.muted)
                    } else {
                        Text("누가 나인가요? 고른 화자는 '나', 나머지는 모두 '상대'로 바꿔 저장합니다.", color = c.muted)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidRadioGroup {
                            p.speakers.forEach { s ->
                                LiquidRadioRow(s, "${p.count(s)}번 말함 · ${p.preview(s)}", me == s, enabled = !saving) { me = s }
                            }
                        }
                    }
                    Spacer(Modifier.height(Gap.Section))
                    LiquidButton(
                        if (p == null) "그대로 저장" else "저장",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving && (p == null || me != null),
                        disabledHint = "먼저 '나'인 화자를 고르세요",
                        onClick = {
                            saving = true
                            scope.launch {
                                val out = if (p == null) l.text else Transcript.convert(p, me!!)
                                val file = Transcript.fileName(l.name, "전사문-" + SimpleDateFormat("yyMMdd-HHmm", Locale.KOREA).format(Date()))
                                val ok = withContext(Dispatchers.IO) { save(context, file, out) }
                                if (ok && p != null) TranscriptPrefs.remember(context, me!!)
                                LogRepository.from(context).add(
                                    "전사문 저장(실험실): ${if (ok) "저장함" else "저장 실패"} · $file · 화자 ${p?.speakers?.size ?: 0}명"
                                )
                                Toast.makeText(context, if (ok) "Download/GOAT/전사문에 저장했습니다" else "저장하지 못했습니다", Toast.LENGTH_LONG).show()
                                onClose()
                            }
                        }
                    )
                    Spacer(Modifier.height(Gap.Item))
                    Text("저장 위치: Download/GOAT/전사문", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 공유받은 글을 읽는다: 첨부 파일(EXTRA_STREAM)이 있으면 그것(본문에는 파일 이름 같은 짧은 글만 올 수 있다),
 * 없으면 본문(EXTRA_TEXT). 파일은 앞 8MB까지만 읽는다.
 */
private fun read(context: Context, intent: Intent?): Loaded {
    if (intent == null) return Loaded.Empty
    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    val uri = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    if (uri == null) return text?.let { Loaded.Ready(subject, it, Transcript.parse(it)) } ?: Loaded.Empty
    return runCatching {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: subject
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(8 * 1024 * 1024) } ?: return Loaded.Empty
        val text = Transcript.decode(bytes)
        if (text.isBlank()) Loaded.Empty else Loaded.Ready(name, text, Transcript.parse(text))
    }.getOrElse {
        LogRepository.from(context).add("전사문 저장(실험실): 파일을 읽지 못함 · ${it.javaClass.simpleName}")
        text?.let { t -> Loaded.Ready(subject, t, Transcript.parse(t)) } ?: Loaded.Empty
    }
}

private fun InputStream.readUpTo(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Download/GOAT/전사문에 새 파일로 저장한다. 이름이 겹치면 시스템이 '(1)'을 붙인다. */
private fun save(context: Context, name: String, text: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GOAT/전사문/")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } != null
}.getOrDefault(false)

/** 지난번에 '나'로 고른 이름. '화자 N' 같은 번호는 기억하지 않는다. */
private object TranscriptPrefs {
    private const val FILE = "transcript"
    private const val KEY_ME = "me"
    private val numbered = Regex("""^(화자|참석자|발언자|Speaker)\s*\d+$""", RegexOption.IGNORE_CASE)

    fun me(context: Context): String? = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ME, null)

    fun remember(context: Context, name: String) {
        if (numbered.matches(name.trim())) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ME, name).apply()
    }
}
