package com.local.folddpifix.domain

import com.local.folddpifix.data.display.ApplyResult
import com.local.folddpifix.data.display.DisplayDensity
import com.local.folddpifix.data.display.DpiController
import com.local.folddpifix.data.display.DpiState
import com.local.folddpifix.data.settings.KeyValueStore
import com.local.folddpifix.data.settings.LastApply
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.DpiPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStore : KeyValueStore {
    val map = mutableMapOf<String, Any?>()
    override fun getInt(key: String) = map[key] as Int?
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean) = map[key] as Boolean? ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getString(key: String) = map[key] as String?
    override fun putString(key: String, value: String?) { map[key] = value }
}

/** Samsung이 덮어쓰는 상황을 흉내 내는 가짜 컨트롤러. */
class FakeController(
    var permission: Boolean = true,
    var physical: Int = 480,
    var override: Int? = null,
    /** true면 apply가 호출돼도 값이 바뀌지 않는다(계속 덮어쓰는 단말). */
    var stubborn: Boolean = false,
    var screen: ScreenInfo? = null,
    var others: MutableList<DisplayDensity> = mutableListOf(),
) : DpiController {
    val applyToCalls = mutableListOf<Pair<Int, Int>>()
    override fun displays(): List<DisplayDensity> = others
    override fun applyTo(displayId: Int, dpi: Int): ApplyResult {
        applyToCalls += displayId to dpi
        val i = others.indexOfFirst { it.id == displayId }
        if (i >= 0 && others[i].readable) others[i] = others[i].copy(override = dpi)
        return ApplyResult(true, "fake", "")
    }
    var applyCalls = 0
    override fun hasPermission() = permission
    override fun read() = DpiState(physical, override, "fake")
    override fun apply(dpi: Int): ApplyResult {
        applyCalls++
        if (!stubborn) override = dpi
        return ApplyResult(!stubborn, "fake", "")
    }
    override fun reset(): ApplyResult { override = null; return ApplyResult(true, "fake", "") }
    override fun currentScreen() = screen
}

class DpiFixerTest {

    private var now = 1_000_000L
    private val logs = mutableListOf<String>()

    private fun fixer(c: FakeController, s: SettingsRepository): DpiFixer {
        ScreenLearner.resetForTest()
        return DpiFixer(c, s, { logs += it }, { now })
    }

    /** 처음 보는 화면은 일정 간격을 두고 두 번 읽혀야 학습된다. */
    private fun DpiFixer.ensureStable(reason: String) {
        ensure(reason, automatic = true)
        now += ScreenLearner.STABLE_MS + 1
        ensure(reason, automatic = true)
    }

    private fun settings(target: Int? = 420) = SettingsRepository(MemoryStore()).also { s ->
        target?.let { s.targetDpi = it }
    }

    @Test
    fun settingsPersistTargetAndSwitches() {
        val store = MemoryStore()
        SettingsRepository(store).apply {
            targetDpi = 420
            autoRecover = false
            applyOnBoot = false
            watchFold = true
            lastApply = LastApply(true, 123L, "420 dpi · x|y")
        }
        // 같은 저장소로 새로 만들어도(앱 재시작) 값이 유지된다.
        SettingsRepository(store).apply {
            assertEquals(420, targetDpi)
            assertFalse(autoRecover)
            assertFalse(applyOnBoot)
            assertTrue(watchFold)
            assertEquals(LastApply(true, 123L, "420 dpi · x|y"), lastApply)
        }
    }

    @Test
    fun defaultsAreSafe() {
        val s = SettingsRepository(MemoryStore())
        assertNull(s.targetDpi)
        assertTrue(s.autoRecover)
        assertTrue(s.applyOnBoot)
        assertTrue(s.watchFold)
        assertEquals(ScreenPolicy.Role.OUTER, s.reference)
    }

    @Test(expected = IllegalArgumentException::class)
    fun settingsRejectInvalidTarget() {
        SettingsRepository(MemoryStore()).targetDpi = 50
    }

    @Test
    fun noPermissionNeverApplies() {
        val c = FakeController(permission = false)
        assertEquals(Decision.NO_PERMISSION, fixer(c, settings()).ensure("t", automatic = true))
        assertEquals(0, c.applyCalls)
    }

    @Test
    fun withPermissionAppliesAndVerifies() {
        val c = FakeController()
        val s = settings()
        assertEquals(Decision.APPLY, fixer(c, s).ensure("t", automatic = true))
        assertEquals(420, c.read().current)
        assertTrue(s.lastApply!!.success)
    }

    @Test
    fun sameDpiIsNotReapplied() {
        val c = FakeController(override = 420)
        assertEquals(Decision.ALREADY_OK, fixer(c, settings()).ensure("t", automatic = true))
        assertEquals(0, c.applyCalls)
    }

