package com.labok.goat.data.lab

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * 텐트 모드 앰비언트(반사광) 화면. 안쪽 화면의 한쪽 절반(기본 왼쪽, 책상·벽을 비추는 면)에만 빛을 그리고 나머지 절반은 검게 둔다
 * (11-02 소장님: 반사광이라 왼쪽에만, 오른쪽은 실제로 살짝 보이는 화면).
 *
 * 텐트(ㅅ)로 세우면 안쪽 화면의 가운데 접는 선이 꼭대기가 된다. 그래서 바깥 화면 가장자리 색을 이렇게 옮긴다:
 * - 바깥 위쪽 → 접는 선 쪽(빛 절반의 가운데 쪽 가장자리), 바깥 아래쪽 → 책상 쪽(바깥 가장자리)
 * - 바깥 왼쪽·오른쪽 → 텐트 양 옆 끝(빛 절반의 위·아래 끝). 안쪽 면은 반대를 보고 있어 좌우를 뒤집는다.
 * 실제 방향은 실기 '방향 확인'으로 맞춘다([swap]: 빛을 오른쪽 절반으로, [mirror]: 양 옆 끝을 뒤집기).
 * 색은 바로 바꾸지 않고 매 프레임 목표 색으로 조금씩 다가가 부드럽게 넘어간다.
 */
class TentAmbientView(context: Context, private val swap: Boolean, private val mirror: Boolean) : View(context) {
    private class Spot(var x: Float, var y: Float) {
        val cur = floatArrayOf(0f, 0f, 0f)
        val tgt = floatArrayOf(0f, 0f, 0f)
    }

    private var spots: List<Spot> = emptyList()
    private var top = 0
    private var bottom = 0
    private var side = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }

    init {
        setBackgroundColor(Color.BLACK)
    }

    /** 바깥 화면 구역 색을 새 목표로 정한다(아무 스레드에서나). */
    fun setZones(z: TentMode.Zones) = post {
        if (z.top.size != top || z.left.size != side) layoutSpots(z.top.size, z.left.size)
        val all = z.top.toList() + z.bottom.toList() + z.left.toList() + z.right.toList()
        spots.forEachIndexed { i, s ->
            val c = all.getOrNull(i) ?: return@forEachIndexed
            s.tgt[0] = Color.red(c).toFloat()
            s.tgt[1] = Color.green(c).toFloat()
            s.tgt[2] = Color.blue(c).toFloat()
        }
        postInvalidateOnAnimation()
    }

    /** 한 가지 색으로 고르게 비춘다(시계만 띄웠을 때의 따뜻한 무드등 등). */
    fun setSolid(color: Int) = post {
        if (spots.isEmpty()) layoutSpots(8, 4)
        spots.forEach { s ->
            s.tgt[0] = Color.red(color).toFloat()
            s.tgt[1] = Color.green(color).toFloat()
            s.tgt[2] = Color.blue(color).toFloat()
        }
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (top > 0) layoutSpots(top, side)
    }

    /** 구역마다 빛 점의 자리를 정한다(위 [n]개·아래 [n]개·양 옆 [m]개씩). */
    private fun layoutSpots(n: Int, m: Int) {
        top = n
        bottom = n
        side = m
        val w = width.toFloat().takeIf { it > 0 } ?: 2448f
        val h = height.toFloat().takeIf { it > 0 } ?: 1848f
        val half = w / 2
        val hinge = half
        val desk = if (swap) w else 0f
        val inset = half * 0.08f
        fun along(t: Float) = if (mirror) t * h else (1 - t) * h
        val hingeX = if (swap) hinge + inset else hinge - inset
        val deskX = if (swap) desk - inset else desk + inset
        val list = ArrayList<Spot>()
        for (i in 0 until n) list += Spot(hingeX, along((i + 0.5f) / n))
        for (i in 0 until n) list += Spot(deskX, along((i + 0.5f) / n))
        // 바깥 왼쪽·오른쪽 띠는 위(접는 선)→아래(책상) 순서라, 빛 절반 안에서 접는 선→책상 방향으로 놓는다
        for (j in 0 until m) list += Spot(hingeX + (deskX - hingeX) * (j + 0.5f) / m, if (mirror) h * 0.06f else h * 0.94f)
        for (j in 0 until m) list += Spot(hingeX + (deskX - hingeX) * (j + 0.5f) / m, if (mirror) h * 0.94f else h * 0.06f)
        // 기존 색을 이어받아 자리만 바꾼다
        val old = spots
        list.forEachIndexed { i, s ->
            old.getOrNull(i)?.let { o ->
                o.cur.copyInto(s.cur)
                o.tgt.copyInto(s.tgt)
            }
        }
        spots = list
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val half = w / 2
        canvas.save()
        if (swap) canvas.clipRect(half, 0f, w, h) else canvas.clipRect(0f, 0f, half, h)
        val r = minOf(half, h) * 0.55f
        var moving = false
        for (s in spots) {
            for (k in 0..2) {
                val d = s.tgt[k] - s.cur[k]
                if (kotlin.math.abs(d) > 0.5f) moving = true
                s.cur[k] += d * 0.12f
            }
            // 반사광답게 조금 밝게(1.25배) 하되 색은 그대로
            val c = Color.rgb((s.cur[0] * 1.25f).toInt().coerceAtMost(255), (s.cur[1] * 1.25f).toInt().coerceAtMost(255), (s.cur[2] * 1.25f).toInt().coerceAtMost(255))
            paint.shader = RadialGradient(s.x, s.y, r, intArrayOf(c, c and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(s.x, s.y, r, paint)
        }
        canvas.restore()
        if (moving) postInvalidateOnAnimation()
    }
}
