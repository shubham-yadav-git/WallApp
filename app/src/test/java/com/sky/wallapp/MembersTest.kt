package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of the website's providerOf test (src/lib/members.test.js). */
class MembersTest {

    @Test fun `google wins over email`() {
        assertEquals("google", Members.providerOf(listOf("firebase", "password", "google.com")))
        assertEquals("email", Members.providerOf(listOf("firebase", "password")))
        assertEquals("email", Members.providerOf(emptyList()))
    }
}
