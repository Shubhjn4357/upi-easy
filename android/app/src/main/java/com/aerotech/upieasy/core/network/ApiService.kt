package com.aerotech.upieasy.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

data class UserProfileResponse(
    val success: Boolean,
    val user: UserDto,
    val organizations: List<OrganizationMembershipDto>? = null
)

data class OrganizationMembershipDto(
    val organizationId: String,
    val organizationName: String,
    val legalBusinessName: String? = null,
    val category: String? = null,
    val panNumber: String? = null,
    val gstin: String? = null,
    val roleName: String,
    val memberStatus: String
)

data class UpdateProfileRequest(
    val fullName: String? = null,
    val email: String? = null
)

data class UserDto(
    val id: String,
    val mobileNumber: String?,
    val fullName: String?,
    val email: String? = null,
    val avatarUrl: String? = null,
    val status: String? = null
)
data class TokensDto(val accessToken: String, val refreshToken: String)

data class OrganizationsResponse(
    val success: Boolean,
    val organizations: List<OrganizationDto>
)
data class OrganizationDto(
    val id: String,
    val name: String,
    val legalBusinessName: String? = null,
    val category: String? = null,
    val panNumber: String? = null,
    val gstin: String? = null,
    val role: String = "OWNER",
    val status: String = "ACTIVE"
)

data class CreateOrgRequest(
    val name: String,
    val legalBusinessName: String? = null,
    val category: String = "RETAIL",
    val panNumber: String? = null,
    val gstin: String? = null
)
data class CreateOrgResponse(val success: Boolean, val organization: OrganizationDto)

data class DashboardResponse(
    val success: Boolean,
    val dashboard: DashboardDto
)
data class DashboardDto(
    val todayReceived: StatDto,
    val todaySent: StatDto,
    val pendingCount: Int,
    val failedCount: Int,
    val activeUpiCount: Int,
    val recentTransactions: List<TransactionDto>
)
data class StatDto(val amount: Double, val count: Int)

data class TransactionDto(
    val id: String,
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
    val occurredAt: String
)

data class TransactionsListResponse(
    val success: Boolean,
    val data: List<TransactionDto>
)

data class CreateTransactionRequest(
    val upiAccountId: String? = null,
    val direction: String = "RECEIVED",
    val amount: Double,
    val payeeName: String,
    val payeeVpa: String,
    val note: String? = null,
    val referenceNumber: String? = null
)
data class CreateTransactionResponse(
    val success: Boolean,
    val transaction: TransactionDto
)

data class UpiListResponse(
    val success: Boolean,
    val upiAccounts: List<UpiAccountDto>
)
data class UpiAccountDto(
    val id: String,
    val vpa: String,
    val payeeName: String,
    val merchantCategoryCode: String = "5411",
    val isDefault: Boolean = false,
    val status: String = "ACTIVE",
    val transactionCount: Int = 0,
    val qrPayload: String? = null
)

// Settlement Bank Accounts DTOs
data class BankAccountDto(
    val id: String,
    val organizationId: String,
    val bankName: String,
    val accountHolderName: String,
    val accountNumberMasked: String,
    val ifscCode: String,
    val accountType: String = "CURRENT",
    val isDefault: Boolean = false,
    val status: String = "ACTIVE"
)

data class BankAccountsResponse(
    val success: Boolean,
    val accounts: List<BankAccountDto>
)

data class AddBankAccountRequest(
    val bankName: String,
    val accountHolderName: String,
    val accountNumber: String,
    val ifscCode: String,
    val accountType: String = "CURRENT",
    val isDefault: Boolean = false
)

// Roles and Permissions (RBAC) DTOs
data class PermissionDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String
)

data class RoleDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val isSystem: Boolean = false,
    val permissions: List<String> = emptyList()
)

data class RolesPermissionsResponse(
    val success: Boolean,
    val roles: List<RoleDto>,
    val permissions: List<PermissionDto>
)

data class UpdateRolePermissionsRequest(
    val permissionIds: List<String>
)

data class AddUpiRequest(
    val vpa: String,
    val payeeName: String,
    val isDefault: Boolean = false
)

data class UpdateUpiRequest(
    val vpa: String? = null,
    val payeeName: String? = null,
    val merchantCategoryCode: String? = null,
    val isDefault: Boolean? = null
)

