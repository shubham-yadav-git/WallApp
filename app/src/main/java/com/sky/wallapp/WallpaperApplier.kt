package com.sky.wallapp

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WallpaperApplier {

    enum class DisplayMode {
        FIXED,
        SCROLLABLE
    }

    private const val PREFS_NAME = "wallpaper_display_prefs"
    private const val KEY_DISPLAY_MODE = "display_mode"

    fun applyBitmap(
        context: Context,
        sourceBitmap: Bitmap,
        targetFlag: Int? = null,
        displayMode: DisplayMode = getSavedDisplayMode(context)
    ) {
        val preparedBitmap = prepareBitmapForWallpaper(context, sourceBitmap, displayMode)
        val wallpaperManager = WallpaperManager.getInstance(context)

        runCatching {
            wallpaperManager.suggestDesiredDimensions(preparedBitmap.width, preparedBitmap.height)
        }

        if (targetFlag != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(preparedBitmap, null, true, targetFlag)
        } else {
            wallpaperManager.setBitmap(preparedBitmap)
        }
    }

    fun getSavedDisplayMode(context: Context): DisplayMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_MODE, DisplayMode.FIXED.name)
        return runCatching { DisplayMode.valueOf(raw ?: DisplayMode.FIXED.name) }
            .getOrDefault(DisplayMode.FIXED)
    }

    fun saveDisplayMode(context: Context, displayMode: DisplayMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_MODE, displayMode.name)
            .apply()
    }

    private fun prepareBitmapForWallpaper(
        context: Context,
        sourceBitmap: Bitmap,
        displayMode: DisplayMode
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val wallpaperManager = WallpaperManager.getInstance(context)
        val screenWidth = metrics.widthPixels.coerceAtLeast(1)
        val targetHeight = metrics.heightPixels.coerceAtLeast(1)
        val targetWidth = when (displayMode) {
            DisplayMode.FIXED -> screenWidth
            DisplayMode.SCROLLABLE -> max(
                (screenWidth * 1.25f).roundToInt(),
                min(
                    wallpaperManager.desiredMinimumWidth.coerceAtLeast(screenWidth),
                    (screenWidth * 1.4f).roundToInt()
                )
            )
        }

        if (sourceBitmap.width == targetWidth && sourceBitmap.height == targetHeight) {
            return sourceBitmap
        }

        // FIXED uses fit-first composition to reduce perceived zoom while keeping full-canvas fill.
        if (displayMode == DisplayMode.FIXED) {
            return createComposedFitBitmap(sourceBitmap, targetWidth, targetHeight)
        }

        return createComposedFitBitmap(sourceBitmap, targetWidth, targetHeight)
    }

    private fun createComposedFitBitmap(
        sourceBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Subtle fill layer to avoid harsh bars when source aspect differs from target.
        drawCenterCrop(
            canvas = canvas,
            bitmap = sourceBitmap,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            alpha = 145
        )
        drawFitCenter(canvas, sourceBitmap, targetWidth, targetHeight)
        return output
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        alpha: Int
    ) {
        val scale = max(
            targetWidth.toFloat() / bitmap.width.toFloat(),
            targetHeight.toFloat() / bitmap.height.toFloat()
        )

        val scaledWidth = (bitmap.width * scale).roundToInt()
        val scaledHeight = (bitmap.height * scale).roundToInt()
        val left = ((targetWidth - scaledWidth) / 2f)
        val top = ((targetHeight - scaledHeight) / 2f)

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(bitmap, null, destRect, paint)
    }

    private fun drawFitCenter(
        canvas: Canvas,
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ) {
        val scale = min(
            targetWidth.toFloat() / bitmap.width.toFloat(),
            targetHeight.toFloat() / bitmap.height.toFloat()
        )

        val drawWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val drawHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val left = ((targetWidth - drawWidth) / 2).coerceAtLeast(0)
        val top = ((targetHeight - drawHeight) / 2).coerceAtLeast(0)

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val destRect = Rect(left, top, left + drawWidth, top + drawHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }
}

