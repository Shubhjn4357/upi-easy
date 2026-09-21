package com.aerotech.upieasy.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organization_invites")
data class OrganizationInviteEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val organizationName: String,
    val invitedBy: String? = null,
    val inviterName: String? = null,
    val inviterEmail: String? = null,
    val invitedMobile: String,
    val invitedName: String? = null,
    val role: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
