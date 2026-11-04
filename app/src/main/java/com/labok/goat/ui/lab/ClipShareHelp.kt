package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.HelpTopic
import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.liquid.GalaxyFont
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LiquidColors
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.nav.CLIP_SHARE_PATHS
import com.labok.goat.ui.text.Copy

/*
 * 클립보드 공유 사용 방법(점 세 개 메뉴 → 사용 방법). 11-04 소장님 지정 시안 5번 '물방울 타임라인':
 * 세로 줄을 따라 물방울 마디가 내려가고, 단계마다 작은 그림(타일·복사·알림창·앱 고르기)이 붙는다.
 * 그림 좌표는 dp 단위로 시안(HTML)과 같게 두었다. 누르는 자리의 물결만 반복해서 움직인다.
 */

/** 알림창 바탕(다크·라이트 테마와 상관없이 알림창처럼 어둡게). */
private val SHADE = Color(0xFF26252A)
private val SHADE_BAR = Color(0xFF5A5960)
private val SHADE_TILE_OFF = Color(0xFF46454B)

@Composable
internal fun ClipShareHelpSheet() {
    SheetColumn(title = "사용 방법") {
        GlassCard {
            val tap = artLoop(1600)
            TimelineStep("1", "빠른 설정에 타일 넣기", "[${Copy.LAB_NOTE_ADD_TILE}]를 누릅니다.", 34) { c, tm -> tilePill(c, tm, Offset(0f, 4f), 150f) }
            TimelineStep("2", "아무 앱에서 복사", "글·사진·여러 장 모두 됩니다.", 70) { c, tm ->
                copyScene(c, tm, Offset(0f, 6f), tap)
                photo(c, Offset(116f, 22f), 30f)
            }
            TimelineStep("3", "알림창을 내려 타일 누르기", "GOAT를 열지 않아도 됩니다.", 70) { c, tm -> shadeScene(c, tm, Offset(0f, 4f), tap) }
            TimelineStep("4", "보낼 앱과 대화방 고르기", "글은 TXT, 사진은 원본으로 갑니다.", 70) { c, tm -> pickScene(c, tm, Offset(0f, 4f), tap) }
            TimelineStep(null, "임시 파일은 바로 지웁니다", "휴대폰 밖으로 보내지 않습니다.", 0, last = true)
        }
        Spacer(Modifier.height(Gap.Card))
        HelpTopic(
            "다른 방법으로 열기",
            "• GOAT 아이콘 길게 누르기: '클립보드 공유' 바로가기가 나옵니다. 끌어서 홈 화면에 둘 수 있습니다.\n" +
                "• 다른 앱의 공유 메뉴: 'TXT로 공유'를 고르면 글을 TXT 파일로 보냅니다.\n" +
                "• 이 화면: [${Copy.LAB_NOTE_PASTE_SHARE}]를 누르거나 아래 입력란에 붙여넣습니다."
        )
        HelpTopic(
            "보낼 수 있는 것",
            "• 글: TXT 파일. 서식은 빠집니다\n" +
                "• 사진(갤러리·브라우저의 '이미지 복사'): 원본 이미지\n" +
                "• 한꺼번에 복사한 여러 장: 모두 한 번에\n" +
                "• 파일 앱에서 복사한 파일: 원본 파일"
        )
        HelpTopic(
            "알아 둘 점",
            "마지막에 복사한 것 하나만 보냅니다. 키보드 클립보드 목록의 예전 항목은 Android가 막아 읽을 수 없습니다. " +
                "공유할 때 뜨는 'GOAT이 클립보드에서 붙여넣음' 안내는 Android의 정상 알림입니다."
        )
    }
}

/**
 * 타임라인 한 마디: 왼쪽에 물방울(번호, [num]이 null이면 체크)과 세로 줄, 오른쪽에 제목·설명과 그림([artHeight]dp, 0이면 없음).
 * [last]면 줄을 물방울 아래로 잇지 않는다.
 */
