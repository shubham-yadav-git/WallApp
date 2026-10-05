package com.sky.wallapp

import android.graphics.Color
import java.util.UUID

/** Pin identity helpers shared with the website (`src/features/public/pinUtils.js`). */
object PinUtils {

    /** Native ad slotted in after every [AD_EVERY] pins. */
    const val AD_EVERY = 15

    /** One shuffle per app session: stable while browsing, fresh on the next launch. */
    val SEED: String = UUID.randomUUID().toString().take(8)

    /** 32-bit FNV-1a over UTF-16 code units, identical to the website's `hash`. */
    fun hash(str: String): Long {
        var h = 2166136261L.toInt()
        for (ch in str) {
            h = h xor ch.code
            h *= 16777619
        }
        return h.toLong() and 0xFFFFFFFFL
    }

    /** Soft placeholder colour derived from the key: hsl(hash % 360, 28%, 88%). */
    fun placeholderColor(key: String): Int =
        Color.HSVToColor(hslToHsv((hash(key) % 360).toFloat(), 0.28f, 0.88f))

    private fun hslToHsv(h: Float, s: Float, l: Float): FloatArray {
        val v = l + s * minOf(l, 1 - l)
        val sv = if (v == 0f) 0f else 2 * (1 - l / v)
        return floatArrayOf(h, sv, v)
    }

    /** Share / deep link for a wallpaper on the website. */
    fun webUrl(image: Wallpaper): String =
        "https://wallapp.shubhamy.in/?c=${encode(image.category)}&pin=${encode(image.key)}"

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
