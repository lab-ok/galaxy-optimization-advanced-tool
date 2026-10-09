package com.local.folddpifix.ui.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.about.DeveloperProfile
import com.local.folddpifix.ui.art.ConnectionIllustration
import com.local.folddpifix.ui.components.CommandBox
import com.local.folddpifix.ui.art.GuideSceneArt
import com.local.folddpifix.ui.liquid.LiquidButton
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import com.local.folddpifix.ui.liquid.GlassCard
import androidx.compose.animation.Crossfade
import com.local.folddpifix.ui.liquid.LiquidChips
import com.local.folddpifix.ui.liquid.LiquidDots
import com.local.folddpifix.ui.liquid.LocalLiquid

/**
 * 최초 권한 부여 마법사. 연결 방식과 PC 운영체제를 고르면 단계를 하나씩 보여 준다.
 * 명령·링크는 복사할 수 있고, 전체 안내를 메일·메신저로 PC에 보낼 수 있다.
 * 앱은 ADB에 접속하지 않으며, [hasPermission]이 true가 되면 완료 화면을 보여 준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrantGuideSheet(hasPermission: Boolean, onCopied: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var connection by rememberSaveable { mutableStateOf(GrantGuideContent.Connection.WIRELESS) }
    var os by rememberSaveable { mutableStateOf(GrantGuideContent.PcOs.WINDOWS) }
    var index by rememberSaveable(connection, os) { mutableIntStateOf(0) }
    val steps = GrantGuideContent.steps(connection, os)
    val copy: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
        onCopied()
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
            .animateContentSize(),
    ) {
        Text("권한 설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "PC의 adb로 한 번만 부여하면 재부팅 후에도 유지됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ConnectionIllustration(wireless = connection == GrantGuideContent.Connection.WIRELESS, done = hasPermission)

        AnimatedVisibility(visible = hasPermission, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("권한 설정 완료", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("이 창을 닫아도 됩니다. 디버깅은 꺼도 됩니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (!hasPermission) {
            Spacer(Modifier.height(16.dp))
            Label("연결 방식")
            Choice(GrantGuideContent.Connection.entries, connection, { it.label }) { connection = it }
            Spacer(Modifier.height(10.dp))
            Label("PC 운영체제")
            Choice(GrantGuideContent.PcOs.entries, os, { it.label }) { os = it }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton("PC로 보내기", modifier = Modifier.weight(1f), onClick = { shareGuide(context, connection, os) }, primary = false)
                LiquidButton("메일로 보내기", modifier = Modifier.weight(1f), onClick = { emailGuide(context, connection, os) }, primary = false)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1} / ${steps.size} 단계",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                LiquidDots(count = steps.size, index = index.coerceIn(0, steps.lastIndex))
            }
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = index.coerceIn(0, steps.lastIndex),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                },
                label = "step",
            ) { i ->
                StepCard(steps[i], onCopy = copy, onOpenDev = { openDeveloperOptions(context) }, onOpenLink = { openLink(context, it) })
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { index-- }, enabled = index > 0) { Text("이전") }
                if (index < steps.lastIndex) {
                    LiquidButton("다음", onClick = { index++ }, burst = false)
                } else {
                    Text(
                        "명령을 실행하면 자동으로 확인합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            TroubleCard(GrantGuideContent.troubles(connection, os), onCopy = copy)
        }
    }
}

/** 안 될 때: 자주 나오는 오류를 접어 두고, 누르면 해결 방법과 명령이 펼쳐진다. */
@Composable
private fun TroubleCard(items: List<GrantGuideContent.Trouble>, onCopy: (String) -> Unit) {
    val c = LocalLiquid.current
    var open by remember { mutableStateOf<String?>(null) }
    GlassCard(padding = 16.dp) {
        Text("안 될 때", fontWeight = FontWeight.SemiBold, color = c.ink)
        Text("PC에 나온 오류 문구와 같은 항목을 눌러 보세요.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        items.forEachIndexed { i, tr ->
            Spacer(Modifier.height(8.dp))
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
            val expanded = open == tr.title
            val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { open = if (expanded) null else tr.title }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr.title, color = c.ink, fontWeight = FontWeight.Medium)
                    tr.error?.let {
                        Text(it, color = c.muted, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.muted, modifier = Modifier.graphicsLayer { rotationZ = rot })
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(tr.body, color = c.ink, style = MaterialTheme.typography.bodySmall)
                    tr.commands.forEach { cmd ->
                        Spacer(Modifier.height(8.dp))
                        CommandBox(cmd, onCopy = { onCopy(cmd) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    step: GrantGuideContent.Step,
    onCopy: (String) -> Unit,
    onOpenDev: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    GlassCard(padding = 16.dp) {
        Column {
            // 단계를 넘기면 장면이 부드럽게 바뀐다.
            Crossfade(step.scene, label = "scene") { GuideSceneArt(it) }
            Spacer(Modifier.height(8.dp))
            Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(step.body, style = MaterialTheme.typography.bodyMedium)
            step.links.forEach { (name, url) ->
                Spacer(Modifier.height(10.dp))
                Text(name, style = MaterialTheme.typography.labelLarge)
                CommandBox(url, onCopy = { onCopy(url) }, extra = { TextButton(onClick = { onOpenLink(url) }) { Text("열기") } })
            }
            step.commands.forEach { cmd ->
                Spacer(Modifier.height(10.dp))
                CommandBox(cmd, onCopy = { onCopy(cmd) })
            }
            if (step.openDevOptions) {
                Spacer(Modifier.height(10.dp))
                LiquidButton("개발자 옵션 열기", modifier = Modifier.fillMaxWidth(), onClick = onOpenDev, primary = false)
            }
        }
    }
}

/** 선택 항목 위의 작은 제목. */
@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Spacer(Modifier.height(6.dp))
    LiquidChips(options, selected, label, onSelect)
}

private fun shareGuide(context: Context, c: GrantGuideContent.Connection, os: GrantGuideContent.PcOs) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${DeveloperProfile.PRODUCT} 권한 설정 안내")
        putExtra(Intent.EXTRA_TEXT, GrantGuideContent.fullText(c, os, DeveloperProfile.PRODUCT))
    }
    context.startActivity(Intent.createChooser(send, "안내 보내기"))
}

private fun emailGuide(context: Context, c: GrantGuideContent.Connection, os: GrantGuideContent.PcOs) {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "${DeveloperProfile.PRODUCT} 권한 설정 안내")
        putExtra(Intent.EXTRA_TEXT, GrantGuideContent.fullText(c, os, DeveloperProfile.PRODUCT))
    }
    try {
        context.startActivity(mail)
    } catch (e: ActivityNotFoundException) {
        shareGuide(context, c, os)
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // 브라우저가 없으면 아무것도 하지 않는다(복사 버튼으로 대신).
    }
}

internal fun openDeveloperOptions(context: Context) {
    val devOn = Settings.Global.getInt(
        context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
    ) == 1
    val intent = Intent(
        if (devOn) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS
    )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
