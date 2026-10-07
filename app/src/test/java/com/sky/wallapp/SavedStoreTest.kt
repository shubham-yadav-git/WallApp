package com.sky.wallapp

import com.sky.wallapp.SavedStore.Collection
import com.sky.wallapp.SavedStore.Entry
import com.sky.wallapp.SavedStore.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the website's src/lib/savedStore.test.js. */
class SavedStoreTest {

    private val empty = SavedStore.emptyState()

    @Test fun `favourites toggle on newest first and off`() {
        var s = SavedStore.toggleFavorite(empty, "a/1", 1)
        s = SavedStore.toggleFavorite(s, "b/2", 2)
        assertEquals(listOf("b/2", "a/1"), s.favorites.map { it.key })
        s = SavedStore.toggleFavorite(s, "a/1", 3)
        assertFalse(SavedStore.isFavorite(s, "a/1"))
        assertTrue(SavedStore.isFavorite(s, "b/2"))
    }

    @Test fun `recent moves repeats to the front without duplicates`() {
        var s = SavedStore.addRecent(empty, "a/1", 1)
        s = SavedStore.addRecent(s, "b/2", 2)
        s = SavedStore.addRecent(s, "a/1", 3)
        assertEquals(listOf("a/1", "b/2"), s.recent.map { it.key })
    }

    @Test fun `recent keeps at most RECENT_MAX`() {
        var s = empty
        for (i in 0 until SavedStore.RECENT_MAX + 10) s = SavedStore.addRecent(s, "c/$i", i.toLong())
        assertEquals(SavedStore.RECENT_MAX, s.recent.size)
        assertEquals("c/${SavedStore.RECENT_MAX + 9}", s.recent[0].key)
    }

    @Test fun `recent clears`() {
        assertEquals(emptyList<Entry>(), SavedStore.clearRecent(SavedStore.addRecent(empty, "a/1", 1)).recent)
    }

    @Test fun `collections create rename toggle delete restore`() {
        var s = SavedStore.createCollection(empty, "  Phone   walls ", 1, "c1")
        assertEquals("c1", s.collections[0].id)
        assertEquals("Phone walls", s.collections[0].name)
        assertEquals(emptyList<String>(), s.collections[0].keys)

        s = SavedStore.renameCollection(s, "c1", "Lock screens", 2)
        assertEquals("Lock screens", s.collections[0].name)
        s = SavedStore.renameCollection(s, "c1", "   ", 3)
        assertEquals("Lock screens", s.collections[0].name) // blank names are ignored

        s = SavedStore.toggleInCollection(s, "c1", "a/1", 4)
        assertTrue(SavedStore.inCollection(s, "c1", "a/1"))
        s = SavedStore.toggleInCollection(s, "c1", "a/1", 5)
        assertFalse(SavedStore.inCollection(s, "c1", "a/1"))

        s = SavedStore.createCollection(s, "Second", 6, "c2")
        val removed = s.collections[1]
        s = SavedStore.deleteCollection(s, "c1")
        assertEquals(listOf("c2"), s.collections.map { it.id })
        s = SavedStore.restoreCollection(s, removed, 1)
        assertEquals(listOf("c2", "c1"), s.collections.map { it.id })
    }

    @Test fun `parse returns empty state for corrupt or foreign data`() {
        assertEquals(empty, SavedStore.parse("not json"))
        assertEquals(empty, SavedStore.parse(null))
        assertEquals(empty, SavedStore.parse("""{"v":2}"""))
    }

    @Test fun `parse drops malformed entries`() {
        val s = SavedStore.parse("""{"v":1,"favorites":[{"key":"a/1","at":1},{"nope":true}],"collections":[{"id":1}]}""")
        assertEquals(listOf(Entry("a/1", 1)), s.favorites)
        assertEquals(emptyList<Collection>(), s.collections)
    }

    @Test fun `serializes exactly like JSON stringify and round-trips`() {
        val state = State(
            favorites = listOf(Entry("a/1", 1)),
            collections = listOf(Collection("c1", "Q \"x\" \\ é\n", listOf("a/1"), 0, 0))
        )
        // Reference output from the website's JSON.stringify
        val expected = """{"v":1,"favorites":[{"key":"a/1","at":1}],"collections":[{"id":"c1","name":"Q \"x\" \\ é\n","keys":["a/1"],"createdAt":0,"updatedAt":0}],"recent":[]}"""
        assertEquals(expected, SavedStore.toJson(state))
        assertEquals(state, SavedStore.parse(SavedStore.toJson(state)))
    }

    @Test fun `merge keeps items from both sides newest first without duplicates`() {
        val device = State(favorites = listOf(Entry("a/1", 5), Entry("b/2", 1)), recent = listOf(Entry("x/1", 9)))
        val account = State(
            favorites = listOf(Entry("b/2", 3), Entry("c/3", 7)),
            recent = listOf(Entry("x/1", 2), Entry("y/2", 4))
        )
        val m = SavedStore.mergeStates(device, account)
        assertEquals(listOf("c/3", "a/1", "b/2"), m.favorites.map { it.key })
        assertEquals(3L, m.favorites.first { it.key == "b/2" }.at)
        assertEquals(listOf("x/1", "y/2"), m.recent.map { it.key })
    }

    @Test fun `merge combines collections with the same id and keeps the newer name`() {
        val device = State(collections = listOf(Collection("c1", "Old", listOf("a/1"), 1, 2)))
        val account = State(
            collections = listOf(
                Collection("c1", "New", listOf("b/2"), 1, 5),
                Collection("c2", "Other", emptyList(), 3, 3)
            )
        )
        val m = SavedStore.mergeStates(device, account)
        assertEquals(listOf("c2", "c1"), m.collections.map { it.id })
        assertEquals("New", m.collections[1].name)
        assertTrue(m.collections[1].keys.containsAll(listOf("a/1", "b/2")))
    }

    @Test fun `merge is a no-op when one side is empty`() {
        val s = SavedStore.toggleFavorite(empty, "a/1", 1)
        assertEquals(s, SavedStore.mergeStates(s, empty))
    }

    // App-only (not in the website tests): Undo after removing from a collection

    @Test fun `restoreToCollection puts a key back at its old position`() {
        var s = SavedStore.createCollection(empty, "Mix", 1, "c1")
        listOf("a/1", "b/2", "c/3").reversed().forEach { s = SavedStore.toggleInCollection(s, "c1", it, 2) }
        s = SavedStore.toggleInCollection(s, "c1", "b/2", 3)
        s = SavedStore.restoreToCollection(s, "c1", "b/2", 1, 4)
        assertEquals(listOf("a/1", "b/2", "c/3"), s.collections.single().keys)
        assertEquals(s, SavedStore.restoreToCollection(s, "c1", "b/2", 0, 5)) // already there: unchanged
    }
}
