package com.labok.goat.data.lab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.labok.goat.R
import com.labok.goat.background.GoatService
import com.labok.goat.data.appsize.AppDensityNotifier
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import java.util.Locale
import kotlin.math.abs

/**
 * 실험실 '충전 실측 용량': 충전하는 동안 실제로 들어간 전류를 쌓아(쿨롱 카운팅) 배터리가 지금 담는 용량을 잰다.
 *
 * 시스템이 주는 남은 전하량(CHARGE_COUNTER)은 삼성 기기에서 '화면 잔량 × 고정 상수'로 만든 값이라 노화를 보여 주지 못한다
 * (삼성 max77705 연료게이지 커널 코드, 실기 기록에서 잔량 1%마다 charge가 정확히 같은 양만큼 바뀜). 그래서 순간 전류(CURRENT_NOW)만 쓴다.
 *
 * 한 번의 충전에서 값을 채택하는 조건(GitHub 배터리 측정 오픈소스들의 조건을 따름, 조사 보고서 2026-10-20):
 * - 충전을 시작하고 [WARMUP_MS](급속 충전이 자리 잡는 시간)가 지난 뒤 잔량이 처음 오른 때부터 잰다. 잔량이 정수라 오른 순간을 경계로 삼는다.
 * - [TOP_LEVEL]% 위(충전 속도를 크게 줄이는 구간)는 뺀다.
 * - 샘플 사이가 [MAX_GAP_MS]보다 벌어지거나 온도가 0~45°C를 벗어나면 그때까지의 구간을 버리고 다음 잔량 상승부터 다시 잰다.
 * - 잰 구간의 잔량 변화가 [MIN_SPAN]%p 이상이어야 한 번으로 친다.
 * 결과는 최근 측정 [KEEP]번까지 저장하고, 화면에는 중앙값을 보인다(3번 이상이면 믿을 만한 값).
 *
 * 측정은 GOAT 상주 서비스([GoatService])가 켜 두고, 충전 중에만 [SAMPLE_MS]마다 전류를 읽는다.
 */
object ChargeMeter {
    const val WARMUP_MS = 180_000L
    const val MAX_GAP_MS = 30_000L
    const val TOP_LEVEL = 95
    const val MIN_SPAN = 20
    const val KEEP = 12
    const val SAMPLE_MS = 5_000L

    private const val PREF = "charge_meter"
    private const val KEY_ON = "on"
    private const val KEY_RESULTS = "results"
    private const val KEY_LIVE = "live"
    private const val KEY_DESIGN = "design"
    private const val KEY_MICRO = "micro"
    private const val CHANNEL_ID = "charge_meter_quiet"

    /** 한 번의 충전에서 잰 값. [capacityMah] = [chargedMah] ÷ (잔량 변화 / 100). */
    data class Result(val time: Long, val fromLevel: Int, val toLevel: Int, val chargedMah: Int, val capacityMah: Int, val maxTempC: Float?) {
        val span: Int get() = toLevel - fromLevel

        fun serialize() = "$time,$fromLevel,$toLevel,$chargedMah,$capacityMah,${maxTempC ?: ""}"

        companion object {
            fun parse(s: String): Result? {
                val p = s.split(',')
                if (p.size < 5) return null
                return Result(
                    p[0].toLongOrNull() ?: return null,
                    p[1].toIntOrNull() ?: return null,
                    p[2].toIntOrNull() ?: return null,
                    p[3].toIntOrNull() ?: return null,
                    p[4].toIntOrNull() ?: return null,
                    p.getOrNull(5)?.toFloatOrNull()
                )
            }
        }
    }

    /** 지금 재고 있는 충전(화면 표시용). [fromLevel]이 null이면 아직 재기 시작 전(첫 3분이나 잔량이 오르기 전). */
    data class Live(val startTime: Long, val startLevel: Int, val level: Int, val fromLevel: Int?, val toLevel: Int?, val chargedMah: Int, val currentMa: Int?) {
        val span: Int get() = if (fromLevel != null && toLevel != null) toLevel - fromLevel else 0

        fun serialize() = "$startTime,$startLevel,$level,${fromLevel ?: ""},${toLevel ?: ""},$chargedMah,${currentMa ?: ""}"

        companion object {
            fun parse(s: String?): Live? {
                val p = s?.split(',') ?: return null
                if (p.size < 7) return null
                return Live(
                    p[0].toLongOrNull() ?: return null,
                    p[1].toIntOrNull() ?: return null,
                    p[2].toIntOrNull() ?: return null,
                    p[3].toIntOrNull(),
                    p[4].toIntOrNull(),
                    p[5].toIntOrNull() ?: 0,
                    p[6].toIntOrNull()
                )
            }
        }
    }

