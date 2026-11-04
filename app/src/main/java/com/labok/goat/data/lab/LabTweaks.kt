package com.labok.goat.data.lab

import android.content.Context
import android.provider.Settings
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.text.Copy

/**
 * 실험실 설정 하나(시스템 설정 표·키). 처음 바꾸기 전 값(없던 키는 없었다는 사실까지)을 남겨 [restore]로 그대로 되돌린다.
 * secure·global은 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로 쓰고, 막히거나 system 표면 Shizuku 셸 `settings put`으로 쓴다.
 * 쓰고 나서 다시 읽어 실제로 바뀌었는지 확인한다.
 */
class LabSetting(private val table: String, val key: String, private val label: String) {
    private val pref = "lab_$table.$key"

    fun read(context: Context): String? = runCatching {
        val r = context.contentResolver
        when (table) {
            "global" -> Settings.Global.getString(r, key)
            "secure" -> Settings.Secure.getString(r, key)
            else -> Settings.System.getString(r, key)
        }
    }.getOrNull()

    /** [value]로 바꾼다(null이면 키를 지운다). 다시 읽어 같으면 true. 작업 스레드에서 부른다. */
    fun write(context: Context, value: String?): Boolean {
        remember(context)
        val ok = put(context, value)
        LogRepository.from(context).add("실험실 $label: ${value ?: "(지움)"}" + if (ok) "" else " 실패")
        return ok
    }

    /** GOAT로 바꾼 적이 있는지(되돌릴 값이 남아 있는지). */
    fun changed(context: Context) = prefs(context).getBoolean("saved", false)

    /** 처음 값으로 되돌린다. 바꾼 적이 없으면 아무것도 하지 않고 true. */
    fun restore(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean("saved", false)) return true
        val original = if (p.contains("value")) p.getString("value", null) else null
        val ok = put(context, original)
        if (ok) p.edit().clear().apply()
        LogRepository.from(context).add("실험실 $label: 처음 값으로 되돌림" + if (ok) "" else " 실패")
        return ok
    }

    private fun put(context: Context, value: String?): Boolean {
        val r = context.contentResolver
        if (table != "system") {
            runCatching {
                when {
                    value == null -> Unit
                    table == "global" -> Settings.Global.putString(r, key, value)
                    else -> Settings.Secure.putString(r, key, value)
                }
            }
            if (value != null && read(context) == value) return true
        }
        val cmd = if (value == null) "settings delete $table $key" else "settings put $table $key ${shellQuote(value)}"
        ShizukuAccess.shell(cmd)
        return read(context) == value
    }

    private fun remember(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("saved", false)) return
        val now = read(context)
        p.edit().putBoolean("saved", true).apply { if (now != null) putString("value", now) }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(pref, Context.MODE_PRIVATE)

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

/**
 * 비행기 모드 때 함께 꺼지는 무선(`airplane_mode_radios`, 쉼표 목록). 목록에서 빼면 비행기 모드를 켜도 그 무선은 그대로 둔다.
 * 키가 없으면 안드로이드 기본값([DEFAULT])을 쓰는 것과 같다.
 */
object AirplaneRadios {
    const val DEFAULT = "cell,bluetooth,wifi,nfc,wimax"
    val setting = LabSetting("global", "airplane_mode_radios", "비행기 모드 무선")

    /** 사용자가 고를 수 있는 무선(셀룰러는 비행기 모드의 뜻이라 늘 끈다). */
    enum class Radio(val id: String, private val labelRes: Int) {
        WIFI("wifi", R.string.labdata_name_wifi),
        BLUETOOTH("bluetooth", R.string.labdata_name_bluetooth),
        NFC("nfc", R.string.labdata_name_nfc);

        val label: String get() = Copy.text(labelRes)
    }

    fun list(raw: String?): List<String> = (raw ?: DEFAULT).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** [radio]가 비행기 모드 때 그대로 켜져 있는지(목록에 없으면 유지). */
    fun kept(raw: String?, radio: Radio) = radio.id !in list(raw)

