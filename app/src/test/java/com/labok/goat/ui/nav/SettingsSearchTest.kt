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
        assertTrue(SettingsSearch.search("까딱", visible).isEmpty())
        assertTrue(SettingsSearch.search("까딱", all).any { it.item == NavItem.LAB_FLEX })
    }

    @Test
    fun blankQueryGivesNothing() {
        assertTrue(SettingsSearch.search("  ", all).isEmpty())
    }
}
