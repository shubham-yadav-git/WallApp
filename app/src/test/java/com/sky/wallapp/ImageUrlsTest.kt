package com.sky.wallapp

import org.junit.Assert.assertEquals
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
}
