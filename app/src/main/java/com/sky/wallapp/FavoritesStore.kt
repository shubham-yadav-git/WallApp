package com.sky.wallapp

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight local favorites storage using SharedPreferences.
 * Keeps onboarding friction low by avoiding account/login requirements.
 */
object FavoritesStore {
    private const val PREFS_NAME = "favorites_store"
    private const val KEY_FAVORITES_JSON = "favorites_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavorite(context: Context, imageUrl: String?): Boolean {
        if (imageUrl.isNullOrBlank()) return false
        return readFavorites(context).any { it.image == imageUrl }
    }

    fun toggleFavorite(context: Context, title: String?, imageUrl: String?): Boolean {
        if (imageUrl.isNullOrBlank()) return false

        val current = readFavorites(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.image == imageUrl }

        return if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            writeFavorites(context, current)
            false
        } else {
            current.add(FavoriteEntry(title = title, image = imageUrl, savedAt = System.currentTimeMillis()))
            writeFavorites(context, current)
            true
        }
    }

    fun getFavorites(context: Context): List<Model> {
        return readFavorites(context)
            .sortedByDescending { it.savedAt }
            .map { Model(title = it.title, image = it.image) }
    }

    private fun readFavorites(context: Context): List<FavoriteEntry> {
        val raw = prefs(context).getString(KEY_FAVORITES_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val image = obj.optString("image").takeIf { it.isNotBlank() }
                    if (image.isNullOrBlank()) continue
                    add(
                        FavoriteEntry(
                            title = obj.optString("title").takeIf { it.isNotBlank() },
                            image = image,
                            savedAt = obj.optLong("savedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeFavorites(context: Context, list: List<FavoriteEntry>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("image", item.image)
            obj.put("savedAt", item.savedAt)
            arr.put(obj)
        }
        prefs(context).edit {
            putString(KEY_FAVORITES_JSON, arr.toString())
        }
    }

    private data class FavoriteEntry(
        val title: String?,
        val image: String,
        val savedAt: Long
    )
}

