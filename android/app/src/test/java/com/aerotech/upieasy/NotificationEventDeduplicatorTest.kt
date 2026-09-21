package com.aerotech.upieasy

import com.aerotech.upieasy.feature.notifications.NotificationEventDeduplicator
import org.junit.Assert.*
import org.junit.Test

class NotificationEventDeduplicatorTest {

    @Test
    fun testFingerprintConsistency() {
        val fp1 = NotificationEventDeduplicator.computeFingerprint(
            packageName = "com.phonepe.app",
            notificationKey = "key_101",
            title = "Payment Received",
            text = "₹500 received",
            postTime = 1726900010000L
        )

        val fp2 = NotificationEventDeduplicator.computeFingerprint(
            packageName = "com.phonepe.app",
            notificationKey = "key_101",
            title = "Payment Received",
            text = "₹500 received",
            postTime = 1726900015000L // 5 seconds later, same minute bucket
        )

        assertEquals(fp1, fp2)
        assertEquals(64, fp1.length) // SHA-256 hex string length
    }

    @Test
    fun testDeduplicatorDetection() {
        val deduplicator = NotificationEventDeduplicator()
        val fp = "some_unique_fingerprint_hash_12345678"

        assertFalse(deduplicator.isDuplicate(fp))
        assertTrue(deduplicator.isDuplicate(fp)) // Second call should be flagged as duplicate
    }
}
