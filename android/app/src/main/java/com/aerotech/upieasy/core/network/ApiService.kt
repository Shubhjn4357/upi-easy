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
    val legalBusinessName: String?,
    val category: String?,
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
    val legalBusinessName: String?,
    val category: String?,
    val role: String,
    val status: String
)

data class CreateOrgRequest(val name: String, val category: String = "RETAIL")
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
    val merchantCategoryCode: String,
    val isDefault: Boolean,
    val status: String,
    val transactionCount: Int
)

data class AddUpiRequest(
    val vpa: String,
    val payeeName: String,
    val isDefault: Boolean = false
)

data class StaffListResponse(
    val success: Boolean,
    val staff: List<StaffMemberDto>
)
data class StaffMemberDto(
    val id: String,
    val userId: String,
    val fullName: String?,
    val mobileNumber: String,
    val email: String?,
    val role: String,
    val status: String
)

data class InviteStaffRequest(
    val mobileNumber: String,
    val fullName: String? = null,
    val role: String
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
    val mobileNumber: String,
    val primaryVpa: String,
    val payeeName: String,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val category: String = "RETAIL"
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

    @GET("api/v1/organizations/{orgId}/staff")
    suspend fun getStaff(@Path("orgId") orgId: String): Response<StaffListResponse>

    @POST("api/v1/organizations/{orgId}/staff/invite")
    suspend fun inviteStaff(
        @Path("orgId") orgId: String,
        @Body request: InviteStaffRequest
    ): Response<ApiResponse<Any>>

    @GET("api/v1/organizations/{orgId}/sync")
    suspend fun getSyncEvents(
        @Path("orgId") orgId: String,
        @Query("afterSequence") afterSequence: Long,
        @Query("limit") limit: Int = 100
    ): Response<SyncResponse>
}
