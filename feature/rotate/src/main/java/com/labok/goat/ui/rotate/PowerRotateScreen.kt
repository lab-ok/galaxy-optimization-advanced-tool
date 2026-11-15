package com.labok.goat.ui.rotate

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
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
import com.labok.goat.ui.components.copyText
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
import com.labok.goat.ui.liquid.LiquidSlider
import com.labok.goat.ui.liquid.LiquidRadioGroup
import com.labok.goat.ui.liquid.LiquidRadioRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.core.R
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.domain.TripleTap
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy

/** Uses the same controls and spacing as the other rotation settings. */
@Composable
private fun PowerRotateCard() {
    val context = LocalContext.current
    val c = LocalLiquid.current
    var enabled by remember { mutableStateOf(PowerRotate.enabled(context)) }
    var permitted by remember { mutableStateOf(PowerRotate.canWriteRotation(context)) }
    var overlay by remember { mutableStateOf(PowerRotate.canDraw(context)) }
    var autoOn by remember { mutableStateOf(ShakeRotate.autoRotateOn(context)) }
    val status by PowerRotate.status.collectAsStateWithLifecycle()
    val detection by PowerRotate.detection.collectAsStateWithLifecycle()
    val changed by PowerRotate.changed.collectAsStateWithLifecycle()
    val window = remember(changed) { PowerRotate.window(context) }
    LifecycleResumeEffect(Unit) {
        overlay = PowerRotate.canDraw(context)
        enabled = PowerRotate.enabled(context)
        permitted = PowerRotate.canWriteRotation(context)
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
    val ready = permitted && overlay
    GlassCard {
        PowerRotateArt()
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.power_rotate_detection_mode), color = c.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow(
            stringResource(R.string.power_rotate_title),
            stringResource(R.string.power_rotate_body, window.seconds),
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
            Text(stringResource(R.string.power_rotate_diag_counts, detection.events, detection.candidates), color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (detection.events > 0) {
                Text(stringResource(R.string.power_rotate_diag_last, detection.count,
                    detection.intervalMs?.let { "${it}ms" } ?: "—", decisionText(detection.decision)),
                    color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!ready) {
            Spacer(Modifier.height(Gap.Item))
            Text(stringResource(R.string.power_rotate_permission), color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (!overlay) {
                Spacer(Modifier.height(Gap.Item))
                PermissionButton(overlay = true)
            }
            if (!permitted) {
                Spacer(Modifier.height(Gap.Item))
                PermissionButton(overlay = false)
            }
        }
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.power_rotate_note), color = c.muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(stringResource(R.string.power_rotate_diag_copy), primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
            if (copyText(context, PowerRotate.diagnose(context))) {
                Toast.makeText(context, R.string.power_rotate_diag_copied, Toast.LENGTH_SHORT).show()
            }
        })
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
            Box(Modifier.widthIn(max = ContentMaxWidth)) { PowerRotateCard() }
        }
        item(key = "setup") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { GestureBarSetupCard() }
        }
        item(key = "action") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { PowerRotateModeCard() }
        }
        item(key = "area") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { GestureBarAreaCard() }
        }
        item(key = "window") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { PowerRotateWindowCard() }
        }
    }
}

@Composable
private fun PermissionButton(overlay: Boolean) {
    val context = LocalContext.current
    LiquidButton(stringResource(if (overlay) R.string.gesture_bar_overlay_button else R.string.gesture_bar_write_button),
        modifier = Modifier.fillMaxWidth(), onClick = {
            if (!PowerRotate.openPermission(context, overlay)) Toast.makeText(context, R.string.gesture_bar_permission_failed, Toast.LENGTH_SHORT).show()
        })
}

/** Only the two user-controlled special-access pages needed for this feature. */
@Composable
private fun GestureBarSetupCard() {
    val context = LocalContext.current
    val c = LocalLiquid.current
    var permitted by remember { mutableStateOf(PowerRotate.canWriteRotation(context)) }
    var overlay by remember { mutableStateOf(PowerRotate.canDraw(context)) }
    LifecycleResumeEffect(Unit) {
        permitted = PowerRotate.canWriteRotation(context)
        overlay = PowerRotate.canDraw(context)
        onPauseOrDispose { }
    }
    GlassCard {
        Text(stringResource(R.string.gesture_bar_setup_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gesture_bar_setup_intro), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.gesture_bar_setup_overlay_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gesture_bar_setup_overlay_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (overlay) R.string.gesture_bar_permission_granted else R.string.gesture_bar_permission_needed), color = c.muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Gap.Item))
        PermissionButton(overlay = true)
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.gesture_bar_setup_write_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gesture_bar_setup_write_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (permitted) R.string.gesture_bar_permission_granted else R.string.gesture_bar_permission_needed), color = c.muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Gap.Item))
        PermissionButton(overlay = false)
        Spacer(Modifier.height(Gap.Section))
        Text(stringResource(R.string.gesture_bar_setup_use_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gesture_bar_setup_use_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.gesture_bar_setup_nav_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.gesture_bar_setup_note), color = c.muted, style = MaterialTheme.typography.labelSmall)
        if (!overlay || !permitted) {
            Text(stringResource(R.string.gesture_bar_setup_restricted), color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Gap.Item))
            LiquidButton(stringResource(R.string.gesture_bar_app_info), primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                if (!PowerRotate.openAppInfo(context)) Toast.makeText(context, R.string.gesture_bar_permission_failed, Toast.LENGTH_SHORT).show()
            })
        }
    }
}

