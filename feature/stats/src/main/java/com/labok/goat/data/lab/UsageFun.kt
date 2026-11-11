package com.labok.goat.data.lab

import com.labok.goat.core.R
import com.labok.goat.ui.text.Copy
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
            r.unfolds?.takeIf { it > 0 }?.let { add(Triple(Copy.text(R.string.labdata_usage_headline_unfolds, it), it, base?.unfolds)) }
            if (r.unlocks > 0) add(Triple(Copy.text(R.string.labdata_usage_headline_unlocks, r.unlocks), r.unlocks, base?.unlocks))
            if (r.screenMs > 0) add(Triple(Copy.text(R.string.labdata_usage_headline_screen, duration(r.screenMs)), (r.screenMs / 60_000).toInt(), base?.let { (it.screenMs / 60_000).toInt() }))
            if (r.notifications > 0) add(Triple(Copy.text(R.string.labdata_usage_headline_notifications, r.notifications), r.notifications, base?.notifications))
            r.volume?.takeIf { it > 0 }?.let { add(Triple(Copy.text(R.string.labdata_usage_headline_volume, it), it, base?.volume)) }
            if (r.appOpens > 0) add(Triple(Copy.text(R.string.labdata_usage_headline_app_opens, r.appOpens), r.appOpens, base?.appOpens))
        }
        // 기준보다 늘어난 비율이 큰 것부터. 기준이 없으면 넣은 순서(펼치기·잠금 해제가 먼저)
        return items.sortedByDescending { (_, now, before) -> if (before == null || before <= 0) 0.0 else now.toDouble() / before }
            .take(2).map { it.first }
    }

    /** 재미로 보는 숫자. 보여 줄 만한 것만(값이 없으면 뺀다). */
    fun facts(r: UsageTally.Result, topApp: Pair<String, Int>?, topNoti: Pair<String, Int>?): List<String> = buildList {
        val peak = r.hourlyMs.indices.maxByOrNull { r.hourlyMs[it] }
        if (peak != null && r.hourlyMs[peak] > 0) add(Copy.text(R.string.labdata_usage_fact_peak_hour, hourName(peak)))
        if (r.unlocks > 0 && r.screenMs > 0) add(Copy.text(R.string.labdata_usage_fact_per_unlock, duration(r.screenMs / r.unlocks)))
        if (r.screenOn >= 3 && r.appOpens > 0) add(Copy.text(R.string.labdata_usage_fact_apps_per_screen_on, oneDecimal(r.appOpens.toFloat() / r.screenOn)))
        topApp?.let { (name, n) -> if (r.appOpens > 0) add(Copy.text(R.string.labdata_usage_fact_top_app, name, n * 100 / r.appOpens)) }
        topNoti?.let { (name, n) -> if (r.notifications > 0) add(Copy.text(R.string.labdata_usage_fact_top_noti, r.notifications, n * 100 / r.notifications, name)) }
    }

    /** 오늘의 칭호(최대 3개). [days]는 기간의 날 수로, 기준을 하루 평균으로 맞춘다. */
    fun titles(r: UsageTally.Result, days: Int): List<Title> {
        val d = days.coerceAtLeast(1)
        val night = (0..3).sumOf { r.hourlyMs[it] } / d
        val screenPerDay = r.screenMs / d
        return buildList {
            r.unfolds?.let { if (it / d >= 30) add(Title(Copy.text(R.string.labdata_usage_title_fold_master), Copy.text(R.string.labdata_usage_title_fold_master_why, it / d))) }
            if (night >= 30 * 60_000L) add(Title(Copy.text(R.string.labdata_usage_title_night_owl), Copy.text(R.string.labdata_usage_title_night_owl_why, duration(night))))
            if (r.notifications / d >= 150) add(Title(Copy.text(R.string.labdata_usage_title_noti_rich), Copy.text(R.string.labdata_usage_title_noti_rich_why, r.notifications / d)))
            r.volume?.let { if (it / d >= 100) add(Title(Copy.text(R.string.labdata_usage_title_volume_master), Copy.text(R.string.labdata_usage_title_volume_master_why, it / d))) }
            if (r.unlocks / d >= 80) add(Title(Copy.text(R.string.labdata_usage_title_unlock_master), Copy.text(R.string.labdata_usage_title_unlock_master_why, r.unlocks / d)))
            r.chargeStartAvg?.let {
                if (it < 15) {
                    add(Title(Copy.text(R.string.labdata_usage_title_battery_edge), Copy.text(R.string.labdata_usage_title_charge_avg_why, it)))
                } else if (it >= 40) {
                    add(Title(Copy.text(R.string.labdata_usage_title_charge_model), Copy.text(R.string.labdata_usage_title_charge_avg_why, it)))
                }
            }
            if (screenPerDay >= 6 * 3_600_000L) {
                add(Title(Copy.text(R.string.labdata_usage_title_screen_keeper), Copy.text(R.string.labdata_usage_title_per_day_why, duration(screenPerDay))))
            } else if (r.screenMs > 0 && screenPerDay < 2 * 3_600_000L) {
                add(Title(Copy.text(R.string.labdata_usage_title_phone_rest), Copy.text(R.string.labdata_usage_title_per_day_why, duration(screenPerDay))))
            }
        }.take(3)
    }

    /** 기준 대비 변화 표시: "▲12", "▼8". 기준이 없거나 같으면 null. */
    fun delta(now: Int?, before: Int?): String? {
        if (now == null || before == null || now == before) return null
        return if (now > before) "▲${now - before}" else "▼${before - now}"
    }

    fun hourName(h: Int): String = when (h) {
        in 0..4 -> Copy.text(R.string.labdata_usage_hour_dawn, h)
        in 5..11 -> Copy.text(R.string.labdata_usage_hour_morning, h)
        12 -> Copy.text(R.string.labdata_usage_hour_noon)
        in 13..17 -> Copy.text(R.string.labdata_usage_hour_afternoon, h - 12)
        else -> Copy.text(R.string.labdata_usage_hour_evening, h - 12)
    }

    fun duration(ms: Long): String {
        val m = ms / 60_000
        return when {
            m >= 60 -> Copy.text(R.string.labdata_usage_duration_hours_minutes, m / 60, m % 60)
            m >= 1 -> Copy.text(R.string.labdata_usage_duration_minutes, m)
            else -> Copy.text(R.string.labdata_usage_duration_seconds, ms / 1000)
        }
    }

    private fun oneDecimal(v: Float) = ((v * 10).roundToInt() / 10f).toString().removeSuffix(".0")
}
