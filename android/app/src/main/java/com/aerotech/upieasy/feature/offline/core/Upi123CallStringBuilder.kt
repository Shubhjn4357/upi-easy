package com.aerotech.upieasy.feature.offline.core

/**
 * Builds the DTMF-encoded dial string for the UPI 123Pay IVR flow.
 *
 * Format: tel:<serviceNumber>,,1,<phoneNumber>,,<amount>,,1
 * where "," is a 2-second dialer pause and the digits between pauses are
 * DTMF tones consumed by the IVR menu (1 = "send money", then recipient,
 * then amount, then 1 = confirm).
 */
object Upi123CallStringBuilder {

    private val SERVICE_NUMBER_REGEX = Regex("^0?[1-9][0-9]{9,11}$")
    private val PHONE_REGEX = Regex("^[1-9][0-9]{9}$")
    private val WHOLE_RUPEES_REGEX = Regex("^[0-9]{1,6}$")

    const val DEFAULT_UPI_SERVICE_NUMBER = "08045163666" // NPCI standard 123Pay number
    const val MIN_AMOUNT_VALUE = 1.0
    const val UPI123PAY_MAX_AMOUNT = 4999.0 // NPCI IVR transaction limit

    enum class Reason {
        SERVICE_NUMBER,
        RECIPIENT_NUMBER,
        AMOUNT_NOT_WHOLE_RUPEES,
        AMOUNT_NOT_A_NUMBER,
        AMOUNT_BELOW_MINIMUM,
        AMOUNT_ABOVE_CAP,
    }

    sealed class Result {
        data class Valid(val callString: String) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    fun build(serviceNumber: String = DEFAULT_UPI_SERVICE_NUMBER, phoneNumber: String, amount: String): Result {
        val service = serviceNumber.filter(Char::isDigit)
        val phone = phoneNumber.filter(Char::isDigit)
        val rupees = amount.trim()

        if (!SERVICE_NUMBER_REGEX.matches(service)) {
            return Result.Invalid(Reason.SERVICE_NUMBER)
        }
        if (!PHONE_REGEX.matches(phone)) {
            return Result.Invalid(Reason.RECIPIENT_NUMBER)
        }
        if (!WHOLE_RUPEES_REGEX.matches(rupees)) {
            return Result.Invalid(Reason.AMOUNT_NOT_WHOLE_RUPEES)
        }
        val value = rupees.toLongOrNull()
            ?: return Result.Invalid(Reason.AMOUNT_NOT_A_NUMBER)
        if (value < MIN_AMOUNT_VALUE.toLong()) {
            return Result.Invalid(Reason.AMOUNT_BELOW_MINIMUM)
        }
        if (value > UPI123PAY_MAX_AMOUNT.toLong()) {
            return Result.Invalid(Reason.AMOUNT_ABOVE_CAP)
        }

        return Result.Valid("tel:$service,,1,$phone,,$value,,1")
    }

    fun messageFor(reason: Reason): String {
        return when (reason) {
            Reason.SERVICE_NUMBER -> "The UPI service number is not valid."
            Reason.RECIPIENT_NUMBER -> "Enter a valid 10-digit mobile number."
            Reason.AMOUNT_NOT_WHOLE_RUPEES -> "Enter a whole rupee amount — the IVR cannot dial paise."
            Reason.AMOUNT_NOT_A_NUMBER -> "Enter a valid numeric amount."
            Reason.AMOUNT_BELOW_MINIMUM -> "Minimum ₹1 per payment."
            Reason.AMOUNT_ABOVE_CAP -> "Maximum ₹4,999 per payment on UPI 123Pay IVR."
        }
    }
}
