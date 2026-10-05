package com.sky.wallapp

/**
 * One public wallpaper, identified across app and website by [key] = "{category}/{id}".
 * Built from feed.json rows or Firebase records ([Model]).
 */
data class Wallpaper(
    val id: String,
    val category: String,
    val categoryName: String,
    val title: String?,
    val search: String?,
    val image: String?,
    val cloudinaryUrl: String?,
    val thumbs: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    /** Popularity: downloads + 2 × favourites (from feed.json). */
    val score: Int = 0
) {
    val key: String get() = "$category/$id"

    val mainUrl: String?
        get() = cloudinaryUrl?.takeIf { it.isNotBlank() } ?: image?.takeIf { it.isNotBlank() }

    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Untitled wallpaper"

    companion object {
        /**
         * Database record -> wallpaper, tolerating odd field types. Null when the record has
         * no image URL (the website skips those too).
         */
        fun fromSnapshot(snapshot: com.google.firebase.database.DataSnapshot, category: String, categoryName: String): Wallpaper? {
            val id = snapshot.key ?: return null
            val data = snapshot.value as? Map<*, *> ?: return null
            fun str(name: String) = (data[name] as? String)?.takeIf { it.isNotBlank() }
            fun int(name: String) = when (val v = data[name]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: 0
                else -> 0
            }
            val wallpaper = Wallpaper(
                id = id,
                category = category,
                categoryName = categoryName,
                title = str("title"),
                search = str("search"),
                image = str("image"),
                cloudinaryUrl = str("cloudinaryUrl"),
                thumbs = str("thumbs"),
                width = int("width"),
                height = int("height")
            )
            return wallpaper.takeIf { it.mainUrl != null }
        }

        /** Splits "{category}/{id}" (ids are push keys, so they never contain '/'). */
        fun splitKey(key: String): Pair<String, String>? {
            val slash = key.lastIndexOf('/')
            if (slash <= 0 || slash == key.lastIndex) return null
            return key.substring(0, slash) to key.substring(slash + 1)
        }
    }
}
