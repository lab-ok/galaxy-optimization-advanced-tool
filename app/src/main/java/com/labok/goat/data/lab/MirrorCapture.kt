package com.labok.goat.data.lab

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import java.io.BufferedOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import kotlin.system.exitProcess

/**
 * 실시간 앰비언트용 화면 미러 도우미(11-02 소장님 "실시간으로 되어 있지 않아").
 *
 * 예전 캡처는 프레임마다 셸 `screencap`을 새로 띄워 화면 전체를 받았다(실기 회당 140~200ms → 초당 약 4장). 영상과 함께 움직이는
 * 반사로 보이기에는 너무 느렸다. 이 도우미는 scrcpy와 같은 방식으로, Shizuku 셸 권한(uid 2000)의 `app_process`로 떠서
 * 기본 화면을 **작은 가상 화면(가로 [width]칸)으로 미러링**하고 새 화면이 그려질 때마다(최대 [fps]장/초) 위쪽 [top] 비율만
 * 표준 출력으로 흘려보낸다. 화면이 멈춰 있으면 새 프레임이 오지 않아 저절로 쉰다.
 *
 * 보호 콘텐츠(DRM·보안 창)는 미러에서도 시스템이 검게 채운다. 우회하지 않는다.
 *
 * 출력 형식: 프레임마다 머리 12바이트(ASCII "GMRF", 폭 int, 높이 int, 리틀 엔디언) + RGBA 바이트(폭×높이×4).
 * GOAT가 파이프를 닫으면(패널 닫힘) 쓰기가 실패해 스스로 끝난다. 화면 크기가 바뀌면(회전) 끝내고 GOAT가 다시 띄운다.
 * 띄우는 명령은 [command].
 */
object MirrorCapture {
    const val MAGIC = 0x46524D47 // "GMRF" little-endian

    fun command(apkPath: String, width: Int, fps: Int, top: Float): String = "CLASSPATH=$apkPath app_process /system/bin --nice-name=goat_mirror ${MirrorCapture::class.java.name} $width $fps $top"

    @JvmStatic
    fun main(args: Array<String>) {
        val width = args.getOrNull(0)?.toIntOrNull() ?: 384
        val fps = args.getOrNull(1)?.toIntOrNull() ?: 20
        val top = args.getOrNull(2)?.toFloatOrNull() ?: 0.5f
        Looper.prepareMainLooper()
        val (sw, sh) = displaySize() ?: fail("화면 크기를 읽지 못했습니다")
        val w = width
        val h = (w.toLong() * sh / sw).toInt().coerceAtLeast(2)
        val rows = (h * top).toInt().coerceIn(1, h)
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val vd = runCatching { createMirror("goat-mirror", w, h, reader.surface) }.getOrElse { fail("가상 화면을 만들지 못했습니다: ${it.javaClass.simpleName} ${it.message}") }
        val out = BufferedOutputStream(FileOutputStream(FileDescriptor.out), 1 shl 16)
        val buf = ByteArray(12 + w * rows * 4)
        writeIntLE(buf, 0, MAGIC)
        writeIntLE(buf, 4, w)
        writeIntLE(buf, 8, rows)
        val minGap = 1000L / fps.coerceIn(1, 60)
        var last = 0L
        val handler = Handler(Looper.getMainLooper())
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.uptimeMillis()
                if (now - last < minGap) return@setOnImageAvailableListener
                last = now
                val plane = img.planes[0]
                val bb = plane.buffer
                val rowStride = plane.rowStride
                var o = 12
                for (y in 0 until rows) {
                    bb.position(y * rowStride)
                    bb.get(buf, o, w * 4)
                    o += w * 4
                }
                out.write(buf)
                out.flush()
            } catch (e: java.io.IOException) {
                // GOAT가 파이프를 닫았다(패널 닫힘)
                release(vd)
                exitProcess(0)
            } finally {
                img.close()
            }
        }, handler)
        // 화면 크기가 바뀌면(회전·접기) 끝낸다. GOAT가 새 크기로 다시 띄운다
        val watch = object : Runnable {
            override fun run() {
                val now = displaySize()
                if (now == null || now.first != sw || now.second != sh) {
                    release(vd)
                    exitProcess(3)
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(watch, 500)
        Looper.loop()
    }

    /** 기본 화면(0)의 실제 크기(회전 반영). */
    private fun displaySize(): Pair<Int, Int>? = runCatching {
        val dmg = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val inst = dmg.getMethod("getInstance").invoke(null)
        val info = dmg.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(inst, 0)
        val c = info.javaClass
        c.getField("logicalWidth").getInt(info) to c.getField("logicalHeight").getInt(info)
    }.getOrNull()

    /**
     * 기본 화면을 미러링하는 가상 화면(Android 14+ 숨은 함수 DisplayManager.createVirtualDisplay(이름, 폭, 높이, 미러할 화면, Surface)).
     * 셸(uid 2000)은 CAPTURE_VIDEO_OUTPUT 권한이 있어 만들 수 있다(scrcpy와 같은 경로).
     */
    private fun createMirror(name: String, w: Int, h: Int, surface: Surface): Any {
        val dm = Class.forName("android.hardware.display.DisplayManager")
        val m = dm.getMethod("createVirtualDisplay", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Surface::class.java)
        return m.invoke(null, name, w, h, 0, surface) ?: error("null")
    }

    private fun release(vd: Any) {
        runCatching { vd.javaClass.getMethod("release").invoke(vd) }
    }

    private fun writeIntLE(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = (v shr 8 and 0xFF).toByte()
        b[o + 2] = (v shr 16 and 0xFF).toByte()
        b[o + 3] = (v shr 24 and 0xFF).toByte()
    }

    private fun fail(msg: String): Nothing {
        System.err.println("GOAT 미러: $msg")
        exitProcess(2)
    }
}
