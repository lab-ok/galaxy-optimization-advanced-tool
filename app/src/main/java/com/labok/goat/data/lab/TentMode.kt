package com.labok.goat.data.lab

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 텐트 모드 시험(실험실, 11-01 소장님): 폴드를 반쯤 접어 엎어 세우고 바깥 화면은 시계·영상, 안쪽 화면은 책상을 비추는
 * 간접조명으로 쓰려면 두 화면을 동시에 켜야 한다. 폴드8에서 되는지 보려고 Shizuku로 기기 상태를 '두 화면 동시 표시'로 바꾸고,
 * 바깥 화면에 앱을 띄우고, 바깥 화면 색을 읽는 시험용 함수를 모은다. 공식 방법(WindowAreaController)은 [com.labok.goat.ui.lab.TentActivity]에 있다.
 */
object TentMode {
    /** 기기 상태 목록(시스템 출력 그대로). Shizuku가 없으면 null. */
    fun states(): String? = ShizukuAccess.shell("cmd device_state print-states")?.out

    /** 두 화면을 함께 켜는 상태 번호. 이름에 CONCURRENT·DUAL이 들어간 상태를 찾는다(기기마다 이름이 다르다). */
    fun concurrentStateId(states: String): Int? = Regex("identifier=(\\d+), name='([^']*)'").findAll(states)
        .firstOrNull { m -> m.groupValues[2].uppercase().let { "CONCURRENT" in it || "DUAL" in it } }
        ?.groupValues?.get(1)?.toInt()

    /** 두 화면 동시 표시 상태로 바꾼다. 성공하면 null, 실패하면 이유. */
    fun enterConcurrent(context: Context): String? {
        val states = states() ?: return "Shizuku 연결이 필요합니다."
        val id = concurrentStateId(states) ?: return "이 기기에는 두 화면 동시 표시 상태가 없습니다.\n$states".take(600)
        val r = ShizukuAccess.shell("cmd device_state state $id") ?: return "Shizuku 연결이 필요합니다."
        val err = if (r.ok) null else r.err.ifBlank { r.out }.trim().take(200).ifBlank { "실패" }
        LogRepository.from(context).add("텐트 모드 시험: 두 화면 동시 표시(상태 $id)" + (err?.let { " 실패($it)" } ?: ""))
        return err
    }

    /** 강제한 기기 상태를 푼다. */
    fun reset(context: Context) {
        val r = ShizukuAccess.shell("cmd device_state state reset")
        LogRepository.from(context).add("텐트 모드 시험: 기기 상태 원래대로" + if (r?.ok == true) "" else " 실패")
    }

    /** 켜져 있는 화면들(번호, 크기). */
    fun displays(context: Context): List<Triple<Int, Point, Int>> = context.getSystemService(DisplayManager::class.java).displays.map { d ->
        val p = Point()
        @Suppress("DEPRECATION")
        d.getRealSize(p)
        Triple(d.displayId, p, d.state)
    }

    /** [current]가 아닌 화면 가운데 켜진 것(바깥 화면). 없으면 null. */
    fun otherDisplay(context: Context, current: Int): Int? = displays(context).filter { it.first != current && it.third == Display.STATE_ON }.minByOrNull { it.second.x * it.second.y }?.first

    /** [pkg]의 첫 화면을 [displayId] 화면에 띄운다(Shizuku 셸이라 어느 화면에나 띄울 수 있다). 성공하면 null. */
    fun launchOnDisplay(context: Context, pkg: String, displayId: Int): String? {
        val comp = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString() ?: return "$pkg 앱이 없습니다."
        return launchComponent(context, comp, displayId)
    }

    /**
     * 화면 이름 [comp]를 [displayId] 화면에 띄운다. 앱이 다른 화면에 직접 띄우면 권한 거부가 나서(폴드8 실기 11-01) Shizuku 셸로 띄운다.
     * 유튜브의 첫 화면 이름에는 `$`가 들어 있어(Shell$HomeActivity) 셸이 변수로 읽지 않게 작은따옴표로 감싼다.
     */
    fun launchComponent(context: Context, comp: String, displayId: Int): String? {
        val pkg = comp.substringBefore('/')
        val r = ShizukuAccess.shell("am start --display $displayId -n '$comp'") ?: return "Shizuku 연결이 필요합니다."
        val err = if (r.ok && !r.out.contains("Error")) null else (r.err + r.out).trim().take(200)
        LogRepository.from(context).add("텐트 모드 시험: $pkg 을(를) 화면 $displayId 에 띄움" + (err?.let { " 실패($it)" } ?: ""))
        return err
    }

    /** SurfaceFlinger의 물리 화면 번호들(screencap -d에 쓴다). */
    fun physicalIds(): List<String> = Regex("Display (\\d+) ").findAll(ShizukuAccess.shell("dumpsys SurfaceFlinger --display-id")?.out.orEmpty()).map { it.groupValues[1] }.toList()

    /**
     * 물리 화면 [physId]를 한 장 찍어(원시 RGBA) 가장자리 띠의 평균 색을 돌려준다. 앰비라이트처럼 영상 가장자리 색을 따라가려고
     * 바깥 15% 띠만 본다. 크기도 함께 돌려줘 어느 물리 화면이 바깥 화면인지 맞춰 볼 수 있게 한다. 실패하면 null.
     */
    fun edgeColor(physId: String): Triple<Int, Int, Int>? {
        val p = ShizukuAccess.stream("screencap -d $physId") ?: return null
        return runCatching {
            val bytes = p.inputStream.use { it.readBytes() }
            p.waitFor()
            if (bytes.size < 16) return null
            fun int(o: Int) = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or ((bytes[o + 2].toInt() and 0xFF) shl 16) or ((bytes[o + 3].toInt() and 0xFF) shl 24)
            val w = int(0)
            val h = int(4)
            if (w <= 0 || h <= 0) return null
            val head = bytes.size - w * h * 4
            if (head < 12) return null
            val bandX = (w * 0.15f).toInt().coerceAtLeast(1)
            val bandY = (h * 0.15f).toInt().coerceAtLeast(1)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            val step = 8
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    if (x < bandX || x >= w - bandX || y < bandY || y >= h - bandY) {
                        val o = head + (y * w + x) * 4
                        r += bytes[o].toInt() and 0xFF
                        g += bytes[o + 1].toInt() and 0xFF
                        b += bytes[o + 2].toInt() and 0xFF
                        n++
                    }
                    x += step
                }
                y += step
            }
            if (n == 0L) return null
            val color = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            Triple(color, w, h)
        }.getOrNull()
    }
}
