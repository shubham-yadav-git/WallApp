package com.sky.wallapp

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringReader

/**
 * The compact feed.json published by the website (scripts/build-feed.mjs) — every image in
 * one ~0.5 MB gzipped download. Port of `src/lib/feedSnapshot.js`; keep [PREFIXES] in sync.
 */
data class FeedSnapshot(
    val generatedAt: String,
    val categories: List<Pair<String, String>>, // path to name, in display order
    val items: Map<String, List<Row>>,
    val popular: Map<String, Map<String, Int>>
) {
    /**
     * [width] / [height]: the original's size from `sizes` (0 = unknown). [index] is the row's
     * position in the file, which is how `sizes` entries line up with it.
     */
    data class Row(
        val id: String,
        val title: String,
        val shortUrl: String,
        val search: String?,
        val width: Int = 0,
        val height: Int = 0,
        val index: Int = 0
    )

    companion object {
        private val PREFIXES = mapOf(
            'c' to "https://res.cloudinary.com/dj8v6t5tr/image/upload/",
            'p' to "https://images.pexels.com/photos/"
        )

        fun expandUrl(short: String): String {
            if (short.length >= 2 && short[1] == ':' && short[0] in 'a'..'z') {
                PREFIXES[short[0]]?.let { return it + short.substring(2) }
            }
            return short
        }

        /** Snapshot row -> wallpaper. Cloudinary URLs fill both url fields, like the website. */
        fun toWallpaper(row: Row, category: String, categoryName: String, score: Int): Wallpaper {
            val url = expandUrl(row.shortUrl)
            val isCloudinary = url.startsWith(PREFIXES.getValue('c'))
            return Wallpaper(
                id = row.id,
                category = category,
                categoryName = categoryName,
                title = row.title,
                search = row.search ?: row.title,
                image = url,
                cloudinaryUrl = if (isCloudinary) url else null,
                width = row.width,
                height = row.height,
                score = score
            )
        }

        /** The snapshot, or null when it is unreadable or a different version. */
        fun parse(json: String): FeedSnapshot? = parse(StringReader(json))

        /**
         * Streaming parse (the file is ~2.3 MB; building a JSON tree for it is several times
         * slower and allocates far more).
         */
        fun parse(source: Reader): FeedSnapshot? = runCatching {
            JsonReader(source).use { reader ->
                var version = 0
                var generatedAt = ""
                var categories: List<Pair<String, String>> = emptyList()
                var items: Map<String, List<Row>>? = null
                var popular: Map<String, Map<String, Int>> = emptyMap()
                var sizes: Map<String, List<IntArray?>> = emptyMap()

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "v" -> version = reader.nextInt()
                        "generatedAt" -> generatedAt = reader.nextString()
                        "categories" -> categories = readCategories(reader)
                        "items" -> items = readItems(reader)
                        "popular" -> popular = readPopular(reader)
                        "sizes" -> sizes = readSizes(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (version != 1 || items == null) null
                else FeedSnapshot(generatedAt, categories, withSizes(items, sizes), popular)
            }
        }.getOrNull()

        private fun readCategories(reader: JsonReader): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            reader.beginArray()
            while (reader.hasNext()) {
                var path: String? = null
                var name: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "path" -> path = reader.nextStringOrNull()
                        "name" -> name = reader.nextStringOrNull()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (!path.isNullOrBlank()) out += path to (name ?: path)
            }
            reader.endArray()
            return out
        }

        private fun readItems(reader: JsonReader): Map<String, List<Row>> {
            val out = LinkedHashMap<String, List<Row>>()
            reader.beginObject()
            while (reader.hasNext()) {
                val path = reader.nextName()
                val rows = ArrayList<Row>()
                reader.beginArray()
                var position = 0
                while (reader.hasNext()) readRow(reader, position++)?.let(rows::add)
                reader.endArray()
                out[path] = rows
            }
            reader.endObject()
            return out
        }

        /** [id, title, shortUrl, search?] */
        private fun readRow(reader: JsonReader, position: Int): Row? {
            val fields = arrayOfNulls<String>(4)
            var i = 0
            reader.beginArray()
            while (reader.hasNext()) {
                val value = reader.nextStringOrNull()
                if (i < fields.size) fields[i] = value
                i++
            }
            reader.endArray()
            val id = fields[0]?.takeIf { it.isNotBlank() } ?: return null
            val url = fields[2]?.takeIf { it.isNotBlank() } ?: return null
            return Row(id, fields[1].orEmpty(), url, fields[3], index = position)
        }

        /** sizes: { [path]: [[width, height] | 0, …] } in the same order as items (0 = unknown). */
        private fun readSizes(reader: JsonReader): Map<String, List<IntArray?>> {
            val out = HashMap<String, List<IntArray?>>()
            reader.beginObject()
            while (reader.hasNext()) {
                val path = reader.nextName()
                val list = ArrayList<IntArray?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        val size = IntArray(2)
                        var i = 0
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val n = if (reader.peek() == JsonToken.NUMBER) reader.nextDouble().toInt() else { reader.skipValue(); 0 }
                            if (i < 2) size[i] = n
                            i++
                        }
                        reader.endArray()
                        list += size.takeIf { it[0] > 0 && it[1] > 0 }
                    } else {
                        reader.skipValue()
                        list += null
                    }
                }
                reader.endArray()
                out[path] = list
            }
            reader.endObject()
            return out
        }

        /** Adds each row's original size (matched by position, so skipped rows don't shift the rest). */
        private fun withSizes(items: Map<String, List<Row>>, sizes: Map<String, List<IntArray?>>): Map<String, List<Row>> {
            if (sizes.isEmpty()) return items
            return items.mapValuesTo(LinkedHashMap()) { (path, rows) ->
                val list = sizes[path] ?: return@mapValuesTo rows
                rows.map { row -> list.getOrNull(row.index)?.let { row.copy(width = it[0], height = it[1]) } ?: row }
            }
        }

        private fun readPopular(reader: JsonReader): Map<String, Map<String, Int>> {
            val out = HashMap<String, Map<String, Int>>()
            reader.beginObject()
            while (reader.hasNext()) {
                val path = reader.nextName()
                val scores = HashMap<String, Int>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val id = reader.nextName()
                    if (reader.peek() == JsonToken.NUMBER) scores[id] = reader.nextInt() else reader.skipValue()
                }
                reader.endObject()
                out[path] = scores
            }
            reader.endObject()
            return out
        }

        private fun JsonReader.nextStringOrNull(): String? = when (peek()) {
            JsonToken.NULL -> { nextNull(); null }
            JsonToken.STRING, JsonToken.NUMBER -> nextString()
            else -> { skipValue(); null }
        }
    }
}
