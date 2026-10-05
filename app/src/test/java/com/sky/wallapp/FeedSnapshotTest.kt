package com.sky.wallapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedSnapshotTest {

    private val json = """
        {"v":1,"generatedAt":"2026-10-05T20:26:09.906Z",
         "categories":[{"path":"nature","name":"Nature"},{"path":"cars","name":"Cars"}],
         "items":{
           "nature":[["-A1","Lake","c:v1/wallapp/nature/a.jpg"],["-A2","Hill","p:15286/x.jpg?h=1200&w=800","hill mountain"]],
           "cars":[["-B1","Car","https://example.com/car.jpg"]]},
         "popular":{"nature":{"-A2":12}}}
    """.trimIndent()

    @Test fun `parses categories rows and scores`() {
        val feed = FeedSnapshot.parse(json)!!
        assertEquals(listOf("nature" to "Nature", "cars" to "Cars"), feed.categories)
        assertEquals(2, feed.items.getValue("nature").size)
        assertEquals(12, feed.popular.getValue("nature").getValue("-A2"))
    }

    @Test fun `expands short urls like the website`() {
        val feed = FeedSnapshot.parse(json)!!
        val (lake, hill) = feed.items.getValue("nature")
        val lakeImage = FeedSnapshot.toWallpaper(lake, "nature", "Nature", 0)
        assertEquals("https://res.cloudinary.com/dj8v6t5tr/image/upload/v1/wallapp/nature/a.jpg", lakeImage.cloudinaryUrl)
        assertEquals(lakeImage.cloudinaryUrl, lakeImage.image)
        assertEquals("Lake", lakeImage.search) // search defaults to the title

        val hillImage = FeedSnapshot.toWallpaper(hill, "nature", "Nature", 12)
        assertEquals("https://images.pexels.com/photos/15286/x.jpg?h=1200&w=800", hillImage.image)
        assertNull(hillImage.cloudinaryUrl)
        assertEquals("hill mountain", hillImage.search)
        assertEquals("nature/-A2", hillImage.key)

        val car = FeedSnapshot.toWallpaper(feed.items.getValue("cars")[0], "cars", "Cars", 0)
        assertEquals("https://example.com/car.jpg", car.image)
    }

    @Test fun `rejects other versions and garbage`() {
        assertNull(FeedSnapshot.parse("""{"v":2,"items":{}}"""))
        assertNull(FeedSnapshot.parse("<html>"))
    }
}