data class StaffListResponse(
    val success: Boolean,
    val staff: List<StaffMemberDto>
)
data class StaffMemberDto(
    val id: String,
    val userId: String,
    val fullName: String?,
    val mobileNumber: String?,
    val email: String?,
    val role: String,
    val status: String
)

data class InviteStaffRequest(
    val mobileNumber: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val role: String
)

data class UpdateOrganizationRequest(
    val name: String? = null,
    val legalBusinessName: String? = null,
    val category: String? = null,
    val panNumber: String? = null,
    val gstin: String? = null
)

data class SyncResponse(
    val success: Boolean,
    val data: SyncDataDto
)
data class SyncDataDto(
    val events: List<SyncEventDto>,
    val latestSequence: Long,
    val hasMore: Boolean
)
data class SyncEventDto(
    val sequence: Long,
    val id: String,
    val eventType: String,
    val payload: Any,
    val createdAt: String
)

// Invitations & Multi-Firm DTOs
data class InvitationDto(
    val id: String,
    val organizationId: String,
    val organizationName: String,
    val role: String,
    val status: String,
    val invitedMobile: String? = null,
    val invitedName: String? = null,
    val invitedEmail: String? = null,
    val inviterName: String? = null,
    val inviterEmail: String? = null,
    val expiresAt: String,
    val createdAt: String? = null
)

data class InvitationsListResponse(
    val success: Boolean,
    val invitations: List<InvitationDto>? = null,
    val invites: List<InvitationDto>? = null
)

data class SendInviteRequest(
    val mobileNumber: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String
)

data class SendInviteResponse(
    val success: Boolean,
    val message: String? = null,
    val invite: InvitationDto? = null
)

data class AcceptInviteResponse(
    val success: Boolean,
    val message: String? = null,
    val organization: OrganizationDto? = null
)

// Device Registration DTOs
data class RegisterDeviceRequest(
    val deviceId: String,
    val platform: String = "ANDROID",
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val fcmToken: String? = null
)

// Multi-Organization Incremental Sync DTOs
data class MultiOrgSyncRequest(
    val organizations: List<OrgSyncCursorRequestDto>
)

data class OrgSyncCursorRequestDto(
    val organizationId: String,
    val cursor: Long = 0L
)

data class MultiOrgSyncResponse(
    val success: Boolean,
    val organizations: List<OrgSyncResultDto>
)

data class OrgSyncResultDto(
    val organizationId: String,
    val nextCursor: Long,
    val hasMore: Boolean,
    val changes: List<SyncChangeDto>
)

data class SyncChangeDto(
    val sequence: Long,
    val type: String,
    val entityId: String? = null,
    val payload: Any? = null,
    val createdAt: String? = null
)

data class GoogleLoginRequest(
    val idToken: String,
    val nonce: String? = null,
    val deviceId: String = "android-device",
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val fcmToken: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null
)

data class GoogleLoginResponse(
    val success: Boolean,
    val user: UserDto,
    val tokens: TokensDto,
    val isSetupComplete: Boolean,
    val defaultOrg: OrganizationDto?
)

data class AppSetupRequest(
    val businessName: String,
    val legalBusinessName: String? = null,
    val mobileNumber: String,
    val primaryVpa: String,
    val payeeName: String,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val category: String = "RETAIL",
    val panNumber: String? = null,
    val gstin: String? = null
)

data class AppSetupResponse(
    val success: Boolean,
    val message: String,
    val organization: OrganizationDto,
    val upiAccount: UpiAccountDto
)

