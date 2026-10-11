package com.labok.goat.ui.liquid

/**
 * 자동 맞추기 액체의 모양·물성 값 한곳(앱과 PC 미리보기 도구 FluidPreview가 같이 쓴다).
 * 달처럼 중력이 약한 곳: 표면장력으로 둥글게 뭉친 점도 높은 물방울이 살짝만 가라앉고, 충격·흔들림(중력 뺀 가속도)에 출렁인다.
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
    /** 중력 배수: 1이면 지구, 0.17이면 달. 살짝 무중력처럼 느릿하게 가라앉는다. */
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
    /** 광택: 빛 받는 쪽(왼쪽 위) 가장자리 방울을 이만큼 밝게 그린다(0~1). */
    val gloss: Float,
) {
    /** 방울 덩어리의 반지름쯤(무게 중심에서 입자까지 거리의 제곱 평균 × √2). [shine]의 기준 길이. */
    fun spread(sim: FluidSim, mx: Float, my: Float): Float {
        var s = 0f
        for (i in 0 until sim.n) { val dx = sim.x[i] - mx; val dy = sim.y[i] - my; s += dx * dx + dy * dy }
        return kotlin.math.sqrt(2f * s / sim.n).coerceAtLeast(1f)
    }

    /** 방울 [i]의 광택 세기(0~1): 무게 중심에서 왼쪽 위 가장자리로 갈수록 밝다. [spread]는 [spread]가 잰 덩어리 반지름. */
    fun shine(sim: FluidSim, i: Int, mx: Float, my: Float, spread: Float): Float {
        val dx = sim.x[i] - mx; val dy = sim.y[i] - my
        val t = ((-0.55f * dx - 0.83f * dy) / spread - 0.15f).coerceIn(0f, 1f)
        return gloss * t * t
    }

    fun apply(sim: FluidSim) {
        sim.rest = rest; sim.k = k; sim.kNear = kNear; sim.sigma = sigma; sim.beta = beta; sim.damping = damping
    }

    companion object {
        val default = LiquidLook(
            particles = 300, hDp = 18f, radiusDp = 14.5f, blurDp = 10f, glassDp = 112f,
            gToPx = 0.05f, gravity = 0.17f, pull = 0.0006f, cohesion = 0.0040f,
            rest = 7f, k = 0.02f, kNear = 0.08f, sigma = 0.25f, beta = 0.05f, damping = 0.99f,
            gloss = 0.45f,
        )
    }
}
