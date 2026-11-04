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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.R
import com.labok.goat.data.lab.MemoShare
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
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

    // 스크롤 없이 화면 높이에 꼭 맞춘다: 위 카드는 내용만큼, 아래 입력 카드가 남은 높이를 모두 쓴다(긴 글은 입력란 안에서 스크롤)
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // 아래 여백(내비게이션 막대)은 contentPadding에 이미 들어 있어, 키보드 여백에서 그만큼 빼야 사이가 뜨지 않는다(1.17.5 전수 점검: 약 48dp 빔)
            .consumeWindowInsets(contentPadding)
            .padding(horizontal = 16.dp)
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
                        clip == null || clip.itemCount == 0 -> Toast.makeText(context, "클립보드가 비어 있습니다.", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "클립보드가 비어 있습니다.", Toast.LENGTH_SHORT).show()
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
                Text("직접 붙여넣기", fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(String.format(Locale.KOREA, "%,d자", text.length), color = c.muted, style = MaterialTheme.typography.labelSmall)
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
                LiquidButton("지우기", modifier = Modifier.weight(1f), primary = false, enabled = text.isNotEmpty(), onClick = { text = "" })
                Spacer(Modifier.width(Gap.Item))
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
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "빠른 설정에 타일을 추가했습니다."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "이미 빠른 설정에 있습니다."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                else -> "추가하지 못했습니다. 알림창 편집 화면에서 직접 넣어 주세요."
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }.onFailure { Toast.makeText(context, "추가하지 못했습니다. 알림창 편집 화면에서 직접 넣어 주세요.", Toast.LENGTH_SHORT).show() }
}

/** 클립보드 공유 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun ClipShareHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic(
            "1. 하는 일",
            "복사해 둔 내용을 파일로 만들어 곧바로 공유 창을 엽니다. 글은 TXT 파일로, 사진·파일은 원본 그대로 보냅니다. " +
                "메신저에서 긴 글이 잘리거나 여러 메시지로 나뉠 때 파일 하나로 깔끔하게 보낼 수 있습니다."
        )
        HelpTopic(
            "2. 가장 빠른 방법: 빠른 설정 타일",
            "이 화면의 [빠른 설정에 타일 추가]를 누르면 알림창에 '클립보드 공유' 타일이 생깁니다. " +
                "직접 넣으려면 알림창을 끝까지 내리고 연필(편집) 버튼을 눌러 타일을 끌어다 놓으세요.\n\n" +
                "그다음부터는 ① 아무 앱에서 글이나 사진을 복사하고 ② 알림창을 내려 타일을 누른 뒤 ③ 공유 창에서 보낼 앱과 대화방을 고르면 됩니다."
        )
        HelpTopic(
            "3. GOAT 아이콘 길게 누르기",
            "홈 화면의 GOAT 아이콘을 길게 누르면 '클립보드 공유' 바로가기가 나옵니다. 바로가기를 끌어 홈 화면에 따로 놓아 둘 수도 있습니다."
        )
        HelpTopic(
            "4. 이 화면에서 쓰기",
            "[붙여넣고 공유]를 누르면 클립보드 내용을 바로 공유합니다. 아래 입력란에 글을 붙여넣어도 바로 TXT로 공유하고, " +
                "직접 쓰거나 고친 글은 [파일로 공유]로 보냅니다."
        )
        HelpTopic(
            "5. 다른 앱의 공유 메뉴",
            "글을 '공유'할 수 있는 앱에서는 공유 목록의 'TXT로 공유'를 고르세요. 그 글을 TXT 파일로 바꿔 공유 창을 다시 엽니다."
        )
        HelpTopic(
            "6. 보낼 수 있는 것",
            "• 글·서식 있는 글: TXT 파일(서식은 빠지고 글만 남습니다)\n" +
                "• 사진(갤러리·브라우저의 '이미지 복사'): 원본 이미지\n" +
                "• 여러 장을 한꺼번에 복사한 사진: 한 번에 모두\n" +
                "• 파일 앱에서 복사한 파일: 원본 파일"
        )
        HelpTopic(
            "7. 알아 둘 점",
            "가장 마지막에 복사한 것 하나만 보냅니다. 키보드의 클립보드 목록에 쌓인 예전 항목은 안드로이드가 막아 읽을 수 없습니다. " +
                "공유할 때 'GOAT이 클립보드에서 붙여넣음' 안내가 뜨는 것은 안드로이드의 정상 알림입니다."
        )
        HelpTopic(
            "8. 개인정보",
            "파일은 휴대폰 안 GOAT 임시 폴더에만 잠깐 만들고, 공유를 마치고 돌아오면 바로 지웁니다. GOAT는 내용을 어디에도 따로 보내거나 저장하지 않습니다."
        )
    }
}
