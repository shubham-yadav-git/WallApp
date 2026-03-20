package com.sky.wallapp

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
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
        if (nextFavorite?.image.isNullOrBlank()) {
            analyticsTracker.logEvent("auto_wallpaper_skipped", mapOf("reason" to "no_favorites"))
            return@withContext Result.success()
        }

        try {
            val bitmap = Glide.with(applicationContext)
                .asBitmap()
                .load(nextFavorite.image)
                .submit()
                .get()

            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

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



