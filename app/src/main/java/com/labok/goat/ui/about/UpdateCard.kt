package com.labok.goat.ui.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.BuildConfig
import com.labok.goat.data.update.Updater
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 홈에 띄울 만한 상태인지: 새 판이 있거나 업데이트 중이거나, 업데이트하다 실패했다. */
internal fun Updater.State.worthShowing() = when (this) {
    is Updater.State.Available, is Updater.State.Downloading, is Updater.State.Installing -> true
    is Updater.State.Failed -> release != null
    else -> false
}

/**
 * 업데이트 카드: 새 판이 있으면 버전과 바뀐 점 첫 줄, [지금 업데이트]. 누르면 내려받고(진행 표시) 바로 설치한다.
 * [manual]이면(앱 정보) 새 판이 없어도 지금 버전과 [업데이트 확인]을 보여 준다.
 */
@Composable
internal fun UpdateCard(manual: Boolean) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.status.collectAsStateWithLifecycle()
    fun run(block: () -> Unit) = scope.launch(Dispatchers.IO) { block() }
    GlassCard {
        when (val s = state) {
            is Updater.State.Available -> {
                Text(Copy.updateTitle(s.release.version), fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(firstLine(s.release.notes), color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(Copy.UPDATE_NOW, modifier = Modifier.fillMaxWidth(), onClick = { run { Updater.update(context, s.release) } })
            }
            is Updater.State.Downloading -> {
                Text(Copy.updateTitle(s.release.version), fontWeight = FontWeight.SemiBold, color = c.ink)
                LiquidLoader("${Copy.UPDATE_DOWNLOADING} ${(s.progress * 100).toInt()}%")
            }
            is Updater.State.Installing -> {
                Text(Copy.updateTitle(s.release.version), fontWeight = FontWeight.SemiBold, color = c.ink)
                LiquidLoader(Copy.UPDATE_INSTALLING)
            }
            is Updater.State.Failed -> {
                Text(Copy.UPDATE_FAILED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(s.message, color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Gap.Item))
                LiquidButton(Copy.UPDATE_RETRY, modifier = Modifier.fillMaxWidth(), primary = false, onClick = {
                    run { s.release?.let { Updater.update(context, it) } ?: Updater.check(context) }
                })
            }
            else -> {
                Text(Copy.UPDATE_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    when (s) {
                        Updater.State.UpToDate -> Copy.updateLatest(BuildConfig.VERSION_NAME)
                        Updater.State.Checking -> Copy.UPDATE_CHECKING
                        else -> Copy.updateCurrent(BuildConfig.VERSION_NAME)
                    },
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
                if (manual) {
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.UPDATE_CHECK, modifier = Modifier.fillMaxWidth(), primary = false, enabled = s != Updater.State.Checking, onClick = { run { Updater.check(context) } })
                }
            }
        }
    }
}

/** 릴리스 노트에서 첫 번째 바뀐 점(제목·빈 줄·목록 기호를 뺀다). */
private fun firstLine(notes: String): String =
    notes.lineSequence().map { it.trim().removePrefix("-").trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: ""
