package com.labok.goat.data.tweaks

import android.content.Context
import android.provider.Settings
import com.labok.goat.data.log.LogRepository
import com.labok.goat.ui.text.Copy

/**
 * 상태 표시줄 아이콘 숨기기: 시스템 UI 튜너가 읽는 보안 설정 [KEY](숨길 아이콘 이름을 쉼표로 이은 목록)를 바꾼다.
 * 목록에 넣은 아이콘은 상태 표시줄에서 바로 사라지고, 빼면 다시 나타난다. 보안 설정이라 고급 화면 배율 설정과 같은
 * 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로 바꾼다.
 *
 * - 목록에는 GOAT가 모르는 이름(다른 앱이 넣은 것)이 있을 수 있어, 바꿀 때 그대로 둔다.
 * - 삼성은 재부팅하면 목록이 비워지는 기기가 있다. [keep]이 켜져 있으면 부팅 직후에만, 목록에 GOAT가 아는 이름이 하나도
 *   없을 때(비워졌다는 신호) [reapply]가 다시 숨긴다. 그 밖에 앱 밖(Good Lock·시스템 UI 튜너)에서 바꾼 값은 덮어쓰지 않는다.
 * - 처음 바꾸기 전 값(키가 있었는지까지)을 [restore]용으로 한 번만 남긴다.
 */
object StatusIcons {
    const val KEY = "icon_blacklist"
    private const val PREF = "status_icons"

    /** 숨길 수 있는 아이콘. [slots]는 시스템이 쓰는 이름(기기·버전마다 달라 여럿을 함께 넣는다). [careful]은 숨기면 상태를 알기 어려운 연결 표시. */
    enum class Icon(val label: String, val desc: String, val slots: List<String>, val careful: Boolean = false) {
        NFC(Copy.ICON_NFC, Copy.ICON_NFC_DESC, listOf("nfc", "nfc_on")),
        ALARM(Copy.ICON_ALARM, Copy.ICON_ALARM_DESC, listOf("alarm_clock")),
        VOLUME(Copy.ICON_VOLUME, Copy.ICON_VOLUME_DESC, listOf("volume", "mute")),
        BLUETOOTH(Copy.ICON_BLUETOOTH, Copy.ICON_BLUETOOTH_DESC, listOf("bluetooth")),
        HEADSET(Copy.ICON_HEADSET, Copy.ICON_HEADSET_DESC, listOf("headset")),
        VOLTE(Copy.ICON_VOLTE, Copy.ICON_VOLTE_DESC, listOf("ims_volte", "volte", "vowifi")),
        DATA_SAVER(Copy.ICON_DATA_SAVER, Copy.ICON_DATA_SAVER_DESC, listOf("data_saver")),
        ZEN(Copy.ICON_ZEN, Copy.ICON_ZEN_DESC, listOf("zen", "dnd")),
        LOCATION(Copy.ICON_LOCATION, Copy.ICON_LOCATION_DESC, listOf("location"), careful = true),
        HOTSPOT(Copy.ICON_HOTSPOT, Copy.ICON_HOTSPOT_DESC, listOf("hotspot"), careful = true),
        VPN(Copy.ICON_VPN, Copy.ICON_VPN_DESC, listOf("vpn"), careful = true),
        CAST(Copy.ICON_CAST, Copy.ICON_CAST_DESC, listOf("cast", "screen_record"), careful = true),
        ETHERNET(Copy.ICON_ETHERNET, Copy.ICON_ETHERNET_DESC, listOf("ethernet"), careful = true),
    }

    /** 지금 목록(쉼표로 나눈 이름들, 빈 이름은 뺀다). */
    fun list(context: Context): List<String> =
        parse(runCatching { Settings.Secure.getString(context.contentResolver, KEY) }.getOrNull())

    internal fun parse(raw: String?): List<String> =
        raw?.takeIf { it != "null" }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

    /** 지금 숨겨진 아이콘(이름 중 하나라도 목록에 있으면 숨긴 것으로 본다). */
    fun hidden(context: Context): Set<Icon> = hiddenIn(list(context))

    internal fun hiddenIn(list: List<String>): Set<Icon> = Icon.entries.filter { icon -> icon.slots.any { it in list } }.toSet()

    /** [current] 목록에서 [icon]을 숨기거나(이름을 모두 넣는다) 보이게(모두 뺀다) 한 새 목록. 모르는 이름은 그대로 둔다. */
    internal fun toggled(current: List<String>, icon: Icon, hide: Boolean): List<String> =
        if (hide) (current + icon.slots).distinct() else current.filterNot { it in icon.slots }

