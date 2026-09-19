package com.aerotech.upieasy.core.database

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
    val syncStatus: String = "SYNCED" // LOCAL_ONLY, QUEUED, SYNCING, SYNCED
)

@Entity(tableName = "local_upi_accounts")
data class UpiAccountEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val vpa: String,
    val payeeName: String,
    val merchantCategoryCode: String,
    val isDefault: Boolean,
    val status: String,
    val transactionCount: Int
)

@Entity(tableName = "local_sync_state")
data class SyncStateEntity(
    @PrimaryKey val organizationId: String,
    val lastSequence: Long,
    val lastSyncedAt: Long
)
