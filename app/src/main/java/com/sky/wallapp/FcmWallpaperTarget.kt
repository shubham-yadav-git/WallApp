package com.sky.wallapp

/** Validates the optional wallpaper deep link carried by an FCM message. */
object FcmWallpaperTarget {

    fun key(wallpaperKey: String?, category: String?, wallpaperId: String?): String? {
        val candidate = wallpaperKey ?: if (!category.isNullOrBlank() && !wallpaperId.isNullOrBlank()) {
            "$category/$wallpaperId"
        } else {
            null
        }
        return candidate?.takeIf { Wallpaper.splitKey(it) != null }
    }
}
