package com.sky.wallapp

import android.app.Application
import android.util.Log
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import androidx.work.Configuration

class WallAppApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        configureFirebasePersistenceOnce()
    }

    private fun configureFirebasePersistenceOnce() {
        if (firebasePersistenceConfigured) return

        synchronized(this) {
            if (firebasePersistenceConfigured) return

            try {
                // Must be called before any FirebaseDatabase usage in process lifetime.
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (_: DatabaseException) {
                // Already initialized elsewhere in this process; avoid crash.
            }

            firebasePersistenceConfigured = true
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        @Volatile
        private var firebasePersistenceConfigured = false
    }
}

