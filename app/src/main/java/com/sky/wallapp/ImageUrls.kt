package com.sky.wallapp

import kotlin.math.roundToInt

/**
 * Sized / downloadable image URLs for Cloudinary uploads and Pexels images (which carry their
 * crop size in the URL). Port of the website's `src/lib/imageUrls.js`; keep the two in sync.
 */
object ImageUrls {

    /** Tile width on normal screens; [GRID_WIDTH_SMALL] on small / low-density ones. */
    const val GRID_WIDTH = 474
    const val GRID_WIDTH_SMALL = 236

    /** Width of the blurred background copy in the close-up. */
    const val AMBIENT_WIDTH = 96

    private const val PEXELS_PREFIX = "https://images.pexels.com/"

    private fun isCloudinary(url: String?) =
        url != null && url.contains("res.cloudinary.com") && url.contains("/upload/")

    private fun isPexels(url: String?) = url != null && url.startsWith(PEXELS_PREFIX)

    private fun transform(url: String, t: String) = url.replaceFirst("/upload/", "/upload/$t/")

    /** Height / width of a Pexels crop URL (…&h=1200&w=800), or null. */
    private fun pexelsRatio(url: String): Double? {
        val w = queryParam(url, "w")?.toDoubleOrNull() ?: return null
        val h = queryParam(url, "h")?.toDoubleOrNull() ?: return null
        return if (w > 0 && h > 0) h / w else null
    }

    private fun pexelsSized(url: String, width: Int, extra: Map<String, String> = emptyMap()): String {
        val ratio = pexelsRatio(url)
        var out = setQueryParam(url, "w", width.toString())
        if (ratio != null) out = setQueryParam(out, "h", (width * ratio).roundToInt().toString())
        extra.forEach { (k, v) -> out = setQueryParam(out, k, v) }
        return out
    }

    /** Resized version of a Cloudinary (never upscaled, auto format) or Pexels URL. */
    fun sizedUrl(url: String, width: Int): String = when {
        isCloudinary(url) -> transform(url, "w_$width,c_limit,f_auto,q_auto")
        isPexels(url) -> pexelsSized(url, width)
        else -> url
    }

    /** Height / width when it can be known without loading the image, else null. */
    fun knownRatio(image: Wallpaper): Double? {
        if (image.width > 0 && image.height > 0) return image.height.toDouble() / image.width
        val url = image.mainUrl ?: return null
        return if (isPexels(url)) pexelsRatio(url) else null
    }

    /** Feed tile URL, or null if the image has no usable source. */
    fun tileUrl(image: Wallpaper, width: Int = GRID_WIDTH): String? {
        val url = image.mainUrl
        if (isCloudinary(url) || isPexels(url)) return sizedUrl(url!!, width)
        return image.thumbs?.takeIf { it.isNotBlank() } ?: url
    }

    /** Sources tried in order when an image fails to load. */
    fun fallbackSources(image: Wallpaper): List<String> =
        listOfNotNull(image.thumbs, image.cloudinaryUrl, image.image)
            .filter { it.isNotBlank() }
            .distinct()

    fun largeUrl(image: Wallpaper): String? {
        val url = image.mainUrl ?: return null
        return sizedUrl(url, if (isPexels(url)) 1080 else 1400)
    }

    /** Tiny copy (96px wide) for the close-up's blurred background; other hosts: the tile. */
    fun ambientUrl(image: Wallpaper): String? {
        val url = image.mainUrl ?: return null
        return if (isCloudinary(url) || isPexels(url)) sizedUrl(url, AMBIENT_WIDTH) else tileUrl(image)
    }

    /** Best-quality version (open full size, set as wallpaper). */
    fun originalUrl(image: Wallpaper): String? {
        val url = image.mainUrl ?: return null
        return if (isPexels(url)) pexelsSized(url, 2160) else url
    }

    /** URL that downloads the file instead of displaying it. */
    fun downloadUrl(image: Wallpaper): String? {
        val url = image.mainUrl ?: return null
        return when {
            isCloudinary(url) -> transform(url, "fl_attachment")
            isPexels(url) -> pexelsSized(url, 2160, mapOf("dl" to "wallapp-${image.id.ifBlank { "wallpaper" }}.jpg"))
            else -> url
        }
    }

    /**
     * Download URL cropped to fill [w] × [h] (Cloudinary picks the subject with g_auto; Pexels crops
     * from its original). Other hosts can't be cropped, so they fall back to [downloadUrl].
     */
    fun sizedDownloadUrl(image: Wallpaper, w: Int, h: Int): String? {
        val url = image.mainUrl ?: return null
        return when {
            isCloudinary(url) -> transform(url, "c_fill,g_auto,w_$w,h_$h,q_auto/fl_attachment")
            isPexels(url) -> listOf(
                "w" to "$w", "h" to "$h", "fit" to "crop",
                "dl" to "wallapp-${image.id.ifBlank { "wallpaper" }}-${w}x$h.jpg"
            ).fold(url) { out, (k, v) -> setQueryParam(out, k, v) }
            else -> downloadUrl(image)
        }
    }

    /** True when filling [w] × [h] would upscale the original by more than 5% (unknown size: false). */
    fun wouldUpscale(image: Wallpaper, w: Int, h: Int): Boolean =
        image.width > 0 && image.height > 0 && maxOf(w.toDouble() / image.width, h.toDouble() / image.height) > 1.05

    /** Whether [sizedDownloadUrl] can actually crop this image. */
    fun canCrop(image: Wallpaper): Boolean = image.mainUrl.let { isCloudinary(it) || isPexels(it) }

    // ---- query string helpers (URLSearchParams.get / .set semantics) ----

    internal fun queryParam(url: String, name: String): String? {
        val query = url.substringBefore('#').substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
    }

    /** Replaces the first [name] param (dropping duplicates) or appends it. */
    internal fun setQueryParam(url: String, name: String, value: String): String {
        val fragment = url.substringAfter('#', "")
        val beforeFragment = url.substringBefore('#')
        val base = beforeFragment.substringBefore('?')
        val query = beforeFragment.substringAfter('?', "")
        val params = if (query.isEmpty()) mutableListOf() else query.split('&').toMutableList()

        val index = params.indexOfFirst { it.substringBefore('=') == name }
        val encoded = "$name=${java.net.URLEncoder.encode(value, "UTF-8")}"
        if (index >= 0) {
            params[index] = encoded
            for (i in params.lastIndex downTo index + 1) {
                if (params[i].substringBefore('=') == name) params.removeAt(i)
            }
        } else {
            params += encoded
        }
        return base + "?" + params.joinToString("&") + if (fragment.isNotEmpty()) "#$fragment" else ""
    }
}
