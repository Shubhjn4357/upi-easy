package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_accounts")
data class PaymentAccountEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val label: String,
    val upiId: String,
    val paymentAppId: String,
    val paymentAppPackage: String,
    val status: String = "ACTIVE",
    val detectionEnabled: Boolean = true,
    val notificationAccessRequired: Boolean = true,
    val lastNotificationDetectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
