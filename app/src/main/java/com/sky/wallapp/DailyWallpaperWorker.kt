package com.sky.wallapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val analyticsTracker = AnalyticsTracker(FirebaseAnalytics.getInstance(appContext))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AutoWallpaperManager.isEnabled(applicationContext)) {
            return@withContext Result.success()
        }

        val nextFavorite = AutoWallpaperManager.pickNextFavorite(applicationContext)
        val imageUrl = nextFavorite?.image
        if (imageUrl.isNullOrBlank()) {
            analyticsTracker.logEvent("auto_wallpaper_skipped", mapOf("reason" to "no_favorites"))
            return@withContext Result.success()
        }

        // Prefer cloudinaryUrl if available as a backup for 402 quota errors
        val urlToLoad = if (!nextFavorite.cloudinaryUrl.isNullOrBlank()) nextFavorite.cloudinaryUrl else nextFavorite.image

        try {
            val bitmap = Glide.with(applicationContext)
                .asBitmap()
                .load(urlToLoad)
                .submit()
                .get()

            WallpaperApplier.applyBitmap(
                applicationContext,
                bitmap,
                android.app.WallpaperManager.FLAG_SYSTEM
            )

            AutoWallpaperManager.markApplied(applicationContext, nextFavorite.image)
            analyticsTracker.logEvent(
                "auto_wallpaper_applied",
                mapOf(
                    "title" to nextFavorite.title,
                    "source" to "favorites"
                )
            )
            Result.success()
        } catch (e: Exception) {
            analyticsTracker.logEvent(
                "auto_wallpaper_failed",
                mapOf("error" to e.message.orEmpty().take(100))
            )
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
