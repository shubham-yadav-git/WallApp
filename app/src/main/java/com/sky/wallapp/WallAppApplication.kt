package com.sky.wallapp

import android.app.Application
import android.util.Log
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger
import androidx.work.Configuration

class WallAppApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        configureFirebasePersistenceOnce()
        enableFirebaseDebugLogging()
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

