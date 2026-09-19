package com.aerotech.upieasy.core.util

import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class UpiPaymentDetails(
    val payeeVpa: String,
    val payeeName: String,
    val amount: Double? = null,
    val currency: String = "INR",
    val transactionNote: String? = null,
    val referenceId: String? = null,
    val merchantCategoryCode: String? = null
)

object UpiUriHelper {

    fun buildUri(details: UpiPaymentDetails): String {
        val uriBuilder = StringBuilder("upi://pay?")
        uriBuilder.append("pa=").append(encode(details.payeeVpa))
        uriBuilder.append("&pn=").append(encode(details.payeeName))
        uriBuilder.append("&cu=").append(encode(details.currency))

        details.amount?.let {
            uriBuilder.append("&am=").append(String.format("%.2f", it))
        }
        details.transactionNote?.let {
            uriBuilder.append("&tn=").append(encode(it))
        }
        details.referenceId?.let {
            uriBuilder.append("&tr=").append(encode(it))
        }
        details.merchantCategoryCode?.let {
            uriBuilder.append("&mc=").append(encode(it))
        }

        return uriBuilder.toString()
    }

    fun parseUri(uriString: String): UpiPaymentDetails? {
        if (!uriString.startsWith("upi://pay", ignoreCase = true)) {
            return null
        }

        return try {
            val queryIndex = uriString.indexOf('?')
            if (queryIndex == -1) return null
            val queryString = uriString.substring(queryIndex + 1)
            val params = mutableMapOf<String, String>()

            queryString.split('&').forEach { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.isNotEmpty()) {
                    val key = parts[0]
                    val value = if (parts.size > 1) {
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    } else ""
                    params[key] = value
                }
            }

            val pa = params["pa"] ?: return null
            val pn = params["pn"] ?: "Merchant"
            val amStr = params["am"]
            val amount = amStr?.toDoubleOrNull()
            val cu = params["cu"] ?: "INR"
            val tn = params["tn"]
            val tr = params["tr"]
            val mc = params["mc"]

            UpiPaymentDetails(
                payeeVpa = pa,
                payeeName = pn,
                amount = amount,
                currency = cu,
                transactionNote = tn,
                referenceId = tr,
                merchantCategoryCode = mc
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
