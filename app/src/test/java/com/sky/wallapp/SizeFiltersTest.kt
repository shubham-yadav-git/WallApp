package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the website's src/lib/sizeFilters.test.js. */
class SizeFiltersTest {

    private fun sized(w: Int, h: Int) = Wallpaper("x", "nature", "Nature", null, null, "https://x/y.jpg", null, width = w, height = h)
    private val phone = sized(1080, 2340)
    private val desktop4k = sized(6000, 4000)
    private val square = sized(3000, 3100)
    private val unknown = sized(0, 0)

    @Test fun `classifies by the original size`() {
        assertEquals(listOf(true, false), listOf(SizeFilters.isPortrait(phone), SizeFilters.isLandscape(phone)))
        assertEquals(listOf(false, true), listOf(SizeFilters.isPortrait(desktop4k), SizeFilters.isLandscape(desktop4k)))
        assertEquals(listOf(false, false), listOf(SizeFilters.isPortrait(square), SizeFilters.isLandscape(square)))
        assertEquals(listOf(true, false, false), listOf(desktop4k, phone, unknown).map(SizeFilters::is4k))
    }

    @Test fun `matches everything with no filters, including unknown sizes`() {
        assertTrue(listOf(phone, desktop4k, square, unknown).all { SizeFilters.matches(it, SizeFilters.Filters()) })
    }

    @Test fun `filters by fit and 4K together`() {
        assertTrue(SizeFilters.matches(phone, SizeFilters.Filters(SizeFilters.Fit.PHONE)))
        assertFalse(SizeFilters.matches(desktop4k, SizeFilters.Filters(SizeFilters.Fit.PHONE)))
        assertTrue(SizeFilters.matches(desktop4k, SizeFilters.Filters(SizeFilters.Fit.DESKTOP, uhd = true)))
        assertFalse(SizeFilters.matches(phone, SizeFilters.Filters(uhd = true)))
        assertFalse(SizeFilters.matches(unknown, SizeFilters.Filters(SizeFilters.Fit.DESKTOP)))
    }

    @Test fun `formats known sizes only`() {
        assertEquals("6000 × 4000", SizeFilters.sizeLabel(desktop4k))
        assertNull(SizeFilters.sizeLabel(unknown))
    }
}
