package com.labok.goat.domain

import com.labok.goat.data.settings.SettingsRepository
import com.labok.goat.domain.DpiPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 화면이 하나인 폰(폴드가 아님)과 외부·내부를 정할 수 없는 화면에서의 DPI 판단.
 * QA 에뮬레이터(1248x1972 한 화면)에서 `wm size`를 여러 번 바꾼 뒤 학습 화면이 4개로 쌓여
 * 지금 화면의 역할이 없어지자, 외부 변경을 묻지 않고 감시가 되돌리던 상황을 재현한다.
 */
class SingleScreenDpiTest {
    private var now = 1_000_000L
    private val phone = ScreenInfo("1248x1972", 420f, 5.56f)

    /** 해상도를 바꿀 때마다 새 화면으로 쌓인 학습 기록(에뮬레이터 실측 값). */
    private val polluted = listOf(
        ScreenInfo("1972x2400", 420f, 7.40f),
        phone,
        ScreenInfo("900x1422", 420f, 4.01f),
        ScreenInfo("1080x2340", 420f, 6.14f)
    )

    @Before
    fun reset() = ScreenLearner.resetForTest()

    private fun setup(foldable: Boolean, auto: Boolean = true, screens: List<ScreenInfo> = polluted): Triple<FakeController, SettingsRepository, DpiFixer> {
        val s = SettingsRepository(MemoryStore()).also {
            it.targetDpi = 290
            it.autoRecover = auto
            it.screens = screens
        }
        val c = FakeController(physical = 420, override = 410, screen = phone, foldable = foldable)
        return Triple(c, s, DpiFixer(c, s, {}, { now }))
    }

    @Test
    fun singleScreenForgetsResolutionSizesAndTargetsSliderValue() {
        val (c, s, f) = setup(foldable = false)
        assertEquals(Decision.APPLY, f.ensure("수동", automatic = false))
        // 해상도마다 쌓인 기록은 지금 화면 하나로 정리되고, 목표는 사용자가 고른 값 그대로다
        assertEquals(listOf("1248x1972"), s.screens.map { it.key })
        assertEquals(290, c.read().current)
        assertEquals(290, s.plan()!!.targetFor(phone).dpi)
    }

    @Test
    fun singleScreenResolutionChangeStaysOneScreen() {
        val (c, s, f) = setup(foldable = false, screens = listOf(phone))
        f.ensure("수동", automatic = false)
        // 밖에서 해상도를 바꿔도 새 화면으로 쌓지 않는다
        c.screen = ScreenInfo("900x1422", 420f, 4.01f)
        f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false)
        assertEquals(listOf("900x1422"), s.screens.map { it.key })
        c.screen = phone
        f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false)
        assertEquals(listOf("1248x1972"), s.screens.map { it.key })
        assertEquals(290, c.read().current)
    }

    @Test
    fun userApplyStaysWhenWatcherWakes() {
        val (c, _, f) = setup(foldable = false)
        f.ensure("수동", automatic = false)
        // 적용으로 생긴 변경 신호에 감시가 깨어나도 같은 값이라 그대로 둔다
        assertEquals(Decision.ALREADY_OK, f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false))
        assertEquals(290, c.read().current)
        assertEquals(1, c.applyCalls)
    }

    @Test
    fun singleScreenExternalChangeIsHeldNotReverted() {
        val (c, s, f) = setup(foldable = false)
        f.ensure("수동", automatic = false)
        // 시스템 설정·adb에서 400으로 바꿈 → 자동 적용이 켜져 있어도 되돌리지 않고 선택을 기다린다
        c.override = 400
        assertEquals(Decision.EXTERNAL_CHANGE, f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false))
        assertEquals(400, c.read().current)
        assertEquals(290, s.externalChange!!.from)
        assertEquals(400, s.externalChange!!.to)
        // 다시 깨어나도 그대로
        assertEquals(Decision.EXTERNAL_CHANGE, f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false))
        assertEquals(1, c.applyCalls)
        // '이 값을 기준으로' → 기준 DPI가 400이 된다
        f.resolveExternal(adopt = true, reason = "adopt")
        assertEquals(400, s.targetDpi)
        assertEquals(400, c.read().current)
        assertNull(s.externalChange)
    }

    @Test
    fun singleScreenRestoreGoesBackToUserValue() {
        val (c, s, f) = setup(foldable = false)
        f.ensure("수동", automatic = false)
        c.override = 400
        f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false)
        f.resolveExternal(adopt = false, reason = "restore")
        assertEquals(290, c.read().current)
        assertEquals(290, s.targetDpi)
    }

    @Test
    fun autoOffNeverReapplies() {
        val (c, s, f) = setup(foldable = false, auto = false)
        f.ensure("수동", automatic = false)
        c.override = 400
        // 바뀐 값은 외부 변경으로 기록될 뿐 다시 적용하지 않는다
        assertNotEquals(Decision.APPLY, f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false))
        assertEquals(400, c.read().current)
        // 부팅 뒤 기기 기본값(420)으로 돌아가도 자동 적용이 꺼져 있으면 건너뛴다
        c.override = null
        s.externalChange = null
        assertEquals(Decision.SKIP_DISABLED, f.ensure("boot", automatic = true))
        assertEquals(420, c.read().current)
        assertEquals(1, c.applyCalls)
    }

    @Test
    fun foldScreenWithoutRoleHoldsExternalChange() {
        // 폴드에서 학습 화면이 셋 이상이라 지금 화면이 가장 작지도 크지도 않으면 역할이 없다(보정 없이 기준 DPI).
        // 이때도 외부 변경은 묻고 기다린다.
        val (c, s, f) = setup(foldable = true)
        f.ensure("수동", automatic = false)
        assertEquals(4, s.screens.size)
        assertEquals(290, c.read().current)
        c.override = 400
        assertEquals(Decision.EXTERNAL_CHANGE, f.ensure("DPI 변경 감지", automatic = true, physicalIsReset = false))
        assertEquals(400, c.read().current)
    }

    @Test
    fun foldKeepsLearningBothScreens() {
        val outer = ScreenInfo("1080x2520", 420f, 6.5f)
        val inner = ScreenInfo("1968x2184", 420f, 8.0f)
        val (c, s, f) = setup(foldable = true, screens = listOf(outer))
        c.screen = inner
        f.ensure("접기", automatic = true)
        now += ScreenLearner.STABLE_MS + 1
        f.ensure("접기", automatic = true)
        assertEquals(listOf("1080x2520", "1968x2184"), s.screens.map { it.key })
        assertEquals(ScreenPolicy.Role.INNER, s.plan()!!.targetFor(inner).role)
    }
}