    /** 아이콘 하나를 숨기거나 보이게 한다. 쓰고 나서 다시 읽어 확인한다. 성공하면 true. */
    fun set(context: Context, icon: Icon, hide: Boolean, how: String): Boolean {
        val before = list(context)
        remember(context, before)
        val next = toggled(before, icon, hide)
        val ok = write(context, next) && (icon in hidden(context)) == hide
        if (ok) prefs(context).edit().putStringSet("wanted", hiddenIn(next).map { it.name }.toSet()).apply()
        LogRepository.from(context).add("상태 표시줄 아이콘($how): ${icon.label} ${if (hide) "숨김" else "보임"}${if (ok) "" else " 실패(시스템 설정 권한 없음)"}")
        return ok
    }

    /**
     * 모두 보이기: GOAT가 아는 이름은 처음 상태로 돌리고(처음부터 숨겨져 있던 것만 남김), 그 밖의 이름(다른 앱이 넣은 것)은
     * 지금 그대로 둔다. 처음에 키가 없었고 결과도 비면 키를 비운다(null).
     */
    fun restore(context: Context, how: String): Boolean {
        val p = prefs(context)
        val before = hidden(context)
        val original = if (p.getBoolean("saved", false)) parse(p.getString("original", null)) else emptyList()
        val target = restoreTarget(list(context), original)
        val hadKey = p.getBoolean("had", true)
        val ok = runCatching {
            Settings.Secure.putString(context.contentResolver, KEY, if (target.isEmpty() && !hadKey) null else target.joinToString(","))
        }.getOrDefault(false)
        // 처음 값·숨기기로 한 것만 잊는다(사용자가 정한 '재부팅 뒤 다시 숨기기'는 그대로)
        if (ok) p.edit().remove("saved").remove("original").remove("had").remove("wanted").apply()
        LogRepository.from(context).add(
            "상태 표시줄 아이콘 모두 보이기($how): 숨김 ${before.size}개 → ${hidden(context).size}개${if (ok) "" else " 실패"}",
        )
        return ok
    }

    /** [restore]의 목표 목록: 지금 목록 − GOAT가 아는 이름 전체 + (처음 목록 ∩ GOAT가 아는 이름). 순서는 지금 목록을 따른다. */
    internal fun restoreTarget(current: List<String>, original: List<String>): List<String> {
        val known = Icon.entries.flatMap { it.slots }.toSet()
        return (current.filterNot { it in known } + original.filter { it in known }).distinct()
    }

    /** 사용자가 숨기기로 한 아이콘(다시 숨기기의 기준). */
    fun wanted(context: Context): Set<Icon> =
        prefs(context).getStringSet("wanted", null)?.mapNotNull { n -> Icon.entries.firstOrNull { it.name == n } }?.toSet().orEmpty()

    /** 재부팅 뒤 목록이 비워지면 다시 숨긴다. */
    fun keep(context: Context) = prefs(context).getBoolean("keep", true)

    fun setKeep(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("keep", on).apply()
        LogRepository.from(context).add("상태 표시줄 아이콘: 재부팅 뒤 다시 숨기기 ${if (on) "켜짐" else "꺼짐"}")
    }

    /**
     * 부팅 직후 부른다(BootReceiver): 숨기기로 한 아이콘이 있는데 목록에 GOAT가 아는 이름이 하나도 없으면(재부팅으로 비워짐)
     * 다시 숨긴다. 일부만 빠진 경우는 사용자가 앱 밖에서 다시 보이게 한 것으로 보고 건드리지 않는다.
     */
    fun reapply(context: Context, how: String) {
        if (!keep(context)) return
        val want = wanted(context)
        val now = list(context)
        if (!needsReapply(want, now)) return
        val next = want.fold(now) { acc, icon -> toggled(acc, icon, true) }
        val ok = write(context, next)
        LogRepository.from(context).add("상태 표시줄 아이콘 다시 숨기기($how): ${want.joinToString { it.label }}${if (ok) "" else " 실패(시스템 설정 권한 없음)"}")
    }

    /** [reapply] 조건: 숨기기로 한 것이 있고, 지금 목록에 GOAT가 아는 이름이 하나도 없다. */
    internal fun needsReapply(want: Set<Icon>, now: List<String>): Boolean = want.isNotEmpty() && hiddenIn(now).isEmpty()

    private fun remember(context: Context, current: List<String>) {
        val p = prefs(context)
        if (p.getBoolean("saved", false)) return
        val had = runCatching { Settings.Secure.getString(context.contentResolver, KEY) }.getOrNull() != null
        p.edit().putBoolean("saved", true).putBoolean("had", had).putString("original", current.joinToString(",")).apply()
    }

    /** 목록을 쓴다. 비면 키에 빈 값을 둔다(시스템 기본 = 아무것도 숨기지 않음). */
    private fun write(context: Context, list: List<String>): Boolean =
        runCatching { Settings.Secure.putString(context.contentResolver, KEY, list.joinToString(",")) }.getOrDefault(false)

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
