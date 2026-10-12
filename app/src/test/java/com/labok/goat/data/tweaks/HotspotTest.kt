package com.labok.goat.data.tweaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotTest {
    private val out = "Row: 0 _id=12, name=SKT LTE, numeric=45005, mcc=450, mnc=05, apn=lte.sktelecom.com, user=NULL, " +
        "type=default,supl, protocol=IPV4V6, carrier_id=1890, authtype=-1"

    @Test fun parsesRow() {
        val a = Hotspot.parse(out).single()
        assertEquals("lte.sktelecom.com", a.apn)
        assertEquals("default,supl", a.type)   // 값 안의 쉼표를 나누지 않는다
        assertFalse("user" in a.values)          // NULL은 버린다
    }

    @Test fun insertCopiesCarrierValuesWithDunType() {
        val cmd = Hotspot.insertCommand(Hotspot.parse(out).single())
        assertTrue("--bind type:s:dun" in cmd)
        assertTrue("--bind apn:s:'lte.sktelecom.com'" in cmd)
        assertTrue("--bind carrier_id:i:1890" in cmd)
        assertTrue("--bind name:s:'${Hotspot.NAME}'" in cmd)
        assertFalse("--bind _id" in cmd)
    }
}
