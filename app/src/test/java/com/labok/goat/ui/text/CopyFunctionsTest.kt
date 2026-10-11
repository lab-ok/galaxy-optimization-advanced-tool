package com.labok.goat.ui.text

import org.junit.Assert.assertTrue
import org.junit.Test

/** 값을 넣어 만드는 문구(Copy의 함수)는 CopyTest가 보지 못하므로, 큰 값을 넣어 길이를 확인한다. */
class CopyFunctionsTest {
    @Test
    fun generatedCopyIsShort() {
        listOf(
            Copy.refreshUnsupported(144),
            Copy.refreshNow(144, 144),
            Copy.refreshAlways(144),
            Copy.iconsHidden(0),
            Copy.iconsHidden(99),
            Copy.chargeBattery(100, Copy.CHARGE_STATE_AT_LIMIT, 45.5f),
            Copy.chargeUnknown(999),
            Copy.chargeLabel("최대", 1, 95),
            Copy.resNow(1440, 3120, 1440, 3120),
            Copy.resNow(1080, 2340, 1440, 3120),
            Copy.resOption("FHD+", 1080, 2340, 75),
            Copy.resOptionDesc(75),
            Copy.resAppsBody(99),
            Copy.resConfirmBody(15),
        ).forEach { assertTrue("'$it'이 너무 길다(${it.length}자)", it.length <= 70) }
    }
}
