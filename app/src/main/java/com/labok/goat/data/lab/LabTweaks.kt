package com.labok.goat.data.lab

import android.content.Context
import android.provider.Settings
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

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
    enum class Radio(val id: String, val label: String) { WIFI("wifi", "Wi-Fi"), BLUETOOTH("bluetooth", "블루투스"), NFC("nfc", "NFC") }

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

    enum class Slot(val id: String, val label: String) {
        ALARM("alarm_clock", "알람"),
        VOLUME("volume", "진동·무음"),
        BLUETOOTH("bluetooth", "블루투스"),
        NFC("nfc", "NFC"),
        HOTSPOT("hotspot", "핫스팟"),
        ROTATE("rotate", "화면 회전 잠금"),
        HEADSET("headset", "헤드셋"),
        LOCATION("location", "위치"),
        DATA_SAVER("data_saver", "데이터 절약"),
        VPN("vpn", "VPN"),
        ZEN("zen", "방해 금지"),
        VOLTE("ims_volte", "VoLTE")
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
 * 화면 모드(삼성 디스플레이 색 모드). 삼성은 `system.screen_mode_setting`에 값(0~4)을 두는 것으로 알려져 있지만
 * 값의 뜻은 기종·One UI마다 달라 확정하지 못했다(갤러리에도 근거 없음). 그래서 실험실에서는 이 기기에 있는 키와
 * 지금 값을 보여 주고, 값을 하나씩 바꿔 보며 화면을 확인한 뒤 언제든 처음 값으로 되돌리게 한다.
 * 삼성이 아닌 기기(픽셀 등)는 안드로이드 공통 `secure.display_color_mode`(0 자연, 1 향상, 2 채도, 3 자동)를 쓴다.
 */
object ScreenMode {
    val samsung = LabSetting("system", "screen_mode_setting", "화면 모드(삼성)")
    val aosp = LabSetting("secure", "display_color_mode", "화면 색 모드")

    /**
     * 고를 수 있는 값. 삼성 값의 뜻은 삼성 프레임워크 SemMdnieManager 상수에서 확인했다:
     * 0 AMOLED 시네마, 1 AMOLED 사진, 2 기본, 3 자연, 4 적응형(설정 앱의 '선명한 화면'), 5 읽기.
     */
    val SAMSUNG_VALUES = listOf("4", "3", "0", "1", "2", "5")
    val AOSP_VALUES = listOf("0" to "자연", "1" to "향상", "2" to "채도", "3" to "자동")

    /** 이 기기에서 쓸 설정: 삼성 키가 있으면 삼성, 아니면 공통 키. 둘 다 없으면 null. */
    fun active(context: Context): LabSetting? = when {
        samsung.read(context) != null -> samsung
        aosp.read(context) != null -> aosp
        else -> null
    }
}

/**
 * 빠른 설정 타일 격자. 예전 One UI는 `qs_tile_column`·`qs_tile_row`(secure)를 읽었지만 One UI 8.5(안드로이드 17) 실기기에서는
 * 값을 써도 패널이 바뀌지 않았다(2026-10-18 진단). 그 기기에는 QuickStar가 쓰는 `quickstar_qs_tile_layout_custom_matrix`(켬)와
 * `quickstar_qs_tile_layout_custom_matrix_width`가 있었지만 QuickStar가 값을 되돌려 써 이것도 통하지 않았다. 지금 격자는
 * [QsOverlay]로 바꾸고, 여기 키들은 예전에 바꿔 본 값을 [처음대로]로 되돌리는 데만 쓴다.
 */
object QsGrid {
    val column = LabSetting("secure", "qs_tile_column", "빠른 설정 한 줄 타일 수")
    val row = LabSetting("secure", "qs_tile_row", "빠른 설정 타일 줄 수")
    val matrix = LabSetting("secure", "quickstar_qs_tile_layout_custom_matrix", "빠른 설정 격자 직접 지정")
    val matrixWidth = LabSetting("secure", "quickstar_qs_tile_layout_custom_matrix_width", "빠른 설정 격자 너비")
    /** [처음대로]로 되돌릴 값 전부(예전 키도 바꾼 적이 있으면 함께 되돌린다). */
    val all = listOf(matrixWidth, matrix, column, row)

    /** 시스템 UI를 다시 띄워 격자 값을 다시 읽게 한다(상태 표시줄·빠른 패널이 잠깐 사라졌다 돌아온다). */
    // force-stop은 상주 앱인 시스템 UI에 듣지 않아(에뮬레이터 확인) 강제 종료 신호로 다시 띄운다
    fun restartSystemUi(): Boolean = ShizukuAccess.shell("am crash com.android.systemui")?.ok == true

    /**
     * 진단 로그용: 시스템 UI가 격자를 어느 값에서 읽는지 찾으려고 빠른 패널과 관련된 설정 키, 시스템 UI 버전,
     * 시스템 UI 상태(dumpsys)에서 격자·타일 줄을 모은다. Shizuku가 없으면 그렇다고만 적는다. 작업 스레드에서 부른다.
     */
    fun diagnose(): String {
        val pat = "'qs|tile|quick|panel|column|grid|sysui'"
        val dumpPat = "'column|columns|maxtiles|max_tiles|tile_size|tilesize|grid|qs_|qspanel|tilelayout|sidelabel|span'"
        val out = ShizukuAccess.shell(
            "for t in secure system global; do echo \"[\$t]\"; settings list \$t | grep -i -E $pat; done; " +
                "echo '[systemui]'; dumpsys package com.android.systemui 2>/dev/null | grep -m2 -E 'versionName|versionCode'; " +
                "echo '[dumpsys systemui]'; dumpsys activity service com.android.systemui/.SystemUIService 2>/dev/null | grep -i -E $dumpPat | head -150"
        ) ?: return "Shizuku가 연결되지 않아 읽지 못했습니다."
        return (out.out + if (out.err.isNotBlank()) "\n(오류) ${out.err.take(500)}" else "").ifBlank { "(빈 결과)" }
    }
}
