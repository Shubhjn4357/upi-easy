package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val bankAccountId: String?,
    val upiAccountId: String?,
    val type: String,
    val direction: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val paymentMethod: String,
    val referenceNumber: String?,
    val payerName: String?,
    val payerVpa: String?,
    val payeeName: String,
    val payeeVpa: String,
    val note: String?,
    val occurredAt: Long,
    val syncStatus: String = "SYNCED", // LOCAL_ONLY, QUEUED, SYNCING, SYNCED
    val paymentAccountId: String? = null,
    val verificationStatus: String = "UNVERIFIED", // OBSERVED, VERIFIED, UNVERIFIED, CONFLICT
    val eventSource: String = "UPI_INTENT" // NOTIFICATION_PHONEPE, NOTIFICATION_GPAY, etc.
)
