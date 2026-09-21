package com.aerotech.upieasy.feature.notifications

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class NotificationEventDeduplicator {

    // Cache of recent fingerprints to avoid duplicate work within a short window
    private val recentFingerprints = ConcurrentHashMap<String, Long>()

    companion object {
        private const val EXPIRATION_MS = 10 * 60 * 1000L // 10 minutes

        fun computeFingerprint(
            packageName: String,
            notificationKey: String,
            title: String?,
            text: String?,
            postTime: Long
        ): String {
            // Bucket postTime into 1-minute windows to tolerate small timing variations on reposts
            val timeBucket = postTime / 60000L
            val raw = "$packageName|$notificationKey|${title.orEmpty()}|${text.orEmpty()}|$timeBucket"

            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun isDuplicate(fingerprint: String): Boolean {
        cleanExpired()
        val now = System.currentTimeMillis()
        val lastSeen = recentFingerprints[fingerprint]
        if (lastSeen != null && (now - lastSeen) < EXPIRATION_MS) {
            return true
        }
        recentFingerprints[fingerprint] = now
        return false
    }

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        recentFingerprints.entries.removeIf { now - it.value > EXPIRATION_MS }
    }
}