    @Test
    fun autoSwitchOffSkipsAutomaticButNotManual() {
        val c = FakeController()
        val s = settings().also { it.autoRecover = false }
        assertEquals(Decision.SKIP_DISABLED, fixer(c, s).ensure("boot", automatic = true))
        assertEquals(0, c.applyCalls)
        assertEquals(Decision.APPLY, fixer(c, s).ensure("manual", automatic = false))
        assertEquals(1, c.applyCalls)
    }

    @Test
    fun retriesAreBoundedWhenSystemKeepsOverwriting() {
        val c = FakeController(stubborn = true)
        val f = fixer(c, settings())
        repeat(20) {
            f.ensure("loop", automatic = true)
            now += 1_000
        }
        assertEquals(DpiPolicy.MAX_APPLIES_PER_WINDOW, c.applyCalls)
        assertTrue(logs.any { "반복 방지" in it })
        // 창이 지나면 다시 시도할 수 있다.
        now += DpiPolicy.APPLY_WINDOW_MS
        assertEquals(Decision.APPLY, f.ensure("later", automatic = true))
    }

    @Test
    fun innerScreenGetsPpiCorrectedDpi() {
        val outer = ScreenInfo("904x2316", 402f, 6.2f)
        val inner = ScreenInfo("1812x2176", 373f, 7.6f)
        val s = settings(420)
        val c = FakeController(screen = outer)
        val f = fixer(c, s)
        f.ensureStable("outer")
        assertEquals(420, c.read().current)
        // 반대쪽 화면을 아직 모르면 확인으로 치지 않는다.
        assertEquals(null, f.satisfiedScreenKey())
        // 펼치면 내부 패널로 바뀐다. 처음 보는 화면이므로 기억한 뒤 보정값을 적용한다.
        c.screen = inner
        f.ensureStable("inner")
        assertEquals(ScreenPolicy.scale(420, 402f, 373f), c.read().current)
        assertEquals(2, s.screens.size)
        assertEquals(inner.key, f.satisfiedScreenKey())
        // 다시 접으면 외부 기준값으로 돌아간다.
        c.screen = outer
        f.ensure("outer again", automatic = true)
        assertEquals(420, c.read().current)
    }

    @Test
    fun appliesToInactiveDisplayWithoutFolding() {
        // 키가 다른 테스트와 겹치지 않게 별도 해상도를 쓴다(적용 기록이 프로세스 단위로 남는다).
        val outer = ScreenInfo("900x2300", 428f, 5.5f)
        val inner = ScreenInfo("1800x2100", 404f, 7.6f)
        val s = settings(360).also { it.screens = listOf(outer, inner) }
        // 접힌 상태로 부팅: 켜진 화면은 외부(-d 0), 내부(-d 1)는 꺼져 있어 현재값을 읽을 수 없다.
        val c = FakeController(screen = outer, others = mutableListOf(DisplayDensity(1, inner, 420, null, readable = false)))
        val f = fixer(c, s)
        f.ensure("boot", automatic = true)
        assertEquals(360, c.read().current)
        val innerDpi = ScreenPolicy.scale(360, 428f, 404f)
        assertEquals(listOf(1 to innerDpi), c.applyToCalls)
        assertEquals(setOf(outer.key, inner.key), f.satisfiedScreenKeys())
        // 다시 확인해도 같은 값을 반복 적용하지 않는다.
        f.ensure("boot 5s", automatic = true)
        assertEquals(1, c.applyToCalls.size)
    }

    @Test
    fun inactiveDisplayPpiDoesNotOverwriteLearnedValue() {
        val outer = ScreenInfo("910x2310", 428f, 5.5f)
        val inner = ScreenInfo("1810x2110", 404f, 7.6f)
        val s = settings(360).also { it.screens = listOf(outer, inner) }
        // 실기기: 꺼진 내부 디스플레이가 외부와 같은 428ppi를 알려 준다.
        val bogusInner = inner.copy(ppi = 428f, diagonalInch = 7.2f)
        val c = FakeController(screen = outer, others = mutableListOf(DisplayDensity(1, bogusInner, 420, null, readable = true)))
        fixer(c, s).ensure("boot", automatic = true)
        assertEquals(404f, s.screens.first { it.key == inner.key }.ppi)
        assertEquals(listOf(1 to ScreenPolicy.scale(360, 428f, 404f)), c.applyToCalls)
    }

    @Test
    fun displayNeverSeenActiveIsLeftAlone() {
        val outer = ScreenInfo("920x2320", 428f, 5.5f)
        val c = FakeController(
            screen = outer,
            others = mutableListOf(DisplayDensity(1, ScreenInfo("1820x2120", 428f, 7.2f), 420, null, readable = true)),
        )
        fixer(c, settings(360)).ensure("boot", automatic = true)
        assertTrue(c.applyToCalls.isEmpty())
    }

