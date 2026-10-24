package com.labok.goat.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class OtherUserCleanupTest {
    @Test
    fun otherUsersSkipsPrimary() {
        val out = """
            Users:
            	UserInfo{0:소유자:4c13} running
            	UserInfo{95:DUAL_APP:2001030} running
            	UserInfo{150:Secure Folder:10001030}
        """.trimIndent()
        assertEquals(listOf(95, 150), OtherUserCleanup.otherUsers(out))
    }

    @Test
    fun onlyPrimaryMeansNothing() {
        assertEquals(emptyList<Int>(), OtherUserCleanup.otherUsers("Users:\n\tUserInfo{0:Owner:c13} running"))
    }
}
