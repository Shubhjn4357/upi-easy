package com.aerotech.upieasy.feature.offline.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Standard Indian grouping for rupee currency representation: 3-2-2 grouping (e.g. ₹1,00,000.00).
 */
object CurrencyFormat {

    private const val LAST_GROUP = 3
    private const val HIGHER_GROUPS = 2
    private const val FRACTION_DIGITS = 2

    fun inr(amount: String): String {
        val value = amount.toBigDecimalOrNull() ?: return amount
        return format(value)
    }

    fun inr(amount: Double): String = format(BigDecimal.valueOf(amount))

    fun formatTimestamp(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(epochMs))
    }

    private fun format(value: BigDecimal): String {
        val rounded = value.setScale(FRACTION_DIGITS, RoundingMode.HALF_EVEN)
        val sign = if (rounded.signum() < 0) "-" else ""
        val (whole, fraction) = rounded.abs().toPlainString().split(".")
        return "$sign${groupIndian(whole)}.$fraction"
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= LAST_GROUP) return digits

        val groups = ArrayDeque<String>()
        groups.addFirst(digits.takeLast(LAST_GROUP))

        var rest = digits.dropLast(LAST_GROUP)
        while (rest.length > HIGHER_GROUPS) {
            groups.addFirst(rest.takeLast(HIGHER_GROUPS))
            rest = rest.dropLast(HIGHER_GROUPS)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)

        return groups.joinToString(",")
    }
}
