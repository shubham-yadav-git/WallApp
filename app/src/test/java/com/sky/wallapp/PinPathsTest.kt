package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the website's src/lib/pinPaths.test.js. */
class PinPathsTest {

    private val id = "-Oo69IdMgtuvbpN_Kbor"

    @Test fun `makes lowercase ASCII words joined by dashes`() {
        assertEquals("minimalist-text-reading-what-if-this-is-all-real", PinPaths.slugify("Minimalist text reading 'What if this is all real.'"))
        assertEquals("cafe-creme-unicode", PinPaths.slugify("Café Crème — Ünïcode"))
        assertEquals("", PinPaths.slugify(""))
    }

    @Test fun `cuts long titles at a word boundary`() {
        val slug = PinPaths.slugify("A breathtaking sunset with vivid colors reflecting over a tranquil ocean shore at dusk")
        assertTrue(slug.length <= 60)
        assertEquals("a-breathtaking-sunset-with-vivid-colors-reflecting-over-a", slug)
    }

    @Test fun `round-trips a wallpaper path`() {
        val path = PinPaths.pinPath("sunset", id, "A breathtaking sunset")
        assertEquals("/sunset/a-breathtaking-sunset-$id/", path)
        assertEquals(PinPaths.Parsed("sunset", "sunset/$id"), PinPaths.parsePath(path))
    }

    @Test fun `falls back to wallpaper when the title has no letters`() {
        assertEquals(PinPaths.Parsed("cars", "cars/$id"), PinPaths.parsePath(PinPaths.pinPath("cars", id, "!!!")))
        assertEquals("/cars/wallpaper-$id/", PinPaths.pinPath("cars", id, ""))
        assertEquals("/cars/wallpaper-$id/", PinPaths.pinPath("cars", id, null))
    }

    @Test fun `parses category paths and ignores everything else`() {
        assertEquals("/city_night/", PinPaths.categoryPath("city_night"))
        assertEquals(PinPaths.Parsed("city_night"), PinPaths.parsePath("/city_night/"))
        assertNull(PinPaths.parsePath("/"))
        assertNull(PinPaths.parsePath("/feed.json"))
        assertNull(PinPaths.parsePath("/nature/short"))
        assertNull(PinPaths.parsePath("/a/b/c"))
    }

    @Test fun `share link is the wallpaper page`() {
        val image = Wallpaper(id, "sunset", "Sunset", "A breathtaking sunset", null, "https://x/y.jpg", null)
        assertEquals("https://wallapp.shubhamy.in/sunset/a-breathtaking-sunset-$id/", PinPaths.webUrl(image))
    }
}
