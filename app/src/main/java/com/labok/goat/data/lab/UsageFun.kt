package com.labok.goat.data.lab

import kotlin.math.roundToInt

/**
 * 사용 통계의 '재미' 부분(11-04 소장님 "1번 위주지만 재미도 살짝 더해서"): 한 줄 요약, 재미로 보는 숫자, 오늘의 칭호.
 * 숫자는 [UsageTally.Result]에서만 만들고, 여러 날을 볼 때는 하루 평균으로 맞춰 판단한다.
 */
object UsageFun {
    data class Title(val name: String, val why: String)

    /** 맨 위 한 줄 요약. 이번 기간에서 가장 눈에 띄는 것 두 가지(기준 기간보다 가장 많이 늘어난 것 먼저). */
    fun headline(r: UsageTally.Result, base: UsageTally.Result?): List<String> {
        val items = buildList {
            r.unfolds?.takeIf { it > 0 }?.let { add(Triple("폰을 ${it}번 펼쳤습니다", it, base?.unfolds)) }
            if (r.unlocks > 0) add(Triple("잠금을 ${r.unlocks}번 풀었습니다", r.unlocks, base?.unlocks))
            if (r.screenMs > 0) add(Triple("화면을 ${duration(r.screenMs)} 봤습니다", (r.screenMs / 60_000).toInt(), base?.let { (it.screenMs / 60_000).toInt() }))
            if (r.notifications > 0) add(Triple("알림을 ${r.notifications}개 받았습니다", r.notifications, base?.notifications))
            r.volume?.takeIf { it > 0 }?.let { add(Triple("볼륨 버튼을 ${it}번 눌렀습니다", it, base?.volume)) }
            if (r.appOpens > 0) add(Triple("앱을 ${r.appOpens}번 열었습니다", r.appOpens, base?.appOpens))
        }
        // 기준보다 늘어난 비율이 큰 것부터. 기준이 없으면 넣은 순서(펼치기·잠금 해제가 먼저)
        return items.sortedByDescending { (_, now, before) -> if (before == null || before <= 0) 0.0 else now.toDouble() / before }
            .take(2).map { it.first }
    }

    /** 재미로 보는 숫자. 보여 줄 만한 것만(값이 없으면 뺀다). */
    fun facts(r: UsageTally.Result, topApp: Pair<String, Int>?, topNoti: Pair<String, Int>?): List<String> = buildList {
        val peak = r.hourlyMs.indices.maxByOrNull { r.hourlyMs[it] }
        if (peak != null && r.hourlyMs[peak] > 0) add("가장 많이 본 때는 ${hourName(peak)}입니다.")
        if (r.unlocks > 0 && r.screenMs > 0) add("잠금을 한 번 풀면 평균 ${duration(r.screenMs / r.unlocks)} 봅니다.")
        if (r.screenOn >= 3 && r.appOpens > 0) add("화면을 한 번 켤 때마다 앱을 평균 ${oneDecimal(r.appOpens.toFloat() / r.screenOn)}개 엽니다.")
        topApp?.let { (name, n) -> if (r.appOpens > 0) add("$name 실행이 전체의 ${n * 100 / r.appOpens}%입니다.") }
        topNoti?.let { (name, n) -> if (r.notifications > 0) add("알림 ${r.notifications}개 중 ${n * 100 / r.notifications}%가 $name 알림입니다.") }
        r.chargeStartAvg?.let { add("충전기는 평균 $it%에서 꽂았습니다.") }
    }

    /** 오늘의 칭호(최대 3개). [days]는 기간의 날 수로, 기준을 하루 평균으로 맞춘다. */
    fun titles(r: UsageTally.Result, days: Int): List<Title> {
        val d = days.coerceAtLeast(1)
        val night = (0..3).sumOf { r.hourlyMs[it] } / d
        val screenPerDay = r.screenMs / d
        return buildList {
            r.unfolds?.let { if (it / d >= 30) add(Title("폴드 장인", "하루 ${it / d}번 펼침")) }
            if (night >= 30 * 60_000L) add(Title("올빼미", "새벽 0~4시에 ${duration(night)}"))
            if (r.notifications / d >= 150) add(Title("알림 부자", "하루 알림 ${r.notifications / d}개"))
            r.volume?.let { if (it / d >= 100) add(Title("볼륨 장인", "하루 ${it / d}번 누름")) }
            if (r.unlocks / d >= 80) add(Title("잠금 해제 달인", "하루 ${r.unlocks / d}번"))
            r.chargeStartAvg?.let {
                if (it < 15) {
                    add(Title("배터리 벼랑 끝", "평균 $it%에서 충전"))
                } else if (it >= 40) {
                    add(Title("충전 모범생", "평균 $it%에서 충전"))
                }
            }
            if (screenPerDay >= 6 * 3_600_000L) {
                add(Title("화면 지킴이", "하루 ${duration(screenPerDay)}"))
            } else if (r.screenMs > 0 && screenPerDay < 2 * 3_600_000L) {
                add(Title("폰과 휴식 중", "하루 ${duration(screenPerDay)}"))
            }
        }.take(3)
    }

    /** 기준 대비 변화 표시: "▲12", "▼8". 기준이 없거나 같으면 null. */
    fun delta(now: Int?, before: Int?): String? {
        if (now == null || before == null || now == before) return null
        return if (now > before) "▲${now - before}" else "▼${before - now}"
    }

    internal fun hourName(h: Int): String = when (h) {
        in 0..4 -> "새벽 ${h}시"
        in 5..11 -> "오전 ${h}시"
        12 -> "낮 12시"
        in 13..17 -> "오후 ${h - 12}시"
        else -> "저녁 ${h - 12}시"
    } + "대"

    internal fun duration(ms: Long): String {
        val m = ms / 60_000
        return when {
            m >= 60 -> "${m / 60}시간 ${m % 60}분"
            m >= 1 -> "${m}분"
            else -> "${ms / 1000}초"
        }
    }

    private fun oneDecimal(v: Float) = ((v * 10).roundToInt() / 10f).toString().removeSuffix(".0")
}
