package com.aerotech.upieasy.data.repository

import android.content.Context
import android.util.Log
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.OrganizationEntity
import com.aerotech.upieasy.core.database.entity.OrganizationInviteEntity
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.network.SendInviteRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale

class OrganizationRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val sessionManager: SessionManager
) {
    private val organizationDao = database.organizationDao()

    fun observeOrganizations(): Flow<List<OrganizationEntity>> =
        organizationDao.observeOrganizations()

    fun observePendingInvites(): Flow<List<OrganizationInviteEntity>> =
        organizationDao.observePendingInvites()

    suspend fun refreshOrganizations(): Result<List<OrganizationEntity>> {
        return try {
            val response = apiService.getProfile()
            if (response.isSuccessful && response.body()?.success == true) {
                val memberships = response.body()?.organizations ?: emptyList()
                val currentOrgId = sessionManager.getCurrentOrgId()

                val entities = memberships.map { dto ->
                    OrganizationEntity(
                        id = dto.organizationId,
                        name = dto.organizationName,
                        legalBusinessName = dto.legalBusinessName,
                        category = dto.category,
                        panNumber = dto.panNumber,
                        gstin = dto.gstin,
                        role = dto.roleName,
                        membershipStatus = dto.memberStatus,
                        isCurrent = dto.organizationId == currentOrgId
                    )
                }

                organizationDao.clearOrganizations()
                organizationDao.insertOrganizations(entities)

                // If currentOrgId is empty or not in memberships, auto-select first
                if (currentOrgId.isNullOrBlank() && entities.isNotEmpty()) {
                    switchOrganization(entities.first().id)
                }

                Result.success(entities)
            } else {
                Result.failure(Exception("Failed to fetch organizations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error refreshing organizations", e)
            Result.failure(e)
        }
    }

    suspend fun refreshInvitations(): Result<List<OrganizationInviteEntity>> {
        return try {
            val response = apiService.getMyInvitations()
            if (response.isSuccessful && response.body()?.success == true) {
                val dtoList = response.body()?.invitations
                    ?: response.body()?.invites
                    ?: emptyList()

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val entities = dtoList.map { dto ->
                    val expires = try {
                        dateFormat.parse(dto.expiresAt)?.time ?: (System.currentTimeMillis() + 7 * 24 * 3600 * 1000L)
                    } catch (_: Exception) {
                        System.currentTimeMillis() + 7 * 24 * 3600 * 1000L
                    }

                    OrganizationInviteEntity(
                        id = dto.id,
                        organizationId = dto.organizationId,
                        organizationName = dto.organizationName,
                        invitedBy = dto.inviterName,
                        inviterName = dto.inviterName,
                        inviterEmail = dto.inviterEmail,
                        invitedMobile = dto.invitedMobile ?: "",
                        invitedName = dto.invitedName,
                        role = dto.role,
                        status = dto.status,
                        expiresAt = expires
                    )
                }

                organizationDao.clearInvites()
                organizationDao.insertInvites(entities)
                Result.success(entities)
            } else {
                Result.failure(Exception("Failed to fetch invitations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error refreshing invitations", e)
            Result.failure(e)
        }
    }

    suspend fun switchOrganization(organizationId: String) {
        val org = organizationDao.getOrganizationById(organizationId) ?: return
        sessionManager.setOrganization(
            orgId = org.id,
            orgName = org.name,
            role = org.role,
            legalName = org.legalBusinessName,
            category = org.category,
            panNumber = org.panNumber,
            gstin = org.gstin
        )
        // Trigger background sync for newly active organization
        SyncScheduler.triggerImmediateSync(context, organizationId)
    }

    suspend fun acceptInvitation(inviteId: String): Result<String> {
        return try {
            val response = apiService.acceptInvitation(inviteId)
            if (response.isSuccessful && response.body()?.success == true) {
                organizationDao.updateInviteStatus(inviteId, "ACCEPTED")
                val orgDto = response.body()?.organization
                refreshOrganizations()
                if (orgDto != null) {
                    switchOrganization(orgDto.id)
                }
                Result.success(orgDto?.name ?: "Organization")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to accept invitation"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error accepting invitation", e)
            Result.failure(e)
        }
    }

    suspend fun rejectInvitation(inviteId: String): Result<Unit> {
        return try {
            val response = apiService.rejectInvitation(inviteId)
            if (response.isSuccessful) {
                organizationDao.updateInviteStatus(inviteId, "REJECTED")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to reject invitation: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error rejecting invitation", e)
            Result.failure(e)
        }
    }

    suspend fun cancelInvitation(inviteId: String): Result<Unit> {
        return try {
            val response = apiService.cancelInvitation(inviteId)
            if (response.isSuccessful) {
                organizationDao.updateInviteStatus(inviteId, "CANCELLED")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to cancel invitation: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error cancelling invitation", e)
            Result.failure(e)
        }
    }

    suspend fun sendInvitation(
        orgId: String,
        mobileNumber: String,
        name: String?,
        email: String?,
        role: String
    ): Result<String> {
        return try {
            val response = apiService.sendInvite(
                orgId = orgId,
                request = SendInviteRequest(
                    mobileNumber = mobileNumber,
                    name = name,
                    email = email,
                    role = role
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Invitation sent successfully")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to send invitation"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("OrganizationRepo", "Error sending invitation", e)
            Result.failure(e)
        }
    }
}
