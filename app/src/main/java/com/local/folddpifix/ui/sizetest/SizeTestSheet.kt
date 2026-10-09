package com.local.folddpifix.ui.sizetest

import androidx.compose.foundation.Canvas
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import android.app.Activity
import com.local.folddpifix.ui.liquid.LiquidSlider
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import com.local.folddpifix.ui.liquid.LiquidButton
import com.local.folddpifix.domain.ScreenGeometry
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.ui.components.InfoRow
import com.local.folddpifix.ui.home.UiState
import com.local.folddpifix.ui.liquid.GlassCard
import com.local.folddpifix.ui.liquid.LocalLiquid
import kotlin.math.abs

/** 기준 도형 한 변(dp). 런처 아이콘 크기와 같다. */
private const val REF_DP = 48

/**
 * 크기 테스트: 기기를 접고 펼치며 같은 dp 도형·글자가 두 화면에서 같은 실제 크기로 보이는지 확인한다.
 * - 줄 수 비교(가장 중요): 폴드는 두 화면의 실제 높이가 거의 같아서, 크기가 맞으면 한 화면에 세로로 들어가는
 *   줄 수도 같다. 두 화면의 예상 줄 수를 보여 주고, 전체 화면 줄 세기 화면에서 마지막 번호로 직접 비교한다
 * - 기준 도형: 48dp 아이콘 줄과 글자 샘플(같은 dp·sp라 맞춰진 상태면 두 화면에서 크기가 같다)
 * - 예상 실측: 두 화면의 DPI·PPI로 48dp가 몇 mm인지 계산해 차이(%)를 보여 준다
 * - 자: 현재 화면의 PPI로 그린 mm 눈금. 실제 자를 대어 기준 도형 크기를 직접 잴 수 있다
 */
