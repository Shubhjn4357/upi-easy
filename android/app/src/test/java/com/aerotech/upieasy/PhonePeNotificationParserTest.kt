package com.aerotech.upieasy

import com.aerotech.upieasy.feature.notifications.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PhonePeNotificationParserTest {

    private lateinit var parser: PhonePeNotificationParser

    @Before
    fun setUp() {
        parser = PhonePeNotificationParser()
    }

    @Test
    fun testSupportsOnlyPhonePePackage() {
        assertTrue(parser.supports("com.phonepe.app"))
        assertFalse(parser.supports("com.google.android.apps.nbu.paisa.user"))
        assertFalse(parser.supports("com.whatsapp"))
    }

    @Test
    fun testParseIncomingPaymentSuccess() {
        val raw = RawPaymentNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key_1",
            notificationId = 100,
            postTime = 1726900000000L,
            title = "Payment Received",
            text = "₹500 received from Rahul Verma via UPI Ref 982774308512",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("500"), parsed.amount)
        assertEquals("982774308512", parsed.reference)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)
        assertEquals("PhonePe", parsed.sourceApp)
    }

    @Test
    fun testParsePaymentWithDecimalsAndVpa() {
        val raw = RawPaymentNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key_2",
            notificationId = 101,
            postTime = 1726900001000L,
            title = "PhonePe",
            text = "Rs. 1,250.75 credited from priya@oksbi with UTR: 123456789012",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("1250.75"), parsed.amount)
        assertEquals("priya@oksbi", parsed.payerVpa)
        assertEquals("123456789012", parsed.reference)
    }

    @Test
    fun testParseDebitOrPaidTo() {
        val raw = RawPaymentNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key_3",
            notificationId = 102,
            postTime = 1726900002000L,
            title = "Payment Sent",
            text = "You paid ₹250 to Fresh Mart. Debited from your bank account.",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.SENT, parsed!!.direction)
        assertEquals(BigDecimal("250"), parsed.amount)
    }

    @Test
    fun testUnrelatedNotificationGivesLowConfidence() {
        val raw = RawPaymentNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key_4",
            notificationId = 103,
            postTime = 1726900003000L,
            title = "PhonePe Offers",
            text = "Get up to 50% cashback on your next recharge today!",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }
}
