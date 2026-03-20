package com.sky.wallapp

import java.util.UUID

/**
 * Keeps a short-lived in-memory list so detail screen can swipe within the same feed.
 */
object WallpaperSwipeSession {

    data class Session(
        val items: List<Model>,
        val source: String
    )

    private const val MAX_SESSIONS = 6
    private val sessions = LinkedHashMap<String, Session>()

    fun createSession(items: List<Model>, source: String): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = Session(items = items, source = source)
        trimOldSessions()
        return sessionId
    }

    fun getSession(sessionId: String?): Session? {
        if (sessionId.isNullOrBlank()) return null
        return sessions[sessionId]
    }

    private fun trimOldSessions() {
        while (sessions.size > MAX_SESSIONS) {
            val oldestKey = sessions.entries.firstOrNull()?.key ?: return
            sessions.remove(oldestKey)
        }
    }
}

