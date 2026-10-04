package com.aerotech.upieasy.feature.offline.core

import android.net.Uri
import android.util.Log
import com.aerotech.upieasy.feature.offline.model.UPIData

/**
 * Strict offline UPI QR code parser according to NPCI specifications.
 */
object QRCodeParser {

    private const val TAG = "QRCodeParser"
    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}$")
    private const val MAX_QR_AMOUNT = 100_000.0

    enum class Reason {
        EMPTY,
        NOT_A_UPI_QR,
        MALFORMED,
        NO_PAYEE_ADDRESS,
        INVALID_PAYEE_ADDRESS,
        INVALID_AMOUNT,
    }

    sealed class ParseResult {
        data class Valid(val data: UPIData) : ParseResult()
        data class Invalid(val reason: Reason) : ParseResult()
    }

    fun parse(qrCode: String): ParseResult {
        val raw = qrCode.trim()
        if (raw.isEmpty()) return ParseResult.Invalid(Reason.EMPTY)

        return when {
            raw.startsWith("upi://", ignoreCase = true) -> parseUpiUri(raw)
            VPA_REGEX.matches(raw) -> ParseResult.Valid(
                UPIData(payeeAddress = raw, payeeName = null, amount = null)
            )
            else -> ParseResult.Invalid(Reason.NOT_A_UPI_QR)
        }
    }

    private fun parseUpiUri(raw: String): ParseResult {
        val queryPart = if (raw.contains("?")) raw.substringAfter("?") else ""
        if (queryPart.isEmpty()) return ParseResult.Invalid(Reason.MALFORMED)

        val params = mutableMapOf<String, String>()
        for (pair in queryPart.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = try {
                    java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1)
                }
                params[key] = value
            }
        }

        val vpa = params["pa"]?.trim().orEmpty()
        if (vpa.isEmpty()) return ParseResult.Invalid(Reason.NO_PAYEE_ADDRESS)
        if (!VPA_REGEX.matches(vpa)) {
            Log.w(TAG, "Rejected structurally invalid VPA in QR")
            return ParseResult.Invalid(Reason.INVALID_PAYEE_ADDRESS)
        }

        val amountParam = params["am"]?.trim().orEmpty()
        var parsedAmount: Double? = null
        if (amountParam.isNotEmpty()) {
            val amount = amountParam.toDoubleOrNull()
            if (amount == null || amount <= 0 || amount > MAX_QR_AMOUNT) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
            if (!amountParam.matches(Regex("^[0-9]+(\\.[0-9]{1,2})?$"))) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
            parsedAmount = amount
        }

        val payeeName = params["pn"]
            ?.trim()
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.take(99)
        val note = params["tn"]?.trim()?.take(99)
        val tr = params["tr"]?.trim()
        val mc = params["mc"]?.trim()

        return ParseResult.Valid(
            UPIData(
                payeeAddress = vpa,
                payeeName = payeeName,
                amount = parsedAmount,
                transactionNote = note,
                transactionRef = tr,
                merchantCode = mc
            )
        )
    }

    fun isValidUPIQRCode(qrCode: String): Boolean = parse(qrCode) is ParseResult.Valid

    fun reasonMessage(reason: Reason): String = when (reason) {
        Reason.EMPTY -> "QR code is empty."
        Reason.NOT_A_UPI_QR -> "Not a valid UPI payment QR."
        Reason.MALFORMED -> "Malformed QR data format."
        Reason.NO_PAYEE_ADDRESS -> "No payee UPI ID found in QR code."
        Reason.INVALID_PAYEE_ADDRESS -> "Invalid UPI ID in QR code."
        Reason.INVALID_AMOUNT -> "Invalid payment amount specified in QR."
    }
}
