package com.sky.wallapp

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoWallpaperManager {

    private const val PREFS_NAME = "auto_wallpaper_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_IMAGE_URL = "last_image_url"
    private const val WORK_NAME = "daily_auto_wallpaper"
    private const val RUN_NOW_WORK_NAME = "daily_auto_wallpaper_run_now"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun hasEligibleFavorites(context: Context): Boolean {
        return FavoritesStore.getFavorites(context).any { !it.image.isNullOrBlank() }
    }

    fun enable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, true) }
        schedule(context)
    }

    fun disable(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENABLED, false) }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun ensureScheduledIfEnabled(context: Context) {
        if (isEnabled(context)) {
            schedule(context)
        }
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyWallpaperWorker>()
            .setConstraints(defaultConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RUN_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pickNextFavorite(context: Context): Model? {
        val favorites = FavoritesStore.getFavorites(context).filter { !it.image.isNullOrBlank() }
        if (favorites.isEmpty()) return null

        val lastImageUrl = prefs(context).getString(KEY_LAST_IMAGE_URL, null)
        if (favorites.size == 1) return favorites.first()

        return favorites.firstOrNull { it.image != lastImageUrl } ?: favorites.first()
    }

    fun markApplied(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        prefs(context).edit { putString(KEY_LAST_IMAGE_URL, imageUrl) }
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(1, TimeUnit.DAYS)
            .setConstraints(defaultConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun defaultConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

