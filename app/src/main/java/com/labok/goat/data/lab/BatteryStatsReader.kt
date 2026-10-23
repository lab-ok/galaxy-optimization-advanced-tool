package com.labok.goat.data.lab

import android.content.Context
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 실험실 배터리 모니터링의 '마지막 충전 이후': 시스템 배터리 통계(`dumpsys batterystats -c --charged`, Shizuku 셸)를 읽는다.
 * 기준은 시스템이 통계를 새로 시작한 때다(거의 완충한 뒤 충전기를 뺀 때, 삼성 설정의 '마지막 충전 이후'와 같은 기준).
 * `-c`는 지금 통계를 읽기만 한다(`--checkin`은 지난 통계 파일을 지우므로 쓰지 않는다).
 * 줄 형식은 AOSP BatteryStats.dumpCheckinLocked: `9,<uid>,l,<종류>,값...`
 */
object BatteryStatsReader {
    /** 앱(uid) 하나의 사용량. [mah]는 시스템 추정 소모량, [frontMs]는 화면 앞에 띄운 시간, [backMs]는 뒤에서 돈 시간(포그라운드 서비스 포함). */
    data class AppUse(val uid: Int, val mah: Double, val frontMs: Long, val backMs: Long)

    data class SinceCharge(
        /** 통계를 새로 시작한 시각(벽시계, ms) */
        val startClock: Long,
        /** 충전기 없이 보낸 시간 */
        val onBatteryMs: Long,
        val screenOnMs: Long,
        /** 배터리 용량 추정(mAh) */
        val capacityMah: Int,
        /** 쓴 양(%p): 시스템은 잔량 눈금 사이 값이라 아래·위 두 값을 준다 */
        val usedPctLow: Int,
        val usedPctHigh: Int,
        val screenOnPct: Int,
        val screenOffPct: Int,
        /** 쿨롱 카운터로 잰 소모량(mAh). 기기가 재지 않으면 0 */
        val usedMah: Double,
        val screenOffMah: Double,
        /** 시스템이 앱·항목별로 나눠 계산한 소모량 합(mAh) */
        val computedMah: Double,
        val apps: List<AppUse>,
        /** 기기 전체를 부품·항목별로 나눈 소모량(이름표 → mAh). 앱 몫도 들어 있어 앱 목록과 더하지 않는다 */
        val system: Map<String, Double>
    ) {
        val screenOffMs: Long get() = (onBatteryMs - screenOnMs).coerceAtLeast(0)
    }

    private const val CMD = "dumpsys batterystats -c --charged | grep -E '^9,[0-9]+,l,(bt|m|dc|pws|pwi|fg|st),'"

    /** Shizuku로 읽는다. Shizuku가 없거나 형식이 맞지 않으면 null. */
    fun read(): SinceCharge? = ShizukuAccess.shell(CMD)?.takeIf { it.ok }?.out?.let(::parse)