interface ApiService {
    @POST("api/v1/auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<GoogleLoginResponse>

    @POST("api/v1/organizations/setup")
    suspend fun completeSetup(@Body request: AppSetupRequest): Response<AppSetupResponse>

    @GET("api/v1/users/me")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PATCH("api/v1/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<Any>>

    @DELETE("api/v1/users/me")
    suspend fun deleteAccount(): Response<ApiResponse<Any>>

    @GET("api/v1/organizations")
    suspend fun getOrganizations(): Response<OrganizationsResponse>

    @POST("api/v1/organizations")
    suspend fun createOrganization(@Body request: CreateOrgRequest): Response<CreateOrgResponse>

    @GET("api/v1/organizations/{orgId}/dashboard")
    suspend fun getDashboard(@Path("orgId") orgId: String): Response<DashboardResponse>

    @PATCH("api/v1/organizations/{orgId}")
    suspend fun updateOrganization(
        @Path("orgId") orgId: String,
        @Body request: UpdateOrganizationRequest
    ): Response<ApiResponse<Any>>

    @GET("api/v1/organizations/{orgId}/transactions")
    suspend fun getTransactions(
        @Path("orgId") orgId: String,
        @Query("status") status: String? = null,
        @Query("direction") direction: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<TransactionsListResponse>

    @POST("api/v1/organizations/{orgId}/transactions")
    suspend fun createTransaction(
        @Path("orgId") orgId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateTransactionRequest
    ): Response<CreateTransactionResponse>

    @GET("api/v1/organizations/{orgId}/upi")
    suspend fun getUpiAccounts(@Path("orgId") orgId: String): Response<UpiListResponse>

    @POST("api/v1/organizations/{orgId}/upi")
    suspend fun addUpiAccount(
        @Path("orgId") orgId: String,
        @Body request: AddUpiRequest
    ): Response<ApiResponse<Any>>

    @PATCH("api/v1/organizations/{orgId}/upi/{upiId}/default")
    suspend fun setDefaultUpi(
        @Path("orgId") orgId: String,
        @Path("upiId") upiId: String
    ): Response<ApiResponse<Any>>

    @DELETE("api/v1/organizations/{orgId}/upi/{upiId}")
    suspend fun deleteUpiAccount(
        @Path("orgId") orgId: String,
        @Path("upiId") upiId: String
    ): Response<ApiResponse<Any>>

    @PUT("api/v1/organizations/{orgId}/upi/{upiId}")
    suspend fun updateUpiAccount(
        @Path("orgId") orgId: String,
        @Path("upiId") upiId: String,
        @Body request: UpdateUpiRequest
    ): Response<ApiResponse<Any>>

    @GET("api/v1/organizations/{orgId}/staff")
    suspend fun getStaff(@Path("orgId") orgId: String): Response<StaffListResponse>

    @POST("api/v1/organizations/{orgId}/staff/invite")
    suspend fun inviteStaff(
        @Path("orgId") orgId: String,
        @Body request: InviteStaffRequest
    ): Response<ApiResponse<Any>>

    @DELETE("api/v1/organizations/{orgId}/staff/{memberId}")
    suspend fun deleteStaff(
        @Path("orgId") orgId: String,
        @Path("memberId") memberId: String
    ): Response<ApiResponse<Any>>

    @GET("api/v1/organizations/{orgId}/sync")
    suspend fun getSyncEvents(
        @Path("orgId") orgId: String,
        @Query("afterSequence") afterSequence: Long,
        @Query("limit") limit: Int = 100
    ): Response<SyncResponse>

    // Multi-Firm Invitations
    @GET("api/v1/me/invitations")
    suspend fun getMyInvitations(): Response<InvitationsListResponse>

    @GET("api/v1/organizations/{orgId}/invites")
    suspend fun getOrganizationInvites(@Path("orgId") orgId: String): Response<InvitationsListResponse>

    @POST("api/v1/organizations/{orgId}/invites")
    suspend fun sendInvite(
        @Path("orgId") orgId: String,
        @Body request: SendInviteRequest
    ): Response<SendInviteResponse>

    @POST("api/v1/invitations/{inviteId}/accept")
    suspend fun acceptInvitation(@Path("inviteId") inviteId: String): Response<AcceptInviteResponse>

    @POST("api/v1/invitations/{inviteId}/reject")
    suspend fun rejectInvitation(@Path("inviteId") inviteId: String): Response<ApiResponse<Any>>

    @POST("api/v1/invitations/{inviteId}/cancel")
    suspend fun cancelInvitation(@Path("inviteId") inviteId: String): Response<ApiResponse<Any>>

    // Multi-Device Registration
    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<ApiResponse<Any>>

    @POST("api/v1/devices/unregister")
    suspend fun unregisterDevice(@Body request: Map<String, String>): Response<ApiResponse<Any>>

    // Multi-Organization Incremental Sync
    @POST("api/v1/sync")
    suspend fun multiOrgSync(@Body request: MultiOrgSyncRequest): Response<MultiOrgSyncResponse>

    // Multi-Payment Accounts & Observed Payment Events
    @GET("api/v1/organizations/{orgId}/payment-accounts")
    suspend fun getPaymentAccounts(@Path("orgId") orgId: String): Response<PaymentAccountsResponse>

    @POST("api/v1/organizations/{orgId}/payment-accounts")
    suspend fun createPaymentAccount(
        @Path("orgId") orgId: String,
        @Body request: CreatePaymentAccountRequest
    ): Response<CreatePaymentAccountResponse>

    @PATCH("api/v1/organizations/{orgId}/payment-accounts/{id}")
    suspend fun updatePaymentAccount(
        @Path("orgId") orgId: String,
        @Path("id") id: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<ApiResponse<PaymentAccountDto>>

    @DELETE("api/v1/organizations/{orgId}/payment-accounts/{id}")
    suspend fun deletePaymentAccount(
        @Path("orgId") orgId: String,
        @Path("id") id: String
    ): Response<ApiResponse<Any>>

    @POST("api/v1/organizations/{orgId}/payment-events/observed")
    suspend fun postObservedPaymentEvent(
        @Path("orgId") orgId: String,
        @Body request: PostObservedPaymentEventRequest
    ): Response<ObservedPaymentEventResponse>

    // Settlement Bank Accounts
    @GET("api/v1/organizations/{orgId}/accounts")
    suspend fun getBankAccounts(@Path("orgId") orgId: String): Response<BankAccountsResponse>

    @POST("api/v1/organizations/{orgId}/accounts")
    suspend fun addBankAccount(
        @Path("orgId") orgId: String,
        @Body request: AddBankAccountRequest
    ): Response<ApiResponse<Any>>

    @PATCH("api/v1/organizations/{orgId}/accounts/{accountId}/default")
    suspend fun setDefaultBankAccount(
        @Path("orgId") orgId: String,
        @Path("accountId") accountId: String
    ): Response<ApiResponse<Any>>

    @DELETE("api/v1/organizations/{orgId}/accounts/{accountId}")
    suspend fun deleteBankAccount(
        @Path("orgId") orgId: String,
        @Path("accountId") accountId: String
    ): Response<ApiResponse<Any>>

    // Roles and Permissions (RBAC)
    @GET("api/v1/organizations/{orgId}/roles")
    suspend fun getRolesAndPermissions(@Path("orgId") orgId: String): Response<RolesPermissionsResponse>

    @PATCH("api/v1/organizations/{orgId}/roles/{roleId}/permissions")
    suspend fun updateRolePermissions(
        @Path("orgId") orgId: String,
        @Path("roleId") roleId: String,
        @Body request: UpdateRolePermissionsRequest
    ): Response<ApiResponse<Any>>
}

// Data Transfer Objects for Payment Accounts & Observed Events
data class PaymentAccountDto(
    val id: String,
    val organizationId: String,
    val label: String,
    val upiId: String,
    val paymentAppId: String,
    val paymentAppPackage: String,
    val status: String,
    val detectionEnabled: Boolean,
    val notificationAccessRequired: Boolean,
    val lastNotificationDetectedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class PaymentAccountsResponse(
    val success: Boolean,
    val data: List<PaymentAccountDto>
)

data class CreatePaymentAccountRequest(
    val label: String,
    val upiId: String,
    val paymentAppId: String,
    val paymentAppPackage: String,
    val detectionEnabled: Boolean = true,
    val notificationAccessRequired: Boolean = true
)

data class CreatePaymentAccountResponse(
    val success: Boolean,
    val data: PaymentAccountDto
)

data class PostObservedPaymentEventRequest(
    val clientEventId: String,
    val source: ObservedSourceDto,
    val paymentAccountId: String?,
    val qrId: String?,
    val amountMinor: Long?,
    val currency: String = "INR",
    val direction: String = "RECEIVED",
    val payerName: String?,
    val payerVpa: String?,
    val reference: String?,
    val observedAt: String,
    val verificationStatus: String = "OBSERVED",
    val matchStatus: String = "MATCHED",
    val fingerprint: String
)

data class ObservedSourceDto(
    val type: String,
    val packageName: String
)

data class ObservedPaymentEventResponse(
    val accepted: Boolean,
    val eventId: String,
    val transactionId: String?,
    val status: String,
    val duplicate: Boolean? = null
)

