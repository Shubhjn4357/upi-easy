package com.aerotech.upieasy.feature.notifications

import java.math.BigDecimal
import java.util.regex.Pattern

class PhonePeNotificationParser : PaymentNotificationParser {

    companion object {
        const val PACKAGE_NAME = "com.phonepe.app"

        private val AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )

        private val RRN_PATTERN = Pattern.compile(
            "(?:UTR|RRN|Ref|UPI Ref(?: No)?|Reference No)?[:\\s#]*([0-9]{12})",
            Pattern.CASE_INSENSITIVE
        )

        private val VPA_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+)",
            Pattern.CASE_INSENSITIVE
        )

        private val FROM_NAME_PATTERN = Pattern.compile(
            "(?:from|by)\\s+([A-Za-z0-9\\s]{2,40})(?:\\s+via|\\s+using|\\s+for|\\s+on|\\.|$)",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun supports(packageName: String): Boolean {
        return packageName == PACKAGE_NAME
    }

    override fun parse(notification: RawPaymentNotification): ParsedPaymentEvent? {
        if (!supports(notification.packageName)) return null

        val title = notification.title ?: ""
        val text = notification.text ?: ""
        val bigText = notification.bigText ?: ""
        val combined = "$title $text $bigText".trim()

        if (combined.isBlank()) return null

        // Determine direction
        val isCreditKeyword = combined.contains("received", ignoreCase = true) ||
                combined.contains("credited", ignoreCase = true) ||
                combined.contains("paid to you", ignoreCase = true) ||
                combined.contains("money received", ignoreCase = true)

        val isDebitKeyword = combined.contains("paid to", ignoreCase = true) ||
                combined.contains("debited", ignoreCase = true) ||
                combined.contains("payment sent", ignoreCase = true)

        val direction = when {
            isCreditKeyword && !isDebitKeyword -> PaymentDirection.RECEIVED
            isDebitKeyword && !isCreditKeyword -> PaymentDirection.SENT
            else -> PaymentDirection.UNKNOWN
        }

        // Extract amount using BigDecimal
        val amountMatcher = AMOUNT_PATTERN.matcher(combined)
        val amount = if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1)?.replace(",", "")
            try {
                amountStr?.let { BigDecimal(it) }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        // If no amount is detected or direction is UNKNOWN, cannot classify as high confidence
        val confidence = when {
            amount != null && direction == PaymentDirection.RECEIVED -> ParseConfidence.HIGH
            amount != null && direction != PaymentDirection.UNKNOWN -> ParseConfidence.MEDIUM
            else -> ParseConfidence.LOW
        }

        // Extract reference (12-digit UTR/RRN)
        val rrnMatcher = RRN_PATTERN.matcher(combined)
        val reference = if (rrnMatcher.find()) rrnMatcher.group(1) else null

        // Extract VPA if present
        val vpaMatcher = VPA_PATTERN.matcher(combined)
        val payerVpa = if (vpaMatcher.find()) vpaMatcher.group(1) else null

        // Extract payer name
        val nameMatcher = FROM_NAME_PATTERN.matcher(combined)
        val payerName = if (nameMatcher.find()) {
            nameMatcher.group(1)?.trim()?.take(50)
        } else if (title.isNotBlank() && !title.contains("PhonePe", ignoreCase = true) && !title.contains("Payment", ignoreCase = true)) {
            title.trim().take(50)
        } else {
            null
        }

        return ParsedPaymentEvent(
            sourcePackage = PACKAGE_NAME,
            sourceApp = "PhonePe",
            direction = direction,
            amount = amount,
            payerName = payerName,
            payerVpa = payerVpa,
            reference = reference,
            rawTitle = notification.title,
            rawText = notification.text ?: notification.bigText,
            observedAt = notification.postTime,
            confidence = confidence
        )
    }
}
