package com.labok.goat.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labok.goat.ui.art.artLoop
import com.labok.goat.ui.liquid.LocalLiquid
import kotlin.math.cos
import kotlin.math.sin

/**
 * 플렉스 모드 그림: 두께 있는 폴드를 비스듬히 내려다본 모습. 두 화면을 얇은 판(위·아래·옆면)으로 그려 두께감을 내고,
 * 모서리는 모두 둥글린다. 힌지가 천천히 접혔다 펴지고, 플렉스 범위(115° 아래)로 들어오면 아래 화면에 조작 패널이,
 * 위 화면에 영상이 스며들듯 켜진다.
 * [kkaddak]이면 까딱 그림: 다 편 채 살짝(145°) 접었다 곧바로 펴면 힌지에서 물결이 퍼지며 펼친 채로 플렉스 모드가 켜지고,
 * 한 번 더 까딱하면 꺼진다.
 */
@Composable
internal fun FlexPanelArt(kkaddak: Boolean = false) {
    val c = LocalLiquid.current
    val t = artLoop(if (kkaddak) 4400 else 3200)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

        // 까딱 한 번: [start]부터 0.1 동안 145°까지 접고, 다음 0.1 동안 다시 편다
        fun dip(start: Float) = when {
            t < start || t > start + 0.2f -> 0f
            t < start + 0.1f -> ease((t - start) / 0.1f)
            else -> 1f - ease((t - start - 0.1f) / 0.1f)
        }
        val theta = if (kkaddak) {
            180f - 35f * (dip(0.1f) + dip(0.6f))
        } else {
            val p = (1 - cos(t * 2 * Math.PI).toFloat()) / 2
            180f - 80f * (p * p * (3 - 2 * p)) // 180° 펼침 → 100°
        }
        // 까딱 그림의 켜짐 정도: 첫 까딱을 마치면 켜지고(0.3~0.4), 두 번째 까딱을 마치면 꺼진다(0.8~0.88)
        val kOn = if (kkaddak) ease((t - 0.3f) / 0.1f) * (1f - ease((t - 0.8f) / 0.08f)) else 0f
        val sc = size.height / 220f * 170f
        val ox = size.width / 2
        val oy = size.height * 150f / 220f
        val yaw = Math.toRadians(-35.0)
        val pitch = Math.toRadians(28.0)
        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()

        // 3차원 점(x 가로, y 위, z 앞쪽) → 화면 좌표
        fun pt(v: FloatArray) = Offset(
            ox + (v[0] * cy + v[2] * sy) * sc,
            oy - (v[1] * cp - (-v[0] * sy + v[2] * cy) * sp) * sc
        )
        fun add(a: FloatArray, b: FloatArray, m: Float = 1f) = floatArrayOf(a[0] + b[0] * m, a[1] + b[1] * m, a[2] + b[2] * m)

        // 모서리를 둥글린 다각형: 각 꼭짓점 앞뒤로 r만큼 물러난 곳을 곡선으로 잇는다(변이 짧으면 반만)
        fun round(q: List<Offset>, r: Float) = Path().apply {
            val n = q.size
            for (i in 0 until n) {
                val prev = q[(i + n - 1) % n]
                val cur = q[i]
                val next = q[(i + 1) % n]
                val d1 = (prev - cur).getDistance()
                val d2 = (next - cur).getDistance()
                val a = cur + (prev - cur) * (minOf(r, d1 / 2) / d1.coerceAtLeast(0.01f))
                val b = cur + (next - cur) * (minOf(r, d2 / 2) / d2.coerceAtLeast(0.01f))
                if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                quadraticTo(cur.x, cur.y, b.x, b.y)
            }
            close()
        }

        // q = [힌지 왼쪽, 힌지 오른쪽, 먼 오른쪽, 먼 왼쪽] 안의 (u: 가로, v: 힌지→먼 쪽) 지점
        fun at(q: List<Offset>, u: Float, v: Float): Offset {
            val a = q[0] + (q[1] - q[0]) * u
            val b = q[3] + (q[2] - q[3]) * u
            return a + (b - a) * v
        }

        val w = 0.5f
        val h = 0.62f
        val d = 0.06f // 판 두께
        val r = Math.toRadians(180.0 - theta)
        val up = floatArrayOf(0f, sin(r).toFloat(), -cos(r).toFloat()) // 위 화면이 힌지에서 뻗는 쪽
        val front = floatArrayOf(0f, cos(r).toFloat(), sin(r).toFloat()) // 위 화면 앞면(화면)이 보는 쪽
        val radius = 5.dp.toPx()
        val edge = lerp(c.ink, c.surface, 0.55f) // 옆면: 앞면과 색이 달라 두께가 보인다
        val edgeTop = lerp(c.ink, c.surface, 0.55f) // 위 화면 옆면: 검은 앞면과 또렷이 갈린다
        val outline = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round)

        // 볼록 껍질(시계 방향 무관): 판의 앞뒤 여덟 점을 감싸는 윤곽. 이걸 옆면 색으로 칠하면 두께가 한 덩어리로 보인다
        fun hull(ps: List<Offset>): List<Offset> {
            val sorted = ps.sortedWith(compareBy({ it.x }, { it.y }))
            fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
            val lower = mutableListOf<Offset>()
            for (p in sorted) {
                while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
                lower.add(p)
            }
            val upper = mutableListOf<Offset>()
            for (p in sorted.asReversed()) {
                while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
                upper.add(p)
            }
            return lower.dropLast(1) + upper.dropLast(1)
        }

        // 판 하나: 앞면과 두께만큼 물러난 뒷면을 감싼 몸통을 옆면 색으로 칠하고 윤곽을 그린 뒤, 앞면(화면)을 얹는다
        fun slab(face: List<FloatArray>, normal: FloatArray, side: Color, top: Color): List<Offset> {
            val q = face.map(::pt)
            val body = round(hull(q + face.map { pt(add(it, normal, -d)) }), radius)
            drawPath(body, side)
            drawPath(body, c.ink, style = outline)
            drawPath(round(q, radius), top)
            drawPath(round(q, radius), c.ink, style = outline)
            return q
        }

        val hingeL = floatArrayOf(-w, 0f, 0f)
        val hingeR = floatArrayOf(w, 0f, 0f)
        val topFace = listOf(hingeL, hingeR, add(hingeR, up, h), add(hingeL, up, h))
        val bottomFace = listOf(hingeL, hingeR, floatArrayOf(w, 0f, h), floatArrayOf(-w, 0f, h))
        // 위 화면(뒤쪽)을 먼저, 앞으로 나온 아래 화면을 나중에 그린다
        val top = slab(topFace, front, edgeTop, c.ink)
        val bottom = slab(bottomFace, floatArrayOf(0f, 1f, 0f), edge, c.surface)
        // 둥근 힌지: 두 판이 만나는 곳을 굵고 둥근 선으로 감싼다
        drawLine(c.ink, bottom[0], bottom[1], 3.6.dp.toPx(), StrokeCap.Round)

        // 까딱을 마친 순간 힌지에서 물결이 한 번 퍼진다(손놀림을 알아챘다는 표시)
        if (kkaddak) {
            for (at0 in listOf(0.3f, 0.8f)) {
                val q = (t - at0) / 0.12f
                if (q in 0f..1f) {
                    val mid = (bottom[0] + bottom[1]) / 2f
                    drawCircle(c.ink.copy(alpha = 0.5f * (1f - q)), (8f + 46f * q).dp.toPx(), mid, style = Stroke(2.dp.toPx()))
                }
            }
        }

        // 플렉스 범위에 들어오면 패널과 영상이 서서히 켜진다(125°→110°). 까딱 그림은 펼친 채 까딱으로 켜고 끈다
        val on = if (kkaddak) kOn else ((125f - theta) / 15f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        if (on > 0f) {
            val pad = listOf(at(bottom, 0.2f, 0.25f), at(bottom, 0.8f, 0.25f), at(bottom, 0.8f, 0.8f), at(bottom, 0.2f, 0.8f))
            drawPath(round(pad, radius * 0.8f), c.line.copy(alpha = on))
            // 재생 삼각형도 위 화면 면 위에 놓아 화면과 함께 기운다(가로 u, 힌지→먼 쪽 v 좌표로 찍는다)
            val rr = 0.085f * (0.7f + 0.3f * on)
            val tri = listOf(
                at(top, 0.5f - rr * 0.7f, 0.55f + rr / h),
                at(top, 0.5f + rr, 0.55f),
                at(top, 0.5f - rr * 0.7f, 0.55f - rr / h)
            )
            drawPath(round(tri, 2.dp.toPx()), c.onInk.copy(alpha = on))
        }
    }
}
