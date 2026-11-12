package com.labok.goat.ui.rotate

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.labok.goat.ui.liquid.LiquidRadioGroup
import com.labok.goat.ui.liquid.LiquidRadioRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.core.R
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.shizuku.ShizukuGuide
import com.labok.goat.ui.text.Copy

/** Uses the same controls and spacing as the other rotation settings. */
@Composable
private fun PowerRotateCard(onGuide: () -> Unit) {
    val context = LocalContext.current
    val c = LocalLiquid.current
    var enabled by remember { mutableStateOf(PowerRotate.enabled(context)) }
    var permitted by remember { mutableStateOf(ShakeRotate.canWrite(context)) }
    var showShizuku by remember { mutableStateOf(false) }
    var autoOn by remember { mutableStateOf(ShakeRotate.autoRotateOn(context)) }
    val shizuku by ShizukuAccess.status.collectAsStateWithLifecycle()
    val status by PowerRotate.status.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        enabled = PowerRotate.enabled(context)
        permitted = ShakeRotate.canWrite(context)
        autoOn = ShakeRotate.autoRotateOn(context)
        onPauseOrDispose { }
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { autoOn = ShakeRotate.autoRotateOn(context) }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val ready = permitted && shizuku == ShizukuAccess.State.READY
    GlassCard {
        PowerRotateArt()
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow(
            stringResource(R.string.power_rotate_title),
            stringResource(R.string.power_rotate_body),
            enabled,
            enabled || ready
        ) { on ->
            enabled = on
            PowerRotate.setEnabled(context, on)
        }
        Spacer(Modifier.height(Gap.Item))
        Text(
            stringResource(if (autoOn) R.string.power_rotate_auto_on else R.string.power_rotate_locked),
            color = c.ink,
            style = MaterialTheme.typography.labelMedium
        )
        if (enabled) {
            Spacer(Modifier.height(Gap.Item))
            val label = when (status) {
                PowerRotate.Status.OFF, PowerRotate.Status.WAITING -> R.string.power_rotate_waiting
                PowerRotate.Status.LISTENING -> R.string.power_rotate_listening
                PowerRotate.Status.ERROR -> R.string.power_rotate_input_error
            }
            Text(stringResource(label), color = if (status == PowerRotate.Status.ERROR) c.danger else c.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (!ready) {
            Spacer(Modifier.height(Gap.Item))
            Text(stringResource(R.string.power_rotate_permission), color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            if (!permitted) LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
            if (shizuku != ShizukuAccess.State.READY) {
                if (!permitted) Spacer(Modifier.height(Gap.Item))
                LiquidButton(stringResource(R.string.power_rotate_connect), onClick = { showShizuku = !showShizuku }, modifier = Modifier.fillMaxWidth())
                if (showShizuku) {
                    Spacer(Modifier.height(Gap.Item))
                    ShizukuGuide(shizuku, stringResource(R.string.power_rotate_permission))
                }
            }
        }
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.power_rotate_note), color = c.muted, style = MaterialTheme.typography.labelSmall)
    }
}


/** A dedicated feature screen, using the same card width and spacing as the other tools. */
@Composable
fun PowerRotateScreen(contentPadding: PaddingValues, onGuide: () -> Unit) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "powerRotate") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { PowerRotateCard(onGuide) }
        }
        item(key = "action") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { PowerRotateModeCard() }
        }
    }
}

@Composable
private fun PowerRotateModeCard() {
    val context = LocalContext.current
    val c = LocalLiquid.current
    var mode by remember { mutableStateOf(PowerRotate.mode(context)) }
    GlassCard {
        Text(stringResource(R.string.power_rotate_action), color = c.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Gap.Item))
        LiquidRadioGroup {
            LiquidRadioRow(
                stringResource(R.string.power_rotate_mode_toggle),
                stringResource(R.string.power_rotate_mode_toggle_body),
                mode == PowerRotate.Mode.TOGGLE,
                true
            ) { mode = PowerRotate.Mode.TOGGLE; PowerRotate.setMode(context, mode) }
            LiquidRadioRow(
                stringResource(R.string.power_rotate_mode_once),
                stringResource(R.string.power_rotate_mode_once_body),
                mode == PowerRotate.Mode.ONCE,
                true
            ) { mode = PowerRotate.Mode.ONCE; PowerRotate.setMode(context, mode) }
        }
    }
}

@Composable
private fun PowerRotateArt() {
    ArtCanvas(170.dp) { c, tm ->
        val center = Offset(size.width / 2, size.height / 2)
        val w = 70.dp.toPx()
        val h = 124.dp.toPx()
        val top = center - Offset(w / 2, h / 2)
        val stroke = Stroke(2.dp.toPx())
        drawRoundRect(c.surface, top, Size(w, h), CornerRadius(10.dp.toPx()))
        drawRoundRect(c.ink, top, Size(w, h), CornerRadius(10.dp.toPx()), style = stroke)
        drawLine(c.muted, center + Offset(-12.dp.toPx(), -50.dp.toPx()), center + Offset(12.dp.toPx(), -50.dp.toPx()), 2.dp.toPx())
        drawRoundRect(c.ink, center + Offset(w / 2 + 2.dp.toPx(), -25.dp.toPx()), Size(3.dp.toPx(), 22.dp.toPx()), CornerRadius(2.dp.toPx()))
        artLabel(tm, "3×", center + Offset(68.dp.toPx(), -14.dp.toPx()), c.ink, sizeSp = 22, bold = true)
        artLabel(tm, "↻", center, c.ink, sizeSp = 38, bold = false)
    }
}

@Composable
fun PowerRotateHelpSheet() {
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(stringResource(R.string.power_rotate_title), stringResource(R.string.power_rotate_body)) { PowerRotateArt() }
        HelpTopic(stringResource(R.string.power_rotate_mode_toggle), stringResource(R.string.power_rotate_mode_toggle_body))
        HelpTopic(stringResource(R.string.power_rotate_mode_once), stringResource(R.string.power_rotate_mode_once_body))
        HelpTopic(Copy.MENU_GUIDE, stringResource(R.string.power_rotate_permission))
        Text(stringResource(R.string.power_rotate_note), color = LocalLiquid.current.muted, style = MaterialTheme.typography.bodySmall)
    }
}