    /** [radio]를 유지할지([keep]) 바꾼 새 목록. 순서는 그대로 두고 셀룰러는 늘 남긴다. */
    fun edit(raw: String?, radio: Radio, keep: Boolean): String {
        val l = list(raw).toMutableList()
        if (keep) {
            l.remove(radio.id)
        } else if (radio.id !in l) {
            l += radio.id
        }
        if ("cell" !in l) l.add(0, "cell")
        return l.joinToString(",")
    }
}

/**
 * 상태 표시줄 아이콘 숨기기(`icon_blacklist`, 쉼표 목록의 아이콘 자리 이름). 안드로이드 시스템 UI 튜너가 쓰는 설정이다.
 * One UI 버전에 따라 일부 자리 이름이 다르거나 숨겨지지 않을 수 있다.
 */
object IconBlacklist {
    val setting = LabSetting("secure", "icon_blacklist", "상태 표시줄 아이콘 숨기기")

    enum class Slot(val id: String, private val labelRes: Int) {
        ALARM("alarm_clock", R.string.labdata_icon_slot_alarm),
        VOLUME("volume", R.string.labdata_icon_slot_volume),
        BLUETOOTH("bluetooth", R.string.labdata_name_bluetooth),
        NFC("nfc", R.string.labdata_name_nfc),
        HOTSPOT("hotspot", R.string.labdata_icon_slot_hotspot),
        ROTATE("rotate", R.string.labdata_icon_slot_rotate),
        HEADSET("headset", R.string.labdata_icon_slot_headset),
        LOCATION("location", R.string.labdata_icon_slot_location),
        DATA_SAVER("data_saver", R.string.labdata_icon_slot_data_saver),
        VPN("vpn", R.string.labdata_icon_slot_vpn),
        ZEN("zen", R.string.labdata_icon_slot_zen),
        VOLTE("ims_volte", R.string.labdata_icon_slot_volte);

        val label: String get() = Copy.text(labelRes)
    }

    fun list(raw: String?): List<String> = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun hidden(raw: String?, slot: Slot) = slot.id in list(raw)

    /** [slot]을 숨길지([hide]) 바꾼 새 목록. 모르는 자리 이름(사용자가 따로 넣은 것)은 그대로 둔다. 비면 null(키를 지운다). */
    fun edit(raw: String?, slot: Slot, hide: Boolean): String? {
        val l = list(raw).toMutableList()
        if (hide) {
            if (slot.id !in l) l += slot.id
        } else {
            l.remove(slot.id)
        }
        return l.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

/**
 * 예전 실험실 '빠른 설정 타일 격자'(1.14.1~1.15.2)가 바꿨던 설정 키. One UI 8.5(안드로이드 17) 실기기는 이 키들을 읽지 않았고,
 * 시스템 UI 리소스 오버레이는 루트가 아닌 셸(Shizuku)에 막혀(Non-root shell cannot fabricate overlays) 실험을 접었다(2026-10-18).
 * 앱을 켤 때 바꿔 둔 값이 남아 있으면 처음 값으로 되돌린다([restoreLeftovers]).
 */
object QsGrid {
    private val all = listOf(
        LabSetting("secure", "quickstar_qs_tile_layout_custom_matrix_width", "빠른 설정 격자 너비"),
        LabSetting("secure", "quickstar_qs_tile_layout_custom_matrix", "빠른 설정 격자 직접 지정"),
        LabSetting("secure", "qs_tile_column", "빠른 설정 한 줄 타일 수"),
        LabSetting("secure", "qs_tile_row", "빠른 설정 타일 줄 수")
    )

    /** 바꿔 둔 값이 남아 있으면 처음 값으로 되돌린다. 작업 스레드에서 부른다. */
    fun restoreLeftovers(context: Context) {
        all.filter { it.changed(context) }.forEach { it.restore(context) }
    }
}
