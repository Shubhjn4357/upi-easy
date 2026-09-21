package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organizations")
data class OrganizationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val legalBusinessName: String? = null,
    val category: String? = null,
    val panNumber: String? = null,
    val gstin: String? = null,
    val role: String, // OWNER, MANAGER, CASHIER, ACCOUNTANT
    val membershipStatus: String = "ACTIVE", // ACTIVE, SUSPENDED
    val isCurrent: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
