package com.aerotech.upieasy.feature.offline.core

import com.aerotech.upieasy.feature.offline.model.SimpleTransaction
import com.aerotech.upieasy.feature.offline.model.TransactionStatus
import java.util.Locale

/**
 * Bank-SMS transaction detection for Indian banking SMS templates.
 * Covers SBI, HDFC, ICICI, Axis, Kotak, PNB, BOB, IDFC, Yes, Canara, Union, IndusInd, Federal, etc.
 * Pure and Context-free.
 */
object SmsTransactionParser {

    private const val NAME_STOP_WORDS =
        "has|have|had|was|were|is|are|will|could|did|does|failed|declined|unsuccessful|not|due"

    private const val NAME_TERMINATOR =
        "(?:\\s+(?:via|@|on|dated|for|from|to|UPI|Ref)\\b|\\s+(?:$NAME_STOP_WORDS)\\b|\\.|,|;|$)"

    private val RECIPIENT_PATTERNS = listOf(
        "(?:sent|paid|transferred)\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",
        "to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",
        "to\\s+(?:merchant|M/s\\.?|Mr\\.?|Mrs\\.?|Ms\\.?)\\s*([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for)\\b|\\s+(?:$NAME_STOP_WORDS)\\b|\\.|,|;|$)",
        "Payment\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:of|for)\\s+(?:Rs|INR|₹)",
        "([a-zA-Z][a-zA-Z\\s\\.]+?)\\s*[-–]\\s*(?:Rs|INR|₹)",
        "(?:Rs\\.?|INR|₹)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:sent|paid|transferred)\\s+to\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",
        "\\bto\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)$NAME_TERMINATOR",
        "to\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@",
        "to\\s+(\\d{10})(?:\\s|\\.|,|;|$)",
        "\\b([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)\\s+credited\\b"
    )

    private val SENDER_PATTERNS = listOf(
        "(?:received|credited)\\s+from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)(?:\\s+(?:via|@|on|for)|\\.|,|;|$)",
        "from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)\\s+(?:via|@)",
        "(?:Rs\\.?|INR|₹)\\s*[0-9,]+(?:\\.[0-9]{2})?\\s+(?:received|credited)\\s+from\\s+([a-zA-Z][a-zA-Z\\s\\.]+?)$NAME_TERMINATOR",
        "\\bfrom\\s+([a-zA-Z][a-zA-Z\\s\\.]{2,40}?)$NAME_TERMINATOR",
        "from\\s+([a-zA-Z][a-zA-Z0-9\\s]+?)@"
    )

