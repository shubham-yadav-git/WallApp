package com.sky.wallapp

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Lightweight event helper to keep analytics calls consistent across screens. */
class AnalyticsTracker(private val firebaseAnalytics: FirebaseAnalytics) {

    fun logEvent(name: String, params: Map<String, String?> = emptyMap()) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            if (!value.isNullOrBlank()) {
                bundle.putString(key, value.take(100))
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }
}