@Composable
private fun TimelineStep(
    num: String?,
    title: String,
    body: String,
    artHeight: Int,
    last: Boolean = false,
    art: (DrawScope.(LiquidColors, TextMeasurer) -> Unit)? = null
) {
    val c = LocalLiquid.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        val tm = rememberTextMeasurer()
        Canvas(Modifier.width(36.dp).fillMaxHeight()) {
            val x = 13.dp.toPx()
            val r = 11.dp.toPx()
            val cy = r + 1.dp.toPx()
            if (!last) drawLine(c.line, Offset(x, cy), Offset(x, size.height), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(c.ink, r, Offset(x, cy))
            if (num != null) {
                artLabel(tm, num, Offset(x, cy), c.onInk, 12, bold = true)
            } else {
                val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(
                    Path().apply {
                        moveTo(x - 4.5.dp.toPx(), cy)
                        lineTo(x - 1.dp.toPx(), cy + 3.5.dp.toPx())
                        lineTo(x + 5.dp.toPx(), cy - 3.dp.toPx())
                    },
                    c.onInk,
                    style = s
                )
            }
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = c.ink, style = MaterialTheme.typography.bodyMedium)
            Text(body, color = c.muted, style = MaterialTheme.typography.bodySmall)
            if (art != null && artHeight > 0) {
                Spacer(Modifier.height(Gap.Tight))
                ArtCanvas(artHeight.dp) { cc, t -> art(cc, t) }
            }
        }
    }
}

/** 시안 좌표(dp)를 픽셀로. */
private fun DrawScope.px(v: Float) = v * density

private fun DrawScope.at(o: Offset, x: Float, y: Float) = Offset(px(o.x + x), px(o.y + y))