    /**
     * 한 번의 충전을 쌓는 계산기(기기와 무관, 단위 테스트 대상). [add]에 시각·잔량·전류(mA, 기기가 준 부호 그대로)·온도를 차례로 넣는다.
     * 전류 부호는 기기마다 달라(충전이 + 또는 -) 쌓은 값의 절댓값을 쓴다.
     */
    class Accumulator(val startTime: Long, val startLevel: Int) {
        private var lastTime = startTime
        private var lastMa: Int? = null
        private var lastLevel = startLevel
        private var total = 0.0 // mAh, 부호 그대로
        private var fromLevel: Int? = null
        private var fromMah = 0.0
        private var toLevel: Int? = null
        private var toMah = 0.0
        private var maxTemp: Float? = null
        var level = startLevel
            private set
        var currentMa: Int? = null
            private set

        fun add(time: Long, level: Int, ma: Int?, tempC: Float?) {
            val dt = time - lastTime
            val bad = dt > MAX_GAP_MS || (tempC != null && (tempC < 0f || tempC > 45f)) || ma == null
            if (bad) {
                // 빠진 구간은 쌓을 수 없다: 지금까지의 구간을 버리고 다음 잔량 상승부터 다시 잰다
                fromLevel = null
                toLevel = null
            } else {
                val prev = lastMa ?: ma!!
                total += (prev + ma!!) / 2.0 * dt / 3_600_000.0
            }
            if (tempC != null && !bad) maxTemp = maxOf(maxTemp ?: tempC, tempC)
            if (level > lastLevel && time - startTime >= WARMUP_MS && level <= TOP_LEVEL && !bad) {
                if (fromLevel == null) {
                    fromLevel = level
                    fromMah = total
                } else {
                    toLevel = level
                    toMah = total
                }
            }
            lastTime = time
            lastMa = ma
            lastLevel = level
            this.level = level
            currentMa = ma
        }

        /** 지금까지 잰 구간에 들어간 양(mAh). */
        val chargedMah: Int get() = if (toLevel != null) abs(toMah - fromMah).toInt() else 0

        fun live() = Live(startTime, startLevel, level, fromLevel, toLevel, chargedMah, currentMa)

        /** 조건을 채웠으면 결과, 아니면 null. [designMah]를 알면 그 40~130% 밖의 값도 버린다(단위·부호를 잘못 읽은 경우). */
        fun result(now: Long, designMah: Int?): Result? {
            val from = fromLevel ?: return null
            val to = toLevel ?: return null
            if (to - from < MIN_SPAN) return null
            val charged = abs(toMah - fromMah)
            val capacity = (charged * 100 / (to - from)).toInt()
            val ok = if (designMah != null) capacity in designMah * 40 / 100..designMah * 130 / 100 else capacity in 1_000..20_000
            if (!ok) return null
            return Result(now, from, to, charged.toInt(), capacity, maxTemp)
        }
    }

    /** 결과들의 중앙값(mAh). 없으면 null. */
    fun median(results: List<Result>): Int? {
        if (results.isEmpty()) return null
        val s = results.map { it.capacityMah }.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /**
     * 전류 원값을 mA로. BATTERY_PROPERTY_CURRENT_NOW는 µA로 정해져 있지만 삼성 등은 mA로 준다.
     * 절댓값이 20,000을 한 번이라도 넘은 기기는 µA로 본다([micro], 폰 충전 전류가 20A를 넘을 일은 없다).
     * 충전 중 전류는 대개 수백 mA 이상이라 첫 샘플에서 단위가 정해진다.
     */
    internal fun toMa(raw: Int, micro: Boolean): Int? {
        if (raw == 0 || raw == Int.MIN_VALUE) return null
        return if (micro || abs(raw) > 20_000) raw / 1000 else raw
    }

    /**
     * 설계 용량(mAh): 삼성 `dumpsys batterystats --usage`의 `Typical:`(표기 용량), 없으면 `Rated:`(정격).
     * 같은 줄의 `Capacity:`는 설계값이 아니라 학습 용량이라 쓰지 않는다(AOSP BatteryStats.getBatteryCapacity).
     */
    internal fun parseDesign(dump: String): Int? {
        fun num(key: String) = Regex("""$key\s*[:=]\s*\[?(\d+)""").find(dump)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 500..20_000 }
        return num("Typical") ?: num("Rated")
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply { if (!on) remove(KEY_LIVE) }.apply()
        LogRepository.from(context).add("충전 실측 용량(실험실): ${if (on) "켬" else "끔"}")
        GoatService.sync(context)
    }

