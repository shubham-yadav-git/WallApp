package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the website's src/lib/imageUrls.test.js. */
class ImageUrlsTest {

    private val cloudinary = "https://res.cloudinary.com/demo/image/upload/v1/wallapp/nature/a.jpg"
    private val pexels = "https://images.pexels.com/photos/15286/pexels-photo.jpg?auto=compress&cs=tinysrgb&fit=crop&h=1200&w=800"

    private fun wallpaper(image: String? = null, cloudinaryUrl: String? = null, id: String = "abc") =
        Wallpaper(id, "nature", "Nature", "t", null, image, cloudinaryUrl)

    @Test fun `adds a Cloudinary resize transform`() {
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/w_474,c_limit,f_auto,q_auto/v1/wallapp/nature/a.jpg",
            ImageUrls.sizedUrl(cloudinary, 474)
        )
    }

    @Test fun `resizes Pexels URLs keeping the crop ratio`() {
        val url = ImageUrls.sizedUrl(pexels, 474)
        assertEquals("474", ImageUrls.queryParam(url, "w"))
        assertEquals("711", ImageUrls.queryParam(url, "h"))
        assertEquals("crop", ImageUrls.queryParam(url, "fit"))
    }

    @Test fun `leaves other URLs untouched`() {
        assertEquals("https://example.com/a.jpg", ImageUrls.sizedUrl("https://example.com/a.jpg", 474))
    }

    @Test fun `reads the ratio from Pexels crop params`() {
        assertEquals(1.5, ImageUrls.knownRatio(wallpaper(image = pexels))!!, 0.001)
    }

    @Test fun `ratio is unknown for Cloudinary images without dimensions`() {
        assertNull(ImageUrls.knownRatio(wallpaper(cloudinaryUrl = cloudinary)))
    }

    @Test fun `ratio comes from stored dimensions`() {
        assertEquals(2.0, ImageUrls.knownRatio(wallpaper(cloudinaryUrl = cloudinary).copy(width = 500, height = 1000))!!, 0.001)
    }

    @Test fun `tile url is null without an image`() {
        assertNull(ImageUrls.tileUrl(wallpaper()))
    }

    @Test fun `forces a Cloudinary download`() {
        assertTrue(ImageUrls.downloadUrl(wallpaper(cloudinaryUrl = cloudinary))!!.contains("/upload/fl_attachment/"))
    }

    @Test fun `forces a Pexels download at 2160px matching the website`() {
        assertEquals(
            "https://images.pexels.com/photos/15286/pexels-photo.jpg?auto=compress&cs=tinysrgb&fit=crop&h=3240&w=2160&dl=wallapp-abc.jpg",
            ImageUrls.downloadUrl(wallpaper(image = pexels))
        )
    }

    @Test fun `uses 1080px for Pexels closeups and 1400px for Cloudinary`() {
        assertEquals("1080", ImageUrls.queryParam(ImageUrls.largeUrl(wallpaper(image = pexels))!!, "w"))
        assertTrue(ImageUrls.largeUrl(wallpaper(cloudinaryUrl = cloudinary))!!.contains("/upload/w_1400,c_limit,f_auto,q_auto/"))
    }

    @Test fun `crops Cloudinary downloads with auto gravity`() {
        val image = wallpaper(cloudinaryUrl = "https://res.cloudinary.com/demo/image/upload/v1/a.jpg", id = "x")
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/c_fill,g_auto,w_3840,h_2160,q_auto/fl_attachment/v1/a.jpg",
            ImageUrls.sizedDownloadUrl(image, 3840, 2160)
        )
        assertTrue(ImageUrls.canCrop(image))
    }

    @Test fun `crops Pexels downloads to the exact size with a named file`() {
        val image = wallpaper(image = "https://images.pexels.com/photos/1/p.jpeg?auto=compress&cs=tinysrgb&fit=crop&h=1200&w=800", id = "-Oabc")
        val url = ImageUrls.sizedDownloadUrl(image, 1920, 1080)!!
        assertEquals(listOf("1920", "1080", "crop"), listOf("w", "h", "fit").map { ImageUrls.queryParam(url, it) })
        assertEquals("wallapp--Oabc-1920x1080.jpg", ImageUrls.queryParam(url, "dl"))
    }

    @Test fun `other hosts fall back to the plain download`() {
        val image = wallpaper(image = "https://example.com/a.jpg")
        assertEquals("https://example.com/a.jpg", ImageUrls.sizedDownloadUrl(image, 100, 100))
        assertFalse(ImageUrls.canCrop(image))
    }

    @Test fun `wouldUpscale flags sizes bigger than the original`() {
        val phoneShot = wallpaper(image = pexels).copy(width = 1080, height = 2340)
        assertFalse(ImageUrls.wouldUpscale(phoneShot, 1080, 2400)) // 2.6% is within the slack
        assertTrue(ImageUrls.wouldUpscale(phoneShot, 3840, 2160))
        assertFalse(ImageUrls.wouldUpscale(wallpaper(image = pexels), 3840, 2160))
    }

    @Test fun `ambient copy is 96px wide`() {
        assertTrue(ImageUrls.ambientUrl(wallpaper(cloudinaryUrl = cloudinary))!!.contains("/upload/w_96,c_limit,f_auto,q_auto/"))
        val pexelsAmbient = ImageUrls.ambientUrl(wallpaper(image = pexels))!!
        assertEquals("96", ImageUrls.queryParam(pexelsAmbient, "w"))
        assertEquals("144", ImageUrls.queryParam(pexelsAmbient, "h"))
    }

    @Test fun `keeps the displayed Pexels crop ratio even when the original size is known`() {
        val image = wallpaper(image = "https://images.pexels.com/photos/1/p.jpeg?auto=compress&fit=crop&h=1200&w=800").copy(width = 6000, height = 4000)
        assertEquals(1.5, ImageUrls.knownRatio(image)!!, 0.001)
        assertEquals(0.5, ImageUrls.knownRatio(wallpaper(cloudinaryUrl = cloudinary).copy(width = 1000, height = 500))!!, 0.001)
    }

    @Test fun `naturalVariant re-crops Pexels to the original shape and marks it`() {
        val image = wallpaper(image = "https://images.pexels.com/photos/1/p.jpeg?auto=compress&fit=crop&h=1200&w=800").copy(width = 6000, height = 4000)
        val natural = ImageUrls.naturalVariant(image)
        assertTrue(natural.natural)
        assertEquals(4000.0 / 6000, ImageUrls.knownRatio(natural)!!, 0.01)
        assertEquals("nature/abc#natural", natural.layoutKey)
        val unknownSize = wallpaper(image = pexels)
        assertEquals(unknownSize, ImageUrls.naturalVariant(unknownSize)) // size unknown: unchanged
    }
}
