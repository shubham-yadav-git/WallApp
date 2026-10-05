package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PinUtilsTest {

    /** Reference values computed with the website's JavaScript `hash`. */
    @Test fun `hash matches the website`() {
        assertEquals(2166136261L, PinUtils.hash(""))
        assertEquals(3266909499L, PinUtils.hash("nature/-Oabc123"))
        assertEquals(2812513119L, PinUtils.hash("ab12cd34nature/-Oabc123"))
        assertEquals(2069256594L, PinUtils.hash("é漢"))
    }

    @Test fun `share link matches the website format`() {
        val image = Wallpaper("-Oabc", "nature", "Nature", null, null, "https://x/y.jpg", null)
        assertEquals("https://wallapp.shubhamy.in/?c=nature&pin=nature%2F-Oabc", PinUtils.webUrl(image))
    }

    @Test fun `splits keys`() {
        assertEquals("nature" to "-Oabc", Wallpaper.splitKey("nature/-Oabc"))
        assertEquals(null, Wallpaper.splitKey("nokey"))
    }
}
