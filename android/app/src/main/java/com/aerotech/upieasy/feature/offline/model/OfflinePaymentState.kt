package com.aerotech.upieasy.feature.offline.model

import java.util.UUID

enum class TimeoutType {
    CALL,
    VERIFICATION,
    NETWORK
}

/**
 * Type-safe payment state machine representing offline UPI (123Pay & USSD) payment lifecycles.
 */
sealed class OfflinePaymentState {
    object Idle : OfflinePaymentState()

    data class Initiating(
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String = UUID.randomUUID().toString()
    ) : OfflinePaymentState()

    data class InProgress(
        val step: String,
        val progress: Float,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String
    ) : OfflinePaymentState()

    data class WaitingForVerification(
        val timeout: Long,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String
    ) : OfflinePaymentState()

    data class Success(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val bankReference: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : OfflinePaymentState()

    data class Failed(
        val error: String,
        val errorCode: String? = null,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String,
        val canRetry: Boolean = true
    ) : OfflinePaymentState()

    data class NeedsReview(
        val transactionId: String,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = ""
    ) : OfflinePaymentState()

    data class Cancelled(
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String,
        val reason: String = "User cancelled"
    ) : OfflinePaymentState()

    data class Timeout(
        val timeoutType: TimeoutType,
        val phoneNumber: String,
        val amount: String,
        val payeeName: String = "",
        val transactionId: String
    ) : OfflinePaymentState()

    fun isTerminal(): Boolean = this is Success || this is Failed || this is Cancelled || this is NeedsReview
    fun isInProgress(): Boolean = this is Initiating || this is InProgress || this is WaitingForVerification

    fun canRetry(): Boolean = when (this) {
        is Failed -> canRetry
        is Timeout -> true
        else -> false
    }

    fun getTransactionIdValue(): String? = when (this) {
        is Initiating -> this.transactionId
        is InProgress -> this.transactionId
        is WaitingForVerification -> this.transactionId
        is Success -> this.transactionId
        is Failed -> this.transactionId
        is NeedsReview -> this.transactionId
        is Cancelled -> this.transactionId
        is Timeout -> this.transactionId
        is Idle -> null
    }
}
