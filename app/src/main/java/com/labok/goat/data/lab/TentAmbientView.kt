package com.labok.goat.data.lab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.Surface
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.pow

/**
 * 텐트 모드 앰비언트(반사광). 바깥 화면을 아주 작게 찍은 그림을 텐트 방향에 맞게 돌리고 뒤집어, 세게 흐려서 안쪽 화면의
 * 왼쪽 패널(펼쳐 세로로 들었을 때의 왼쪽 절반)에 비춘다(11-02 소장님 제안: 바깥 화면을 반전해 블러로 쏘기). 오른쪽 패널은 검게 둔다.
 * 구역 평균보다 화면의 색 배치가 그대로 살아나 방향마다 다채롭다.
 *
 * - 방향: 텐트(ㅅ)는 접는 선이 꼭대기라, 바깥 위 → 접는 선 쪽, 바깥 아래 → 책상 쪽, 바깥 왼쪽·오른쪽 → 패널 위·아래 끝(뒤집어서).
 *   [swap]은 빛을 오른쪽 패널로, [mirror]는 패널 위·아래를 뒤집는다(실기 방향 확인용).
 * - 회전: 안쪽 화면이 가로·세로로 돌아도 늘 같은 물리적 왼쪽 패널이 되게 화면 회전을 되돌려 '펼친 세로' 좌표로 그린다.
 * - 색(11-02 소장님: 거의 무조건 밝게): 채도 1.45배, 밝기는 50~92%로 끌어올려 화면 색만 따라가고,
 *   화면이 거의 검으면(시계 등) [mood] 무드등을 그대로 켠다. 프레임 사이는 0.4초 동안 이전 그림에서 새 그림으로 서서히 넘어간다.
 * - 경계(11-02 소장님: 딱 자르지 말고 번지게): 빛 패널 끝의 색을 접는 선 너머로 늘여 칠하고, 접는 선 앞뒤로 서서히 사라지게 가린다.
 * - [opening]이면 펼치는 동안의 간접등([AmbientOpen]): 바깥 화면(세로)을 텐트처럼 돌리지 않고 왼쪽 패널에 좌우만 뒤집어 비춘다
 *   (바깥 화면이 왼쪽 패널 뒷면이라 바깥 왼쪽=접는 선 쪽). 색은 어두운 곳도 깎지 않고 더 밝고 진하게 다듬는다. [level]은 전체 밝기(0~1).
 */
class TentAmbientView(context: Context, private val swap: Boolean, private val mirror: Boolean, private val opening: Boolean = false) : View(context) {
    private var cur: Bitmap? = null
    private var prev: Bitmap? = null
    private var since = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val matrix = Matrix()
    private var shown = 0f
    private var drawnAt = 0L

    /** 전체 밝기 목표(0~1). 실제 밝기는 짧게 따라가 끊김 없이 바뀐다. */
    @Volatile
    var level = 1f
        set(v) {
            field = v.coerceIn(0f, 1f)
            postInvalidateOnAnimation()
        }

    /** 지금 보이는 밝기(사라짐 확인용). */
    val shownLevel get() = shown

    /** 화면이 거의 검을 때 켜는 무드등 색. */
    var mood: Int = Color.rgb(255, 150, 70)

    init {
        setBackgroundColor(Color.BLACK)
    }

    /** 바깥 화면 작은 그림을 새로 받는다(아무 스레드에서나). 색을 다듬고 흐려 둔 뒤 그린다. */
    fun setFrame(frame: Bitmap) {
        val graded = gradeAndBlur(frame)
        post {
            prev = cur
            cur = graded
            since = System.currentTimeMillis()
            postInvalidateOnAnimation()
        }
    }

    /** 한 가지 색으로 비춘다(바깥을 찍지 못할 때). */
    fun setSolid(color: Int) = setFrame(Bitmap.createBitmap(intArrayOf(color), 1, 1, Bitmap.Config.ARGB_8888))

