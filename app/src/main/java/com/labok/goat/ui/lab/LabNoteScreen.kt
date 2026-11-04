package com.labok.goat.ui.lab

import android.app.StatusBarManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.R
import com.labok.goat.data.lab.MemoShare
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 한 번에 이만큼 이상 늘어나면 붙여넣기로 보고 바로 공유한다. */
private const val PASTE_MIN_CHARS = 30

/**
 * 클립보드 공유(1.49.0 정식, 'TXT로 공유'에서 이름 바꿈): 긴 글을 붙여넣으면 곧바로 임시 txt 파일로 저장해 공유 창을 연다([MemoShare]).
 * 클립보드에 이미지·파일이 있으면 [붙여넣고 공유]가 원본 그대로 공유한다.
 * 공유를 마치고 돌아오면 파일과 글을 지운다. [붙여넣고 공유]는 클립보드 글을 한 번에 처리하고,
 * 입력란에 직접 붙여넣어도(한 번에 [PASTE_MIN_CHARS]자 이상 늘면) 같은 일을 한다.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        Toast.makeText(context, Copy.text(R.string.lab_note_temp_cleared), Toast.LENGTH_SHORT).show()
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

    // 스크롤 없이 화면 높이에 꼭 맞춘다: 위 카드는 내용만큼, 아래 입력 카드가 남은 높이를 모두 쓴다(긴 글은 입력란 안에서 스크롤)
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // 아래 여백(내비게이션 막대)은 contentPadding에 이미 들어 있어, 키보드 여백에서 그만큼 빼야 사이가 뜨지 않는다(1.17.5 전수 점검: 약 48dp 빔)
            .consumeWindowInsets(contentPadding)
            .padding(horizontal = Space.Edge)
            .padding(top = 4.dp, bottom = 16.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 키보드가 올라오면 위 카드를 접어 입력란이 한 줄로 줄지 않게 한다(힌트 글이 잘렸다)
        if (!WindowInsets.isImeVisible) {
            GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                Text(Copy.FEATURE_LAB_NOTE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(Copy.LAB_NOTE_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(Copy.LAB_NOTE_PASTE_SHARE, modifier = Modifier.fillMaxWidth(), onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val onlyText = clip != null && (0 until clip.itemCount).all { clip.getItemAt(it).uri == null }
                    val pasted = if (onlyText) clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty() else ""
                    when {
                        clip == null || clip.itemCount == 0 -> Toast.makeText(context, Copy.text(R.string.lab_clip_empty), Toast.LENGTH_SHORT).show()
                        onlyText && clip.itemCount == 1 && pasted.isNotBlank() -> {
                            text = pasted
                            share(pasted)
                        }
                        !sharing -> {
                            // 이미지·파일(또는 여러 항목)은 입력란에 넣지 않고 원본 그대로 공유한다
                            sharing = true
                            scope.launch {
                                val files = withContext(Dispatchers.IO) { MemoShare.saveClip(context, clip) }
                                if (files.isEmpty()) {
                                    sharing = false
                                    Toast.makeText(context, Copy.text(R.string.lab_clip_empty), Toast.LENGTH_SHORT).show()
                                } else {
                                    launcher.launch(MemoShare.chooser(context, files))
                                }
                            }
                        }
                    }
                })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.LAB_NOTE_ADD_TILE, modifier = Modifier.fillMaxWidth(), primary = false, onClick = { requestTile(context) })
                }
                Spacer(Modifier.height(Gap.Item))
                Text(Copy.LAB_NOTE_SHARE_MENU, color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!WindowInsets.isImeVisible) Spacer(Modifier.height(Gap.Card))
        GlassCard(modifier = Modifier.widthIn(max = ContentMaxWidth).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lab_note_paste_direct), fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.lab_note_char_count, text.length), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Gap.Item))
            Box(Modifier.fillMaxWidth().weight(1f)) {
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
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(Gap.Item))
            Row {
                LiquidButton(stringResource(R.string.lab_note_clear), modifier = Modifier.weight(1f), primary = false, enabled = text.isNotEmpty(), onClick = { text = "" })
                Spacer(Modifier.width(Space.Between))
                LiquidButton(Copy.LAB_NOTE_SHARE, modifier = Modifier.weight(1f), enabled = text.isNotBlank() && !sharing, onClick = { share(text) })
            }
        }
    }
}

/**
 * 빠른 설정에 '클립보드 공유' 타일을 넣어 달라고 시스템에 묻는다(안드로이드 13+). 사용자가 알림창 편집 화면을 찾지 않아도
 * 한 번 눌러 추가할 수 있다. 결과는 짧은 안내로 알린다.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestTile(context: Context) {
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    val main = context.mainExecutor
    runCatching {
        sbm.requestAddTileService(
            ComponentName(context, TxtShareTileService::class.java),
            Copy.FEATURE_LAB_NOTE,
            Icon.createWithResource(context, R.drawable.ic_tile_txt),
            main
        ) { result ->
            val msg = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Copy.text(R.string.lab_note_tile_added)
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> Copy.text(R.string.lab_note_tile_already)
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                else -> Copy.text(R.string.lab_note_tile_failed)
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }.onFailure { Toast.makeText(context, Copy.text(R.string.lab_note_tile_failed), Toast.LENGTH_SHORT).show() }
}
