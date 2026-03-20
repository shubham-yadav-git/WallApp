package com.sky.wallapp

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

object WallpaperApplier {

    fun applyBitmap(context: Context, sourceBitmap: Bitmap, targetFlag: Int? = null) {
        val preparedBitmap = prepareBitmapForWallpaper(context, sourceBitmap)
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

    private fun prepareBitmapForWallpaper(context: Context, sourceBitmap: Bitmap): Bitmap {
        val metrics = context.resources.displayMetrics
        val targetWidth = metrics.widthPixels.coerceAtLeast(1)
        val targetHeight = metrics.heightPixels.coerceAtLeast(1)

        if (sourceBitmap.width == targetWidth && sourceBitmap.height == targetHeight) {
            return sourceBitmap
        }

        val scale = max(
            targetWidth.toFloat() / sourceBitmap.width.toFloat(),
            targetHeight.toFloat() / sourceBitmap.height.toFloat()
        )

        val scaledWidth = (sourceBitmap.width * scale).roundToInt().coerceAtLeast(targetWidth)
        val scaledHeight = (sourceBitmap.height * scale).roundToInt().coerceAtLeast(targetHeight)

        val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true)
        val cropLeft = ((scaledBitmap.width - targetWidth) / 2).coerceAtLeast(0)
        val cropTop = ((scaledBitmap.height - targetHeight) / 2).coerceAtLeast(0)

        return Bitmap.createBitmap(
            scaledBitmap,
            cropLeft,
            cropTop,
            targetWidth.coerceAtMost(scaledBitmap.width),
            targetHeight.coerceAtMost(scaledBitmap.height)
        )
    }
}

