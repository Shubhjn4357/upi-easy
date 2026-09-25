package com.aerotech.upieasy.feature.notifications

import java.math.BigDecimal
import java.util.regex.Pattern

class GooglePayNotificationParser : PaymentNotificationParser {

    companion object {
        const val PACKAGE_NAME = "com.google.android.apps.nbu.paisa.user"

        private val NON_PAYMENT_KEYWORDS = listOf(
            // Offers & Promotions
            "offer", "offers", "deal", "deals", "discount", "discounts", "sale", "special",
            "cashback", "scratch card", "scratchcard", "reward", "rewards", "coupon", "coupons",
            "voucher", "vouchers", "voucher code", "promo", "promotional", "exclusive",
            "win up to", "win upto", "won up to", "won upto", "get up to", "get upto",
            "flat rs", "flat ₹", "flat inr", "save up to", "save upto", "save rs", "save ₹",
            "bumper", "jackpot", "festive", "explore", "claim your", "claim now",
            "refer", "referral", "invite friends", "invite your", "spin & win", "spin and win",
            "spin to win", "spin the wheel", "contest", "play now", "earn up to", "earn upto",
            "points", "point", "coins", "coin", "supercoin", "supercoins", "token", "tokens",
            "gift card", "gift voucher", "congratulations",

            // Financial Products & Upselling
            "loan", "pre-approved", "preapproved", "personal loan", "business loan",
            "insurance", "policy", "premium", "mutual fund", "mutual funds", "sip", "gold",
            "digital gold", "credit card", "credit score", "cibil score", "cibil",

            // Bills, Recharges & Reminders (Not completed payments!)
            "recharge offer", "bill offer", "recharge now", "bill due", "bill generated",
            "due on", "due date", "upcoming bill", "bill payment due", "payment due",
            "electricity bill", "water bill", "gas bill", "broadband", "dth",
            "payment request", "requested money", "requested rs", "requested ₹",
            "has requested", "requested you", "remind", "reminder", "pay request",
            "autopay scheduled", "autopay due", "mandate created", "mandate approved",
            "e-mandate", "standing instruction",

            // Commerce & Delivery Status
            "order placed", "order confirmed", "order delivered", "swiggy", "zomato",
            "flipkart", "amazon", "myntra", "blinkit", "zepto", "instamart", "uber", "ola",

            // System, Security & General Notifications
            "kyc", "update kyc", "kyc pending", "rate us", "feedback", "survey",
            "security alert", "login alert", "otp", "verification code", "update available",
            "new feature", "don't miss", "hurry up", "limited time", "limited period",
            "failed", "declined", "rejected", "reversed", "refunded", "cancelled", "canceled"
        )

        private val CREDIT_PATTERN = Pattern.compile(
            "(?:(?:sent|paid)\\s+you\\s*(?:Rs\\.?|INR|₹)|(?:money\\s+)?received\\s*(?:of\\s*)?(?:Rs\\.?|INR|₹)|(?:Rs\\.?|INR|₹)\\s*[0-9,.]+\\s*(?:credited|received))",
            Pattern.CASE_INSENSITIVE
        )

        private val DEBIT_PATTERN = Pattern.compile(
            "(?:you\\s+(?:sent|paid)\\s*(?:Rs\\.?|INR|₹)|paid\\s+(?:Rs\\.?|INR|₹)|(?:Rs\\.?|INR|₹)\\s*[0-9,.]+\\s*(?:debited|sent))",
            Pattern.CASE_INSENSITIVE
        )

        private val AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )

        private val RRN_PATTERN = Pattern.compile(
            "(?:UTR|RRN|Ref|UPI Ref(?: No)?|UPI transaction ID)?[:\\s#]*([0-9]{12})",
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

        private val SENT_YOU_NAME_PATTERN = Pattern.compile(
            "^([A-Za-z0-9\\s]{2,30})\\s+sent you",
            Pattern.CASE_INSENSITIVE
        )

        private val TO_NAME_PATTERN = Pattern.compile(
            "(?:to|towards)\\s+([A-Za-z0-9\\s]{2,40})(?:\\s+via|\\s+using|\\s+for|\\s+on|\\.|$)",
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

        // Extract amount
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

        // Extract reference (12-digit UTR/RRN)
        val rrnMatcher = RRN_PATTERN.matcher(combined)
        val reference = if (rrnMatcher.find()) rrnMatcher.group(1) else null

        // Extract VPA
        val vpaMatcher = VPA_PATTERN.matcher(combined)
        val payerVpa = if (vpaMatcher.find()) vpaMatcher.group(1) else null

        // Extract party name
        val fromMatcher = FROM_NAME_PATTERN.matcher(combined)
        val sentYouTextMatcher = SENT_YOU_NAME_PATTERN.matcher(text.trim())
        val sentYouBigTextMatcher = if (bigText.isNotBlank()) SENT_YOU_NAME_PATTERN.matcher(bigText.trim()) else null
        val toMatcher = TO_NAME_PATTERN.matcher(combined)

        val partyName = when {
            direction == PaymentDirection.RECEIVED && sentYouTextMatcher.find() -> sentYouTextMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.RECEIVED && sentYouBigTextMatcher != null && sentYouBigTextMatcher.find() -> sentYouBigTextMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.RECEIVED && fromMatcher.find() -> fromMatcher.group(1)?.trim()?.take(50)
            direction == PaymentDirection.SENT && toMatcher.find() -> toMatcher.group(1)?.trim()?.take(50)
            title.isNotBlank() && !title.contains("Google Pay", ignoreCase = true) && !title.contains("Payment", ignoreCase = true) -> title.trim().take(50)
            else -> null
        }

        return ParsedPaymentEvent(
            sourcePackage = PACKAGE_NAME,
            sourceApp = "Google Pay",
            direction = direction,
            amount = amount,
            payerName = partyName,
            payerVpa = payerVpa,
            reference = reference,
            rawTitle = notification.title,
            rawText = notification.text ?: notification.bigText,
            observedAt = notification.postTime,
            confidence = ParseConfidence.HIGH
        )
    }
}
