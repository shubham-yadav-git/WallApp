package com.sky.wallapp

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import org.json.JSONObject

/**
 * Anonymous popularity counters at /stats/{category}/{id}: { downloads, favorites }. Rules only
 * allow +1 (downloads) and ±1 (favorites) on existing images. Each device counts a download
 * once per image; favourites mirror the heart. Port of the website's `stats.js`.
 */
object PopularityStats {

    private const val PREFS = "stats_counted"
    private const val KEY = "wallapp:counted:v1"

    private fun read(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val obj = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
        if (obj.optJSONObject("d") == null) obj.put("d", JSONObject())
        if (obj.optJSONObject("f") == null) obj.put("f", JSONObject())
        return obj
    }

    private fun write(context: Context, counted: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, counted.toString()) }
    }

    private fun bump(image: Wallpaper, field: String, by: Long) {
        // Best-effort: failures (offline, rules) are never shown to the user.
        runCatching {
            FirebaseDatabase.getInstance()
                .getReference("stats").child(image.category).child(image.id)
                .updateChildren(mapOf(field to ServerValue.increment(by)))
        }
    }

    fun recordDownload(context: Context, image: Wallpaper) {
        val counted = read(context)
        val downloads = counted.getJSONObject("d")
        if (downloads.has(image.key)) return
        downloads.put(image.key, 1)
        write(context, counted)
        bump(image, "downloads", 1)
    }

    /** Call after toggling a favourite; [added] is the new state. */
    fun recordFavorite(context: Context, image: Wallpaper, added: Boolean) {
        val counted = read(context)
        val favorites = counted.getJSONObject("f")
        if (added && !favorites.has(image.key)) {
            favorites.put(image.key, 1)
            write(context, counted)
            bump(image, "favorites", 1)
        } else if (!added && favorites.has(image.key)) {
            favorites.remove(image.key)
            write(context, counted)
            bump(image, "favorites", -1)
        }
    }
}
