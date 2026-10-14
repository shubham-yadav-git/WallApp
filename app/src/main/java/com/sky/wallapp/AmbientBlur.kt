package com.sky.wallapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * The close-up's ambient background (website `.closeup-ambient`: `blur(70px) saturate(1.5)` at
 * [opacity] over the page). Blurs a ≤96px copy once on Glide's thread and blends it over
 * [background], so the result is opaque: crossfading two of them never shows the page through,
 * and nothing is re-blurred while the screen scrolls (no RenderEffect, works on every API level).
 */
class AmbientBlur(private val background: Int, private val opacity: Float) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val scale = minOf(1f, ImageUrls.AMBIENT_WIDTH.toFloat() / toTransform.width)
        val w = (toTransform.width * scale).roundToInt().coerceAtLeast(1)
        val h = (toTransform.height * scale).roundToInt().coerceAtLeast(1)
        val out = pool.get(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(SATURATION) })
        }
        Canvas(out).drawBitmap(toTransform, null, Rect(0, 0, w, h), paint)

        val pixels = IntArray(w * h)
        val scratch = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = (w * BLUR_FRACTION).roundToInt().coerceAtLeast(1)
        repeat(BLUR_PASSES) { // three box blurs ≈ a gaussian
            boxBlur(pixels, scratch, w, h, radius, horizontal = true)
            boxBlur(scratch, pixels, w, h, radius, horizontal = false)
        }
        blendOver(pixels)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1) // edges repeat
                return if (horizontal) line * w + c else c * w + line
            }
            var red = 0
            var green = 0
            var blue = 0
            for (i in -r..r) {
                val p = src[at(i)]
                red += Color.red(p); green += Color.green(p); blue += Color.blue(p)
            }
            for (i in 0 until length) {
                dst[at(i)] = Color.rgb(red / window, green / window, blue / window)
                val add = src[at(i + r + 1)]
                val remove = src[at(i - r)]
                red += Color.red(add) - Color.red(remove)
                green += Color.green(add) - Color.green(remove)
                blue += Color.blue(add) - Color.blue(remove)
            }
        }
    }

    private fun blendOver(pixels: IntArray) {
        val keep = 1f - opacity
        val bgR = Color.red(background) * keep
        val bgG = Color.green(background) * keep
        val bgB = Color.blue(background) * keep
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = Color.rgb(
                (bgR + Color.red(p) * opacity).roundToInt(),
                (bgG + Color.green(p) * opacity).roundToInt(),
                (bgB + Color.blue(p) * opacity).roundToInt()
            )
        }
    }

    override fun equals(other: Any?) =
        other is AmbientBlur && other.background == background && other.opacity == opacity

    override fun hashCode() = 31 * background + opacity.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID)
        messageDigest.update(ByteBuffer.allocate(8).putInt(background).putFloat(opacity).array())
    }

    private companion object {
        val ID = "com.sky.wallapp.AmbientBlur.v1".toByteArray()
        const val SATURATION = 1.5f
        const val BLUR_FRACTION = 0.07f // of the width, ~ the website's 70px on a full-size image
        const val BLUR_PASSES = 3
    }
}
