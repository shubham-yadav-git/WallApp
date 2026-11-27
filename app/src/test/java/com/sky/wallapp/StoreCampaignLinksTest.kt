package com.sky.wallapp

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCampaignLinksTest {

    @Test
    fun `app share link carries Play campaign attribution`() {
        val url = StoreCampaignLinks.appShareUrl("com.sky.wallapp")

        assertTrue(url.startsWith("https://play.google.com/store/apps/details?id=com.sky.wallapp&referrer="))
        val encodedReferrer = url.substringAfter("&referrer=")
        assertEquals(
            "utm_source=wallapp&utm_medium=app_share&utm_campaign=friend_referral",
            URLDecoder.decode(encodedReferrer, "UTF-8")
        )
    }
}
