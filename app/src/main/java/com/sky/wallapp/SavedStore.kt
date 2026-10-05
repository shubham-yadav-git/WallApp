package com.sky.wallapp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Visitor's saved data (favourites, collections, recently viewed). Pure reducers ported from
 * the website's `src/lib/savedStore.js` — that file is the source of truth. The JSON shape is
 * shared with the website so it syncs through /users/{uid}/saved unchanged.
 */
object SavedStore {

    const val STORAGE_KEY = "wallapp:saved:v1"
    const val RECENT_MAX = 50
    const val NAME_MAX = 50

    data class Entry(val key: String, val at: Long)

    data class Collection(
        val id: String,
        val name: String,
        val keys: List<String>,
        val createdAt: Long,
        val updatedAt: Long
    )

    data class State(
        val favorites: List<Entry> = emptyList(),
        val collections: List<Collection> = emptyList(),
        val recent: List<Entry> = emptyList()
    )

    fun emptyState() = State()

    // ── Parse / serialize ───────────────────────────────────────────────────────

    /** Parses stored JSON; anything unreadable becomes an empty state. */
    fun parse(raw: String?): State {
        if (raw == null) return emptyState()
        return try {
            val data = JSONObject(raw)
            if (data.opt("v") != 1) return emptyState()
            State(
                favorites = entries(data.optJSONArray("favorites")),
                recent = entries(data.optJSONArray("recent")).take(RECENT_MAX),
                collections = collections(data.optJSONArray("collections"))
            )
        } catch (_: Exception) {
            emptyState()
        }
    }

    private fun entries(arr: JSONArray?): List<Entry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.opt(i) as? JSONObject ?: return@mapNotNull null
            val key = e.opt("key") as? String ?: return@mapNotNull null
            val at = e.opt("at") as? Number ?: return@mapNotNull null
            Entry(key, at.toLong())
        }
    }

    private fun collections(arr: JSONArray?): List<Collection> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.opt(i) as? JSONObject ?: return@mapNotNull null
            val id = c.opt("id") as? String ?: return@mapNotNull null
            val name = c.opt("name") as? String ?: return@mapNotNull null
            val keys = c.opt("keys") as? JSONArray ?: return@mapNotNull null
            Collection(
                id = id,
                name = name,
                keys = (0 until keys.length()).mapNotNull { keys.opt(it) as? String },
                createdAt = (c.opt("createdAt") as? Number)?.toLong() ?: 0L,
                updatedAt = (c.opt("updatedAt") as? Number)?.toLong() ?: 0L
            )
        }
    }

    /**
     * Serializes in the website's shape: { v, favorites, collections, recent }. Written by
     * hand (not org.json) so '/' in keys isn't escaped and the output matches JSON.stringify.
     */
    fun toJson(state: State): String = buildString {
        append("{\"v\":1,\"favorites\":")
        appendEntries(state.favorites)
        append(",\"collections\":[")
        state.collections.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append("{\"id\":").appendString(c.id)
            append(",\"name\":").appendString(c.name)
            append(",\"keys\":[")
            c.keys.forEachIndexed { k, key -> if (k > 0) append(','); appendString(key) }
            append("],\"createdAt\":").append(c.createdAt)
            append(",\"updatedAt\":").append(c.updatedAt).append('}')
        }
        append("],\"recent\":")
        appendEntries(state.recent)
        append('}')
    }

    private fun StringBuilder.appendEntries(list: List<Entry>) {
        append('[')
        list.forEachIndexed { i, e ->
            if (i > 0) append(',')
            append("{\"key\":").appendString(e.key).append(",\"at\":").append(e.at).append('}')
        }
        append(']')
    }

    private fun StringBuilder.appendString(value: String): StringBuilder {
        append('"')
        value.forEach { ch ->
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch == '\b' -> append("\\b")
                ch == '\u000C' -> append("\\f")
                ch < ' ' -> append(String.format("\\u%04x", ch.code))
                else -> append(ch)
            }
        }
        return append('"')
    }

    // ── Favourites ──────────────────────────────────────────────────────────────

    fun isFavorite(state: State, key: String) = state.favorites.any { it.key == key }

    fun toggleFavorite(state: State, key: String, now: Long) = state.copy(
        favorites = if (isFavorite(state, key)) {
            state.favorites.filter { it.key != key }
        } else {
            listOf(Entry(key, now)) + state.favorites
        }
    )

    // ── Recently viewed ─────────────────────────────────────────────────────────

    fun addRecent(state: State, key: String, now: Long) = state.copy(
        recent = (listOf(Entry(key, now)) + state.recent.filter { it.key != key }).take(RECENT_MAX)
    )

    fun clearRecent(state: State) = state.copy(recent = emptyList())

    // ── Collections ─────────────────────────────────────────────────────────────

    private fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ").take(NAME_MAX)

    fun createCollection(state: State, name: String, now: Long, id: String) = state.copy(
        collections = listOf(
            Collection(id, cleanName(name).ifEmpty { "Untitled" }, emptyList(), now, now)
        ) + state.collections
    )

    fun renameCollection(state: State, id: String, name: String, now: Long) = state.copy(
        collections = state.collections.map {
            if (it.id == id) it.copy(name = cleanName(name).ifEmpty { it.name }, updatedAt = now) else it
        }
    )

    fun deleteCollection(state: State, id: String) =
        state.copy(collections = state.collections.filter { it.id != id })

    /** Puts a deleted collection back at its old position (for Undo). */
    fun restoreCollection(state: State, collection: Collection, index: Int): State {
        val collections = state.collections.filter { it.id != collection.id }.toMutableList()
        collections.add(minOf(index, collections.size), collection)
        return state.copy(collections = collections)
    }

    fun inCollection(state: State, id: String, key: String) =
        state.collections.firstOrNull { it.id == id }?.keys?.contains(key) == true

    fun toggleInCollection(state: State, id: String, key: String, now: Long) = state.copy(
        collections = state.collections.map { c ->
            if (c.id != id) {
                c
            } else {
                c.copy(
                    keys = if (key in c.keys) c.keys.filter { it != key } else listOf(key) + c.keys,
                    updatedAt = now
                )
            }
        }
    )

    // ── Sync ────────────────────────────────────────────────────────────────────

    private fun unionByKey(a: List<Entry>, b: List<Entry>): List<Entry> {
        val byKey = LinkedHashMap<String, Entry>()
        (a + b).forEach { e ->
            val prev = byKey[e.key]
            if (prev == null || e.at > prev.at) byKey[e.key] = e
        }
        return byKey.values.sortedByDescending { it.at }
    }

    /**
     * Combines two states without losing anything (once, when a visitor signs in). Collections
     * with the same id merge their images; the newer name wins.
     */
    fun mergeStates(a: State, b: State): State {
        val collections = LinkedHashMap<String, Collection>()
        (a.collections + b.collections).forEach { c ->
            val prev = collections[c.id]
            if (prev == null) {
                collections[c.id] = c
                return@forEach
            }
            val newer = if (c.updatedAt >= prev.updatedAt) c else prev
            collections[c.id] = newer.copy(
                keys = (newer.keys + prev.keys + c.keys).distinct(),
                createdAt = minOf(c.createdAt, prev.createdAt)
            )
        }
        return State(
            favorites = unionByKey(a.favorites, b.favorites),
            recent = unionByKey(a.recent, b.recent).take(RECENT_MAX),
            collections = collections.values.sortedByDescending { it.createdAt }
        )
    }
}
