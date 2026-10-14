package com.sky.wallapp

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Readable public URLs, shared with the website (port of `src/lib/pinPaths.mjs`; keep in sync —
 * share links must produce the same slug as the site):
 *
 *   /{category}/                 a category feed
 *   /{category}/{slug}-{id}/     one wallpaper (id = 20-character push key, may start with "-")
 */
object PinPaths {

    const val ORIGIN = "https://wallapp.shubhamy.in"
    private const val ID_LENGTH = 20
    private const val SLUG_MAX = 60
    private val COMBINING_MARKS = Regex("[̀-ͯ]")
    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

    /** "A Breathtaking Sunset, Vivid!" -> "a-breathtaking-sunset-vivid" (ASCII, max 60 chars, whole words). */
    fun slugify(text: String?): String {
        val slug = Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
            .lowercase().replace(NOT_ALPHANUMERIC, "-").trim('-')
        if (slug.length <= SLUG_MAX) return slug
        val cut = slug.take(SLUG_MAX + 1)
        val dash = cut.lastIndexOf('-')
        return cut.take(if (dash > 20) dash else SLUG_MAX).trimEnd('-')
    }

    fun categoryPath(category: String) = "/${encodeURIComponent(category)}/"

    fun pinPath(category: String, id: String, title: String?) =
        "/${encodeURIComponent(category)}/${slugify(title).ifEmpty { "wallpaper" }}-$id/"

    /** Share link for a wallpaper: its page on the website (also what link previews show). */
    fun webUrl(image: Wallpaper) = ORIGIN + pinPath(image.category, image.id, image.title)

    /** A category or wallpaper path; [pin] is the wallpaper key ("{category}/{id}"). */
    data class Parsed(val category: String, val pin: String? = null)

    /** Parses a path made by the functions above; null for anything else (e.g. /feed.json). */
    fun parsePath(path: String?): Parsed? {
        val parts = path.orEmpty().split('/').filter { it.isNotEmpty() }.map { part ->
            runCatching { URLDecoder.decode(part.replace("+", "%2B"), "UTF-8") }.getOrDefault(part)
        }
        if (parts.size == 1 && !parts[0].contains('.')) return Parsed(parts[0])
        if (parts.size == 2 && parts[1].length > ID_LENGTH && parts[1][parts[1].length - ID_LENGTH - 1] == '-') {
            return Parsed(parts[0], "${parts[0]}/${parts[1].takeLast(ID_LENGTH)}")
        }
        return null
    }

    /** JavaScript's encodeURIComponent (URLEncoder writes spaces as "+" and escapes !'()~). */
    private fun encodeURIComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
}