    fun results(context: Context): List<Result> =
        prefs(context).getString(KEY_RESULTS, "").orEmpty().split(';').mapNotNull { Result.parse(it) }

    fun live(context: Context): Live? = Live.parse(prefs(context).getString(KEY_LIVE, null))

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_RESULTS).apply()
        LogRepository.from(context).add("충전 실측 용량(실험실): 측정 기록 지움")
    }

    /** 설계 용량(작업 스레드). 한 번 읽으면 기억한다. Shizuku가 없으면 null. */
    fun design(context: Context): Int? {
        prefs(context).getInt(KEY_DESIGN, 0).takeIf { it > 0 }?.let { return it }
        val d = ShizukuAccess.shell("dumpsys batterystats --usage | grep -m1 'Capacity:'")?.out?.let(::parseDesign) ?: return null
        prefs(context).edit().putInt(KEY_DESIGN, d).apply()
        return d
    }

    private fun save(context: Context, r: Result) {
        val list = (results(context) + r).takeLast(KEEP)
        prefs(context).edit().putString(KEY_RESULTS, list.joinToString(";") { it.serialize() }).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 충전 실측만 켜져 있을 때의 상주 알림. 충전 중이면 지금까지 잰 양을 보인다. */
    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "충전 실측 용량", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 30, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val l = live(context)
        val text = when {
            l == null -> "충전을 시작하면 실제로 들어가는 양을 잽니다"
            l.fromLevel == null -> "충전 중 · 잔량이 오르면 재기 시작합니다"
            else -> String.format(Locale.KOREA, "충전 실측 중 · %d%% → %d%% · %,dmAh", l.fromLevel, l.toLevel ?: l.fromLevel, l.chargedMah)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle("충전 실측 용량")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    /** 상주 서비스 안에서 도는 측정기. 배터리 방송으로 충전 시작·끝을 알고, 충전 중에만 [SAMPLE_MS]마다 전류를 읽는다. */
    class Engine(private val context: Context) {
        private val handler = Handler(Looper.getMainLooper())
        private var acc: Accumulator? = null
        private var lastLevel = -1
        private var micro = prefs(context).getBoolean(KEY_MICRO, false)
        private val tick = object : Runnable {
            override fun run() {
                sample()
                if (acc != null) handler.postDelayed(this, SAMPLE_MS)
            }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = onBattery(intent)
        }

        fun start() {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
                ?.let(::onBattery)
        }

        fun stop() {
            runCatching { context.unregisterReceiver(receiver) }
            handler.removeCallbacks(tick)
            acc = null
            prefs(context).edit().remove(KEY_LIVE).apply()
        }

        private fun onBattery(i: Intent) {
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val charging = plugged && status == BatteryManager.BATTERY_STATUS_CHARGING
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (charging && acc == null) {
                acc = Accumulator(System.currentTimeMillis(), level)
                LogRepository.from(context).add("충전 실측 용량(실험실): 충전 시작 $level%")
                handler.post(tick)
            } else if (!charging && acc != null) {
                finish()
            }
        }

        private fun sample() {
            val a = acc ?: return
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it != 0 }?.let { it / 10f }
            val raw = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            if (!micro && raw != Int.MIN_VALUE && abs(raw) > 20_000) {
                micro = true
                prefs(context).edit().putBoolean(KEY_MICRO, true).apply()
            }
            a.add(System.currentTimeMillis(), level, toMa(raw, micro), temp)
            prefs(context).edit().putString(KEY_LIVE, a.live().serialize()).apply()
            if (level != lastLevel) {
                lastLevel = level
                refreshNotification()
            }
        }

        private fun finish() {
            val a = acc ?: return
            acc = null
            handler.removeCallbacks(tick)
            prefs(context).edit().remove(KEY_LIVE).apply()
            Thread {
                val r = a.result(System.currentTimeMillis(), design(context))
                if (r != null) save(context, r)
                LogRepository.from(context).add(
                    "충전 실측 용량(실험실): 충전 끝 ${a.startLevel}% → ${a.level}% · " +
                        (r?.let { "${it.fromLevel}~${it.toLevel}%에 ${it.chargedMah}mAh → 용량 ${it.capacityMah}mAh" } ?: "조건 미달로 버림(${a.live().span}%p)")
                )
                handler.post { refreshNotification() }
            }.start()
        }

        private fun refreshNotification() {
            // 다른 기능의 알림이 앞서면 그 알림을 그대로 둔다
            if (GoatService.notificationOwner(context) != GoatService.Owner.CHARGE) return
            context.getSystemService(NotificationManager::class.java).notify(AppDensityNotifier.NOTIFICATION_ID, buildNotification(context))
        }
    }
}