@Composable
private fun GestureBarAreaCard() {
    val context = LocalContext.current
    val c = LocalLiquid.current
    val changed by PowerRotate.changed.collectAsStateWithLifecycle()
    var area by remember(changed) { mutableStateOf(PowerRotate.area(context)) }
    val widthLabel = stringResource(R.string.gesture_bar_width)
    val heightLabel = stringResource(R.string.gesture_bar_height)
    GlassCard {
        Text(stringResource(R.string.gesture_bar_area_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gesture_bar_area_body), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidSwitchRow(stringResource(R.string.gesture_bar_area_visible), stringResource(R.string.gesture_bar_area_visible_body), area.visible, true) {
            area = area.copy(visible = it); PowerRotate.setArea(context, area)
        }
        Spacer(Modifier.height(Gap.Section))
        Text(widthLabel, color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(area.widthDp, 48..360, { area = area.copy(widthDp = it) },
            { PowerRotate.setArea(context, area) }, { "$it dp" }, widthLabel)
        Spacer(Modifier.height(Gap.Section))
        Text(heightLabel, color = c.ink, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(Gap.Item))
        LiquidSlider(area.heightDp, 8..96, { area = area.copy(heightDp = it) },
            { PowerRotate.setArea(context, area) }, { "$it dp" }, heightLabel)
        Spacer(Modifier.height(Gap.Item))
        LiquidButton(stringResource(R.string.gesture_bar_area_reset), primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
            area = PowerRotate.Area(); PowerRotate.setArea(context, area)
        })
    }
}

@Composable
private fun PowerRotateWindowCard() {
    val context = LocalContext.current
    val c = LocalLiquid.current
    val changed by PowerRotate.changed.collectAsStateWithLifecycle()
    val selected = remember(changed) { PowerRotate.window(context) }
    GlassCard {
        Text(stringResource(R.string.power_rotate_window_title), color = c.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Gap.Item))
        Text(stringResource(R.string.power_rotate_window_note), color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Item))
        LiquidRadioGroup {
            PowerRotate.Window.entries.forEach { window ->
                LiquidRadioRow(
                    stringResource(R.string.power_rotate_window_seconds, window.seconds),
                    stringResource(when (window) {
                        PowerRotate.Window.SHORT -> R.string.power_rotate_window_default
                        PowerRotate.Window.MEDIUM -> R.string.power_rotate_window_medium
                        PowerRotate.Window.LONG -> R.string.power_rotate_window_long
                    }), selected == window, true
                ) { PowerRotate.setWindow(context, window) }
            }
        }
    }
}

@Composable
private fun decisionText(decision: TripleTap.Decision?): String = stringResource(when (decision) {
    TripleTap.Decision.FIRST -> R.string.power_rotate_diag_first
    TripleTap.Decision.SECOND -> R.string.power_rotate_diag_second
    TripleTap.Decision.TRIPLE -> R.string.power_rotate_diag_matched
    TripleTap.Decision.WINDOW_EXPIRED -> R.string.power_rotate_diag_expired
    TripleTap.Decision.CANCELLED -> R.string.power_rotate_diag_reset
    null -> R.string.power_rotate_waiting
})

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
        drawLine(c.ink, center + Offset(-14.dp.toPx(), 53.dp.toPx()), center + Offset(14.dp.toPx(), 53.dp.toPx()), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        artLabel(tm, "3×", center + Offset(68.dp.toPx(), 42.dp.toPx()), c.ink, sizeSp = 22, bold = true)
        artLabel(tm, "↻", center, c.ink, sizeSp = 38, bold = false)
    }
}

@Composable
fun PowerRotateHelpSheet() {
    val window = PowerRotate.window(LocalContext.current)
    SheetColumn(title = Copy.MENU_HELP) {
        HelpTopic(stringResource(R.string.power_rotate_detection_mode), stringResource(R.string.power_rotate_note))
        HelpTopic(stringResource(R.string.power_rotate_title), stringResource(R.string.power_rotate_body, window.seconds)) { PowerRotateArt() }
        HelpTopic(stringResource(R.string.power_rotate_mode_toggle), stringResource(R.string.power_rotate_mode_toggle_body))
        HelpTopic(stringResource(R.string.power_rotate_mode_once), stringResource(R.string.power_rotate_mode_once_body))
        HelpTopic(Copy.MENU_GUIDE, stringResource(R.string.power_rotate_permission))
        Text(stringResource(R.string.power_rotate_note), color = LocalLiquid.current.muted, style = MaterialTheme.typography.bodySmall)
    }
}
