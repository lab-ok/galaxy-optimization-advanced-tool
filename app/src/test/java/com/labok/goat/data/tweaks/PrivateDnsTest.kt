package com.labok.goat.data.tweaks

import com.labok.goat.data.tweaks.PrivateDns.Link
import com.labok.goat.data.tweaks.PrivateDns.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsTest {

    @Test
    fun validHostAcceptsAsciiDomainsOnly() {
        assertTrue(PrivateDns.validHost("dns.adguard-dns.com"))
        assertTrue(PrivateDns.validHost("abc123.dns.nextdns.io"))
        assertFalse("점 없음", PrivateDns.validHost("localhost"))
        assertFalse("한글", PrivateDns.validHost("광고.차단.kr"))
        assertFalse("대문자는 set이 소문자로 바꾼 뒤 검사", PrivateDns.validHost("DNS.Google"))
        assertFalse("하이픈으로 시작", PrivateDns.validHost("-a.example.com"))
        assertFalse("빈 칸", PrivateDns.validHost("a..com"))
        assertFalse("공백", PrivateDns.validHost("a b.com"))
        assertFalse("너무 긴 칸", PrivateDns.validHost("a".repeat(64) + ".com"))
    }

    @Test
    fun currentMapsModesToProviders() {
        assertEquals(Provider.DISABLED, PrivateDns.Current("off", null).provider)
        assertEquals(Provider.AUTOMATIC, PrivateDns.Current("opportunistic", null).provider)
        assertEquals("알 수 없는 모드는 자동", Provider.AUTOMATIC, PrivateDns.Current("weird", null).provider)
        assertEquals(Provider.ADGUARD, PrivateDns.Current("hostname", "dns.adguard-dns.com").provider)
        assertEquals("대소문자 무시", Provider.QUAD9, PrivateDns.Current("hostname", "DNS.Quad9.net").provider)
        assertEquals(Provider.CUSTOM, PrivateDns.Current("hostname", "abc.dns.nextdns.io").provider)
        assertEquals("직접 입력은 주소를 보여 준다", "abc.dns.nextdns.io", PrivateDns.Current("hostname", "abc.dns.nextdns.io").label)
    }

    private fun judge(vararg links: Link): Link? {
        val j = PrivateDns.Judge()
        var r: Link? = null
        for (l in links) { r = j.feed(l); if (r != null) break }
        return r
    }

    @Test
    fun judgeNeedsStableConnection() {
        // 바꾼 직후 이전 서버의 '확인됨'이 한두 번 보여도 성공으로 치지 않는다
        assertNull(judge(Link.CONNECTED, Link.CONNECTED, Link.CHECKING))
        assertEquals(Link.CONNECTED, judge(*Array(PrivateDns.STABLE) { Link.CONNECTED }))
    }

    @Test
    fun judgeNeedsRepeatedFailure() {
        assertNull("다시 확인하는 동안의 한 번 실패는 넘긴다", judge(Link.FAILED, Link.CHECKING, Link.FAILED, Link.CHECKING))
        assertEquals(Link.FAILED, judge(*Array(PrivateDns.FAILS) { Link.FAILED }))
    }

    @Test
    fun judgeTimesOut() {
        val n = (PrivateDns.CHECK_MS / 1000).toInt()
        assertEquals("확인 중으로 시간이 다 되면 실패", Link.FAILED, judge(*Array(n) { Link.CHECKING }))
        assertEquals("인터넷이 없으면 되돌리지 않는다", Link.NONE, judge(*Array(n) { Link.NONE }))
    }
}
