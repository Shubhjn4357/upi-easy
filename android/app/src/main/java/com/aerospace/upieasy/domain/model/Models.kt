package com.aerospace.upieasy.domain.model

data class Transaction(
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
    val occurredAt: Long
)

data class UpiAccount(
    val id: String,
    val vpa: String,
    val payeeName: String,
    val merchantCategoryCode: String,
    val isDefault: Boolean,
    val status: String,
    val transactionCount: Int,
    val defaultQrPayload: String? = null
)

data class BankAccount(
    val id: String,
    val bankName: String,
    val accountHolderName: String,
    val accountNumberMasked: String,
    val ifscCode: String,
    val accountType: String,
    val status: String
)

data class Organization(
    val id: String,
    val name: String,
    val legalBusinessName: String?,
    val category: String?,
    val role: String,
    val status: String
)

data class StaffMember(
    val id: String,
    val userId: String,
    val fullName: String?,
    val mobileNumber: String,
    val email: String?,
    val role: String,
    val status: String
)

data class DashboardStats(
    val todayReceivedAmount: Double,
    val todayReceivedCount: Int,
    val todaySentAmount: Double,
    val todaySentCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val activeUpiCount: Int,
    val recentTransactions: List<Transaction>
)
