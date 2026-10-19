package com.labok.goat.ui.liquid

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 액체 미리보기(개발용): 환경 변수 LIQUID_PREVIEW=<폴더>가 있을 때만 돈다. 앱과 같은 [FluidSim]·[LiquidLook]에
 * 임의의 흔들기·충격 입력을 넣고, 앱의 goo(흐림 + 알파 임계)와 같은 방식으로 그려 프레임들을 montage.png 한 장으로 모은다.
 * (에뮬레이터에 센서가 없어 만든 도구. 안드로이드 단위 테스트에는 AWT가 없어 PNG를 직접 쓴다.)
 */
class FluidPreview {
    private val size = 400
    private val dp = 1.6f

    @Test
    fun render() {
        val out = System.getenv("LIQUID_PREVIEW") ?: ""
        assumeTrue(out.isNotEmpty())
        val cfg = LiquidLook.default
        val sim = FluidSim(cfg.particles, cfg.hDp * dp).apply { cfg.apply(this) }
        val glass = cfg.glassDp * dp
        sim.reset(size / 2f, size / 2f, glass)
        val frames = mutableListOf<FloatArray>()
        val g = cfg.gToPx * dp
        val moon = 9.8f * cfg.gravity * g // 세워 든 폰의 약한 중력(아래쪽)
        fun run(n: Int, force: (Int) -> Pair<Float, Float>, snapAt: List<Int> = emptyList()) {
            repeat(n) { i ->
                val (ax, ay) = force(i)
                repeat(2) { sim.step(0.5f, ax, ay + moon, 0f, cfg.pull, cfg.cohesion) }
                if (i in snapAt) frames += draw(sim, glass, cfg)
            }
        }
        run(150, { 0f to 0f }, snapAt = listOf(149)) // 가만히
        run(40, { i -> (-g * 24f * sin(2 * PI * 4 * i / 60.0).toFloat()) to 0f }, snapAt = listOf(4, 8, 13, 20)) // 좌우 흔들기 4Hz ±18
        run(80, { 0f to 0f }, snapAt = listOf(10, 30, 79)) // 다시 뭉침
        // 세게 한 번 침: 앱에서는 검출기가 충격을 잡으면(기준의 최대 3배) 반대쪽으로 물방울을 튀긴다
        val hitX = 50f
        val hitY = -35f
        sim.splash(-hitX, -hitY, 3f * cfg.splash * dp)
        run(50, { i -> if (i < 3) (g * hitX) to (g * hitY) else 0f to 0f }, snapAt = listOf(2, 6, 12, 22, 49))
        // 흔들기 판정: 사방으로 톡 터진다
        sim.burst(cfg.splash * dp)
        run(50, { 0f to 0f }, snapAt = listOf(3, 8, 16, 49))
        val cols = 4
        val rows = (frames.size + cols - 1) / cols
        val w = size * cols
        val h = size * rows
        val pix = ByteArray(w * h) { 0xEE.toByte() }
        frames.forEachIndexed { k, f ->
            val ox = (k % cols) * size
            val oy = (k / cols) * size
            for (y in 0 until size) for (x in 0 until size) pix[(oy + y) * w + ox + x] = (f[y * size + x] * 255).toInt().coerceIn(0, 255).toByte()
        }
        File(out).mkdirs()
        File(out, "montage.png").writeBytes(png(w, h, pix))
    }

    /** 앱과 같은 그리기: 입자 원(알파) → 가우시안 흐림 → 알파 임계(22a − 9) → 잉크. 잔 테두리도 그린다. 밝기(0~1)를 돌려준다. */
    private fun draw(sim: FluidSim, glass: Float, cfg: LiquidLook): FloatArray {
        val a = FloatArray(size * size)
        val r = cfg.radiusDp * dp
        for (i in 0 until sim.n) stamp(a, sim.x[i], sim.y[i], r)
        val b = blur(a, cfg.blurDp * dp)
        val img = FloatArray(size * size)
        val ring = glass + 6 * dp
        for (p in 0 until size * size) {
            val al = (b[p] * 22f - 9f).coerceIn(0f, 1f)
            var v = 0.93f * (1 - al) + 0.09f * al
            val dx = p % size - size / 2f
            val dy = p / size - size / 2f
            if (abs(sqrt(dx * dx + dy * dy) - ring) < 1.2f) v = minOf(v, 0.8f)
            img[p] = v
        }
        return img
    }

    private fun stamp(a: FloatArray, cx: Float, cy: Float, r: Float) {
        val x0 = (cx - r).toInt().coerceAtLeast(0)
        val x1 = (cx + r).toInt().coerceAtMost(size - 1)
        val y0 = (cy - r).toInt().coerceAtLeast(0)
        val y1 = (cy + r).toInt().coerceAtMost(size - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r * r) a[y * size + x] = 1f
            }
        }
    }

    private fun blur(src: FloatArray, sigma: Float): FloatArray {
        val rad = (sigma * 2.5f).toInt().coerceAtLeast(1)
        val k = FloatArray(rad * 2 + 1) { i -> exp(-((i - rad) * (i - rad)) / (2 * sigma * sigma)) }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        val tmp = FloatArray(size * size)
        val dst = FloatArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                var s = 0f
                for (i in -rad..rad) s += src[y * size + (x + i).coerceIn(0, size - 1)] * k[i + rad]
                tmp[y * size + x] = s
            }
        }
        for (y in 0 until size) {
            for (x in 0 until size) {
                var s = 0f
                for (i in -rad..rad) s += tmp[(y + i).coerceIn(0, size - 1) * size + x] * k[i + rad]
                dst[y * size + x] = s
            }
        }
        return dst
    }

    /** 8비트 회색조 PNG. */
    private fun png(w: Int, h: Int, pix: ByteArray): ByteArray {
        val raw = ByteArray((w + 1) * h)
        for (y in 0 until h) {
            raw[y * (w + 1)] = 0
            System.arraycopy(pix, y * w, raw, y * (w + 1) + 1, w)
        }
        val def = Deflater(6)
        def.setInput(raw)
        def.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (!def.finished()) {
            val n = def.deflate(buf)
            z.write(buf, 0, n)
        }
        val o = ByteArrayOutputStream()
        val d = DataOutputStream(o)
        d.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size)
            val t = type.toByteArray()
            d.write(t)
            d.write(data)
            val c = CRC32()
            c.update(t)
            c.update(data)
            d.writeInt(c.value.toInt())
        }
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply {
            writeInt(w)
            writeInt(h)
            writeByte(8)
            writeByte(0)
            writeByte(0)
            writeByte(0)
            writeByte(0)
        }
        chunk("IHDR", ihdr.toByteArray())
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        return o.toByteArray()
    }
}