@Composable
internal fun SizeTestSheet(state: UiState, onCount: () -> Unit, onCalibrate: (String, Float?) -> Unit) {
    val c = LocalLiquid.current
    val panels = state.plan?.panels.orEmpty()
    val screens = panels.map { it.screen }
    val outer = ScreenPolicy.screenFor(screens, ScreenPolicy.Role.OUTER)?.let { s -> panels.first { it.screen.key == s.key } }
    val inner = ScreenPolicy.screenFor(screens, ScreenPolicy.Role.INNER)?.let { s -> panels.first { it.screen.key == s.key } }
    val outerDpi = state.target
    val innerDpi = inner?.let { state.plan?.targetFor(it.screen)?.dpi }
    val current = panels.find { it.screen.key == state.activeScreen?.key }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
    ) {
        Text("크기 테스트", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Text(
            "이 화면을 연 채로 기기를 접고 펼쳐 아래 기준 도형과 글자가 두 화면에서 같은 크기로 보이는지 확인합니다.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))

        GlassCard(padding = 16.dp, accent = c.ink) {
            Text("세로 줄 수 비교", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                "두 화면은 실제 높이가 거의 같아서, 크기가 맞으면 한 화면에 들어가는 줄 수도 같습니다. 가장 구분하기 쉬운 방법입니다.",
                color = c.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            val cover = outer?.screen?.key
            val outerRows = if (outer != null && outerDpi != null) ScreenGeometry.orientedSize(outer.screen.key, cover, false)?.let { ScreenGeometry.rowsFor(it.second, outerDpi) } else null
            val innerRows = if (inner != null && innerDpi != null) ScreenGeometry.orientedSize(inner.screen.key, cover, true)?.let { ScreenGeometry.rowsFor(it.second, innerDpi) } else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RowsFigure("외부 화면", outerRows)
                RowsFigure("내부 화면", innerRows)
            }
            if (outerRows != null && innerRows != null) {
                val diff = (innerRows - outerRows) / outerRows * 100f
                Spacer(Modifier.height(6.dp))
                Text(
                    "차이 %+.1f%% · %s".format(diff, if (abs(diff) < 2f) "일치" else if (diff > 0) "내부 화면이 더 작게 표시됨(보정 + 방향)" else "내부 화면이 더 크게 표시됨(보정 − 방향)"),
                    color = if (abs(diff) < 2f) c.ink else c.danger, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(12.dp))
            LiquidButton("전체 화면으로 줄 수 보기", modifier = Modifier.fillMaxWidth(), onClick = onCount)
            Text(
                "전체 화면에 번호 매긴 줄을 채웁니다. 이 상태로 접고 펼쳐서 두 화면의 마지막 번호를 비교해 보세요.",
                color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        GlassCard(padding = 16.dp) {
            Text("기준 도형", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text("한 변 ${REF_DP}dp(앱 아이콘 크기)", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Canvas(Modifier.size(REF_DP.dp)) {
                        val r = CornerRadius(size.minDimension * 0.28f)
                        drawRoundRect(c.ink.copy(alpha = 0.85f - i * 0.15f), cornerRadius = r)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            listOf(14, 18, 24).forEach { sp ->
                Text("가나다 Aa 123 · ${sp}sp", fontSize = sp.sp, color = c.ink)
            }
        }
        Spacer(Modifier.height(10.dp))

        GlassCard(padding = 16.dp) {
            Text("예상 실측 크기", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text("${REF_DP}dp 기준 도형이 각 화면에서 차지하는 실제 길이입니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            val outerMm = if (outer != null && outerDpi != null) ScreenGeometry.dpToMm(REF_DP, outerDpi, state.rulerPpi[outer.screen.key] ?: outer.screen.ppi) else null
            val innerMm = if (inner != null && innerDpi != null) ScreenGeometry.dpToMm(REF_DP, innerDpi, state.rulerPpi[inner.screen.key] ?: inner.screen.ppi) else null
            InfoRow("외부 화면", outerMm?.let { "%.2f mm · %d dpi · %.0f ppi".format(it, outerDpi, outer!!.screen.ppi) } ?: "정보 없음")
            InfoRow("내부 화면", innerMm?.let { "%.2f mm · %d dpi · %.0f ppi".format(it, innerDpi, inner!!.screen.ppi) } ?: "정보 없음")
            if (outerMm != null && innerMm != null) {
                val diff = (innerMm - outerMm) / outerMm * 100f
                val ok = abs(diff) < 2f
                InfoRow("차이", "%+.1f%% · %s".format(diff, if (ok) "일치" else "불일치"), emphasize = true)
                if (!ok) Text(
                    if (diff > 0) "내부 화면이 더 크게 표시됩니다. 내부 화면 보정을 − 방향으로 조정하세요."
                    else "내부 화면이 더 작게 표시됩니다. 내부 화면 보정을 + 방향으로 조정하세요.",
                    color = c.danger, style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("기기를 한 번 접었다 펼치면 두 화면을 비교할 수 있습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))

        RulerCard(state, current, onCalibrate)
    }
}

/**
 * 실제 길이 확인: 기기가 알려 주는 PPI는 실제 패널과 다를 수 있어서, 카드(짧은 변 54 mm)를 대고
 * 칸 폭을 실물에 맞추면 그 화면의 실제 PPI를 얻는다. 저장한 값은 mm 눈금과 예상 실측에만 쓴다(DPI 계산에는 쓰지 않음).
 */
@Composable
private fun RulerCard(state: UiState, current: com.local.folddpifix.domain.PanelSpec.Resolved?, onCalibrate: (String, Float?) -> Unit) {
    val c = LocalLiquid.current
    GlassCard(padding = 16.dp) {
        Text("실제 길이 확인", fontWeight = FontWeight.SemiBold, color = c.ink)
        if (current == null) {
            Text("현재 화면 정보를 아직 수집하지 못했습니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            return@GlassCard
        }
        val key = current.screen.key
        val saved = state.rulerPpi[key]
        val base = saved ?: current.screen.ppi
        var pct by remember(key, saved) { mutableIntStateOf(0) }
        val ppi = ScreenGeometry.calibratedPpi(base, 1f + pct / 100f)
        Text(
            "신용카드나 신분증의 짧은 변(54 mm)을 아래 칸에 대고, 칸의 양 끝이 카드 모서리와 꼭 맞도록 조절하세요. 맞추면 아래 mm 눈금이 실제 길이와 같아집니다.",
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        CardEdge(ppi)
        LiquidSlider(
            value = pct, range = -25..25,
            onValueChange = { pct = it }, onValueChangeFinished = {},
            valueText = { if (it > 0) "+$it%" else "$it%" },
            description = "카드 폭 맞추기", centered = true,
        )
        Text(
            "기준 PPI %.0f · %s".format(ppi, if (saved != null && pct == 0) "카드로 맞춘 값" else if (pct == 0) "기기가 알려 준 값" else "조절 중"),
            color = c.muted, style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidButton("이 폭으로 저장", modifier = Modifier.weight(1f), enabled = pct != 0, onClick = { onCalibrate(key, ppi) })
            LiquidButton("기기 값으로", modifier = Modifier.weight(1f), primary = false, enabled = saved != null, onClick = { onCalibrate(key, null) })
        }
        Spacer(Modifier.height(14.dp))
        val px = with(LocalDensity.current) { REF_DP.dp.toPx() }
        Text(
            "아래 상자는 ${REF_DP}dp로, 이 화면에서 약 %.1f mm입니다.".format(px / ppi * 25.4f),
            color = c.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Ruler(ppi = ppi, boxPx = px)
    }
}

/** 카드 짧은 변(54 mm) 칸: 양 끝 세로선 사이가 [ppi] 기준 54 mm. */
@Composable
private fun CardEdge(ppi: Float) {
    val c = LocalLiquid.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelMedium.copy(color = c.ink)
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val w = ScreenGeometry.mmToPx(ScreenGeometry.CARD_SHORT_MM, ppi)
        val left = 4.dp.toPx()
        val r = CornerRadius(ScreenGeometry.mmToPx(3.18f, ppi))
        drawRoundRect(c.ink.copy(alpha = 0.07f), Offset(left, 0f), Size(w, size.height), r)
        drawRoundRect(c.ink, Offset(left, 0f), Size(w, size.height), r, style = Stroke(1.5.dp.toPx()))
        val t = measurer.measure("54 mm", label)
        drawText(t, topLeft = Offset(left + (w - t.size.width) / 2f, (size.height - t.size.height) / 2f))
    }
}

/** 현재 화면 PPI로 그린 mm 자와 그 위에 놓인 48dp 상자. 0 눈금과 상자 왼쪽을 맞춘다. */
@Composable
private fun Ruler(ppi: Float, boxPx: Float) {
    val c = LocalLiquid.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = c.muted)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val pxPerMm = ppi / 25.4f
        val left = 4.dp.toPx()
        // 48dp 상자
        drawRoundRect(c.ink.copy(alpha = 0.12f), Offset(left, 0f), Size(boxPx, boxPx), CornerRadius(3.dp.toPx()))
        drawRoundRect(c.ink, Offset(left, 0f), Size(boxPx, boxPx), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
        // 자
        val base = boxPx + 10.dp.toPx()
        val mmCount = ((size.width - left * 2) / pxPerMm).toInt()
        drawLine(c.ink, Offset(left, base), Offset(left + mmCount * pxPerMm, base), 1.dp.toPx())
        for (mm in 0..mmCount) {
            val x = left + mm * pxPerMm
            val len = when {
                mm % 10 == 0 -> 14.dp.toPx()
                mm % 5 == 0 -> 9.dp.toPx()
                else -> 5.dp.toPx()
            }
            drawLine(c.ink, Offset(x, base), Offset(x, base + len), if (mm % 10 == 0) 1.5.dp.toPx() else 1.dp.toPx())
            if (mm % 10 == 0) {
                val t = measurer.measure(if (mm == 0) "0 cm" else "${mm / 10}", label)
                drawText(t, topLeft = Offset(if (mm == 0) x else x - t.size.width / 2f, base + 16.dp.toPx()))
            }
        }
    }
}

@Composable
private fun RowsFigure(label: String, rows: Float?) {
    val c = LocalLiquid.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rows?.let { "%.1f".format(it) } ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text("$label · 줄", color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 전체 화면 줄 세기: 48dp 줄에 번호를 매겨 화면 끝까지 채운다. 오른쪽 위에는 이 화면에 들어가는 줄 수.
 * 열어 둔 채 접고 펼치면 두 화면의 마지막 번호를 바로 비교할 수 있다.
 * 별도 창(Dialog)은 접고 펼칠 때 크기를 따라가지 않아서, 앱 화면 위에 덮는 층으로 그린다.
 */
@Composable
internal fun RowCountOverlay(onClose: () -> Unit) {
    val c = LocalLiquid.current
    BackHandler(onBack = onClose)
    // 줄 세기 동안에는 상태 표시줄·탐색 막대를 숨겨 화면 전체를 줄로 채운다(줄을 가리지 않게).
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // 줄 수는 실제로 그려진 크기(화면 높이 px ÷ 첫 줄 높이 px)로 센다. dp로 바꿔 계산하면 접고 펼 때
    // 화면 크기와 밀도가 따로 바뀌는 순간 이전 화면의 밀도로 계산돼 틀린 값이 나온다(실기기 19.4줄 표시 오류의 원인).
    var boxPx by remember { mutableIntStateOf(0) }
    var rowPx by remember { mutableIntStateOf(0) }
    val rows = if (boxPx > 0 && rowPx > 0) boxPx.toFloat() / rowPx else 0f
    run {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .onSizeChanged { boxPx = it.height }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.fillMaxSize()) {
                repeat(if (rows > 0f) rows.toInt() + 2 else 40) { i ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ScreenGeometry.ROW_DP.dp)
                            .then(if (i == 0) Modifier.onSizeChanged { rowPx = it.height } else Modifier)
                            .background(if (i % 2 == 0) c.surface else c.bg)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.width(48.dp))
                        Text("가나다라 Aa 123", fontSize = 16.sp, color = c.ink)
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.ink)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("이 화면 %.1f줄".format(rows), color = c.onInk, fontWeight = FontWeight.Bold)
                Text("눌러서 닫기", color = c.onInk.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
