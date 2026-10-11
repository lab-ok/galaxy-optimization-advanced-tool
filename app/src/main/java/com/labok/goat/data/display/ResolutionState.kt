package com.labok.goat.data.display

import android.content.Context

/**
 * GOAT가 해상도를 낮춰 둔 동안의 기록(해상도 낮추기). 잠금을 풀기 전(직접 부팅)에도 읽고 되돌릴 수 있도록
 * 기기 보호 저장소(device-protected storage)에 둔다.
 *
 * - [base]: GOAT가 처음 바꾸기 직전의 논리 해상도(짧은 변, 긴 변). 이 값이 있는 동안만 DPI 기능이 이 크기를 화면 키로,
 *   지금 크기와의 비율을 [com.labok.goat.domain.ScreenInfo.scale]로 쓴다. 삼성 공식 해상도(FHD+ 등)로 이미 줄여 둔 기기도
 *   GOAT가 바꾸기 전에는 그대로라(비율 1) 기존 DPI 설정이 달라지지 않는다.
 * - [hadForced]: 그때 이미 강제 해상도가 걸려 있었는지(되돌릴 때 지우지 않고 [base]로 다시 건다).
 * - [baseDensity]·[densityOverride]: 그때의 밀도와 강제 밀도(없었으면 null). DPI 기능을 쓰지 않는 사용자의 밀도를 되돌릴 때 쓴다.
 * - [deadline]: 확인을 기다리는 중이면 자동으로 되돌릴 시각(ms, 벽시계). 0이면 확인됨.
 */
object ResolutionState {
    private const val PREF = "resolution_state"

    data class Saved(val base: Pair<Int, Int>, val hadForced: Boolean, val baseDensity: Int, val densityOverride: Int?)

    fun saved(context: Context): Saved? {
        val p = prefs(context)
        if (!p.getBoolean("active", false)) return null
        val s = p.getInt("baseShort", 0); val l = p.getInt("baseLong", 0)
        if (s <= 0 || l <= 0) return null
        return Saved(s to l, p.getBoolean("hadForced", false), p.getInt("baseDensity", 0), p.getInt("densityOverride", -1).takeIf { it > 0 })
    }

    /** DPI 기능이 쓰는 기준 크기. GOAT가 해상도를 바꿔 둔 동안만 있다. */
    fun base(context: Context): Pair<Int, Int>? = saved(context)?.base

    fun save(context: Context, s: Saved) {
        prefs(context).edit()
            .putBoolean("active", true)
            .putInt("baseShort", s.base.first).putInt("baseLong", s.base.second)
            .putBoolean("hadForced", s.hadForced)
            .putInt("baseDensity", s.baseDensity)
            .putInt("densityOverride", s.densityOverride ?: -1)
            .commit()
    }

    fun clear(context: Context) { prefs(context).edit().clear().commit() }

    fun deadline(context: Context): Long = prefs(context).getLong("deadline", 0L)

    fun setDeadline(context: Context, at: Long) { prefs(context).edit().putLong("deadline", at).commit() }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
