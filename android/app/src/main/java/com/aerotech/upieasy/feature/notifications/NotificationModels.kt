package com.aerotech.upieasy.feature.notifications

import java.math.BigDecimal

enum class PaymentDirection {
    RECEIVED,
    SENT,
    UNKNOWN
}

enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class PaymentMatchStatus {
    MATCHED,
    UNMATCHED,
    AMBIGUOUS
}

data class RawPaymentNotification(
    val packageName: String,
    val notificationKey: String,
    val notificationId: Int,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String? = null,
    val category: String? = null
)

data class ParsedPaymentEvent(
    val sourcePackage: String,
    val sourceApp: String,
    val direction: PaymentDirection,
    val amount: BigDecimal?,
    val payerName: String?,
    val payerVpa: String?,
    val reference: String?, // UTR / RRN (12 digits)
    val rawTitle: String?,
    val rawText: String?,
    val observedAt: Long,
    val confidence: ParseConfidence
)
