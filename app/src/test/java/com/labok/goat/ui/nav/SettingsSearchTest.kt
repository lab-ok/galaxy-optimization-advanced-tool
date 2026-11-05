package com.labok.goat.ui.nav

import com.labok.goat.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsSearchTest {
    // 공통 문구가 리소스로 옮겨져 JVM 테스트에서는 xml을 직접 읽어 넣는다
    @Before
    fun strings() = TestStrings.install()

    // NavItem은 만들 때 문구를 읽으므로, 이 테스트가 먼저 돌면 @Before보다 앞서 읽히지 않게 늦게 만든다
    private val all by lazy { NavItem.entries.filter { it != NavItem.HOME } }

    @Test
    fun featureNameComesFirst() {
        val hits = SettingsSearch.search("볼륨", all)
        assertEquals(NavItem.VOLUME_ROTATE, hits.first().item)
        assertEquals("볼륨 버튼 회전", hits.first().title)
    }

    @Test
    fun findsSettingInsideFeature() {
        val hits = SettingsSearch.search("카메라에서는", all)
        assertEquals(NavItem.VOLUME_ROTATE, hits.first().item)
        assertEquals("카메라에서는 그대로", hits.first().title)
    }

    @Test
    fun ignoresSpacesAndCase() {
        assertTrue(SettingsSearch.search("wifi켜두기", all).any { it.item == NavItem.AIRPLANE })
        assertTrue(SettingsSearch.search("햅틱세기", all).any { it.item == NavItem.SETTINGS })
    }

    @Test
    fun chosungSearch() {
        assertEquals("ㅂㄹㅂㅌㅎㅈ", SettingsSearch.chosung("볼륨버튼회전"))
        assertTrue(SettingsSearch.search("ㅈㅅㅇ", all).any { it.item == NavItem.REFRESH })
    }

    @Test
    fun hiddenFeaturesAreNotSearched() {
        val visible = all.filter { it !in NavItem.LAB_ITEMS }
        // 실험실 기능(앰비언트 모드)은 실험실을 열기 전에는 검색되지 않는다. 자동 플렉스(1.29.0)·TXT로 공유(1.49.0)·사용 통계(1.50.9)는 정식 기능이라 늘 검색된다
        assertTrue(SettingsSearch.search("텐트", visible).isEmpty())
        assertTrue(SettingsSearch.search("텐트", all).any { it.item == NavItem.LAB_AMBIENT })
        assertTrue(SettingsSearch.search("충전 기록", visible).any { it.item == NavItem.LAB_STATS })
        assertTrue(SettingsSearch.search("TXT로 공유", visible).any { it.item == NavItem.LAB_NOTE })
        assertTrue(SettingsSearch.search("까딱", visible).any { it.item == NavItem.AUTO_FLEX })
    }

    @Test
    fun blankQueryGivesNothing() {
        assertTrue(SettingsSearch.search("  ", all).isEmpty())
    }
}
