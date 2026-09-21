package com.aerotech.upieasy.feature.notifications

interface PaymentNotificationParser {
    fun supports(packageName: String): Boolean
    fun parse(notification: RawPaymentNotification): ParsedPaymentEvent?
}
