package com.labok.goat.ui.tweaks

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.art.ArtCanvas
import com.labok.goat.ui.art.LocalDeviceShape
import com.labok.goat.ui.art.artLabel
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.art.artPill
import com.labok.goat.ui.art.deviceFrame
import com.labok.goat.ui.liquid.LiquidColors

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

/**
 * 개인 DNS 그림: 두 폰(외부 화면 비율)에서 같은 웹 페이지가 위에서부터 열린다. 왼쪽(기본 DNS)은 광고 칸 둘이 끼어들고,
 * 오른쪽(고른 서버)은 [blocking]이면 광고 칸이 '차단'으로 바뀌었다가 접혀 사라지며 글이 위로 올라붙는다.
 * 광고를 막지 않는 서버면 오른쪽도 광고가 그대로 남는다. 아래 알약에 보인 광고 수를 적는다.
 */
@Composable
internal fun DnsArt(blocking: Boolean, label: String) {
    val t = artLoop(3200)
    val aspect = LocalDeviceShape.current.cover
    ArtCanvas(176.dp) { c, tm ->
        val h = 116.dp.toPx(); val w = h * aspect
        val top = 10.dp.toPx()
        listOf(0.3f to false, 0.7f to blocking).forEachIndexed { i, (fx, block) ->
            val tl = Offset(size.width * fx - w / 2, top)
            val screen = deviceFrame(c, tl, Size(w, h), emphasis = if (i == 1) 1f else 0.6f)
            page(c, tm, screen, t, block)
            val shown = if (block) 0 else 2
            artPill(tm, c, if (shown == 0) "광고 없음" else "광고 ${shown}개", Offset(screen.center.x, top + h + 16.dp.toPx()), filled = i == 1)
            artLabel(tm, if (i == 0) "기본 DNS" else label, Offset(screen.center.x, top + h + 36.dp.toPx()), if (i == 1) c.ink else c.muted, bold = i == 1)
        }
    }
}

/**
 * 웹 페이지 한 장: 주소창, 제목, 글줄, 광고 칸 둘. 글줄은 0.05~0.3에 차례로 나타나고 광고는 0.32에 끼어든다.
 * [block]이면 광고 칸이 0.45~0.55에 '차단' 표시로 바뀐 뒤 0.6~0.75에 높이가 0으로 접힌다.
 */
private fun DrawScope.page(c: LiquidColors, tm: TextMeasurer, screen: Rect, t: Float, block: Boolean) {
    val pad = screen.width * 0.1f
    val inner = screen.width - pad * 2
    val lineH = screen.height * 0.035f
    val gap = screen.height * 0.035f
    val adFull = screen.height * 0.17f
    val adIn = ease(seg(t, 0.32f, 0.42f))
    val fold = if (block) ease(seg(t, 0.6f, 0.75f)) else 0f
    val adH = adFull * adIn * (1 - fold)
    clipRect(screen.left, screen.top, screen.right, screen.bottom) {
        // 주소창
        drawRoundRect(c.line, Offset(screen.left + pad, screen.top + screen.height * 0.06f), Size(inner, screen.height * 0.06f), CornerRadius(screen.height * 0.03f))
        var y = screen.top + screen.height * 0.17f
        // 글줄 블록 셋 사이에 광고 둘: 글 → 광고 → 글 → 광고 → 글
        val blocks = listOf(2, 3, 2)
        var n = 0
        blocks.forEachIndexed { b, lines ->
            repeat(lines) { k ->
                val appear = ease(seg(t, 0.05f + n * 0.035f, 0.12f + n * 0.035f))
                val len = if (k == lines - 1) 0.62f else 1f
                if (appear > 0f) drawRoundRect(c.muted.copy(alpha = 0.55f * appear), Offset(screen.left + pad, y), Size(inner * len, lineH), CornerRadius(lineH / 2))
                y += lineH + gap * 0.6f
                n++
            }
            if (b < blocks.size - 1) {
                if (adH > 0.5f) ad(c, tm, Rect(screen.left + pad, y + gap * 0.3f, screen.right - pad, y + gap * 0.3f + adH), if (block) seg(t, 0.45f, 0.55f) else 0f)
                y += gap * 0.6f + adH
            }
        }
    }
}

/** 광고 칸: 옅은 칸과 테두리, 'AD'. [blocked] 0→1이면 사선이 그어지고 '차단'으로 바뀐다. */
private fun DrawScope.ad(c: LiquidColors, tm: TextMeasurer, r: Rect, blocked: Float) {
    val corner = CornerRadius(3.dp.toPx())
    drawRoundRect(c.ink.copy(alpha = 0.12f + 0.1f * (1 - blocked)), r.topLeft, r.size, corner)
    drawRoundRect(c.ink.copy(alpha = 0.5f), r.topLeft, r.size, corner, style = Stroke(1.dp.toPx()))
    if (r.height < 10.dp.toPx()) return
    // 사선이 끝까지 그어지면 사선은 지우고 '차단' 글자만 남긴다(글자와 겹치지 않게)
    if (blocked > 0f && blocked < 1f) {
        val s = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        val end = Offset(r.left + (r.right - r.left) * blocked, r.top + (r.bottom - r.top) * blocked)
        drawLine(c.ink, r.topLeft, end, s.width, StrokeCap.Round)
    }
    artLabel(tm, if (blocked >= 1f) "차단" else "AD", r.center, c.ink, sizeSp = 9, bold = true)
}