private fun DrawScope.box(color: Color, o: Offset, x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(color, at(o, x, y), Size(px(w), px(h)), CornerRadius(px(r)))

private fun DrawScope.frame(color: Color, o: Offset, x: Float, y: Float, w: Float, h: Float, r: Float, width: Float = 1f) = drawRoundRect(color, at(o, x, y), Size(px(w), px(h)), CornerRadius(px(r)), style = Stroke(px(width)))

/** 회색 글줄 [n]개(마지막 줄은 짧게). */
private fun DrawScope.textLines(c: LiquidColors, o: Offset, x: Float, y: Float, w: Float, n: Int) {
    repeat(n) { i -> box(c.line, o, x, y + i * 8f, if (i == n - 1) w * 0.6f else w, 4f, 2f) }
}

/** 누르는 자리: [t]에 따라 퍼졌다 사라지는 물결. */
private fun DrawScope.tapRipple(c: LiquidColors, center: Offset, r: Float, t: Float) {
    drawCircle(c.ink.copy(alpha = 0.12f * (1f - t)), px(r + 4f + 8f * t), center)
    drawCircle(c.ink.copy(alpha = 0.35f), px(r), center)
}

/** 클립보드 공유 아이콘(메뉴·타일과 같은 그림). */
private fun DrawScope.clipIcon(color: Color, topLeft: Offset, sizeDp: Float) {
    val k = px(sizeDp) / 24f
    val m = Matrix().apply {
        translate(topLeft.x, topLeft.y)
        scale(k, k)
    }
    val s = Stroke(1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    CLIP_SHARE_PATHS.forEach { drawPath(PathParser().parsePathString(it).toPath().apply { transform(m) }, color, style = s) }
    listOf(11f to 6f, 14.2f to 6f, 17.4f to 3.5f).forEach { (y, w) ->
        drawLine(color, Offset(topLeft.x + 8.5f * k, topLeft.y + y * k), Offset(topLeft.x + (8.5f + w) * k, topLeft.y + y * k), 1.5f * k, StrokeCap.Round)
    }
}

/** 빠른 설정의 '클립보드 공유' 타일. */
private fun DrawScope.tilePill(c: LiquidColors, tm: TextMeasurer, o: Offset, w: Float) {
    box(c.ink, o, 0f, 0f, w, 26f, 13f)
    clipIcon(c.onInk, at(o, 7f, 3f), 20f)
    val l = tm.measure("클립보드 공유", TextStyle(fontFamily = GalaxyFont, color = c.onInk, fontSize = (minOf(10.5f, (w - 38f) / 6.3f) / fontScale).sp, fontWeight = FontWeight.Bold))
    drawText(l, topLeft = at(o, 31f, 13f) - Offset(0f, l.size.height / 2f))
}

/** 사진 한 장(산과 해). */
private fun DrawScope.photo(c: LiquidColors, o: Offset, s: Float) {
    val k = s / 30f
    box(c.ink.copy(alpha = 0.35f), o, 0f, 0f, s, s, 5f * k)
    val p = Path().apply {
        moveTo(px(o.x + 3f * k), px(o.y + 25f * k))
        lineTo(px(o.x + 11f * k), px(o.y + 16f * k))
        lineTo(px(o.x + 17f * k), px(o.y + 22f * k))
        lineTo(px(o.x + 21f * k), px(o.y + 18f * k))
        lineTo(px(o.x + 28f * k), px(o.y + 25f * k))
        close()
    }
    drawPath(p, c.ink.copy(alpha = 0.85f))
    drawCircle(Color.White, px(3.2f * k), at(o, 22f * k, 9f * k))
}

/** TXT 문서 한 장. */
private fun DrawScope.txtDoc(c: LiquidColors, tm: TextMeasurer, o: Offset, k: Float) {
    val p = Path().apply {
        moveTo(px(o.x), px(o.y))
        lineTo(px(o.x + 18f * k), px(o.y))
        lineTo(px(o.x + 26f * k), px(o.y + 8f * k))
        lineTo(px(o.x + 26f * k), px(o.y + 34f * k))
        lineTo(px(o.x), px(o.y + 34f * k))
        close()
    }
    drawPath(p, c.surface)
    drawPath(p, c.ink, style = Stroke(px(1.6f), join = StrokeJoin.Round))
    artLabel(tm, "TXT", at(o, 13f * k, 22f * k), c.ink, 7, bold = true)
}

/** ① 글을 고르고 '복사'를 누르는 장면. */
private fun DrawScope.copyScene(c: LiquidColors, tm: TextMeasurer, o: Offset, t: Float) {
    box(c.surface, o, 0f, 14f, 96f, 40f, 6f)
    frame(c.line, o, 0f, 14f, 96f, 40f, 6f)
    textLines(c, o, 8f, 22f, 80f, 3)
    box(c.ink.copy(alpha = 0.22f), o, 8f, 20f, 60f, 12f, 2f)
    box(Color(0xFF3A3A3C), o, 18f, -4f, 40f, 16f, 8f)
    artLabel(tm, "복사", at(o, 38f, 4f), Color.White, 9, bold = true)
    tapRipple(c, at(o, 38f, 4f), 5f, t)
}

/** ② 알림창을 내려 타일을 누르는 장면. */
private fun DrawScope.shadeScene(c: LiquidColors, tm: TextMeasurer, o: Offset, t: Float) {
    box(SHADE, o, 0f, 0f, 120f, 62f, 10f)
    box(SHADE_BAR, o, 8f, 8f, 104f, 8f, 4f)
    tilePill(c, tm, Offset(o.x + 8f, o.y + 22f), 104f)
    box(SHADE_TILE_OFF, o, 8f, 52f, 50f, 5f, 2.5f)
    box(SHADE_TILE_OFF, o, 62f, 52f, 50f, 5f, 2.5f)
    tapRipple(c, at(o, 32f, 35f), 6f, t)
}

/** ③ 공유 창에서 보낼 앱을 고르는 장면. */
private fun DrawScope.pickScene(c: LiquidColors, tm: TextMeasurer, o: Offset, t: Float) {
    box(c.surface, o, 0f, 0f, 120f, 62f, 10f)
    frame(c.line, o, 0f, 0f, 120f, 62f, 10f)
    txtDoc(c, tm, Offset(o.x + 8f, o.y + 8f), 0.8f)
    listOf(c.ink, c.ink.copy(alpha = 0.4f), c.line).forEachIndexed { i, col -> drawCircle(col, px(8f), at(o, 52f + i * 22f, 22f)) }
    box(c.ink.copy(alpha = 0.12f), o, 8f, 44f, 104f, 10f, 5f)
    artLabel(tm, "보낼 앱 고르기", at(o, 60f, 49f), c.ink, 7, bold = true)
    tapRipple(c, at(o, 52f, 22f), 5f, t)
}
