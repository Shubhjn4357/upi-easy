package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observed_payment_events",
    indices = [
        Index(
            value = ["organizationId", "eventFingerprint"],
            unique = true
        )
    ]
)
data class ObservedPaymentEventEntity(
    @PrimaryKey
    val id: String,
    val organizationId: String?,
    val paymentAccountId: String?,
    val qrId: String?,
    val sourcePackage: String,
    val sourceApp: String,
    val direction: String,
    val amountMinor: Long?,
    val currency: String = "INR",
    val payerName: String?,
    val payerVpa: String?,
    val reference: String?,
    val notificationTitle: String?,
    val notificationText: String?,
    val eventFingerprint: String,
    val matchStatus: String, // MATCHED, UNMATCHED, AMBIGUOUS
    val verificationStatus: String = "OBSERVED",
    val observedAt: Long,
    val syncState: String = "PENDING_UPLOAD" // PENDING_UPLOAD, SYNCED, FAILED, REJECTED
)
