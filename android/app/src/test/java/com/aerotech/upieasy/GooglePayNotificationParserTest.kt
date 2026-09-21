package com.aerotech.upieasy

import com.aerotech.upieasy.feature.notifications.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class GooglePayNotificationParserTest {

    private lateinit var parser: GooglePayNotificationParser

    @Before
    fun setUp() {
        parser = GooglePayNotificationParser()
    }

    @Test
    fun testSupportsOnlyGooglePayPackage() {
        assertTrue(parser.supports("com.google.android.apps.nbu.paisa.user"))
        assertFalse(parser.supports("com.phonepe.app"))
    }

    @Test
    fun testParseIncomingPaymentReceived() {
        val raw = RawPaymentNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_1",
            notificationId = 200,
            postTime = 1726901000000L,
            title = "Google Pay",
            text = "You received ₹750 from Vikram Singh (vikram@okicici). UPI Ref No: 998877665544",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("750"), parsed.amount)
        assertEquals("vikram@okicici", parsed.payerVpa)
        assertEquals("998877665544", parsed.reference)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)
        assertEquals("Google Pay", parsed.sourceApp)
    }

    @Test
    fun testParseIncomingPaymentSentYou() {
        val raw = RawPaymentNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_2",
            notificationId = 201,
            postTime = 1726901001000L,
            title = "Google Pay",
            text = "Ananya sent you ₹1,500.00 for billing",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("1500.00"), parsed.amount)
        assertEquals("Ananya", parsed.payerName)
    }

    @Test
    fun testParseDebitSent() {
        val raw = RawPaymentNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_3",
            notificationId = 202,
            postTime = 1726901002000L,
            title = "Google Pay",
            text = "You sent ₹300 to Chai Point. Debited from A/c XX1234",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.SENT, parsed!!.direction)
        assertEquals(BigDecimal("300"), parsed.amount)
    }
}
