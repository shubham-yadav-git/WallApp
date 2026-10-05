package com.sky.wallapp

import android.app.Application
import android.util.Log
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger
import androidx.work.Configuration
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.executor.GlideExecutor

class WallAppApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        configureGlide()
        configureFirebasePersistenceOnce()
        enableFirebaseDebugLogging()
        SavedRepository.init(this)
        WallpaperRepository.prewarm(this)
        AutoWallpaperManager.ensureScheduledIfEnabled(this)
    }

    private fun configureFirebasePersistenceOnce() {
        if (firebasePersistenceConfigured) return

        synchronized(this) {
            if (firebasePersistenceConfigured) return

            try {
                // Must be called before any FirebaseDatabase usage in process lifetime.
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
                Log.d(TAG, "✅ Firebase persistence enabled")
            } catch (e: DatabaseException) {
                // Already initialized elsewhere in this process; avoid crash.
                Log.w(TAG, "⚠️ Firebase already initialized: ${e.message}")
            }

            firebasePersistenceConfigured = true
        }
    }

    private fun enableFirebaseDebugLogging() {
        try {
            FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG)
            Log.d(TAG, "✅ Firebase debug logging enabled")
            
            // Log the Firebase database URL for verification
            val dbUrl = FirebaseDatabase.getInstance().reference.toString()
            Log.d(TAG, "🔗 Firebase Database URL: $dbUrl")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enabling Firebase debug logging: ${e.message}")
        }
    }

    /**
     * Configured here (not via @GlideModule, which needs Glide's annotation processor): a 500 MB
     * disk cache and more download threads so a batch of masonry tiles is measured in parallel.
     */
    private fun configureGlide() {
        Glide.init(
            this,
            GlideBuilder()
                .setDiskCache(InternalCacheDiskCacheFactory(this, 500L * 1024 * 1024))
                .setSourceExecutor(GlideExecutor.newSourceBuilder().setThreadCount(8).build())
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        private const val TAG = "WallAppApplication"
        
        @Volatile
        private var firebasePersistenceConfigured = false
    }
}

