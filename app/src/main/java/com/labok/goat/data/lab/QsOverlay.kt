package com.labok.goat.data.lab

import android.content.Context
import android.util.TypedValue
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 시스템 UI 정수 값 덮어쓰기(실험실 빠른 설정 격자). One UI 8.5는 격자 열 수를 설정 키로 읽지 않아(2026-10-18 실기기 진단),
 * 시스템 UI 리소스 값(예: integer/quick_settings_num_columns)을 런타임 리소스 오버레이(fabricated overlay)로 덮어쓴다.
 * Shizuku 셸 권한으로 오버레이 서비스에 직접 등록하며(셸 명령 `cmd overlay fabricate`는 루트만 허용), 소유자는 셸 패키지다.
 * 시스템 UI를 다시 띄워야 반영되고, [remove]로 오버레이를 지우면 원래대로 돌아온다.
 */
object QsOverlay {
    const val TARGET = "com.android.systemui"
    private const val OWNER = "com.android.shell"
    private const val NAME = "goat_qs_grid"

    data class Value(val name: String, val value: Int)

    private fun oms(): Any = HiddenApi.asInterface("android.content.om.IOverlayManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("overlay")))

    /**
     * 시스템 UI의 정수 리소스 가운데 이름이 [pattern]에 맞는 것과 지금 값(오버레이가 있으면 덮어쓴 값). 리소스 표를 직접 훑는다
     * (패키지 0x7f·0x7e × 형식 1~80 가운데 'integer' 형식을 찾아 항목을 차례로 읽음). 작업 스레드에서 부른다.
     */
    fun integers(context: Context, pattern: Regex = Regex("qs|tile|column|quick|panel", RegexOption.IGNORE_CASE)): List<Value> {
        val res = runCatching { context.packageManager.getResourcesForApplication(TARGET) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Value>()
        // 시스템 UI 리소스의 패키지 번호(AOSP 0x7f, 삼성 0x7e)를 아는 이름으로 먼저 찾는다(없는 번호를 훑으면 로그가 넘친다)
        val known = listOf("qs_frame" to "id", "quick_settings_panel" to "id", "status_bar" to "layout")
            .map { (n, t) -> res.getIdentifier(n, t, TARGET) }.firstOrNull { it != 0 }
        val pkgs = known?.let { intArrayOf(it ushr 24) } ?: intArrayOf(0x7f, 0x7e)
        for (pkg in pkgs) {
            var missingTypes = 0
            for (type in 1..80) {
                val base = (pkg shl 24) or (type shl 16)
                // 형식 번호는 거의 이어져 있지만 첫 항목이 빈 형식도 있어, 여러 번 연달아 없을 때만 그만 훑는다
                val typeName = runCatching { res.getResourceTypeName(base) }.getOrNull()
                if (typeName == null) {
                    if (++missingTypes >= 8) break
                    continue
                }
                missingTypes = 0
                if (typeName != "integer") continue
                var misses = 0
                var i = 0
                while (misses < 64 && i < 0xFFFF) {
                    val id = base or i
                    val name = runCatching { res.getResourceEntryName(id) }.getOrNull()
                    if (name == null) {
                        misses++
                    } else {
                        misses = 0
                        if (pattern.containsMatchIn(name)) runCatching { res.getInteger(id) }.getOrNull()?.let { out += Value(name, it) }
                    }
                    i++
                }
            }
        }
        return out.distinctBy { it.name }
    }

    /**
     * 빠른 설정 열 수를 정하는 리소스로 보이는 것: 이름에 column과 qs·quick·tile이 함께 들어가고, 가로 화면 전용·위쪽 작은 줄(quick_qs)이 아닌 것.
     * 삼성 One UI 8.5 실기기에는 sec_quick_settings_num_columns(_fold·_fold_sub·_tablet·_power_saving)와
     * quick_settings_infinite_grid_num_columns 등이 있었다(2026-10-18 진단).
     */
    fun columnNames(values: List<Value>) = values.filter {
        it.name.contains("column", true) && Regex("qs|quick|tile", RegexOption.IGNORE_CASE).containsMatchIn(it.name) &&
            !it.name.contains("land", true) && !it.name.contains("quick_qs", true)
    }.map { it.name }

    /** [names]의 정수 리소스를 모두 [value]로 덮어쓰는 오버레이를 등록하고 켠다. 실패하면 이유를 담은 예외. */
    fun apply(names: List<String>, value: Int): Result<Unit> = runCatching {
        require(names.isNotEmpty()) { "덮어쓸 리소스를 찾지 못했습니다" }
        HiddenApi.exemptAll()
        val bc = Class.forName("android.content.om.FabricatedOverlay\$Builder")
        val b = bc.getConstructor(String::class.java, String::class.java, String::class.java).newInstance(OWNER, NAME, TARGET)
        val set = bc.getMethod("setResourceValue", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        names.forEach { set.invoke(b, "$TARGET:integer/$it", TypedValue.TYPE_INT_DEC, value) }
        val fo = bc.getMethod("build").invoke(b)!!
        val id = fo.javaClass.getMethod("getIdentifier").invoke(fo)!!
        val tc = Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
        val t = tc.getConstructor().newInstance()
        tc.getMethod("registerFabricatedOverlay", fo.javaClass).invoke(t, fo)
        tc.getMethod("setEnabled", id.javaClass, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(t, id, true, 0)
        commit(tc.getMethod("build").invoke(t)!!)
    }

    /** 등록한 오버레이를 지운다(없으면 아무 일 없음). */
    fun remove(): Result<Unit> = runCatching {
        if (!present()) return@runCatching
        HiddenApi.exemptAll()
        val id = Class.forName("android.content.om.OverlayIdentifier").getConstructor(String::class.java, String::class.java).newInstance(OWNER, NAME)
        val tc = Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
        val t = tc.getConstructor().newInstance()
        tc.getMethod("unregisterFabricatedOverlay", id.javaClass).invoke(t, id)
        commit(tc.getMethod("build").invoke(t)!!)
    }

    /** 오버레이가 등록돼 있는지(`cmd overlay list`에 이름이 보이는지). */
    fun present(): Boolean = ShizukuAccess.shell("cmd overlay list $TARGET")?.out?.contains(NAME) == true

    private fun commit(tx: Any) {
        val oms = oms()
        oms.javaClass.getMethod("commit", tx.javaClass).invoke(oms, tx)
    }

    /** 진단 로그용: 오버레이 상태와 후보 정수 리소스. */
    fun diagnose(context: Context): String = buildString {
        appendLine("오버레이: ${if (present()) "있음" else "없음"}")
        val v = integers(context)
        if (v.isEmpty()) appendLine("정수 리소스를 찾지 못했습니다") else v.forEach { appendLine("integer/${it.name} = ${it.value}") }
    }
}
