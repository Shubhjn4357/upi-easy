package com.aerotech.upieasy.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aerotech.upieasy.core.database.entity.OrganizationEntity
import com.aerotech.upieasy.core.database.entity.OrganizationInviteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE membershipStatus = 'ACTIVE' ORDER BY name ASC")
    fun observeOrganizations(): Flow<List<OrganizationEntity>>

    @Query("SELECT * FROM organizations WHERE membershipStatus = 'ACTIVE' ORDER BY name ASC")
    suspend fun getOrganizations(): List<OrganizationEntity>

    @Query("SELECT * FROM organizations WHERE id = :orgId LIMIT 1")
    suspend fun getOrganizationById(orgId: String): OrganizationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizations(organizations: List<OrganizationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganization(organization: OrganizationEntity)

    @Query("DELETE FROM organizations WHERE id = :orgId")
    suspend fun deleteOrganization(orgId: String)

    @Query("DELETE FROM organizations")
    suspend fun clearOrganizations()

    // Invitations
    @Query("SELECT * FROM organization_invites WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePendingInvites(): Flow<List<OrganizationInviteEntity>>

    @Query("SELECT * FROM organization_invites ORDER BY createdAt DESC")
    suspend fun getAllInvites(): List<OrganizationInviteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvites(invites: List<OrganizationInviteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvite(invite: OrganizationInviteEntity)

    @Query("UPDATE organization_invites SET status = :status WHERE id = :inviteId")
    suspend fun updateInviteStatus(inviteId: String, status: String)

    @Query("DELETE FROM organization_invites WHERE id = :inviteId")
    suspend fun deleteInvite(inviteId: String)

    @Query("DELETE FROM organization_invites")
    suspend fun clearInvites()
}
