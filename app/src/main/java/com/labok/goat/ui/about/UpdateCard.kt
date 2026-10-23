package com.labok.goat.ui.about

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.BuildConfig
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.update.Updater
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.UiMotion
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
    // 받는 속도: 진행률이 바뀔 때마다 (늘어난 양 × 파일 크기) ÷ 걸린 시간으로 재고 부드럽게 따라간다
    val dl = state as? Updater.State.Downloading
    var lastP by remember { mutableFloatStateOf(0f) }
    var lastAt by remember { mutableLongStateOf(0L) }
    var bps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(dl?.progress) {
        val d = dl
        if (d == null) {
            lastAt = 0L
            bps = 0f
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        if (lastAt != 0L && now > lastAt && d.progress >= lastP) {
            val inst = (d.progress - lastP) * d.release.size / ((now - lastAt) / 1000f)
            bps = if (bps == 0f) inst else bps * 0.7f + inst * 0.3f
        }
        lastP = d.progress
        lastAt = now
    }
    // 그림에 넘기는 속도(0~1): 초당 4MB면 가장 크게 출렁인다
    val rate = (bps / (4f * 1024 * 1024)).coerceIn(0f, 1f)
    var askStable by remember { mutableStateOf(false) }
    if (askStable) {
        AlertDialog(
            onDismissRequest = { askStable = false },
            title = { Text(Copy.UPDATE_TO_STABLE) },
            text = { Text(Copy.UPDATE_TO_STABLE_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    askStable = false
                    LabUnlock.lock(context)
                    LogRepository.from(context).add("안정 버전으로 돌아가기: 실험실을 숨기고 안정 버전만 받음")
                    Toast.makeText(context, Copy.UPDATE_TO_STABLE_DONE, Toast.LENGTH_LONG).show()
                }) { Text(Copy.UPDATE_TO_STABLE_OK) }
            },
            dismissButton = { TextButton(onClick = { askStable = false }) { Text(Copy.CANCEL) } }
        )
    }
    GlassCard {
        // 상태가 바뀌면 카드 높이도 물처럼 늘었다 줄어든다
        Column(Modifier.animateContentSize(spring(UiMotion.ratio(0.7f), UiMotion.stiffness(320f, 0.7f)))) {
            LiquidUpdateArt(state, dl?.progress ?: 0f, rate)
            Spacer(Modifier.height(Gap.Item))
            when (val s = state) {
                is Updater.State.Available -> {
                    Text(Copy.updateTitle(s.release.version) + if (s.release.test) Copy.UPDATE_TEST_SUFFIX else "", fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(firstLine(s.release.notes), color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.UPDATE_NOW, modifier = Modifier.fillMaxWidth(), onClick = { run { Updater.update(context, s.release) } })
                }
                is Updater.State.Downloading -> {
                    Text(Copy.updateTitle(s.release.version), fontWeight = FontWeight.SemiBold, color = c.ink)
                    // 받은 양·전체 크기·속도
                    Text(Copy.updateProgress((s.progress * 100).toInt(), s.progress * s.release.size, s.release.size.toFloat(), bps), color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
                is Updater.State.Installing -> {
                    Text(Copy.updateTitle(s.release.version), fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.UPDATE_INSTALLING, color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
                is Updater.State.Failed -> {
                    // 받을 판(release)이 없으면 확인 단계에서 실패한 것이다: 업데이트를 못 한 것이 아니라 확인을 못 한 것
                    Text(if (s.release == null) Copy.UPDATE_CHECK_FAILED_TITLE else Copy.UPDATE_FAILED_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
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
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (manual) {
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.UPDATE_CHECK, modifier = Modifier.fillMaxWidth(), primary = false, enabled = s != Updater.State.Checking, onClick = { run { Updater.check(context) } })
                    }
                }
            }
            // 버전 채널: 실험실을 열었거나 시험 버전을 쓰는 중이면 안내하고, 실험실을 열었으면 안정 버전으로 돌아가는 버튼을 둔다
            if (manual && (LabUnlock.unlocked || Updater.isTestBuild())) {
                Spacer(Modifier.height(Gap.Section))
                Text(Copy.UPDATE_CHANNEL_TITLE, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(
                    when {
                        LabUnlock.unlocked -> Copy.UPDATE_CHANNEL_TEST
                        else -> Copy.updateBackToStable(BuildConfig.VERSION_NAME)
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (LabUnlock.unlocked) {
                    Spacer(Modifier.height(Gap.Item))
                    LiquidButton(Copy.UPDATE_TO_STABLE, modifier = Modifier.fillMaxWidth(), primary = false, onClick = { askStable = true })
                }
            }
        }
    }
}

/** 릴리스 노트에서 첫 번째 바뀐 점(제목·빈 줄·목록 기호를 뺀다). */
private fun firstLine(notes: String): String = notes.lineSequence().map { it.trim().removePrefix("-").trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: ""
