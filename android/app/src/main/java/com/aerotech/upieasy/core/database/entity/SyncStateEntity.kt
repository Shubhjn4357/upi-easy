package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_sync_state")
data class SyncStateEntity(
    @PrimaryKey val organizationId: String,
    val lastSequence: Long,
    val lastSyncedAt: Long
)
