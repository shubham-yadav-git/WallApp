package com.sky.wallapp

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * Records the signed-in account at /members/{uid} = { provider, createdAt, lastSeen } so the
 * owner's Studio can count registered users. At most once a day per device; best effort.
 * Port of the website's `recordMember.js` — the rules accept exactly these three fields.
 */
object Members {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val PREFS = "wallapp_prefs"

    private fun seenKey(uid: String) = "wallapp:member-seen:$uid"

    /** "google" or "email" from a Firebase user's provider ids (Google wins when both). */
    fun providerOf(providerIds: List<String>) = if ("google.com" in providerIds) "google" else "email"

    fun record(context: Context, user: FirebaseUser) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(seenKey(user.uid), 0L) < DAY_MS) return
        runCatching {
            FirebaseDatabase.getInstance().getReference("members").child(user.uid).updateChildren(
                mapOf(
                    "provider" to providerOf(user.providerData.map { it.providerId }),
                    "createdAt" to (user.metadata?.creationTimestamp?.takeIf { it > 0 } ?: now),
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            ).addOnSuccessListener {
                prefs.edit { putLong(seenKey(user.uid), System.currentTimeMillis()) }
            } // failures (offline, rules) are never shown
        }
    }
}
