package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
