package com.sky.wallapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Applies the next favourite as the home screen wallpaper (daily auto wallpaper). */
class DailyWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(appContext))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (!AutoWallpaperManager.isEnabled(context)) return@withContext Result.success()

        SavedRepository.init(context)
        val key = AutoWallpaperManager.pickNextFavoriteKey(context)
        if (key == null) {
            analyticsTracker.logEvent("auto_wallpaper_skipped", mapOf("reason" to "no_favorites"))
            return@withContext Result.success()
        }

        try {
            val image = WallpaperRepository.lookup(context, key)
            val url = image?.let(ImageUrls::originalUrl)
            if (url == null) {
                // Deleted wallpaper: move on to the next favourite tomorrow
                AutoWallpaperManager.markApplied(context, key)
                analyticsTracker.logEvent("auto_wallpaper_skipped", mapOf("reason" to "missing_image"))
                return@withContext Result.success()
            }

            val metrics = context.resources.displayMetrics
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(url)
                .downsample(DownsampleStrategy.CENTER_OUTSIDE)
                .submit((metrics.widthPixels * 1.5f).toInt(), metrics.heightPixels)
                .get()

            WallpaperApplier.applyBitmap(context, bitmap, android.app.WallpaperManager.FLAG_SYSTEM)
            AutoWallpaperManager.markApplied(context, key)
            analyticsTracker.logEvent("auto_wallpaper_applied", mapOf("item_id" to key, "source" to "favorites"))
            Result.success()
        } catch (e: Exception) {
            analyticsTracker.logEvent("auto_wallpaper_failed", mapOf("error" to e.message.orEmpty().take(100)))
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
