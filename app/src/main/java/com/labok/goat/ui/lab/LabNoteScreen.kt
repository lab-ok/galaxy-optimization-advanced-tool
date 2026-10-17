package com.labok.goat.ui.lab

import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.data.lab.MemoShare
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 한 번에 이만큼 이상 늘어나면 붙여넣기로 보고 바로 공유한다. */
private const val PASTE_MIN_CHARS = 30

/**
 * 실험실 메모장: 긴 글을 붙여넣으면 곧바로 임시 txt 파일로 저장해 공유 창을 연다([MemoShare]).
 * 공유를 마치고 돌아오면 파일과 글을 지운다. [붙여넣고 공유]는 클립보드 글을 한 번에 처리하고,
 * 입력란에 직접 붙여넣어도(한 번에 [PASTE_MIN_CHARS]자 이상 늘면) 같은 일을 한다.
 */
@Composable
internal fun LabNoteScreen(contentPadding: PaddingValues) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    // 공유 창에서 돌아오면(보냈든 취소했든) 임시 파일과 글을 지운다
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        MemoShare.clear(context)
        text = ""
        sharing = false
        Toast.makeText(context, "공유한 메모를 지웠습니다.", Toast.LENGTH_SHORT).show()
    }
    // 화면을 열 때 지난번에 남은 임시 파일이 있으면 지운다
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { MemoShare.clear(context) } }
    fun share(body: String) {
        if (body.isBlank() || sharing) return
        sharing = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) { MemoShare.save(context, body) }
            launcher.launch(MemoShare.chooser(context, uri))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Text(Copy.FEATURE_LAB_NOTE, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(Copy.LAB_NOTE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(Copy.LAB_NOTE_PASTE_SHARE, modifier = Modifier.fillMaxWidth(), onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (pasted.isBlank()) {
                    Toast.makeText(context, "클립보드에 글이 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    text = pasted
                    share(pasted)
                }
            })
            Spacer(Modifier.height(Gap.Item))
            Text(Copy.LAB_NOTE_SHARE_MENU, color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("직접 붙여넣기", fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(String.format(Locale.KOREA, "%,d자", text.length), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Gap.Item))
            Box(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                if (text.isEmpty()) Text(Copy.LAB_NOTE_HINT, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        val pasted = new.length - text.length >= PASTE_MIN_CHARS
                        text = new
                        if (pasted) share(new)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                )
            }
            Spacer(Modifier.height(Gap.Item))
            Row {
                LiquidButton("지우기", modifier = Modifier.weight(1f), primary = false, enabled = text.isNotEmpty(), onClick = { text = "" })
                Spacer(Modifier.width(Gap.Item))
                LiquidButton(Copy.LAB_NOTE_SHARE, modifier = Modifier.weight(1f), enabled = text.isNotBlank() && !sharing, onClick = { share(text) })
            }
        }
    }
}
