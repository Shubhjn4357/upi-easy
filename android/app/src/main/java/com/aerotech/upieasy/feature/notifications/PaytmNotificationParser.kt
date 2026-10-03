package com.aerotech.upieasy.feature.notifications

import java.math.BigDecimal
import java.util.regex.Pattern

class PaytmNotificationParser : PaymentNotificationParser {

    companion object {
        const val PACKAGE_NAME = "net.one97.paytm"

        private val NON_PAYMENT_KEYWORDS = NotificationFilterConstants.COMMON_NON_PAYMENT_KEYWORDS + listOf(
            // Paytm-specific products & loyalty tokens
            "paytm postpaid", "postpaid", "cashback points", "first points"
        )

        private val CREDIT_PATTERN = Pattern.compile(
            "(?:(?:money\\s+)?received\\s*(?:in|to)?\\s*(?:Paytm\\s+Payments\\s+Bank|bank\\s+account|wallet)?\\s*(?:of\\s*)?(?:Rs\\.?|INR|₹)|(?:Rs\\.?|INR|₹)\\s*[0-9,.]+\\s*(?:received|credited)|credited\\s*(?:with|by)?\\s*(?:Rs\\.?|INR|₹)|paid\\s+(?:to\\s+you|you)\\s*(?:Rs\\.?|INR|₹)|you\\s+(?:have\\s+)?received\\s*(?:Rs\\.?|INR|₹)|payment\\s+received\\s+from)",
            Pattern.CASE_INSENSITIVE
        )

        private val DEBIT_PATTERN = Pattern.compile(
            "(?:you\\s+(?:paid|sent)\\s*(?:Rs\\.?|INR|₹)|paid\\s+(?:Rs\\.?|INR|₹)|(?:Rs\\.?|INR|₹)\\s*[0-9,.]+\\s*(?:debited|paid|sent)|payment\\s+of\\s*(?:Rs\\.?|INR|₹)\\s*[0-9,.]+\\s*(?:to|successful))",
            Pattern.CASE_INSENSITIVE
        )

        private val AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )

        private val RRN_PATTERN = Pattern.compile(
            "(?:UTR|RRN|Ref|UPI Ref(?: No)?|UPI transaction ID|Ref No|Order ID|Txn ID)?[:\\s#]*([0-9]{12})",
            Pattern.CASE_INSENSITIVE
        )

        private val VPA_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+)",
            Pattern.CASE_INSENSITIVE
        )

        private val FROM_NAME_PATTERN = Pattern.compile(
            "(?:from|by|received from)\\s+([A-Za-z0-9\\s]{2,40}?)(?:\\s+(?:via|using|for|on)|[.]|$)",
            Pattern.CASE_INSENSITIVE
        )

        private val SENT_YOU_NAME_PATTERN = Pattern.compile(
            "^([A-Za-z0-9\\s]{2,30}?)\\s+sent you",
            Pattern.CASE_INSENSITIVE
        )

        private val TO_NAME_PATTERN = Pattern.compile(
            "(?:to|towards)\\s+([A-Za-z0-9\\s]{2,40}?)(?:\\s+(?:via|using|for|on)|[.]|$)",
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

        // 1. Strict filter: Discard any promotional/marketing/offer/bill notifications immediately
        val isNonPayment = NON_PAYMENT_KEYWORDS.any { keyword ->
            combined.contains(keyword, ignoreCase = true)
        }
        if (isNonPayment) {
            return null
        }

        // 2. Strict positive payment direction matching using contextual regex patterns
        val isCredit = CREDIT_PATTERN.matcher(combined).find()
        val isDebit = DEBIT_PATTERN.matcher(combined).find()

        val direction = when {
            isCredit && !isDebit -> PaymentDirection.RECEIVED
            isDebit && !isCredit -> PaymentDirection.SENT
            else -> return null // Strictly reject unknown, ambiguous, or non-matching notifications
        }

        // 3. Extract amount
        val amountMatcher = AMOUNT_PATTERN.matcher(combined)
        val amount = if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1)?.replace(",", "")
            try {
                amountStr?.let { BigDecimal(it) }?.takeIf { it > BigDecimal.ZERO }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        if (amount == null) {
            return null // Reject notifications without a valid transaction amount
        }

        // 4. Extract reference (12-digit UTR/RRN)
        val rrnMatcher = RRN_PATTERN.matcher(combined)
        val reference = if (rrnMatcher.find()) rrnMatcher.group(1) else null

        // 5. Extract VPA
        val vpaMatcher = VPA_PATTERN.matcher(combined)
        val payerVpa = if (vpaMatcher.find()) vpaMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':') else null

        // 6. Extract party name
        val fromMatcher = FROM_NAME_PATTERN.matcher(combined)
        val sentYouTextMatcher = SENT_YOU_NAME_PATTERN.matcher(text.trim())
        val sentYouBigTextMatcher = if (bigText.isNotBlank()) SENT_YOU_NAME_PATTERN.matcher(bigText.trim()) else null
        val toMatcher = TO_NAME_PATTERN.matcher(combined)

        val partyName = when {
            direction == PaymentDirection.RECEIVED && sentYouTextMatcher.find() -> sentYouTextMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.RECEIVED && sentYouBigTextMatcher != null && sentYouBigTextMatcher.find() -> sentYouBigTextMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.RECEIVED && fromMatcher.find() -> fromMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.SENT && toMatcher.find() -> toMatcher.group(1)?.trim()?.take(50)
            title.isNotBlank() && !title.contains("Paytm", ignoreCase = true) && !title.contains("Payment", ignoreCase = true) -> title.trim().take(50)
            else -> null
        }

        val confidence = if (reference != null) ParseConfidence.HIGH else ParseConfidence.MEDIUM

        return ParsedPaymentEvent(
            sourcePackage = PACKAGE_NAME,
            sourceApp = "Paytm",
            direction = direction,
            amount = amount,
            payerName = partyName,
            payerVpa = payerVpa,
            reference = reference,
            rawTitle = notification.title,
            rawText = notification.text ?: notification.bigText,
            observedAt = notification.postTime,
            confidence = confidence
        )
    }
}