    @Test
    fun unidentifiedDisplayIsLeftAlone() {
        val c = FakeController(
            screen = ScreenInfo("904x2316", 402f, 6.2f),
            others = mutableListOf(DisplayDensity(1, null, 420, null, readable = false)),
        )
        fixer(c, settings(360)).ensure("boot", automatic = true)
        assertTrue(c.applyToCalls.isEmpty())
    }

    @Test
    fun transientResolutionIsNotLearned() {
        val outer = ScreenInfo("930x2330", 428f, 5.5f)
        val s = settings(360)
        val c = FakeController(screen = outer)
        val f = fixer(c, s)
        f.ensureStable("outer")
        // 접는 도중 잠깐 나타난 해상도: 한 번만 읽히고 사라진다.
        c.screen = ScreenInfo("1500x2200", 410f, 6.9f)
        f.ensure("transition", automatic = true)
        c.screen = outer
        now += 2_000
        f.ensure("outer", automatic = true)
        assertEquals(listOf(outer.key), s.screens.map { it.key })
    }

    @Test
    fun manualChangesAreNeverRateLimited() {
        val c = FakeController(stubborn = true)
        val f = fixer(c, settings())
        repeat(12) { f.ensure("수동", automatic = false); now += 500 }
        assertEquals(12, c.applyCalls)
        // 수동 적용은 자동 반복 방지 횟수에 세지 않는다.
        assertEquals(DpiPolicy.Decision.APPLY, f.ensure("boot", automatic = true))
    }

    @Test
    fun bootScheduleMatchesSpec() {
        assertEquals(listOf(0L, 5L, 15L, 30L, 60L), DpiPolicy.BOOT_CHECK_OFFSETS_SEC)
    }
}

class ExternalChangeTest {
    private val outer = ScreenInfo("1100x2400", 420f, 6.5f)
    private val inner = ScreenInfo("1900x2200", 380f, 8.0f)

    private fun setup(): Triple<FakeController, SettingsRepository, DpiFixer> {
        val s = SettingsRepository(MemoryStore()).also { it.targetDpi = 360; it.autoRecover = true; it.screens = listOf(outer, inner) }
        val c = FakeController(physical = 420, screen = outer)
        return Triple(c, s, DpiFixer(c, s, {}))
    }

    @Test
    fun externalChangeIsHeldNotOverwritten() {
        val (c, s, f) = setup()
        f.ensure("start", automatic = true)
        assertEquals(360, c.read().current)
        // 시스템 설정에서 450으로 바꿈 → 자동 경로는 덮어쓰지 않고 선택을 기다린다.
        c.override = 450
        assertEquals(DpiPolicy.Decision.EXTERNAL_CHANGE, f.ensure("watch", automatic = true, physicalIsReset = false))
        assertEquals(450, c.read().current)
        assertEquals(ExternalChange("1100x2400", ScreenPolicy.Role.OUTER, 360, 450, s.externalChange!!.timeMs), s.externalChange)
    }

    @Test
    fun restoreGoesBackToAppValue() {
        val (c, s, f) = setup()
        f.ensure("start", automatic = true)
        c.override = 450
        f.ensure("watch", automatic = true, physicalIsReset = false)
        f.resolveExternal(adopt = false, reason = "restore")
        assertEquals(360, c.read().current)
        assertEquals(null, s.externalChange)
    }

    @Test
    fun adoptMakesChangedValueTheReference() {
        val (c, s, f) = setup()
        f.ensure("start", automatic = true)
        c.override = 450
        f.ensure("watch", automatic = true, physicalIsReset = false)
        f.resolveExternal(adopt = true, reason = "adopt")
        assertEquals(450, s.targetDpi)
        assertEquals(450, c.read().current)
        assertEquals(null, s.externalChange)
    }

    @Test
    fun adoptOnInnerKeepsInnerExactly() {
        val (c, s, f) = setup()
        c.screen = inner
        f.ensure("start", automatic = true)
        val innerTarget = c.read().current!!
        c.override = innerTarget + 37
        f.ensure("watch", automatic = true, physicalIsReset = false)
        f.resolveExternal(adopt = true, reason = "adopt")
        assertEquals(innerTarget + 37, c.read().current)
        assertEquals(innerTarget + 37, s.plan()!!.targetFor(inner).dpi)
    }

    @Test
    fun bootResetToPhysicalIsRestoredAutomatically() {
        val (c, _, f) = setup()
        f.ensure("start", automatic = true)
        c.override = null   // 부팅 뒤 기기가 기본값(420)으로 되돌림
        assertEquals(DpiPolicy.Decision.APPLY, f.ensure("boot", automatic = true))
        assertEquals(360, c.read().current)
    }
}
