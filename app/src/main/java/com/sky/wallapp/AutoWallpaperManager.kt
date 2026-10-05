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
    private const val KEY_LAST_KEY = "last_wallpaper_key"
    private const val WORK_NAME = "daily_auto_wallpaper"
    private const val RUN_NOW_WORK_NAME = "daily_auto_wallpaper_run_now"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    /** True when the visitor has at least one favourite (SavedRepository must be initialised). */
    fun hasEligibleFavorites(): Boolean = SavedRepository.state.value.favorites.isNotEmpty()

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

    /** Next favourite key to apply: the newest one that wasn't applied last time. */
    fun pickNextFavoriteKey(context: Context): String? {
        val keys = SavedRepository.state.value.favorites.map { it.key }
        if (keys.isEmpty()) return null
        val last = prefs(context).getString(KEY_LAST_KEY, null)
        return keys.firstOrNull { it != last } ?: keys.first()
    }

    fun markApplied(context: Context, key: String) {
        prefs(context).edit { putString(KEY_LAST_KEY, key) }
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