    private val BANK_KEYWORDS = mapOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBI" to "State Bank of India",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "PNB" to "Punjab National Bank",
        "BOB" to "Bank of Baroda",
        "IDFC" to "IDFC First Bank",
        "YES" to "Yes Bank",
        "PAYTM" to "Paytm Payments Bank",
        "UNION" to "Union Bank",
        "CANARA" to "Canara Bank",
        "IndusInd" to "IndusInd Bank",
        "Federal" to "Federal Bank"
    )

    private val SUCCESS_INDICATORS = listOf(
        "successful", "successfully", "completed", "credited",
        "debited", "transferred", "sent to", "received from",
        "payment of", "paid to", "txn successful"
    )

    private val FAILURE_INDICATORS = listOf(
        "failed", "failure", "declined", "rejected", "unsuccessful",
        "not successful", "could not be processed", "cannot be processed",
        "not processed", "not completed", "insufficient", "reversed",
        "not debited", "txn expired", "timed out"
    )

    private val DEBIT_INDICATORS = listOf(
        "debited", "sent to", "paid to", "withdrawn", "spent", "transferred to"
    )

    private val AMOUNT_PATTERNS = listOf(
        "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "amount\\s*(?:of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        "([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)"
    )

    fun parse(
        sender: String,
        body: String,
        expectedAmount: String? = null,
        clock: () -> Long = System::currentTimeMillis,
        randomSuffix: () -> Int = { (1000..9999).random() }
    ): SimpleTransaction? {
        val bankName = detectBank(sender, body) ?: return null
        if (!isTransactionMessage(body)) return null
        val amount = extractAmount(body) ?: return null

        val transactionId = extractTransactionId(body, clock) ?: generateTransactionId(clock, randomSuffix)
        val upiId = extractUPIId(body)
        val transactionType = detectTransactionType(body)
        val (recipientName, phoneNumber) = extractRecipientInfo(body, transactionType)

        if (transactionType != "CREDIT") {
            if (!describesTransaction(body)) return null
            if (!expectedAmount.isNullOrEmpty() && !isAmountMatching(amount, expectedAmount)) {
                return null
            }
        }

        val status = if (detectsFailure(body)) TransactionStatus.FAILED.name else TransactionStatus.SUCCESS.name

        return SimpleTransaction(
            transactionId = transactionId,
            amount = amount,
            status = status,
            bankName = bankName,
            smsExcerpt = buildExcerpt(amount, transactionType, bankName, status),
            timestamp = clock(),
            upiId = upiId,
            transactionType = transactionType,
            recipientName = recipientName,
            phoneNumber = phoneNumber
        )
    }

    private val TRANSACTION_VERBS = listOf(
        "debited", "credited", "sent", "paid", "transferred",
        "withdrawn", "spent", "received", "deducted"
    )

    internal fun describesTransaction(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())
        return TRANSACTION_VERBS.any { bodyLower.contains(it) } || detectsFailure(body)
    }

    internal fun detectsFailure(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())
        return FAILURE_INDICATORS.any { bodyLower.contains(it) }
    }

    private const val PAISE_PER_RUPEE = 100

    internal fun isAmountMatching(extracted: String, expected: String): Boolean {
        val extractedNum = extracted.replace(",", "").toDoubleOrNull() ?: return false
        val expectedNum = expected.replace(",", "").toDoubleOrNull() ?: return false
        return Math.round(extractedNum * PAISE_PER_RUPEE) == Math.round(expectedNum * PAISE_PER_RUPEE)
    }

    private val AMBIGUOUS_BODY_PHRASES = mapOf(
        "YES" to Regex("\\bYES\\s+BANK\\b"),
        "BOB" to Regex("\\bBANK\\s+OF\\s+BARODA\\b|\\bBOB\\s+BANK\\b")
    )

    internal fun detectBank(sender: String, body: String): String? {
        val senderUpper = sender.uppercase(Locale.getDefault())
        val bodyUpper = body.uppercase(Locale.getDefault())

        for ((keyword, bankName) in BANK_KEYWORDS) {
            if (senderUpper.contains(keyword.uppercase())) {
                return bankName
            }
        }

        for ((keyword, bankName) in BANK_KEYWORDS) {
            val ambiguous = AMBIGUOUS_BODY_PHRASES[keyword.uppercase()]
            val matched = if (ambiguous != null) {
                ambiguous.containsMatchIn(bodyUpper)
            } else {
                Regex("\\b${Regex.escape(keyword.uppercase())}\\b").containsMatchIn(bodyUpper)
            }
            if (matched) {
                return bankName
            }
        }

        if (sender.matches(Regex("^[A-Z]{2}-[A-Z0-9]{6}(-[A-Z])?$")) ||
            sender.matches(Regex("^[A-Z]{6}$")) ||
            sender.matches(Regex("^[0-9]{6}$"))
        ) {
            return "Bank"
        }

        return null
    }

    internal fun claimKey(body: String): String {
        return body.trim().replace(Regex("\\s+"), " ").take(120)
    }

    internal fun isTransactionMessage(body: String): Boolean {
        val bodyLower = body.lowercase(Locale.getDefault())

        for (indicator in SUCCESS_INDICATORS) {
            if (bodyLower.contains(indicator)) {
                return true
            }
        }

        if (detectsFailure(body)) {
            return true
        }

        for (pattern in AMOUNT_PATTERNS) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) {
                return true
            }
        }

        return false
    }

    private val BALANCE_CONTEXT =
        Regex("(?:avl|available|avlbl|a/c|account)?\\s*bal(?:ance)?\\s*[:.]?\\s*$", RegexOption.IGNORE_CASE)
    private const val BALANCE_LOOKBEHIND_CHARS = 24

    internal fun extractAmount(body: String): String? {
        val candidates = AMOUNT_PATTERNS
            .flatMap { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).findAll(body).mapNotNull { match ->
                    val amount = match.groups[1]?.value?.replace(",", "")
                    if (amount.isNullOrEmpty()) null else match.range.first to amount
                }
            }
            .sortedBy { it.first }
        if (candidates.isEmpty()) return null

        val nonBalance = candidates.filterNot { (start, _) ->
            BALANCE_CONTEXT.containsMatchIn(body.take(start).takeLast(BALANCE_LOOKBEHIND_CHARS))
        }
        return (nonBalance.ifEmpty { candidates }).first().second
    }

    internal fun extractTransactionId(body: String, clock: () -> Long): String? {
        val patterns = listOf(
            "\\b(?:ref|txn|transaction|id)\\b\\s*(?:no|number|id)?\\s*[:.#]?\\s*([A-Z0-9]+)",
            "([A-Z0-9]{10,})"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)

            if (match != null && match.groups.size > 1) {
                val baseId = match.groups[1]?.value
                if (!baseId.isNullOrEmpty()) {
                    return "${baseId}_${clock()}"
                }
            }
        }

        return null
    }

    internal fun extractUPIId(body: String): String? {
        val patterns = listOf(
            "(?:UPI:|from|to|UPI ID:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)",
            "(?:VPA:?)\\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(body)
            if (match != null && match.groups.size > 1) {
                return match.groups[1]?.value
            }
        }
        return null
    }

    internal fun detectTransactionType(body: String): String {
        val bodyLower = body.lowercase(Locale.getDefault())
        return when {
            DEBIT_INDICATORS.any { bodyLower.contains(it) } -> "DEBIT"
            bodyLower.contains("credited") ||
                bodyLower.contains("received") ||
                bodyLower.contains("added") -> "CREDIT"
            else -> "DEBIT"
        }
    }

    internal fun generateTransactionId(clock: () -> Long, randomSuffix: () -> Int): String =
        "TXN${clock()}${randomSuffix()}"

    internal fun extractRecipientInfo(body: String, transactionType: String): Pair<String?, String?> {
        var recipientName: String? = null
        var phoneNumber: String? = null

        val patterns = if (transactionType == "CREDIT") SENDER_PATTERNS else RECIPIENT_PATTERNS

        for (pattern in patterns) {
            val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val match = regex.find(body) ?: continue
            val extracted = match.groups[1]?.value?.trim()
            if (extracted.isNullOrEmpty()) continue

            if (extracted.matches(Regex("\\d{10}"))) {
                phoneNumber = extracted
                continue
            }

            val cleanedName = cleanupName(extracted)
            if (!cleanedName.isNullOrEmpty()) {
                recipientName = cleanedName
                break
            }
        }

        if (recipientName.isNullOrEmpty()) {
            val upiName = extractNameFromUPI(body)
            if (!upiName.isNullOrEmpty()) {
                recipientName = upiName
            }
        }

        return Pair(recipientName, phoneNumber)
    }

    internal fun cleanupName(name: String): String? {
        var cleaned = name
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\-–]$"), "")
            .replace(Regex("\\.$"), "")
            .trim()

        val prefixesToRemove = listOf("M/s", "Mr", "Mrs", "Ms", "Dr", "merchant", "Merchant")
        for (prefix in prefixesToRemove) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                if (cleaned.startsWith(".")) {
                    cleaned = cleaned.substring(1).trim()
                }
            }
        }

        if (cleaned.length < 2 || cleaned.length > 50) {
            return null
        }

        if (!cleaned.contains(Regex("[a-zA-Z]"))) {
            return null
        }

        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    internal fun extractNameFromUPI(body: String): String? {
        val upiPattern = Regex("([a-zA-Z][a-zA-Z0-9._-]+)@[a-zA-Z0-9]+", RegexOption.IGNORE_CASE)
        val upiPrefix = upiPattern.find(body)?.groups?.get(1)?.value ?: return null

        return upiPrefix
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    internal fun buildExcerpt(
        amount: String,
        transactionType: String,
        bankName: String,
        status: String
    ): String {
        val verb = when {
            status == TransactionStatus.FAILED.name -> "payment failed"
            transactionType == "CREDIT" -> "credited"
            else -> "debited"
        }
        return buildString {
            append("₹").append(amount).append(" ").append(verb)
            if (bankName.isNotBlank()) append(" — ").append(bankName)
        }
    }
}
