package com.local.folddpifix.domain

import kotlin.math.roundToInt

/**
 * 물리 화면 하나. Galaxy Fold는 접고 펼칠 때 기본 display(0) 뒤의 패널이 바뀌므로
 * 해상도(짧은 변×긴 변)로 패널을 구분한다. 해상도는 `wm density`의 영향을 받지 않는다.
 */
data class ScreenInfo(
    /** "짧은변x긴변" 픽셀 */
    val key: String,
    /** 패널의 실제 픽셀 밀도(DisplayMetrics.xdpi/ydpi 평균) */
    val ppi: Float,
    /** 대각선 길이(인치). 외부·내부 구분에 쓴다. */
    val diagonalInch: Float,
)

/**
 * 내부·외부 화면에서 글자와 아이콘이 **실제로 같은 크기**로 보이게 하는 DPI 계산.
 *
 * 1dp의 실제 길이(인치) = (density / 160) / ppi 이므로, 두 화면에서 같게 하려면
 * density_a / ppi_a = density_b / ppi_b, 즉 density_a = 기준 DPI × ppi_a / ppi_기준 이어야 한다.
 */
object ScreenPolicy {

    enum class Role(val label: String) { OUTER("외부"), INNER("내부") }

    const val MIN_PPI = 100f
    const val MAX_PPI = 1000f

    fun isPlausible(s: ScreenInfo): Boolean = s.ppi in MIN_PPI..MAX_PPI && s.diagonalInch > 0f

    /**
     * 두 화면 이상을 알아야 외부·내부를 정할 수 있다. 픽셀 수가 적은 쪽이 외부(커버), 많은 쪽이 내부(메인)다.
     * 대각선·PPI는 시스템 보고값이 부정확하거나 사용자가 잘못 입력할 수 있어 판정에 쓰지 않는다.
     */
    fun roleOf(screens: List<ScreenInfo>, key: String): Role? {
        val known = screens.filter(::isPlausible)
        if (known.size < 2 || known.none { it.key == key }) return null
        val outer = known.minBy(::pixelCount)
        val inner = known.maxBy(::pixelCount)
        return when (key) {
            outer.key -> Role.OUTER
            inner.key -> Role.INNER
            else -> null
        }
    }

    fun screenFor(screens: List<ScreenInfo>, role: Role): ScreenInfo? {
        val known = screens.filter(::isPlausible)
        if (known.size < 2) return null
        return if (role == Role.OUTER) known.minBy(::pixelCount) else known.maxBy(::pixelCount)
    }

    /** 해상도 key("짧은변x긴변")의 픽셀 수. 해석할 수 없으면 0. */
    fun pixelCount(s: ScreenInfo): Long {
        val p = s.key.split('x')
        val w = p.getOrNull(0)?.toLongOrNull() ?: return 0
        val h = p.getOrNull(1)?.toLongOrNull() ?: return 0
        return w * h
    }

    /** 보정 비율(보정 화면 PPI ÷ 기준 화면 PPI)이 이 범위를 벗어나면 입력이 잘못된 것으로 보고 적용하지 않는다. */
    const val MIN_RATIO = 0.7f
    const val MAX_RATIO = 1.4f

    /** [blocked]가 있으면 계산 결과를 믿을 수 없어 적용하지 않는다(사유 문구). */
    data class Target(val dpi: Int, val note: String?, val role: Role?, val blocked: String? = null)

    /** 지금 켜진 화면에 적용할 DPI. 반대 화면 정보가 없으면 보정 없이 기준 DPI를 쓴다. */
    fun effectiveTarget(
        referenceDpi: Int,
        reference: Role,
        screens: List<ScreenInfo>,
        active: ScreenInfo?,
        adjust: Int = 0,
    ): Target {
        if (active == null || !isPlausible(active)) return Target(referenceDpi, "화면 정보를 읽지 못해 보정 없이 적용", null)
        val role = roleOf(screens, active.key)
            ?: return Target(referenceDpi, "반대쪽 화면 정보가 아직 없어 보정 없이 적용(한 번 접었다 펴 주세요)", null)
        if (role == reference) return Target(referenceDpi, null, role)
        val ref = screenFor(screens, reference) ?: return Target(referenceDpi, "기준 화면 정보 없음", role)
        val ratio = active.ppi / ref.ppi
        val scaled = scale(referenceDpi, ref.ppi, active.ppi) + adjust
        val clamped = scaled.coerceIn(DpiPolicy.MIN_DPI, DpiPolicy.MAX_DPI)
        val blocked = when {
            ratio !in MIN_RATIO..MAX_RATIO ->
                "보정 비율 ${"%.2f".format(ratio)}가 비정상입니다(${"%.1f".format(active.ppi)}/${"%.1f".format(ref.ppi)}ppi). 화면 정보를 다시 수집하세요"
            clamped != scaled -> "보정 결과 $scaled dpi가 허용 범위(${DpiPolicy.MIN_DPI}~${DpiPolicy.MAX_DPI})를 벗어납니다"
            else -> null
        }
        val note = buildString {
            append("${reference.label} ${referenceDpi}dpi 기준 보정: ")
            append("${"%.1f".format(active.ppi)}/${"%.1f".format(ref.ppi)}ppi")
            if (adjust != 0) append(" ${if (adjust > 0) "+" else ""}$adjust")
            append(" → $scaled")
            if (clamped != scaled) append(" (허용 범위로 $clamped)")
        }
        return Target(clamped, note, role, blocked)
    }

    fun scale(referenceDpi: Int, referencePpi: Float, activePpi: Float): Int =
        (referenceDpi * activePpi / referencePpi).roundToInt()

    /** 알고 있는 두 화면 이상이 모두 목표값으로 확인됐는지. 감시 서비스를 끌 시점을 정한다. */
    fun allConfirmed(screens: List<ScreenInfo>, confirmedKeys: Set<String>): Boolean {
        val known = screens.filter(::isPlausible)
        return known.size >= 2 && known.all { it.key in confirmedKeys }
    }

    /** 설정 저장용 직렬화: key,ppi,diag;key,ppi,diag */
    fun encode(screens: List<ScreenInfo>): String =
        screens.joinToString(";") { "${it.key},${it.ppi},${it.diagonalInch}" }

    fun decode(raw: String?): List<ScreenInfo> =
        raw.orEmpty().split(';').mapNotNull { part ->
            val f = part.split(',')
            if (f.size != 3) return@mapNotNull null
            val ppi = f[1].toFloatOrNull() ?: return@mapNotNull null
            val diag = f[2].toFloatOrNull() ?: return@mapNotNull null
            ScreenInfo(f[0], ppi, diag)
        }

    /** 같은 패널이면 최신 값으로 바꾸고, 최대 [MAX_SCREENS]개만 기억한다. */
    fun remember(screens: List<ScreenInfo>, s: ScreenInfo): List<ScreenInfo> =
        (screens.filter { it.key != s.key } + s).takeLast(MAX_SCREENS)

    private const val MAX_SCREENS = 4
}
