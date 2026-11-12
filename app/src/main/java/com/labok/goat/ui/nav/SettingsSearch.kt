package com.labok.goat.ui.nav

import com.labok.goat.R
import com.labok.goat.data.lab.AirplaneRadios
import com.labok.goat.data.lab.IconBlacklist
import com.labok.goat.ui.text.Copy

/**
 * 사이드바 '설정 검색': 기능 이름과 각 기능 안의 설정 이름으로 찾아, 고르면 그 기능 화면을 연다.
 * 글자 일부(띄어쓰기·대소문자 무시)나 초성(예: "ㅂㄹ" → 볼륨)으로 찾는다.
 */
object SettingsSearch {
    data class Hit(val item: NavItem, val title: String)

    /** 기능마다 찾을 수 있는 설정 이름(화면의 제목·스위치 이름 그대로). 기능 이름은 [NavItem.label]로 따로 넣는다. */
    private val settings: Map<NavItem, List<String>>
        get() = mapOf(
            NavItem.DPI_MATCH to listOf(Copy.AUTO_TITLE, Copy.text(R.string.search_dpi_screen_dpi), Copy.text(R.string.search_dpi_size_test)),
            NavItem.APP_SIZE to listOf(Copy.APP_SIZE_NOTIFY_TITLE, Copy.APP_SIZE_HOME_TITLE),
            NavItem.ANIMATION to listOf(Copy.ANIM_PRESET_TITLE, Copy.ANIM_DETAIL_TITLE, Copy.text(R.string.search_anim_window), Copy.text(R.string.search_anim_transition), Copy.text(R.string.search_anim_animator)),
            NavItem.SHAKE_ROTATE to listOf(
                Copy.SHAKE_SENSITIVITY_TITLE,
                Copy.SHAKE_CALIBRATE,
                Copy.SHAKE_FAST_TITLE,
                Copy.SHAKE_LARGE_TITLE,
                Copy.SHAKE_TAPS_TITLE,
                Copy.SHAKE_ADVANCED_TITLE,
                Copy.text(R.string.search_shake_vibration),
                Copy.text(R.string.search_shake_droplet)
            ),
            NavItem.POWER_ROTATE to listOf(Copy.text(R.string.power_rotate_auto_on), Copy.text(R.string.power_rotate_locked), Copy.text(R.string.power_rotate_mode_once)),
            NavItem.CAMERA_MUTE to listOf(Copy.text(R.string.search_camera_shutter_sound), Copy.text(R.string.search_camera_shutter)),
            NavItem.APP_CLEANUP to listOf(Copy.BLOAT_INTRO_TITLE),
            NavItem.REFRESH to listOf(Copy.REFRESH_SYSTEM, Copy.REFRESH_SMOOTH, Copy.REFRESH_SAVER, "120Hz", "60Hz"),
            NavItem.GOS to listOf(Copy.GOS_SWITCH_TITLE),
            NavItem.DOZE to listOf(Copy.FEATURE_DOZE, Copy.text(R.string.search_doze_late_notifications)),
            NavItem.RESOLUTION to listOf(Copy.RES_PICK_TITLE, "FHD", "HD"),
            NavItem.DARK to listOf(Copy.DARK_FORCE_TITLE, Copy.DARK_NIGHT_TITLE),
            NavItem.AIRPLANE to AirplaneRadios.Radio.entries.map { Copy.text(R.string.search_airplane_keep_on_fmt, it.label) },
            NavItem.VOLUME_ROTATE to listOf(Copy.VOLUME_ON, Copy.VOLUME_INVERT, Copy.VOLUME_CAMERA, Copy.VOLUME_CLOSED, Copy.VOLUME_OPEN),
            NavItem.STATUS_ICONS to listOf(Copy.LAB_STATUS_HIDE, Copy.LAB_PRIVACY_DOT_SWITCH) + IconBlacklist.Slot.entries.map { Copy.text(R.string.search_icons_hide_fmt, it.label) },
            NavItem.AUTO_FLEX to listOf(Copy.LAB_FLEX_DIM, Copy.LAB_FLEX_CLICK, Copy.LAB_FLEX_APPS_TITLE, Copy.LAB_FLEX_RANGE_TITLE),
            NavItem.LAB_NOTE to listOf(Copy.text(R.string.search_note_share_txt), Copy.text(R.string.search_note_text_file), Copy.text(R.string.search_note_long_text), Copy.text(R.string.search_note_copy_image)),
            NavItem.LAB_TOOLS to listOf(Copy.text(R.string.search_tools_settings_diff), Copy.text(R.string.search_tools_api_probe)),
            NavItem.LAB_STATS to listOf(Copy.text(R.string.search_stats_unfolds), Copy.text(R.string.search_stats_unlocks), Copy.text(R.string.search_stats_screen_on), Copy.text(R.string.search_stats_volume_presses), Copy.text(R.string.search_stats_stats), Copy.text(R.string.search_stats_battery), Copy.text(R.string.search_stats_charge_history), Copy.text(R.string.search_stats_hourly_usage)),
            NavItem.LAB_AMBIENT to listOf(Copy.text(R.string.search_ambient_tent), Copy.text(R.string.search_ambient_standby_clock), Copy.text(R.string.search_ambient_mood_light)),
            NavItem.SETTINGS to listOf(Copy.INK_TITLE, Copy.HAPTIC_TITLE, Copy.MOTION_TITLE, Copy.GOAT_STYLE_TITLE, Copy.LOG_FILE_TITLE, Copy.text(R.string.search_settings_update_check), Copy.text(R.string.search_settings_changelog), Copy.text(R.string.search_settings_permissions))
        )

    /**
     * [query]로 찾은 결과. [visible]은 지금 사이드바에 보이는 기능(실험실을 열지 않았으면 실험실 기능은 빠진다).
     * 기능 이름이 맞으면 맨 앞, 그다음 이름이 검색어로 시작하는 설정, 그다음 중간에 들어 있는 설정 순이다.
     */
    fun search(query: String, visible: Collection<NavItem>): List<Hit> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<Pair<Int, Hit>>()
        val table = settings
        for (item in visible) {
            score(item.label, q)?.let { hits += (it - 10) to Hit(item, item.label) }
            table[item].orEmpty().forEach { name -> score(name, q)?.let { hits += it to Hit(item, name) } }
        }
        return hits.sortedBy { it.first }.map { it.second }.distinct().take(20)
    }

    /** 맞으면 순위(작을수록 앞), 아니면 null. */
    private fun score(name: String, q: String): Int? {
        val n = norm(name)
        return when {
            n.startsWith(q) -> 0
            n.contains(q) -> 1
            isChosung(q) && chosung(n).contains(q) -> 2
            else -> null
        }
    }

    /** 글자와 숫자만 남긴다(띄어쓰기·가운뎃점·하이픈 등은 빼고 비교: "wifi"로 "Wi-Fi"를 찾게). */
    internal fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    private fun isChosung(q: String) = q.all { it in CHO }

    /** 한글 음절은 초성으로 바꾸고 나머지 글자는 그대로 둔다. */
    internal fun chosung(s: String) = s.map { ch -> if (ch in '가'..'힣') CHO[(ch - '가') / 588] else ch }.joinToString("")
}
