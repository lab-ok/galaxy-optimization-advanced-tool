package com.labok.goat.ui.liquid

import android.content.Context
import com.labok.goat.ui.text.Copy

/**
 * 자동 맞추기 액체를 조절바로 바꾸는 값들. 각 값은 정수(조절바 눈금)로 저장하고 [look]이 [LiquidLook.default]에 곱해 물성을 만든다.
 * 기본 눈금이면 [LiquidLook.default]와 똑같다. 값은 이 기기에만 남고(앱 설정), [reset]으로 기본으로 돌린다.
 */
object LiquidTune {
    /** 조절바 하나: 저장 이름, 제목, 눈금 범위, 기본 눈금, 눈금 → 보이는 글자. */
    class Knob(val key: String, val label: String, val range: IntRange, val default: Int, val text: (Int) -> String)

    private val percent: (Int) -> String = { "$it%" }
    val WATER = Knob("water", Copy.FLUID_WATER, 40..420, LiquidLook.default.particles) { "방울 ${it}개" }
    val VISCOSITY = Knob("viscosity", Copy.FLUID_VISCOSITY, 10..400, 100, percent)
    val TENSION = Knob("tension", Copy.FLUID_TENSION, 0..400, 100, percent)
    val SCATTER = Knob("scatter", Copy.FLUID_SCATTER, 20..400, 100, percent)
    val SPLASH = Knob("splash", Copy.FLUID_SPLASH, 0..400, 100, percent)
    val GRAVITY = Knob("gravity", Copy.FLUID_GRAVITY, 0..100, 5) { if (it == 0) "무중력" else "지구의 $it%" }

    /** 출렁임이 이어지는 정도: 0이면 금방 멈추고 100이면 오래 출렁인다(속도 감쇠 0.958~0.998, 기본 50 = 0.978). */
    val WOBBLE = Knob("wobble", Copy.FLUID_WOBBLE, 0..100, 50) { "$it" }
    val PULL = Knob("pull", Copy.FLUID_PULL, 0..400, 100, percent)
    val all = listOf(WATER, VISCOSITY, TENSION, SCATTER, SPLASH, GRAVITY, WOBBLE, PULL)

    fun defaults(): Map<String, Int> = all.associate { it.key to it.default }

    /** 눈금들 → 물성. 빠진 값은 기본 눈금으로 본다. */
    fun look(v: Map<String, Int>): LiquidLook {
        val d = LiquidLook.default
        fun of(k: Knob) = (v[k.key] ?: k.default).coerceIn(k.range)
        fun ratio(k: Knob) = of(k) / k.default.toFloat()
        val visc = ratio(VISCOSITY)
        return d.copy(
            particles = of(WATER),
            sigma = d.sigma * visc,
            beta = d.beta * visc,
            cohesion = d.cohesion * ratio(TENSION),
            scatter = d.scatter * ratio(SCATTER),
            splash = d.splash * ratio(SPLASH),
            gravity = of(GRAVITY) / 100f,
            damping = 0.958f + 0.04f * of(WOBBLE) / 100f,
            pull = d.pull * ratio(PULL)
        )
    }

    fun read(context: Context): Map<String, Int> {
        val p = prefs(context)
        // 기본 물성이 바뀌면(눈금은 기본에 대한 비율이라) 예전 눈금은 버린다
        if (p.getInt(VERSION_KEY, 0) != VERSION) p.edit().clear().putInt(VERSION_KEY, VERSION).apply()
        return all.associate { it.key to p.getInt(it.key, it.default).coerceIn(it.range) }
    }

    fun save(context: Context, knob: Knob, value: Int) = prefs(context).edit().putInt(knob.key, value).apply()

    fun reset(context: Context) = prefs(context).edit().clear().putInt(VERSION_KEY, VERSION).apply()

    /** 기본 물성([LiquidLook.default])을 바꿀 때마다 올린다. */
    private const val VERSION = 2
    private const val VERSION_KEY = "version"

    private fun prefs(context: Context) = context.getSharedPreferences("liquid_tune", Context.MODE_PRIVATE)
}
