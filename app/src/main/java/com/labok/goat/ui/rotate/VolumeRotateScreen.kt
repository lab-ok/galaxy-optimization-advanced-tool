package com.labok.goat.ui.rotate

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.data.display.DeviceKind
import com.labok.goat.data.lab.VolumeRotate
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.components.ContentMaxWidth
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.components.featurePadding
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidButton
import com.labok.goat.ui.liquid.LiquidChips
import com.labok.goat.ui.liquid.LiquidColors
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.text.Copy
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/**
 * 볼륨 버튼 회전([VolumeRotate]): 그림, 켜기·반대로·카메라 예외, 지금 화면 방향에서 바뀌는지.
 * 시스템 설정 권한으로 접근성 서비스를 켜므로 권한이 없으면 안내 카드를 먼저 보인다.
 */
@Composable
internal fun VolumeRotateScreen(contentPadding: PaddingValues, permitted: Boolean, onGuide: () -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val on = remember(version) { VolumeRotate.enabled(context) }
    // 폴드는 접었을 때·펼쳤을 때 설정을 따로 둔다. 처음엔 지금 화면 쪽을 보여 준다
    val foldable = remember { DeviceKind.isFoldable(context) }
    var editOpen by remember { mutableStateOf(VolumeRotate.isOpen(context)) }
    var invert by remember(editOpen) { mutableStateOf(VolumeRotate.invert(context, editOpen)) }
    var camera by remember(editOpen) { mutableStateOf(VolumeRotate.cameraExempt(context, editOpen)) }
    // 왼쪽·오른쪽 가로끼리 바뀔 때는 구성 변경이 없어 화면 표시 변경을 직접 듣고 방향을 다시 읽는다
    var rotation by remember { mutableIntStateOf(VolumeRotate.rotation(context)) }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                rotation = VolumeRotate.rotation(context)
            }
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm.unregisterDisplayListener(listener) }
    }
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = featurePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Gap.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "intro") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    Text(Copy.FEATURE_VOLUME_ROTATE, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(Copy.VOLUME_BODY, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Gap.Item))
                    VolumeRotateArt()
                }
            }
        }
        if (!permitted) {
            item(key = "permission") {
                Box(Modifier.widthIn(max = ContentMaxWidth)) {
                    GlassCard {
                        Text(Copy.STATUS_NEED_SETUP, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(Copy.VOLUME_NEED_PERMISSION, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidButton(Copy.MENU_GUIDE, onClick = onGuide, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item(key = "controls") {
            Box(Modifier.widthIn(max = ContentMaxWidth)) {
                GlassCard {
                    LiquidSwitchRow(Copy.VOLUME_ON, Copy.VOLUME_ON_DESC, on, permitted) {
                        if (!VolumeRotate.setEnabled(context, it)) Toast.makeText(context, Copy.LAB_WRITE_FAILED, Toast.LENGTH_SHORT).show()
                        version++
                    }
                    Spacer(Modifier.height(Gap.Section))
                    if (foldable) {
                        Text(Copy.VOLUME_PER_SCREEN, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Gap.Item))
                        LiquidChips(
                            options = listOf(false, true),
                            selected = editOpen,
                            label = { if (it) Copy.VOLUME_OPEN else Copy.VOLUME_CLOSED },
                            onSelect = { editOpen = it }
                        )
                        Spacer(Modifier.height(Gap.Item))
                    }
                    LiquidSwitchRow(Copy.VOLUME_INVERT, Copy.VOLUME_INVERT_DESC, invert, on) {
                        invert = it
                        VolumeRotate.setInvert(context, it, editOpen)
                    }
                    Spacer(Modifier.height(Gap.Section))
                    LiquidSwitchRow(Copy.VOLUME_CAMERA, Copy.VOLUME_CAMERA_DESC, camera, on) {
                        camera = it
                        VolumeRotate.setCameraExempt(context, it, editOpen)
                    }
                    Spacer(Modifier.height(Gap.Section))
                    // 아래 줄은 지금 화면(접힘·펼침) 설정 기준
                    val swap = VolumeRotate.swapFor(rotation, VolumeRotate.invert(context))
                    Text(
                        "지금 화면: ${VolumeRotate.rotationLabel(rotation)} · " + if (!on) {
                            "꺼져 있음"
                        } else if (swap) {
                            "올리기·내리기를 바꿈"
                        } else {
                            "그대로"
                        },
                        color = c.ink,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 볼륨 버튼 회전 그림(시안 10 '늘어나는 방울'): 폰이 왼쪽으로 돌아 버튼이 위로 가는 가로가 되면, 위·아래에 있던
 * 올리기(+, 잉크)·내리기(−) 방울이 늘어났다 둥글어지며 오른쪽·왼쪽으로 흘러간다. 잠시 멈췄다가 세로로 돌아온다.
 */
@Composable
internal fun VolumeRotateArt() {
    val t = artLoop(3200)
    ArtCanvas(170.dp) { c, tm ->
        // 0~0.3 돌기, 0.3~0.6 머묾, 0.6~0.9 되돌기, 0.9~1 쉼(동작 줄이기면 t=1 → 가로로 멈춘 그림 대신 끝 장면)
        val k = when {
            t < 0.3f -> ease(t / 0.3f)
            t < 0.6f -> 1f
            t < 0.9f -> 1f - ease((t - 0.6f) / 0.3f)
            else -> 0f
        }.let { if (t >= 1f) 1f else it }
        val center = Offset(size.width / 2, size.height / 2)
        val ph = 120.dp.toPx()
        val pw = ph * 0.6f
        rotate(-90f * k, center) { phone(c, center, pw, ph) }
        val s = ease(((k - 0.25f) / 0.6f).coerceIn(0f, 1f))
        val gap = ph * 0.2f
        drop(c, tm, center + Offset(0f, -gap), center + Offset(gap * 1.15f, 0f), s, c.ink, "+", c.onInk)
        drop(c, tm, center + Offset(0f, gap), center + Offset(-gap * 1.15f, 0f), s, c.line, "−", c.ink)
    }
}

private fun DrawScope.phone(c: LiquidColors, center: Offset, w: Float, h: Float) {
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(c.surface, tl, Size(w, h), CornerRadius(h * 0.09f))
    drawRoundRect(c.ink, tl, Size(w, h), CornerRadius(h * 0.09f), style = Stroke(2.dp.toPx()))
    // 오른쪽 옆면 볼륨 버튼(위가 올리기)
    val bw = 3.dp.toPx()
    listOf(-0.3f, -0.12f).forEach { oy ->
        drawRoundRect(c.muted, Offset(center.x + w / 2 + 1.dp.toPx(), center.y + h * oy - h * 0.06f), Size(bw, h * 0.12f), CornerRadius(bw / 2))
    }
}

private fun DrawScope.drop(c: LiquidColors, tm: TextMeasurer, from: Offset, to: Offset, s: Float, color: Color, label: String, labelColor: Color) {
    val p = from + (to - from) * s
    val stretch = 1f + 0.6f * sin(PI * s).toFloat()
    val r = 14.dp.toPx()
    // 움직이는 방향으로 늘어난 타원(가로·세로 성분을 섞어 대각선 이동에도 자연스럽게)
    val dir = (to - from).let { d ->
        val l = d.getDistance().coerceAtLeast(1f)
        Offset(d.x / l, d.y / l)
    }
    val ang = atan2(dir.y, dir.x) * 180f / PI.toFloat()
    rotate(ang, p) {
        drawOval(color, Offset(p.x - r * stretch, p.y - r / stretch), Size(2 * r * stretch, 2 * r / stretch))
    }
    artLabel(tm, label, p, labelColor, sizeSp = 14, bold = true)
}

/** 사용 방법(점 세 개 메뉴 → 사용 방법). */
@Composable
internal fun VolumeRotateHelpSheet() {
    SheetColumn(title = "사용 방법") {
        HelpTopic("1. 하는 일", "화면을 돌려 볼륨 버튼이 위로 가면, 아이패드처럼 화면 기준으로 오른쪽이 올리기, 왼쪽이 내리기가 되도록 바꿉니다. 거꾸로 들었을 때도 위쪽이 올리기가 됩니다.") { VolumeRotateArt() }
        HelpTopic("2. 반대 방향으로", "버튼이 위로 가게 돌렸는데 왼쪽이 올리기가 되면 켜세요. 폴드 메인 화면처럼 기기마다 기본 방향이 달라 생깁니다. 켜면 반대쪽(오른쪽으로 돌린 가로)에서 올리기·내리기를 바꿉니다.")
        HelpTopic("3. 카메라에서는 그대로", "카메라 앱이 앞에 있을 때는 바꾸지 않아 볼륨 키 셔터를 그대로 쓸 수 있습니다.")
        HelpTopic("4. 권한과 접근성", "GOAT가 시스템 설정 권한으로 접근성 서비스를 직접 켭니다. 볼륨 키만 받고 화면 내용은 읽지 않습니다. 끄면 접근성 목록에서도 빠집니다.")
    }
}
