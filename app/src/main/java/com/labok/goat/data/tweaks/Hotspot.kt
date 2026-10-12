package com.labok.goat.data.tweaks

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 핫스팟 제한 우회(사용자 책임, 처음 쓸 때 경고): 통신사가 테더링을 따로 정한 APN(dun)으로만 보내게 막아 둔 경우,
 * 지금 쓰는 인터넷 APN과 같은 값으로 유형만 dun인 APN을 하나 더해 테더링도 일반 데이터 경로를 타게 한다.
 *
 * 1순위: Shizuku 셸로 지금 쓰는 APN(preferapn)을 읽어, 같은 값에 이름 [NAME]·유형 dun인 APN을 직접 넣는다.
 * 2순위: 기기가 셸의 APN 쓰기를 막으면, 통신사 기기에서 숨겨진 'APN 추가' 화면을 열고 입력값을 안내한다
 *        (갤럭시 마이너 갤러리에 알려진 방법: APN 유형 칸은 'dun' 글자를 막는 기기가 있어 전각 [DUN_FULLWIDTH]를 넣는다).
 * 되돌리기는 이름이 [NAME]인 APN을 지운다. 지우지 못하면 '모바일 네트워크 설정 초기화'로 원래대로 돌아간다.
 */
object Hotspot {
    const val NAME = "GOAT 테더링"
    /** 전각 dun(일본어 쿼티 자판으로 입력하는 글자). */
    const val DUN_FULLWIDTH = "ｄｕｎ"
    private const val URI = "content://telephony/carriers"
    private const val PREF = "hotspot"

    /** APN 한 줄(셸 content query 결과에서 읽은 값). */
    data class Apn(val values: Map<String, String>) {
        val name get() = values["name"].orEmpty()
        val apn get() = values["apn"].orEmpty()
        val type get() = values["type"].orEmpty()
    }

    /** 새 APN에 옮겨 적을 칸(정수 칸은 :i:, 나머지는 :s:). 이름·유형은 따로 정한다. */
    private val COPY_STRING = listOf("apn", "numeric", "mcc", "mnc", "protocol", "roaming_protocol", "user", "password", "mvno_type", "mvno_match_data")
    private val COPY_INT = listOf("carrier_id", "authtype", "network_type_bitmask", "bearer_bitmask")

    /** `content query` 출력 → APN 목록. 한 줄이 `Row: n k=v, k=v, …`이다. */
    internal fun parse(out: String): List<Apn> = out.lineSequence().filter { it.startsWith("Row:") }.map { line ->
        val body = line.substringAfter(' ').substringAfter(' ')
        val map = linkedMapOf<String, String>()
        // 값에 쉼표가 드물게 들어가므로, ", 이름=" 꼴에서만 나눈다
        Regex("""(?:^|, )([a-z_]+)=""").findAll(body).toList().let { ms ->
            ms.forEachIndexed { i, m ->
                val end = if (i + 1 < ms.size) ms[i + 1].range.first else body.length
                val v = body.substring(m.range.last + 1, end)
                if (v != "NULL") map[m.groupValues[1]] = v
            }
        }
        Apn(map)
    }.toList()

    /** 국내 통신사 기본 인터넷 APN(통신사 번호 → APN). 기기가 APN 목록을 읽지 못하게 막을 때 쓴다. */
    internal val KNOWN = mapOf(
        "45005" to "lte.sktelecom.com", "45011" to "lte.sktelecom.com",
        "45008" to "lte.ktfwing.com", "45002" to "lte.ktfwing.com", "45004" to "lte.ktfwing.com",
        "45006" to "internet.lguplus.co.kr",
    )

    /** 마지막으로 찾은 APN의 출처나 실패 이유(화면 안내용). */
    @Volatile var lastNote: String = ""
        private set

    /**
     * 지금 쓰는 인터넷 APN(작업 스레드). 차례로 시도한다:
     * ① 데이터 SIM의 preferapn(구독 번호별) ② preferapn ③ 지금 통신사 번호(numeric)의 인터넷(default) APN
     * ④ 국내 통신사 기본값([KNOWN]). 모두 실패하면 null.
     */
    fun current(): Apn? {
        val sub = ShizukuAccess.shell("settings get global multi_sim_data_call")?.out?.trim()?.toIntOrNull()
        val numeric = ShizukuAccess.shell("getprop gsm.sim.operator.numeric")?.out?.trim()?.split(',')?.map { it.trim() }?.firstOrNull { it.length in 5..6 }
        fun query(cmd: String) = ShizukuAccess.shell(cmd)?.takeIf { it.ok }?.let { parse(it.out) }.orEmpty()
        val tries = buildList {
            if (sub != null && sub >= 0) add("구독 preferapn" to "content query --uri $URI/preferapn/subId/$sub")
            add("preferapn" to "content query --uri $URI/preferapn")
            if (numeric != null) add("통신사 APN 목록" to "content query --uri $URI --where \"numeric='$numeric'\"")
        }
        for ((label, cmd) in tries) {
            val rows = query(cmd).filter { it.apn.isNotEmpty() && it.name != NAME }
            val pick = rows.firstOrNull { "default" in it.type || it.type.isEmpty() || it.type == "*" } ?: rows.firstOrNull()
            if (pick != null) { lastNote = "${label}에서 찾음(${pick.apn})"; return pick }
        }
        val known = numeric?.let { KNOWN[it] }
        if (numeric != null && known != null) {
            lastNote = "APN 목록을 읽지 못해 통신사 기본값을 씀($known)"
            return Apn(mapOf("apn" to known, "numeric" to numeric, "mcc" to numeric.take(3), "mnc" to numeric.drop(3), "protocol" to "IPV4V6", "roaming_protocol" to "IPV4V6"))
        }
        lastNote = if (numeric == null) "통신사 번호를 읽지 못함(유심 확인)" else "APN 목록을 읽지 못하고 기본값도 모르는 통신사($numeric)"
        return null
    }