    internal fun parse(text: String): SinceCharge? {
        var bt: List<String>? = null
        var misc: List<String>? = null
        var dc: List<String>? = null
        var pws: List<String>? = null
        val uidMah = HashMap<Int, Double>()
        val front = HashMap<Int, Long>()
        val back = HashMap<Int, Long>()
        val system = LinkedHashMap<String, Double>()
        for (line in text.lineSequence()) {
            val f = line.trim().split(',')
            if (f.size < 5 || f[0] != "9" || f[2] != "l") continue
            val uid = f[1].toIntOrNull() ?: continue
            val v = f.drop(4)
            when (f[3]) {
                "bt" -> bt = v
                "m" -> misc = v
                "dc" -> dc = v
                "pws" -> pws = v
                "pwi" -> {
                    // pwi,<이름표>,<mAh>,<숨김>,... 앱은 이름표가 uid이고 줄의 uid 칸이 그 앱이다
                    val mah = v.getOrNull(1)?.toDoubleOrNull() ?: continue
                    if (v[0] == "uid") {
                        uidMah[uid] = (uidMah[uid] ?: 0.0) + mah
                    } else if (mah > 0 && v[0] != "???") {
                        system[v[0]] = (system[v[0]] ?: 0.0) + mah
                    }
                }
                // fg,<화면 앞 시간 ms>,<횟수>
                "fg" -> v.getOrNull(0)?.toLongOrNull()?.let { front[uid] = it }
                // st,<top>,<포그라운드 서비스>,<포그라운드>,<백그라운드>,... (ms). 뒤에서 돈 시간 = 포그라운드 서비스 + 백그라운드
                "st" -> back[uid] = (v.getOrNull(1)?.toLongOrNull() ?: 0L) + (v.getOrNull(3)?.toLongOrNull() ?: 0L)
            }
        }
        // bt,<시작 횟수>,<배터리 실시간 ms>,<배터리 가동 ms>,<전체 실시간>,<전체 가동>,<시작 시각>,<화면 꺼짐 실시간>,<화면 꺼짐 가동>,<용량 mAh>,...
        val b = bt ?: return null
        val onBattery = b.getOrNull(1)?.toLongOrNull() ?: return null
        val startClock = b.getOrNull(5)?.toLongOrNull() ?: return null
        val screenOffBt = b.getOrNull(6)?.toLongOrNull()
        // m,<화면 켜짐 ms>,... 없으면 화면 꺼짐 시간에서 거꾸로 구한다
        val screenOn = misc?.getOrNull(0)?.toLongOrNull() ?: screenOffBt?.let { onBattery - it } ?: 0L
        fun d(i: Int) = dc?.getOrNull(i)?.toDoubleOrNull() ?: 0.0
        val apps = uidMah.keys.union(front.keys).map { uid ->
            AppUse(uid, uidMah[uid] ?: 0.0, front[uid] ?: 0L, back[uid] ?: 0L)
        }.filter { it.mah > 0 || it.frontMs > 0 }.sortedWith(compareByDescending<AppUse> { it.mah }.thenByDescending { it.frontMs })
        return SinceCharge(
            startClock = startClock,
            onBatteryMs = onBattery,
            screenOnMs = screenOn.coerceIn(0, onBattery),
            capacityMah = b.getOrNull(8)?.toIntOrNull() ?: 0,
            // dc,<쓴 양 아래>,<위>,<화면 켜짐 %>,<화면 꺼짐 %>,<mAh>,<화면 꺼짐 mAh>,...
            usedPctLow = d(0).toInt(),
            usedPctHigh = d(1).toInt(),
            screenOnPct = d(2).toInt(),
            screenOffPct = d(3).toInt(),
            usedMah = d(4),
            screenOffMah = d(5),
            computedMah = pws?.getOrNull(1)?.toDoubleOrNull() ?: uidMah.values.sum(),
            apps = apps,
            system = system
        )
    }

    /** 시스템 항목 이름표를 한국어로. 모르는 것은 그대로. */
    fun systemLabel(key: String) = when (key) {
        "scrn" -> "화면"
        "cpu" -> "프로세서"
        "cell", "phone" -> "모바일 네트워크·통화 대기"
        "wifi" -> "Wi-Fi"
        "blue" -> "블루투스"
        "idle" -> "기기 대기"
        "camera" -> "카메라"
        "audio" -> "오디오"
        "video" -> "동영상"
        "flashlight" -> "손전등"
        "sensors" -> "센서"
        "gnss" -> "위치(GPS)"
        "memory" -> "메모리"
        "ambi" -> "Always On Display"
        else -> key
    }

    /** 시스템 uid의 이름. 앱 uid(10000 이상)는 null(패키지 이름으로 찾는다). */
    fun systemUidLabel(uid: Int): String? = when {
        uid % 100_000 >= 10_000 -> null
        uid == 0 -> "커널"
        uid == 1000 -> "Android 시스템"
        uid == 1001 -> "전화·통신"
        uid == 1002 -> "블루투스"
        uid == 1010 -> "Wi-Fi"
        uid == 1013 -> "미디어"
        uid == 1041 -> "오디오"
        uid == 1047 -> "카메라"
        else -> "시스템($uid)"
    }

    /**
     * 이 패턴으로 쓰면 완충(100%)으로 갈 시간(ms). 쓴 양이 3%p 미만이면 오차가 커서 null.
     * 쓴 양은 아래·위 값의 가운데를 쓴다.
     */
    fun fullChargeMs(s: SinceCharge): Long? {
        val used = (s.usedPctLow + s.usedPctHigh) / 2.0
        if (used < 3 || s.onBatteryMs <= 0) return null
        return (s.onBatteryMs * 100 / used).toLong()
    }

    /** 시간당 쓴 양(%p). [pct]를 [ms] 동안 썼을 때. 30분이 안 되면 null. */
    fun perHour(pct: Int, ms: Long): Double? = if (ms < 30 * 60_000L) null else pct * 3_600_000.0 / ms

    /** uid의 표시 이름: 앱이면 앱 이름(같은 uid를 쓰는 앱이 여럿이면 첫 앱), 시스템이면 정해 둔 이름. */
    fun uidName(context: Context, uid: Int): String {
        systemUidLabel(uid)?.let { return it }
        val pm = context.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return "삭제된 앱"
        return runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
