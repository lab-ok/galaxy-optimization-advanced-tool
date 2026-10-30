package com.labok.goat.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val all = NavItem.entries.filter { it != NavItem.HOME }

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
        // 실험실 기능(앱 폭 좁히기)은 실험실을 열기 전에는 검색되지 않는다. 자동 플렉스는 1.29.0에서 정식 기능이 되어 늘 검색된다
        assertTrue(SettingsSearch.search("폭 좁히기", visible).isEmpty())
        assertTrue(SettingsSearch.search("폭 좁히기", all).any { it.item == NavItem.LAB_NARROW })
        assertTrue(SettingsSearch.search("까딱", visible).any { it.item == NavItem.AUTO_FLEX })
    }

    @Test
    fun blankQueryGivesNothing() {
        assertTrue(SettingsSearch.search("  ", all).isEmpty())
    }
}