    /** GOAT가 넣은 APN(작업 스레드). Shizuku가 없으면 null, 없으면 빈 목록. */
    fun ours(): List<Apn>? = ShizukuAccess.shell("content query --uri $URI --where \"name='$NAME'\"")?.let { if (it.ok || it.out.contains("No result")) parse(it.out) else null }

    /** [base]와 같은 값에 이름 [NAME]·유형 dun인 APN을 넣는 셸 명령(시험할 수 있게 뗐다). */
    internal fun insertCommand(base: Apn): String {
        fun q(v: String) = "'" + v.replace("'", "'\\''") + "'"
        val binds = buildList {
            add("--bind name:s:${q(NAME)}"); add("--bind type:s:dun")
            COPY_STRING.forEach { k -> base.values[k]?.takeIf { it.isNotEmpty() }?.let { add("--bind $k:s:${q(it)}") } }
            COPY_INT.forEach { k -> base.values[k]?.toLongOrNull()?.let { add("--bind $k:i:$it") } }
        }
        return "content insert --uri $URI " + binds.joinToString(" ")
    }

    /** 결과. */
    enum class Result { ADDED, ALREADY, NO_APN, BLOCKED, NO_SHIZUKU }

    /** 지금 APN을 바탕으로 dun APN을 직접 넣는다(작업 스레드). 넣은 뒤 다시 읽어 확인한다. */
    fun add(context: Context): Result {
        val log = LogRepository.from(context)
        val mine = ours() ?: return Result.NO_SHIZUKU
        if (mine.isNotEmpty()) return Result.ALREADY
        val base = current() ?: run { log.add("핫스팟 제한 우회: 지금 쓰는 APN을 찾지 못함($lastNote)"); return Result.NO_APN }
        val r = ShizukuAccess.shell(insertCommand(base))
        val ok = r?.ok == true && ours().orEmpty().isNotEmpty()
        if (ok) setApplied(context, true)
        if (!ok) lastNote = "APN 쓰기가 막힘: " + (r?.err ?: r?.out ?: "").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120)
        log.add("핫스팟 제한 우회: APN 추가 " + if (ok) "성공(${base.apn}, $lastNote)" else "실패($lastNote)")
        return if (ok) Result.ADDED else Result.BLOCKED
    }

    /** GOAT가 넣은(또는 안내대로 직접 넣은) 'GOAT 테더링' APN을 지운다(작업 스레드). 지웠거나 원래 없으면 true. */
    fun remove(context: Context): Boolean {
        val r = ShizukuAccess.shell("content delete --uri $URI --where \"name='$NAME'\"")
        val ok = r?.ok == true && ours().orEmpty().isEmpty()
        if (ok) setApplied(context, false)
        LogRepository.from(context).add("핫스팟 제한 우회: 추가한 APN 지우기 " + if (ok) "성공" else "실패")
        return ok
    }

    /** 되돌리기 2단계: APN 목록 전체를 통신사 기본값으로 복원한다(TelephonyProvider의 restore, 작업 스레드). */
    fun restoreDefaults(context: Context): Boolean {
        val r = ShizukuAccess.shell("content delete --uri $URI/restore")
        val ok = r?.ok == true
        if (ok) setApplied(context, false)
        LogRepository.from(context).add("핫스팟 제한 우회: APN 기본값 복원 " + if (ok) "성공" else "실패(${(r?.err ?: "").take(80)})")
        return ok
    }

    /**
     * 적용 상태: APN 목록을 읽을 수 있으면 그 결과를, 읽지 못하는 기기면 GOAT가 기억한 값([setApplied])을 쓴다.
     * 직접 추가하기로 넣은 뒤 [다 했어요]를 누르면 기억해 두어, 목록을 못 읽는 기기에서도 되돌리기 버튼이 보인다.
     */
    fun applied(context: Context): Boolean? = ours()?.isNotEmpty() ?: prefs(context).getBoolean("applied", false).takeIf { it }

    fun setApplied(context: Context, on: Boolean) = prefs(context).edit().putBoolean("applied", on).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 통신사 기기에서 숨겨진 'APN 추가' 화면을 연다(Shizuku 셸, 작업 스레드). */
    fun openEditor(): Boolean =
        ShizukuAccess.shell("am start -a android.intent.action.INSERT -d $URI --ei simId -1")?.ok == true

    /** 경고를 읽고 동의했는지. */
    fun agreed(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("agreed", false)
    fun agree(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("agreed", true).apply()
}
