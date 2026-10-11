package com.labok.goat.ui.liquid

/**
 * 자동 맞추기 액체의 모양·물성 값 한곳(앱과 PC 미리보기 도구 FluidPreview가 같이 쓴다).
 * 중력이 1/20뿐인 곳: 표면장력으로 둥글게 뭉친 점도 높은 물방울이 살짝만 가라앉고, 충격·흔들림(중력 뺀 가속도)에 출렁인다.
 */
data class LiquidLook(
    /** 입자 수와 상호작용 거리(dp). 입자가 많고 촘촘할수록 표면이 매끄럽다. */
    val particles: Int,
    val hDp: Float,
    /** 그릴 때 입자 원 반지름(dp)과 goo 흐림 반지름(dp). 흐림이 입자 간격의 두 배쯤이면 동그라미 티가 사라진다. */
    val radiusDp: Float,
    val blurDp: Float,
    /** 잔 반지름(dp). */
    val glassDp: Float,
    /** 가속도(m/s²) → 힘(dp/프레임²) 배수. */
    val gToPx: Float,
    /** 중력 배수: 1이면 지구, 0.05면 1/20. 거의 무중력처럼 아주 느릿하게 가라앉는다. */
    val gravity: Float,
    /** 잔 가운데로 끄는 약한 힘(가만히 두면 가운데로 돌아온다). */
    val pull: Float,
    /** 방울 무게 중심으로 모으는 힘(표면장력: 흩어져도 다시 둥근 한 방울로 맺힌다). */
    val cohesion: Float,
    val rest: Float,
    val k: Float,
    val kNear: Float,
    val sigma: Float,
    val beta: Float,
    val damping: Float,
    /** 휘두름·충격이 잡히면 물방울을 튀기는 세기(dp/프레임, 세기가 판정 기준의 몇 배인지(최대 3배)에 곱한다). */
    val splash: Float,
) {

    fun apply(sim: FluidSim) {
        sim.rest = rest; sim.k = k; sim.kNear = kNear; sim.sigma = sigma; sim.beta = beta; sim.damping = damping
    }

    companion object {
        val default = LiquidLook(
            particles = 300, hDp = 21f, radiusDp = 17f, blurDp = 11.5f, glassDp = 112f,
            gToPx = 0.05f, gravity = 0.05f, pull = 0.0006f, cohesion = 0.0070f,
            rest = 7f, k = 0.02f, kNear = 0.08f, sigma = 0.4f, beta = 0.08f, damping = 0.986f,
            splash = 6f,
        )
    }
}
