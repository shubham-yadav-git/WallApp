package com.sky.wallapp

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * The device copy of the visitor's saved data ([SavedStore.State]) plus the actions the UI
 * uses — mirrors the website's `useSaved.js` (`savedActions`), including its analytics.
 */
object SavedRepository {

    private const val PREFS = "saved_store"
    private const val LEGACY_PREFS = "favorites_store"
    private const val LEGACY_KEY = "favorites_json"

    private lateinit var appContext: Context
    private val analytics by lazy { AnalyticsTracker(FirebaseAnalytics.getInstance(appContext)) }

    private val _state = MutableStateFlow(SavedStore.emptyState())
    val state: StateFlow<SavedStore.State> = _state

    /** Called after every local change (not for [replace]); used by [SavedSync]. */
    internal var onLocalChange: (() -> Unit)? = null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _state.value = SavedStore.parse(prefs().getString(SavedStore.STORAGE_KEY, null))
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(next: SavedStore.State) {
        prefs().edit { putString(SavedStore.STORAGE_KEY, SavedStore.toJson(next)) }
    }

    fun update(transform: (SavedStore.State) -> SavedStore.State) {
        val next = transform(_state.value)
        persist(next)
        _state.value = next
        onLocalChange?.invoke()
    }

    /** Replaces everything (sign-in merge, change from another device, sign-out clear). */
    fun replace(next: SavedStore.State) {
        persist(next)
        _state.value = next
    }

    private fun now() = System.currentTimeMillis()

    // ── Actions ─────────────────────────────────────────────────────────────────

    fun isFavorite(key: String) = SavedStore.isFavorite(_state.value, key)

    /** Toggles a favourite (and its popularity counter); returns true if it was added. */
    fun toggleFavorite(image: Wallpaper): Boolean {
        val adding = !isFavorite(image.key)
        update { SavedStore.toggleFavorite(it, image.key, now()) }
        PopularityStats.recordFavorite(appContext, image, adding)
        analytics.logEvent(
            if (adding) "add_to_wishlist" else "remove_from_wishlist",
            mapOf("item_id" to image.key, "category" to image.categoryName)
        )
        return adding
    }

    fun addRecent(key: String) = update { SavedStore.addRecent(it, key, now()) }

    /** Clears recently viewed and returns an undo function. */
    fun clearRecent(): () -> Unit {
        val before = _state.value.recent
        update(SavedStore::clearRecent)
        return { update { s -> s.copy(recent = (before + s.recent).distinctBy { it.key }.take(SavedStore.RECENT_MAX)) } }
    }

    fun createCollection(name: String, firstKey: String?): String {
        val id = "c${now().toString(36)}${(Math.random() * 36 * 36 * 36 * 36).toLong().toString(36).padStart(4, '0')}"
        update { s ->
            val next = SavedStore.createCollection(s, name, now(), id)
            if (firstKey != null) SavedStore.toggleInCollection(next, id, firstKey, now()) else next
        }
        analytics.logEvent("create_collection")
        return id
    }

    fun renameCollection(id: String, name: String) = update { SavedStore.renameCollection(it, id, name, now()) }

    /** Deletes a collection and returns an undo function. */
    fun deleteCollection(id: String): () -> Unit {
        val index = _state.value.collections.indexOfFirst { it.id == id }
        val collection = _state.value.collections.getOrNull(index) ?: return {}
        update { SavedStore.deleteCollection(it, id) }
        return { update { SavedStore.restoreCollection(it, collection, index) } }
    }

    fun toggleInCollection(id: String, key: String) = update { SavedStore.toggleInCollection(it, id, key, now()) }

    /** Removes a wallpaper from a collection and returns an undo that restores its position. */
    fun removeFromCollection(id: String, key: String): () -> Unit {
        val index = _state.value.collections.firstOrNull { it.id == id }?.keys?.indexOf(key) ?: -1
        if (index < 0) return {}
        update { SavedStore.toggleInCollection(it, id, key, now()) }
        return { update { SavedStore.restoreToCollection(it, id, key, index, now()) } }
    }

    // ── Migration from the old URL-based favourites ─────────────────────────────

    /**
     * Moves favourites saved by older app versions (stored by image URL) into the shared
     * key-based format. Run once every wallpaper is loaded; unmatched (deleted) ones are dropped.
     */
    fun migrateLegacyFavorites(records: List<Wallpaper>) {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = legacy.getString(LEGACY_KEY, null) ?: return

        val byUrl = HashMap<String, String>()
        records.forEach { w ->
            w.image?.let { byUrl[it] = w.key }
            w.cloudinaryUrl?.let { byUrl[it] = w.key }
        }
        val migrated = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = byUrl[obj.optString("image")] ?: return@mapNotNull null
                SavedStore.Entry(key, obj.optLong("savedAt", now()))
            }
        }.getOrDefault(emptyList())

        if (migrated.isNotEmpty()) {
            update { s -> s.copy(favorites = (s.favorites + migrated).distinctBy { it.key }.sortedByDescending { it.at }) }
            migrated.forEach { entry ->
                records.firstOrNull { it.key == entry.key }?.let { PopularityStats.recordFavorite(appContext, it, true) }
            }
        }
        legacy.edit { clear() }
    }
}
