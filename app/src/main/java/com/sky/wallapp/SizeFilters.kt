package com.sky.wallapp

/**
 * Filters on an image's ORIGINAL size (width/height on the record): what it fits and whether
 * it's 4K. Port of the website's `src/lib/sizeFilters.js`; keep in sync.
 */
object SizeFilters {

    /** [id] is the `fit` param of the `filter_size` event and the website's `?fit=`. */
    enum class Fit(val id: String) { ALL("all"), PHONE("phone"), DESKTOP("desktop") }

    data class Filters(val fit: Fit = Fit.ALL, val uhd: Boolean = false) {
        val active: Boolean get() = fit != Fit.ALL || uhd
    }

    private const val SLACK = 1.1 // within 10% of square counts as neither portrait nor landscape
    private const val UHD_SIDE = 3840

    fun hasSize(image: Wallpaper) = image.width > 0 && image.height > 0
    fun isPortrait(image: Wallpaper) = hasSize(image) && image.height > image.width * SLACK
    fun isLandscape(image: Wallpaper) = hasSize(image) && image.width > image.height * SLACK
    fun is4k(image: Wallpaper) = hasSize(image) && maxOf(image.width, image.height) >= UHD_SIDE

    /** Images of unknown size never match an active filter. */
    fun matches(image: Wallpaper, filters: Filters): Boolean {
        if (filters.fit == Fit.PHONE && !isPortrait(image)) return false
        if (filters.fit == Fit.DESKTOP && !isLandscape(image)) return false
        return !filters.uhd || is4k(image)
    }

    /** "6000 × 4000", or null when the size is unknown. */
    fun sizeLabel(image: Wallpaper): String? = if (hasSize(image)) "${image.width} × ${image.height}" else null
}
