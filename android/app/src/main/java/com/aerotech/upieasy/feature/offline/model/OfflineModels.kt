package com.aerotech.upieasy.feature.offline.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING,
    CANCELLED,
    UNVERIFIED
}

@Parcelize
data class SimpleTransaction(
    val transactionId: String,
    val amount: String,
    val status: String,
    val bankName: String,
    /** Privacy-safe summary built from extracted fields — never the raw SMS body. */
    val smsExcerpt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upiId: String? = null,
    val transactionType: String = "DEBIT",
    val recipientName: String? = null,
    val phoneNumber: String? = null
) : Parcelable

@Parcelize
data class UPIData(
    val payeeAddress: String, // VPA (e.g. merchant@upi)
    val payeeName: String? = null,
    val amount: Double? = null,
    val transactionNote: String? = null,
    val transactionRef: String? = null,
    val merchantCode: String? = null
) : Parcelable
