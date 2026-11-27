package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FcmWallpaperTargetTest {

    @Test
    fun `uses a valid explicit wallpaper key`() {
        assertEquals("nature/push-id", FcmWallpaperTarget.key("nature/push-id", null, null))
    }

    @Test
    fun `builds a wallpaper key from category and id`() {
        assertEquals("nature/push-id", FcmWallpaperTarget.key(null, "nature", "push-id"))
    }

    @Test
    fun `rejects malformed or incomplete targets`() {
        assertNull(FcmWallpaperTarget.key("nature/", null, null))
        assertNull(FcmWallpaperTarget.key(null, "nature", null))
        assertNull(FcmWallpaperTarget.key(null, " ", "push-id"))
    }
}