    /** 색 다듬기 + 상자 흐림 두 번(작은 그림이라 가볍다). 거의 검은 화면이면 무드등 한 색. */
    private fun gradeAndBlur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var sum = 0f
        for (c in px) sum += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        if (sum / px.size < 0.04f) return Bitmap.createBitmap(intArrayOf(mood), 1, 1, Bitmap.Config.ARGB_8888)
        val hsl = FloatArray(3)
        for (i in px.indices) {
            val c = px[i]
            ColorUtils.colorToHSL(c, hsl)
            // 11-02 소장님: 앰비언트는 거의 무조건 밝게. 어두운 곳도 깎지 않고 밝기를 최소 50% 위로 끌어올린다(색만 따라간다)
            hsl[1] = (hsl[1] * 1.45f + 0.05f).coerceAtMost(1f)
            hsl[2] = (0.5f + 0.42f * hsl[2].pow(0.6f)).coerceAtMost(0.92f)
            px[i] = ColorUtils.HSLToColor(hsl)
        }
        repeat(2) { boxBlur(px, w, h, 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (pass in 0..1) {
            val (len, lines) = if (pass == 0) w to h else h to w
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var rr = 0
                    var gg = 0
                    var bb = 0
                    var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (pass == 0) px[line * w + j] else px[j * w + line]
                        rr += Color.red(c)
                        gg += Color.green(c)
                        bb += Color.blue(c)
                        n++
                    }
                    val idx = if (pass == 0) line * w + i else i * w + line
                    tmp[idx] = Color.rgb(rr / n, gg / n, bb / n)
                }
            }
            tmp.copyInto(px)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val img = cur ?: return
        val nowMs = System.currentTimeMillis()
        // 밝기는 목표를 0.12초 남짓 늦게 따라간다(각도가 1초에 8번 와도 매끄럽게)
        val dt = if (drawnAt == 0L) 0.016f else ((nowMs - drawnAt) / 1000f).coerceIn(0.001f, 0.1f)
        drawnAt = nowMs
        shown += (level - shown) * (1f - kotlin.math.exp(-dt * 9f))
        if (kotlin.math.abs(level - shown) < 0.004f) shown = level
        val w = width.toFloat()
        val h = height.toFloat()
        val rot = display?.rotation ?: Surface.ROTATION_0
        val turned = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
        val nw = if (turned) h else w
        val nh = if (turned) w else h
        canvas.save()
        // 화면 회전을 되돌려 '펼친 세로' 좌표로
        when (rot) {
            Surface.ROTATION_90 -> {
                canvas.translate(0f, nw)
                canvas.rotate(-90f)
            }
            Surface.ROTATION_180 -> {
                canvas.translate(nw, nh)
                canvas.rotate(180f)
            }
            Surface.ROTATION_270 -> {
                canvas.translate(nh, 0f)
                canvas.rotate(90f)
            }
        }
        val half = nw / 2
        val t = ((nowMs - since) / 400f).coerceIn(0f, 1f)
        val a = (255 * shown).toInt()
        run {
            // 빛 패널 + 접는 선 너머 번짐 폭까지 칠한 뒤, 접는 선 앞뒤를 서서히 가린다
            // 펼칠 때 간접등은 왼쪽만 보이게 번짐을 좁게(실기: 양쪽에 나온다고 함)
            val feather = half * if (opening) 0.14f else 0.42f
            val left = if (swap) half else 0f
            val x0 = if (swap) half - feather else 0f
            val x1 = if (swap) nw else half + feather
            val layer = canvas.saveLayer(x0, 0f, x1, nh, null)
            prev?.takeIf { t < 1f }?.let { drawPanel(canvas, it, swap, left, half, nh, x0, x1, a) }
            drawPanel(canvas, img, swap, left, half, nh, x0, x1, (a * t).toInt())
            val inside = if (swap) half + feather * 0.35f else half - feather * 0.35f
            val outside = if (swap) half - feather else half + feather
            mask.shader = LinearGradient(
                inside,
                0f,
                outside,
                0f,
                intArrayOf(Color.BLACK, Color.argb(190, 0, 0, 0), Color.argb(80, 0, 0, 0), Color.argb(20, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.3f, 0.6f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(x0, 0f, x1, nh, mask)
            canvas.restoreToCount(layer)
        }
        canvas.restore()
        if (t < 1f || shown != level) postInvalidateOnAnimation()
    }

    /** 그림 [b]를 패널 변환으로 붙이되, 가장자리 색을 늘여(CLAMP) [x0]~[x1] 전체를 칠한다. */
    private fun drawPanel(canvas: Canvas, b: Bitmap, right: Boolean, left: Float, pw: Float, ph: Float, x0: Float, x1: Float, alpha: Int) {
        if (alpha <= 0) return
        paint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(panelMatrix(b, right, left, pw, ph)) }
        paint.alpha = alpha.coerceAtMost(255)
        canvas.drawRect(x0, 0f, x1, ph, paint)
        paint.shader = null
    }

    /**
     * 바깥 그림(가로 bw × 세로 bh, 위=접는 선)을 패널(폭 [pw], 높이 [ph], 왼쪽 끝 [left])에 놓는 변환.
     * 바깥 세로축(위→아래)은 패널 가로축(접는 선 쪽→책상 쪽), 바깥 가로축(왼→오)은 패널 세로축(아래→위, 안쪽 면이라 뒤집음).
     */
    private fun panelMatrix(b: Bitmap, right: Boolean, left: Float, pw: Float, ph: Float): Matrix {
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        matrix.reset()
        if (opening) {
            // 세로 바깥 화면을 그대로 패널에 늘이고 좌우만 뒤집는다(뒷면). 오른쪽 패널이면 뒤집지 않는다. [mirror]는 반대로
            matrix.postScale(pw / bw, ph / bh)
            if (right == mirror) {
                matrix.postScale(-1f, 1f)
                matrix.postTranslate(pw, 0f)
            }
            matrix.postTranslate(left, 0f)
            return Matrix(matrix)
        }
        // 그림을 90° 돌려 바깥 위가 오른쪽(접는 선 쪽)으로 오게: (x, y) → (bh - y, x)
        matrix.postRotate(90f)
        matrix.postTranslate(bh, 0f)
        // 이제 폭 bh × 높이 bw. 바깥 왼쪽이 위로 와 있다 → 안쪽 면이라 위아래 뒤집기(mirror면 그대로)
        if (!mirror) {
            matrix.postScale(1f, -1f)
            matrix.postTranslate(0f, bw)
        }
        matrix.postScale(pw / bh, ph / bw)
        // 빛을 오른쪽 패널에 둘 때는 접는 선이 패널 왼쪽이 되도록 좌우를 뒤집는다
        if (right) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(pw, 0f)
        }
        matrix.postTranslate(left, 0f)
        return Matrix(matrix)
    }
}
