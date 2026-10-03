package com.aerotech.upieasy

import com.aerotech.upieasy.feature.notifications.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class BhimNotificationParserTest {

    private lateinit var parser: BhimNotificationParser

    @Before
    fun setUp() {
        parser = BhimNotificationParser()
    }

    @Test
    fun testSupportsOnlyBhimPackage() {
        assertTrue(parser.supports("in.org.npci.upiapp"))
        assertFalse(parser.supports("com.phonepe.app"))
        assertFalse(parser.supports("com.google.android.apps.nbu.paisa.user"))
        assertFalse(parser.supports("net.one97.paytm"))
    }

    @Test
    fun testParseIncomingPaymentReceived() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_1",
            notificationId = 301,
            postTime = 1726902000000L,
            title = "BHIM",
            text = "Payment received of ₹450.00 from rahul.sharma@upi. UPI Ref No: 123456789012",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("450.00"), parsed.amount)
        assertEquals("rahul.sharma@upi", parsed.payerVpa)
        assertEquals("123456789012", parsed.reference)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)
        assertEquals("BHIM UPI", parsed.sourceApp)
    }

    @Test
    fun testParseCreditedFormat() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_2",
            notificationId = 302,
            postTime = 1726902001000L,
            title = "BHIM UPI",
            text = "Rs. 1,250 credited to your account from Priya Verma. Ref: 987654321098",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("1250"), parsed.amount)
        assertEquals("Priya Verma", parsed.payerName)
        assertEquals("987654321098", parsed.reference)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun testParseHindiTransliterationCredited() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_3",
            notificationId = 303,
            postTime = 1726902002000L,
            title = "BHIM",
            text = "खाते में जमा: ₹300.00 प्राप्त हुए। UTR 112233445566",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.RECEIVED, parsed!!.direction)
        assertEquals(BigDecimal("300.00"), parsed.amount)
        assertEquals("112233445566", parsed.reference)
    }

    @Test
    fun testParseDebitSent() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_4",
            notificationId = 304,
            postTime = 1726902003000L,
            title = "BHIM",
            text = "You paid ₹200 to Grocery Store. Debited from Account",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(PaymentDirection.SENT, parsed!!.direction)
        assertEquals(BigDecimal("200"), parsed.amount)
    }

    @Test
    fun testIgnorePromotionalNotification() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_promo",
            notificationId = 305,
            postTime = 1726902004000L,
            title = "BHIM Offer",
            text = "Refer friends and earn up to ₹100 cashback this festive season!",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }

    @Test
    fun testIgnoreBillPaymentReminder() {
        val raw = RawPaymentNotification(
            packageName = "in.org.npci.upiapp",
            notificationKey = "bhim_bill",
            notificationId = 306,
            postTime = 1726902005000L,
            title = "BHIM",
            text = "Electricity bill due on 25th Oct. Pay now to avoid penalty.",
            bigText = null
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }
}
