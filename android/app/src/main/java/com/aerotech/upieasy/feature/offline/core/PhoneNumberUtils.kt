package com.aerotech.upieasy.feature.offline.core

import android.util.Log

object PhoneNumberUtils {

    private const val TAG = "PhoneNumberUtils"

    fun normalizePhoneNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) {
            return null
        }
        return phoneNumber.replace(Regex("[^0-9]"), "")
    }

    fun normalize(phoneNumber: String?): String {
        return normalizePhoneNumber(phoneNumber)?.takeLast(10) ?: ""
    }

    fun formatPhoneForDisplay(phoneNumber: String?, lastDigits: Int = 4): String {
        if (phoneNumber.isNullOrBlank()) {
            return "••••••0000"
        }

        val normalized = normalizePhoneNumber(phoneNumber) ?: return "••••••0000"

        return if (normalized.length >= lastDigits) {
            val dots = "•".repeat(normalized.length - lastDigits)
            "$dots${normalized.takeLast(lastDigits)}"
        } else {
            phoneNumber
        }
    }
}
