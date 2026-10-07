package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoWallpaperManagerTest {

    @Test fun `rotation walks through every favourite and wraps round`() {
        val keys = listOf("a/1", "b/2", "c/3")
        var last: String? = null
        val applied = (1..4).map { AutoWallpaperManager.nextKey(keys, last)!!.also { last = it } }
        assertEquals(listOf("a/1", "b/2", "c/3", "a/1"), applied)
    }

    @Test fun `unknown last key starts from the newest`() {
        assertEquals("a/1", AutoWallpaperManager.nextKey(listOf("a/1", "b/2"), "gone/9"))
        assertNull(AutoWallpaperManager.nextKey(emptyList(), "a/1"))
    }
}
