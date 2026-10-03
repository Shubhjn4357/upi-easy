package com.aerotech.upieasy

import com.aerotech.upieasy.feature.notifications.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PaytmNotificationParserTest {

    private lateinit var parser: PaytmNotificationParser

    @Before
    fun setUp() {
        parser = PaytmNotificationParser()
    }

    @Test
    fun testSupportsOnlyPaytmPackage() {
        assertTrue(parser.supports("net.one97.paytm"))
        assertFalse(parser.supports("com.phonepe.app"))
        assertFalse(parser.supports("com.google.android.apps.nbu.paisa.user"))
        assertFalse(parser.supports("in.org.npci.upiapp"))
    }

    @Test
    fun testParseIncomingPaymentReceived() {
        val raw = RawPaymentNotification(
            packageName = "net.one97.paytm",
            notificationKey = "paytm_1",
            notificationId = 401,
            postTime = 1726903000000L,
            title = "Paytm",
            text = "Received ₹850 in Paytm Payments Bank from Rohan (rohan@paytm). UPI Ref: 445566778899",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("850"), parsed.amount)
        assertEquals("rohan@paytm", parsed.payerVpa)
        assertEquals("445566778899", parsed.reference)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)
        assertEquals("Paytm", parsed.sourceApp)
    }

    @Test
    fun testParseYouHaveReceivedFormat() {
        val raw = RawPaymentNotification(
            packageName = "net.one97.paytm",
            notificationKey = "paytm_2",
            notificationId = 402,
            postTime = 1726903001000L,
            title = "Paytm",
            text = "You have received ₹2,400 from Sneha Gupta for order billing",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("2400"), parsed.amount)
        assertEquals("Sneha Gupta", parsed.payerName)
        assertEquals(ParseConfidence.MEDIUM, parsed.confidence)
    }

    @Test
    fun testParseDebitSent() {
        val raw = RawPaymentNotification(
            packageName = "net.one97.paytm",
            notificationKey = "paytm_3",
            notificationId = 403,
            postTime = 1726903002000L,
            title = "Paytm",
            text = "You paid ₹150 to Cafe Coffee Day. Debited from Wallet",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.SENT, parsed!!.direction)
        assertEquals(BigDecimal("150"), parsed.amount)
    }

    @Test
    fun testIgnorePromotionalScratchCard() {
        val raw = RawPaymentNotification(
            packageName = "net.one97.paytm",
            notificationKey = "paytm_promo",
            notificationId = 404,
            postTime = 1726903003000L,
            title = "Cashback Won!",
            text = "You've won a scratch card! Win up to ₹500 flat cashback on recharge",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }

    @Test
    fun testIgnoreLoanAndPostpaidNotification() {
        val raw = RawPaymentNotification(
            packageName = "net.one97.paytm",
            notificationKey = "paytm_loan",
            notificationId = 405,
            postTime = 1726903004000L,
            title = "Paytm Postpaid",
            text = "Pre-approved personal loan of ₹50,000 available. Check credit score now.",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }
}
